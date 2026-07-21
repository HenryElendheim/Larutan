package world.larutan.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import world.larutan.app.ui.model.BeingDot
import world.larutan.app.ui.model.DriveBar
import world.larutan.app.ui.model.FollowedBeing
import world.larutan.app.ui.model.GoalView
import world.larutan.app.ui.model.GodAction
import world.larutan.app.ui.model.MapCell
import world.larutan.app.ui.model.MapView
import world.larutan.app.ui.model.MomentView
import world.larutan.app.ui.model.RelationView
import world.larutan.app.ui.model.RosterEntry
import world.larutan.app.ui.model.RosterFilter
import world.larutan.app.ui.model.Settings
import world.larutan.app.ui.model.Speed
import world.larutan.app.ui.model.TimelineMomentView
import world.larutan.app.ui.model.UiState
import world.larutan.app.ui.model.WorldInfo
import world.larutan.engine.being.Being
import world.larutan.engine.being.DriveType
import world.larutan.engine.being.Sentiment
import world.larutan.engine.being.SkillType
import world.larutan.engine.event.WorldEvent
import world.larutan.engine.god.FateBoon
import world.larutan.engine.god.FateTrigger
import world.larutan.engine.god.God
import world.larutan.engine.sim.Persistence
import world.larutan.engine.sim.Simulation
import world.larutan.engine.sim.Timeline
import world.larutan.engine.sim.WorldConfig
import world.larutan.engine.sim.WorldGen
import world.larutan.engine.world.Season
import world.larutan.engine.world.Terrain
import world.larutan.engine.world.World
import java.io.File

/**
 * Holds the living world and turns it, and hands Compose an immutable snapshot to
 * draw after every tick. The engine does the thinking; this just paces it and
 * translates state into something the UI can render.
 */
class SimulationViewModel(app: Application) : AndroidViewModel(app) {

    private var sim: Simulation
    private var god: God
    private var loop: Job? = null
    private var active = true // false while the app is backgrounded -> the world waits for you

    // A rolling history of the world, so time can be rolled back (§10.5). Session-only.
    private val timeline = Timeline()

    // A short stack of snapshots taken just before each god act, so they can be undone,
    // and a matching stack of undone states, so they can be put back.
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()

    // Significant-moment surfacing (§10.4): how many significant events we've already
    // shown, and the latest one still up on the banner.
    private var seenSignificant = 0
    private var currentMoment: WorldEvent? = null

    // The world survives being closed: continuity is the point.
    private val saveFile = File(app.filesDir, "world.json")

    // Player preferences live in their own small store, apart from the world.
    private val prefs = app.getSharedPreferences("larutan_settings", android.content.Context.MODE_PRIVATE)
    private var settings = loadSettings()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var speed: Speed = Speed.PAUSED
    private var followedId: Int = 0
    private var rosterFilter: RosterFilter = RosterFilter.LIVING
    private var realmFilter: String? = null // null shows every realm; a label narrows to one

    init {
        sim = loadOrCreate()
        god = God(sim)
        timeline.record(sim) // the moment you arrive is the first you can return to
        resetMomentTracking()
        publish()
    }

    private fun loadOrCreate(): Simulation {
        if (saveFile.exists()) {
            try {
                return Persistence.restore(Persistence.deserialize(saveFile.readText()))
            } catch (e: Exception) {
                Log.w("Larutan", "Could not load saved world, starting fresh", e)
            }
        }
        val config = WorldConfig(width = 32, height = 32, startingPopulation = 5)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    /** Start a brand new world, discarding the current one. */
    fun newWorld() {
        speed = Speed.PAUSED
        loop?.cancel()
        val config = WorldConfig(width = 32, height = 32, startingPopulation = 5)
        val (world, beings) = WorldGen.create(config)
        sim = Simulation(config, world, beings)
        god = God(sim)
        followedId = 0
        timeline.clear()
        timeline.record(sim)
        resetMomentTracking()
        save()
        publish()
    }

    fun setSpeed(newSpeed: Speed) {
        speed = newSpeed
        restartLoop()
        publish()
    }

    fun follow(id: Int) {
        followedId = id
        publish()
    }

    /** Switch the roster between the living and the dead. */
    fun setRosterFilter(filter: RosterFilter) {
        rosterFilter = filter
        if (filter == RosterFilter.LIVING) realmFilter = null
        publish()
    }

    /** Among the dead, narrow to one realm (or pass null for all of them). */
    fun setRealmFilter(realm: String?) {
        realmFilter = realm
        publish()
    }

    /** Advance exactly one hour, for close observation. */
    fun stepOnce() {
        if (sim.living().isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.Default) { sim.step() }
            timeline.maybeRecord(sim)
            surfaceMoments()
            publish()
        }
    }

    /** Reverse time: roll the whole world back to an earlier moment, then hold there (§10.5). */
    fun rewindTo(tick: Long) {
        val json = timeline.rewindTo(tick) ?: return
        speed = Speed.PAUSED
        loop?.cancel()
        sim = Persistence.restore(Persistence.deserialize(json))
        god = God(sim)
        resetMomentTracking() // the restored past shouldn't re-announce itself
        save()
        publish()
    }

    /** Tap a surfaced moment: follow whoever it happened to, and clear the banner. */
    fun openMoment() {
        currentMoment?.beingId?.let { followedId = it }
        currentMoment = null
        publish()
    }

    /** Wave a surfaced moment away without following it. */
    fun dismissMoment() {
        currentMoment = null
        publish()
    }

    private fun resetMomentTracking() {
        seenSignificant = sim.chronicle.entries.count { it.significant }
        currentMoment = null
    }

    /**
     * Surface anything meaningful that just happened (§10.4). New significant events
     * go up on the banner; a payoff beat -- a birth, death, or a goal reached -- during
     * a fast run also pulls time back to a watchable pace so you don't blur past it.
     */
    private fun surfaceMoments() {
        val sig = sim.chronicle.entries.filter { it.significant }
        when {
            sig.size > seenSignificant -> {
                val fresh = sig.subList(seenSignificant, sig.size)
                seenSignificant = sig.size
                // Only put a moment on the banner if the player wants them.
                if (settings.showMomentBanners) currentMoment = fresh.last()
                // Ease off a fast run for a payoff beat -- unless they've chosen to blitz.
                if (settings.slowForMoments &&
                    fresh.any { it.kind.isPayoff } && (speed == Speed.WATCH || speed == Speed.DRIFT)
                ) {
                    speed = Speed.REFLECT // the loop reads speed each turn, so it eases on its own
                }
            }
            sig.size < seenSignificant -> seenSignificant = sig.size // chronicle trimmed; re-sync
        }
    }

    // ---- settings -----------------------------------------------------------

    /** Apply a new set of preferences and remember them. */
    fun updateSettings(new: Settings) {
        settings = new
        prefs.edit()
            .putBoolean("slowForMoments", new.slowForMoments)
            .putBoolean("showMomentBanners", new.showMomentBanners)
            .putBoolean("largerText", new.largerText)
            .apply()
        publish()
    }

    private fun loadSettings(): Settings {
        val d = Settings()
        return Settings(
            slowForMoments = prefs.getBoolean("slowForMoments", d.slowForMoments),
            showMomentBanners = prefs.getBoolean("showMomentBanners", d.showMomentBanners),
            largerText = prefs.getBoolean("largerText", d.largerText),
        )
    }

    /** Send the soul you're following back into a new life, and follow the newborn (§10.7). */
    fun reincarnateFollowed() {
        snapshotForUndo()
        val newId = god.reincarnate(followedId) ?: return
        followedId = newId
        rosterFilter = RosterFilter.LIVING // the story is with the living again
        realmFilter = null
        timeline.maybeRecord(sim)
        save()
        publish()
    }

    /** Reach in and touch the being you're following. */
    fun invoke(action: GodAction) {
        snapshotForUndo()
        val id = followedId
        when (action) {
            GodAction.PROVIDE -> god.provide(id)
            GodAction.WARM -> god.warm(id)
            GodAction.BLESS -> god.bless(id)
            GodAction.INSPIRE -> god.inspire(id)
            // Agelessness is a toggle now: grant it, or take it back if it's already on.
            GodAction.IMMORTALITY ->
                if (sim.byId(id)?.immortal == true) god.revokeImmortality(id) else god.grantImmortality(id)
        }
        publish()
    }

    /** Call a new being into the world and follow it straight away. */
    fun spawnBeing() {
        snapshotForUndo()
        followedId = god.create()
        rosterFilter = RosterFilter.LIVING
        realmFilter = null
        timeline.maybeRecord(sim)
        publish()
    }

    /** Call a new being into the world at a tapped spot, and follow it. */
    fun spawnBeingAt(x: Int, y: Int) {
        snapshotForUndo()
        followedId = god.createAt(x, y)
        rosterFilter = RosterFilter.LIVING
        realmFilter = null
        timeline.maybeRecord(sim)
        publish()
    }

    /** End the life of the being you're following. */
    fun smiteFollowed() {
        snapshotForUndo()
        god.smite(followedId)
        publish()
    }

    // ---- over all of them at once ----------------------------------------------

    fun blessAll() { snapshotForUndo(); god.blessAll(); publish() }
    fun provideAll() { snapshotForUndo(); god.provideAll(); publish() }
    fun warmAll() { snapshotForUndo(); god.warmAll(); publish() }

    // ---- reshape the land at a tapped spot -------------------------------------

    fun growFoodAt(x: Int, y: Int) { snapshotForUndo(); god.growFood(x, y); publish() }
    fun makeWaterAt(x: Int, y: Int) { snapshotForUndo(); god.makeWater(x, y); publish() }
    fun raiseShelterAt(x: Int, y: Int) { snapshotForUndo(); god.raiseShelter(x, y); publish() }

    // ---- undo / redo the last god act ------------------------------------------

    private fun snapshotOf(): String = Persistence.serialize(Persistence.snapshot(sim))

    /** Snapshot the world right before a divine act, so it can be taken back. */
    private fun snapshotForUndo() {
        undoStack.addLast(snapshotOf())
        while (undoStack.size > 12) undoStack.removeFirst()
        redoStack.clear() // a fresh act branches history -> nothing to redo onto
    }

    /** Swap the world for a stored snapshot, rebinding everything to it. */
    private fun restoreFrom(json: String) {
        loop?.cancel()
        speed = Speed.PAUSED
        sim = Persistence.restore(Persistence.deserialize(json))
        god = God(sim)
        // The being we followed may be gone (e.g. an undone spawn); fall back to a living one.
        if (sim.byId(followedId)?.alive != true) {
            followedId = sim.living().firstOrNull()?.id ?: followedId
        }
        resetMomentTracking()
        save()
        publish()
    }

    /** Undo the last god act, restoring the world to just before it. */
    fun undo() {
        val json = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(snapshotOf()) // remember where we were, so it can be redone
        restoreFrom(json)
    }

    /** Redo a god act that was undone. */
    fun redo() {
        val json = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snapshotOf()) // so the redo can itself be undone
        restoreFrom(json)
    }

    // ---- true godhood: edit the being you're following directly ----------------

    /** Rename the being you're following. Blank keeps the old name. */
    fun editName(name: String) {
        sim.byId(followedId)?.let { if (name.isNotBlank()) it.name = name.trim().take(24) }
        publish()
    }

    /** Recolour them: hue is 0..360 around the wheel. */
    fun editHue(hue: Float) {
        sim.byId(followedId)?.let { it.appearanceSeed = hue.toInt().coerceIn(0, 359) }
        publish()
    }

    /** Resize their dot on the map, 1.0 being ordinary. */
    fun editSize(size: Float) {
        sim.byId(followedId)?.let { it.size = size.toDouble().coerceIn(0.4, 2.5) }
        publish()
    }

    /** Age or de-age them by setting their years outright (life stage follows from it). */
    fun editAge(years: Float) {
        sim.byId(followedId)?.let { it.ageYears = years.toDouble().coerceIn(0.0, 90.0) }
        publish()
    }

    /** Set one of their drives directly, 0..1, instead of nudging it with a blessing. */
    fun editDrive(label: String, value: Float) {
        val type = DriveType.entries.firstOrNull { it.label == label } ?: return
        sim.byId(followedId)?.let { it.drives[type] = (value * 100f).toDouble().coerceIn(0.0, 100.0) }
        publish()
    }

    /**
     * Set a fate on the being you're following: an intention that waits until their
     * life meets the trigger, then comes to pass on its own (§10.6). The two halves
     * arrive as the engine enum names the UI mirrored.
     */
    fun decreeFate(triggerId: String, boonId: String) {
        snapshotForUndo()
        val trigger = FateTrigger.valueOf(triggerId)
        val boon = FateBoon.valueOf(boonId)
        god.decree(followedId, trigger, boon)
        publish()
    }

    /** Time only advances while you're watching (§10.4), and the world is saved when you leave. */
    fun setActive(isActive: Boolean) {
        active = isActive
        if (!isActive) save()
        restartLoop()
    }

    private fun save() {
        val snapshot = Persistence.snapshot(sim)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                saveFile.writeText(Persistence.serialize(snapshot))
            } catch (e: Exception) {
                Log.w("Larutan", "Could not save world", e)
            }
        }
    }

    private fun restartLoop() {
        loop?.cancel()
        if (!active || speed == Speed.PAUSED) return
        loop = viewModelScope.launch {
            while (true) {
                if (sim.living().isEmpty()) break
                withContext(Dispatchers.Default) { sim.step() }
                timeline.maybeRecord(sim)
                surfaceMoments()
                publish()
                delay(speed.tickDelayMillis)
            }
        }
    }

    override fun onCleared() {
        loop?.cancel()
        super.onCleared()
    }

    // ---- snapshotting the engine into UI models -----------------------------

    private fun publish() {
        val world = sim.world
        // You may follow the dead too, so don't force the followed one to be alive.
        val followed = sim.byId(followedId) ?: sim.living().firstOrNull()
        if (followed != null) followedId = followed.id

        _state.value = UiState(
            settings = settings,
            world = WorldInfo(
                width = world.width,
                height = world.height,
                year = world.year,
                season = world.season.label,
                dayOfSeason = world.dayOfSeason + 1,
                daysPerSeason = World.DAYS_PER_SEASON.toInt(),
                timeOfDay = world.timeOfDay.name.lowercase(),
                weather = world.weather.label,
                isNight = world.isNight,
                population = sim.living().size,
                harshSpell = world.inHarshSpell,
                settlementName = world.settlementName,
                foundingMyth = world.foundingMyth,
            ),
            map = buildMapView(),
            beings = sim.beings.map { it.toDot(followed?.id) },
            roster = buildRoster(followed?.id),
            rosterFilter = rosterFilter,
            realmFilter = realmFilter,
            followed = followed?.toFollowed(),
            speed = speed,
            moment = currentMoment?.let { MomentView(text = it.text, beingId = it.beingId) },
            timeline = timeline.moments().map { m ->
                val day = m.tick / World.TICKS_PER_DAY
                val hour = (m.tick % World.TICKS_PER_DAY).toInt()
                val seasonIdx = ((day / World.DAYS_PER_SEASON) % 4).toInt()
                val dayOfSeason = (day % World.DAYS_PER_SEASON).toInt()
                TimelineMomentView(
                    tick = m.tick,
                    year = day / (World.DAYS_PER_SEASON * 4),
                    monthIndex = seasonIdx,
                    monthLabel = Season.entries[seasonIdx].label,
                    week = dayOfSeason / 4,
                    dayOfSeason = dayOfSeason,
                    timeLabel = timeOfDayLabel(hour),
                    isNow = m.tick == world.tick,
                )
            },
            chronicle = sim.chronicle.significant(80).map { it.text }.reversed(),
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
        )
    }

    /** Build the who-to-follow list for the current filter: the living, or the dead by realm. */
    private fun buildRoster(selectedId: Int?): List<RosterEntry> {
        val pool = when (rosterFilter) {
            RosterFilter.LIVING -> sim.beings.filter { it.alive }
                .sortedWith(compareByDescending<Being> { it.generation }.thenBy { it.name })
            RosterFilter.DEAD -> sim.beings
                // Souls that have been reborn have moved on -> they leave the afterlife.
                .filter { !it.alive && !it.reincarnated && (realmFilter == null || it.realm?.label == realmFilter) }
                .sortedByDescending { it.id } // most-recently born-and-lost first
        }
        return pool.map { b ->
            RosterEntry(
                id = b.id,
                name = b.name,
                alive = b.alive,
                selected = b.id == selectedId,
                note = if (b.alive) b.lifeStage.label else "lost to ${b.deathCause ?: "the world"}",
                realm = b.realm?.label,
            )
        }
    }

    private fun Being.toDot(selectedId: Int?): BeingDot = BeingDot(
        id = id,
        x = x,
        y = y,
        hue = appearanceSeed.toFloat(),
        valence = emotion.valence.toFloat(),
        alive = alive,
        selected = id == selectedId,
        immortal = immortal,
        size = size.toFloat(),
    )

    private fun Being.toFollowed(): FollowedBeing {
        val driveOrder = listOf(
            DriveType.HUNGER, DriveType.THIRST, DriveType.ENERGY, DriveType.WARMTH,
            DriveType.HEALTH, DriveType.SECURITY, DriveType.CONNECTION, DriveType.INTIMACY,
            DriveType.PURPOSE, DriveType.CURIOSITY, DriveType.AUTONOMY,
        )
        return FollowedBeing(
            id = id,
            name = name,
            generation = generation,
            lifeStage = lifeStage.label,
            ageYears = ageYears.toInt(),
            action = currentAction,
            mood = emotion.moodLabel(),
            atypical = personality.isAtypical,
            atypicalSignature = personality.signature?.phrase,
            hue = appearanceSeed.toFloat(),
            size = size.toFloat(),
            foodStore = foodStore.toInt(),
            alive = alive,
            ailing = ailing,
            atHome = hasHome && kotlin.math.max(kotlin.math.abs(x - homeX), kotlin.math.abs(y - homeY)) <= 1,
            standing = standingLabel(this),
            vice = vice?.mark,
            immortal = immortal,
            realm = realm?.label,
            deathCause = deathCause,
            finalThought = finalThought,
            epitaph = epitaph,
            valence = emotion.valence.toFloat(),
            emotions = emotion.active.sortedByDescending { it.intensity }.take(4).map { it.name.label },
            drives = driveOrder.map { DriveBar(it.label, (drives[it] / 100f).toFloat()) },
            goal = goal?.let { g ->
                GoalView(
                    target = g.target,
                    progress = g.progress.toFloat(),
                    milestones = g.milestones.map { m -> m.label to m.reached },
                    bornFrom = g.bornFrom,
                )
            },
            lastThought = lastThought,
            lastDream = lastDream,
            pastThoughts = recentThoughts.asReversed().take(8),
            memories = memory.salient(6).map { it.detail },
            relationships = relationships.values
                .filter { it.bond > 5 && sim.byId(it.otherId) != null }
                .sortedByDescending { it.bond }
                .take(6)
                .map { r ->
                    RelationView(
                        name = sim.nameOf(r.otherId),
                        sentiment = r.sentiment.label(),
                        bond = (r.bond / 100f).toFloat(),
                    )
                },
            skills = SkillType.entries
                .filter { skills[it] > 0.02 } // only what they've actually begun to learn
                .map { DriveBar(it.label, skills[it].toFloat()) },
            beliefs = beliefs.sortedByDescending { it.strength }.take(5).map { it.statement },
            fates = sim.fates
                .filter { it.subjectId == id && !it.fulfilled }
                .map { it.sentence(name) },
        )
    }

    /**
     * The land, summarised for the map: only the tiles worth drawing -- water, ground
     * with food on it, and standing shelter -- so the world reads as a place with
     * resources, not an empty grid.
     */
    private fun buildMapView(): MapView {
        val w = sim.world
        val water = ArrayList<MapCell>()
        val food = ArrayList<MapCell>()
        val shelters = ArrayList<MapCell>()
        for (y in 0 until w.height) {
            for (x in 0 until w.width) {
                val t = w.tileAt(x, y)
                when {
                    t.terrain == Terrain.WATER ->
                        water += MapCell(x, y, (t.water / 100.0).toFloat().coerceIn(0.35f, 1f))
                    t.foodCapacity > 0.0 && t.food > 6.0 ->
                        food += MapCell(x, y, (t.food / t.foodCapacity).toFloat().coerceIn(0f, 1f))
                }
                // Shelter can sit on the same ground as food, so it's its own pass.
                if (t.shelterQuality > 0.25) shelters += MapCell(x, y, t.shelterQuality.toFloat())
            }
        }
        return MapView(water = water, food = food, shelters = shelters)
    }

    /** How the group's regard reads, in a word — or null when there's nothing to say. */
    private fun standingLabel(b: Being): String? {
        val rep = b.reputation
        val healer = b.skills[SkillType.CAREGIVING] > 0.4
        return when {
            b.eminent -> "one the others look to"
            rep >= 0.5 && healer -> "a healer"
            rep >= 0.5 -> "well-loved"
            rep >= 0.2 -> "well thought of"
            rep <= -0.4 -> "ill-regarded"
            rep <= -0.15 -> "looked at warily"
            else -> null
        }
    }

    /** A friendly name for a snapshot's time of day, for the rewind picker's last level. */
    private fun timeOfDayLabel(hour: Int): String {
        val word = when (hour) {
            in 5..6 -> "dawn"
            in 7..10 -> "morning"
            in 11..13 -> "midday"
            in 14..17 -> "afternoon"
            in 18..20 -> "dusk"
            else -> "night"
        }
        return "$word · ${hour.toString().padStart(2, '0')}h"
    }

    private fun Sentiment.label(): String = when (this) {
        Sentiment.STRANGER -> "a stranger"
        Sentiment.ACQUAINTANCE -> "an acquaintance"
        Sentiment.FRIENDSHIP -> "a friend"
        Sentiment.LOVE -> "loved"
        Sentiment.RIVALRY -> "a rival"
        Sentiment.RESENTMENT -> "resented"
        Sentiment.GRIEF -> "lost, and grieved"
    }
}
