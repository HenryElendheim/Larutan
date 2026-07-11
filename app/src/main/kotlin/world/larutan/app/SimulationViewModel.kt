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
import world.larutan.app.ui.model.RelationView
import world.larutan.app.ui.model.RosterEntry
import world.larutan.app.ui.model.Speed
import world.larutan.app.ui.model.UiState
import world.larutan.app.ui.model.WorldInfo
import world.larutan.engine.being.Being
import world.larutan.engine.being.DriveType
import world.larutan.engine.being.Sentiment
import world.larutan.engine.god.God
import world.larutan.engine.sim.Persistence
import world.larutan.engine.sim.Simulation
import world.larutan.engine.sim.WorldConfig
import world.larutan.engine.sim.WorldGen
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

    // The world survives being closed: continuity is the point.
    private val saveFile = File(app.filesDir, "world.json")

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var speed: Speed = Speed.PAUSED
    private var followedId: Int = 0

    init {
        sim = loadOrCreate()
        god = God(sim)
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

    /** Advance exactly one hour, for close observation. */
    fun stepOnce() {
        if (sim.living().isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.Default) { sim.step() }
            publish()
        }
    }

    /** Reach in and touch the being you're following. */
    fun invoke(action: GodAction) {
        val id = followedId
        when (action) {
            GodAction.PROVIDE -> god.provide(id)
            GodAction.WARM -> god.warm(id)
            GodAction.BLESS -> god.bless(id)
            GodAction.INSPIRE -> god.inspire(id)
            GodAction.IMMORTALITY -> god.grantImmortality(id)
        }
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
        val followed = sim.byId(followedId)?.takeIf { it.alive } ?: sim.living().firstOrNull()
        if (followed != null) followedId = followed.id

        _state.value = UiState(
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
            ),
            beings = sim.beings.map { it.toDot(followed?.id) },
            roster = sim.beings
                .sortedWith(compareByDescending<Being> { it.alive }.thenByDescending { it.generation })
                .map { b ->
                    RosterEntry(
                        id = b.id,
                        name = b.name,
                        alive = b.alive,
                        selected = b.id == followed?.id,
                        note = if (b.alive) b.lifeStage.label else "lost to ${b.deathCause ?: "the world"}",
                    )
                },
            followed = followed?.toFollowed(),
            speed = speed,
            chronicle = sim.chronicle.significant(8).map { it.text }.reversed(),
        )
    }

    private fun Being.toDot(selectedId: Int?): BeingDot = BeingDot(
        id = id,
        x = x,
        y = y,
        hue = appearanceSeed.toFloat(),
        valence = emotion.valence.toFloat(),
        alive = alive,
        selected = id == selectedId,
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
        )
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
