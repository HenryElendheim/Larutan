package world.larutan.engine.sim

import world.larutan.engine.Rng
import world.larutan.engine.action.ActionType
import world.larutan.engine.being.Being
import world.larutan.engine.being.BeliefKind
import world.larutan.engine.being.DriveType
import world.larutan.engine.being.EmotionName
import world.larutan.engine.being.Goal
import world.larutan.engine.being.GoalKind
import world.larutan.engine.being.LifeStage
import world.larutan.engine.being.GoalStatus
import world.larutan.engine.being.MemoryEvent
import world.larutan.engine.being.MemoryKind
import world.larutan.engine.being.Personality
import world.larutan.engine.being.Realm
import world.larutan.engine.being.Relationship
import world.larutan.engine.being.Sentiment
import world.larutan.engine.being.SkillType
import world.larutan.engine.event.Chronicle
import world.larutan.engine.event.EventKind
import world.larutan.engine.event.WorldEvent
import world.larutan.engine.god.Fate
import world.larutan.engine.god.FateBoon
import world.larutan.engine.god.FateTrigger
import world.larutan.engine.narrative.Thoughts
import world.larutan.engine.world.Season
import world.larutan.engine.world.Terrain
import world.larutan.engine.world.Weather
import world.larutan.engine.world.World

/**
 * The living loop. Holds the world and its beings and advances everything one
 * tick at a time. This is pure logic: it emits events and mutates state, and
 * never draws or writes prose beyond the template thoughts it asks for.
 */
class Simulation(
    val config: WorldConfig,
    val world: World,
    val beings: MutableList<Being>,
    val rng: Rng = Rng(config.seed xor 0x5151),
    val chronicle: Chronicle = Chronicle(),
    /** Intentions set on the future, waiting for a life to turn (§10.6). */
    val fates: MutableList<Fate> = mutableListOf(),
) {
    private var nextId: Int = (beings.maxOfOrNull { it.id } ?: -1) + 1
    private var lastSeason: Season = world.season
    private val lastBirthTick = HashMap<Int, Long>()

    fun nameOf(id: Int): String = beings.firstOrNull { it.id == id }?.name ?: "someone"
    fun living(): List<Being> = beings.filter { it.alive }
    fun byId(id: Int): Being? = beings.firstOrNull { it.id == id }

    /** Advance the whole world by one tick (one world-hour). */
    fun step() {
        val startOfNight = world.hourOfDay == 21
        rollWeather()

        // A fate meets its moment before the hour is lived, so its boon can carry
        // the being through the very turn that called it up.
        resolveFates()

        for (being in beings.filter { it.alive }) {
            updateBeing(being, startOfNight)
        }

        regrowResources()

        if (world.hourOfDay == 0) endOfDay()

        world.tick++

        if (world.season != lastSeason) {
            chronicle.add(WorldEvent(world.tick, EventKind.SEASON_TURN, "The season turned to ${world.season.label}.", significant = world.season == Season.WINTER))
            // Spring's return is the year's renewal -> the people mark it together (§9).
            if (world.season == Season.SPRING) holdFestival()
            lastSeason = world.season
        }
    }

    fun run(ticks: Int) { repeat(ticks) { if (living().isNotEmpty()) step() } }

    // ---- fate (§10.6) -------------------------------------------------------

    /** Set an intention on a being's future; it waits until their life turns. */
    fun decree(fate: Fate) { fates += fate }

    /**
     * Walk the waiting fates and let any whose moment has come to pass. A fulfilled
     * fate stays in the list, marked, so a rewind can arm it again with the past.
     */
    private fun resolveFates() {
        if (fates.isEmpty()) return
        for (fate in fates) {
            if (fate.fulfilled) continue
            val b = byId(fate.subjectId)?.takeIf { it.alive } ?: continue
            if (!triggerMet(b, fate.trigger)) continue
            grantBoon(b, fate.boon)
            fate.fulfilled = true
            chronicle.add(WorldEvent(world.tick, EventKind.MILESTONE,
                "A fate long set for ${b.name} came to pass.", b.id, significant = true))
        }
    }

    private fun triggerMet(b: Being, trigger: FateTrigger): Boolean = when (trigger) {
        FateTrigger.HUNGER -> b.drives[DriveType.HUNGER] < 25.0
        FateTrigger.LONELINESS -> b.drives[DriveType.CONNECTION] < 20.0 || b.emotion.dominant() == EmotionName.LONELINESS
        FateTrigger.COLD -> b.drives[DriveType.WARMTH] < 25.0
        FateTrigger.DESPAIR -> b.emotion.valence < -0.6
        FateTrigger.LIFES_END -> b.lifeStage == LifeStage.ELDER && b.drives[DriveType.HEALTH] < 35.0
    }

    private fun grantBoon(b: Being, boon: FateBoon) {
        when (boon) {
            FateBoon.PROVISION -> {
                b.foodStore += 40.0
                b.drives.change(DriveType.HUNGER, 25.0)
                b.memory.record(MemoryEvent(world.tick, MemoryKind.STORED, "food, arriving just as it was needed", null, 0.5, 0.6, salience = 1.0))
            }
            FateBoon.EASE -> {
                listOf(DriveType.HUNGER, DriveType.THIRST, DriveType.WARMTH, DriveType.ENERGY, DriveType.HEALTH)
                    .forEach { b.drives[it] = (b.drives[it] + 35.0).coerceAtMost(100.0) }
                b.emotion.valence = (b.emotion.valence + 0.4).coerceAtMost(1.0)
                b.emotion.feel(EmotionName.RELIEF, 0.5)
                b.emotion.distressLoad = (b.emotion.distressLoad - 25.0).coerceAtLeast(0.0)
            }
            FateBoon.WARMTH -> b.drives[DriveType.WARMTH] = 100.0
            FateBoon.PURPOSE -> {
                val g = Goal.formFor(b.personality, b.memory)
                if (g != null) {
                    g.status = GoalStatus.ACTIVE
                    b.goal = g
                    b.drives.change(DriveType.PURPOSE, 20.0)
                    b.think("A purpose settled on me, sure as sunrise. I want to ${g.target}.")
                }
            }
        }
    }

    // ---- gathering (§9) -----------------------------------------------------

    /**
     * The turn of the year, marked together: everyone alive takes a lift from it, and
     * those gathered near one another warm to each other and come to hold that there is
     * joy in gathering. The bright counterpart to mourning.
     */
    private fun holdFestival() {
        val folk = living()
        if (folk.size < 2) return // it takes a few to make a gathering
        for (b in folk) {
            b.emotion.valence = (b.emotion.valence + 0.3).coerceAtMost(1.0)
            b.emotion.feel(EmotionName.JOY, 0.5)
            b.emotion.distressLoad = (b.emotion.distressLoad - 15.0).coerceAtLeast(0.0)
            b.drives.change(DriveType.CONNECTION, 25.0)
            b.hold(BeliefKind.THERE_IS_JOY_IN_GATHERING, 0.08, "a festival at the turn of the year")
        }
        // Those close enough to share it come away a little closer.
        for (i in folk.indices) {
            for (j in i + 1 until folk.size) {
                val a = folk[i]
                val c = folk[j]
                if (chebyshev(a.x, a.y, c.x, c.y) <= 4) {
                    a.relationshipWith(c.id).warm(4.0)
                    c.relationshipWith(a.id).warm(4.0)
                    record(a, MemoryKind.SOCIALIZED, "the festival with ${c.name}", 0.5, 0.6, c.id)
                }
            }
        }
        chronicle.add(WorldEvent(world.tick, EventKind.MILESTONE,
            "Spring came round, and the people gathered to mark the year's turn.", folk.first().id, significant = true))
    }

    /**
     * The pull of a place. Being home is its own quiet good; and a home lost to the
     * weather is felt, once, before it's let go (§4.8).
     */
    internal fun feelForHome(b: Being) {
        if (!b.hasHome) return
        val home = world.tileAt(b.homeX, b.homeY)
        if (home.built < 0.1) {
            // What they made has gone back to the ground.
            b.emotion.feel(EmotionName.GRIEF, 0.2)
            b.emotion.distressLoad += 6.0
            b.hold(BeliefKind.THE_WORLD_TAKES_WHAT_YOU_LOVE, 0.06, "a home that weathered away")
            record(b, MemoryKind.LOST, "the home they'd made, gone back to the ground", 0.6, -0.5)
            chronicle.add(WorldEvent(world.tick, EventKind.HARDSHIP, "${b.name}'s home has weathered away.", b.id))
            b.homeX = -1; b.homeY = -1
            return
        }
        if (chebyshev(b.x, b.y, b.homeX, b.homeY) <= 1) {
            b.drives.change(DriveType.SECURITY, 8.0)
            b.emotion.valence = (b.emotion.valence + 0.05).coerceAtMost(1.0)
            if (rng.chance(0.02)) record(b, MemoryKind.RESTED, "a quiet hour at home", 0.3, 0.4)
        }
    }

    /**
     * Standing, held long enough by someone grown, makes a figure the group looks to (§9).
     * It isn't a title anyone grants -- it emerges, and it can be lost if regard falls away.
     */
    internal fun recognizeEminence(b: Being) {
        val grown = b.lifeStage == LifeStage.ADULT || b.lifeStage == LifeStage.ELDER
        if (!b.eminent && grown && b.reputation >= 0.6) {
            b.eminent = true
            record(b, MemoryKind.ACHIEVED, "the others have come to look to them", 0.7, 0.6)
            chronicle.add(WorldEvent(world.tick, EventKind.MILESTONE,
                "The others have come to look to ${b.name}.", b.id, significant = true))
        } else if (b.eminent && b.reputation < 0.35) {
            b.eminent = false // regard fell away, and with it the standing
        }
    }

    // ---- per-being update ---------------------------------------------------

    private fun updateBeing(b: Being, startOfNight: Boolean) {
        // One being's hour, in order:
        // age -> needs drift -> health -> feelings -> (sleep?) -> pick and do an
        // action -> nudge their goal -> cope if hurting -> maybe a thought -> live or die.
        age(b)
        driftDrives(b)
        healthEffects(b)
        progressIllness(b)
        maybeFallIll(b)
        updateEmotion(b)

        // Sleep through the night once tired; that's when dreams happen.
        if (world.isNight && b.drives[DriveType.ENERGY] < 55) {
            b.currentAction = "sleeping"
            b.drives.change(DriveType.ENERGY, 6.0)
            // A sleeper huddles against the night; enough to survive a mild season,
            // never enough to beat a hard winter.
            b.drives.change(DriveType.WARMTH, 4.0)
            if (startOfNight) b.lastDream = Thoughts.dream(b, ::nameOf, rng)
            return
        }

        maybeFormGoal(b)
        val action = chooseAction(b)
        execute(action, b)
        advanceGoal(b, action)
        feelForHome(b)
        recognizeEminence(b)
        cope(b)
        maybeDiscoverCultivation(b)
        maybeThink(b)
        checkMortality(b)
    }

    private fun age(b: Being) {
        if (b.immortal) return // a granted immortality halts aging entirely
        // Age in world-years; a year is DAYS_PER_SEASON*4 days of TICKS_PER_DAY ticks.
        val ticksPerYear = World.DAYS_PER_SEASON * 4 * World.TICKS_PER_DAY
        b.ageYears += 1.0 / ticksPerYear
    }

    private fun driftDrives(b: Being) {
        val stageFactor = when (b.lifeStage.label) {
            "infant", "child" -> 1.15   // grow fast, tire fast
            "elder" -> 1.1
            else -> 1.0
        }
        for (d in DriveType.entries) {
            if (d == DriveType.HEALTH || d == DriveType.WARMTH) continue
            b.drives.change(d, -d.driftPerHour * stageFactor)
        }
        // Warmth is entirely an exposure story: mild loss always, real bite only when
        // it's cold and you're out in the open. Summer barely touches it; winter hurts.
        val shelter = world.tileAt(b.x, b.y).shelterQuality
        val warmthLoss = 0.4 + world.coldness * (1.2 + (1.0 - shelter) * 1.6)
        b.drives.change(DriveType.WARMTH, -warmthLoss)
    }

    internal fun healthEffects(b: Being) {
        val hunger = b.drives[DriveType.HUNGER]
        val warmth = b.drives[DriveType.WARMTH]
        var dh = 0.0
        if (hunger < 12) dh -= (12 - hunger) * 0.08
        if (warmth < 12) dh -= (12 - warmth) * 0.07
        if (hunger > 45 && warmth > 35) dh += 0.6 // recover when fed and warm enough
        // Elders decline slowly no matter what.
        if (b.lifeStage.label == "elder") dh -= 0.05
        // A hard spell is where the shelter you built and the food you put by pay off:
        // the exposed and empty-handed suffer for it, the sheltered and stocked ride it out.
        if (world.inHarshSpell) {
            val sheltered = world.tileAt(b.x, b.y).shelterQuality > 0.4
            val stocked = b.foodStore > 1.0
            if (!sheltered) dh -= 0.9
            if (!stocked && hunger < 40) dh -= 0.6
            if (sheltered && stocked) dh += 0.3 // safe and provided, they weather it fine
        }
        b.drives.change(DriveType.HEALTH, dh)

        if (hunger < 8) recordOnce(b, MemoryKind.STARVED, "going hungry in ${world.season.label}", -0.8, 0.8)
        if (warmth < 8) {
            recordOnce(b, MemoryKind.FROZE, "the cold in ${world.season.label}", -0.8, 0.8)
            b.hold(BeliefKind.WINTERS_ARE_CRUEL, 0.06, "a cold that nearly took them")
        }
    }

    // ---- illness and caregiving (§3.5, §4) ----------------------------------

    /**
     * Sickness may take a being who's run down: gnawing hunger, deep cold, or a body
     * already failing leaves them open to it. A well-kept life rarely falls ill.
     */
    private fun maybeFallIll(b: Being) {
        if (b.illness > 0.0) return
        val hunger = b.drives[DriveType.HUNGER]
        val warmth = b.drives[DriveType.WARMTH]
        val health = b.drives[DriveType.HEALTH]
        // Vulnerability rises as the body's margins thin; near zero for the well-fed and warm.
        var risk = 0.0
        if (hunger < 30) risk += (30 - hunger) * 0.00020
        if (warmth < 30) risk += (30 - warmth) * 0.00020
        if (health < 45) risk += (45 - health) * 0.00016
        if (b.lifeStage == LifeStage.ELDER || b.lifeStage == LifeStage.INFANT) risk += 0.0008
        if (risk <= 0.0) return
        if (rng.chance(risk)) {
            b.illness = 0.30
            record(b, MemoryKind.HURT, "a sickness came over them", 0.6, -0.6)
            chronicle.add(WorldEvent(world.tick, EventKind.HARDSHIP, "${b.name} fell ill.", b.id))
        }
    }

    /** An illness runs its course: it drains the body, and eases as the body fights back. */
    private fun progressIllness(b: Being) {
        if (b.illness <= 0.0) return
        // The sickness pulls health down, harder the worse it is.
        b.drives.change(DriveType.HEALTH, -b.illness * 2.4)
        // A fed, warm, rested body pushes it back; a run-down one lets it deepen.
        val fighting = b.drives[DriveType.HUNGER] > 40 && b.drives[DriveType.WARMTH] > 35
        b.illness = if (fighting) b.illness - 0.010 else b.illness + 0.004
        b.illness = b.illness.coerceIn(0.0, 1.0)
        if (b.illness <= 0.0) {
            record(b, MemoryKind.RESTED, "coming through the sickness", 0.5, 0.5)
            chronicle.add(WorldEvent(world.tick, EventKind.COPED, "${b.name} came through the sickness.", b.id))
        }
    }

    /**
     * Tend someone who's ailing: sit with them, ease the sickness along. It's the
     * caregiving skill in practice — it grows with the doing — and it weighs to the
     * good. Warmth and a steadier hand make for better care.
     */
    private fun tendSick(carer: Being, patient: Being) {
        if (!patient.ailing || carer.illness > 0.15) return
        val skill = carer.skills[SkillType.CAREGIVING]
        val relief = 0.03 + skill * 0.05 + carer.personality.warmth.coerceAtLeast(0.0) * 0.02
        patient.illness = (patient.illness - relief).coerceAtLeast(0.0)
        patient.drives.change(DriveType.HEALTH, 1.0 + skill * 1.5)
        patient.emotion.feel(EmotionName.GRATITUDE, 0.3)
        patient.relationshipWith(carer.id).warm(3.0)
        carer.skills.practice(SkillType.CAREGIVING, 0.02)
        carer.moralLedger += 0.2
        regard(carer, 0.04) // tending the sick is seen, and remembered
        carer.relationshipWith(patient.id).warm(2.0)
        recordOnce(carer, MemoryKind.SOCIALIZED, "tending ${patient.name} while they were ill", 0.3, 0.4)
    }

    /** Shift how the group regards a being, from what they're seen to do (§4.8). */
    private fun regard(b: Being, amount: Double) {
        b.reputation = (b.reputation + amount).coerceIn(-1.0, 1.0)
    }

    /**
     * Hurt, vented often enough onto the same person, can harden into a lasting rift:
     * the bond curdles to resentment on both sides, and no ordinary meeting warms it
     * again until it's mended (§4.8).
     */
    internal fun maybeRift(b: Being, other: Being) {
        val rel = b.relationshipWith(other.id)
        if (rel.sentiment == Sentiment.RESENTMENT || rel.sentiment == Sentiment.RIVALRY) return
        if (rel.bond < 20.0 && rng.chance(0.3 + b.personality.temper.coerceAtLeast(0.0) * 0.25)) {
            rel.sentiment = Sentiment.RESENTMENT
            other.relationshipWith(b.id).sentiment = Sentiment.RESENTMENT
            regard(b, -0.06) // the one who broke it is seen to have
            record(b, MemoryKind.HURT, "a falling-out with ${other.name}", 0.6, -0.5, other.id)
            record(other, MemoryKind.HURT, "a falling-out with ${b.name}", 0.6, -0.5, b.id)
            chronicle.add(WorldEvent(world.tick, EventKind.BOND_BROKEN,
                "A rift opened between ${b.name} and ${other.name}.", b.id, other.id, significant = true))
        }
    }

    /**
     * Two who are estranged, brought together again: a forgiving heart may reach across
     * and mend it, and making peace weighs to the good. Otherwise the meeting stays cold.
     */
    internal fun maybeReconcile(b: Being, other: Being, rel: Relationship, otherRel: Relationship) {
        val forgiving = (b.personality.optimism * 0.5 + b.personality.warmth * 0.5).coerceAtLeast(0.0)
        if (rng.chance((0.05 + forgiving * 0.12).coerceIn(0.02, 0.3))) {
            rel.sentiment = Sentiment.ACQUAINTANCE
            otherRel.sentiment = Sentiment.ACQUAINTANCE
            rel.warm(15.0)
            otherRel.warm(15.0)
            b.moralLedger += 0.3
            other.moralLedger += 0.3
            regard(b, 0.05); regard(other, 0.03) // making peace lifts them both in the group's eyes
            b.hold(BeliefKind.OTHERS_CAN_BE_TRUSTED, 0.05, "mending things with ${other.name}")
            record(b, MemoryKind.SOCIALIZED, "made peace with ${other.name}", 0.6, 0.5, other.id)
            chronicle.add(WorldEvent(world.tick, EventKind.BOND_FORMED,
                "${b.name} and ${other.name} made their peace.", b.id, other.id, significant = true))
        } else {
            // Still cold: the distance holds, and it wears on them.
            b.drives.change(DriveType.CONNECTION, -4.0)
        }
    }

    /** Illness can pass between two who are close; a strong body shrugs it off more often. */
    private fun maybeContagion(a: Being, b: Being) {
        val sick = when {
            a.ailing && b.illness <= 0.0 -> b
            b.ailing && a.illness <= 0.0 -> a
            else -> return
        }
        val resistance = (sick.drives[DriveType.HEALTH] / 100.0).coerceIn(0.0, 1.0)
        if (rng.chance(0.05 * (1.0 - resistance * 0.7))) {
            sick.illness = 0.30
            record(sick, MemoryKind.HURT, "catching a sickness from another", 0.5, -0.5)
            chronicle.add(WorldEvent(world.tick, EventKind.HARDSHIP, "${sick.name} caught the sickness going round.", sick.id))
        }
    }

    // ---- emotion ------------------------------------------------------------

    private fun updateEmotion(b: Being) {
        val e = b.emotion
        e.decay()

        // Valence: how well needs are met, tilted by disposition and recent memory.
        val met = DriveType.entries.filter { it != DriveType.HEALTH }.map { b.drives[it] }.average() / 100.0
        val recentMood = b.memory.salient(4).map { it.valenceAtTime * it.salience }.let {
            if (it.isEmpty()) 0.0 else it.average()
        }
        val target = ((met - 0.5) * 2.0) * 0.7 + recentMood * 0.3 + b.personality.optimism * 0.15
        e.valence += (target.coerceIn(-1.0, 1.0) - e.valence) * 0.25

        // Arousal: how loud the dominant need is, plus temper.
        val urgency = b.drives.urgency(b.drives.dominant())
        e.arousal = ((urgency * 0.6) + (b.personality.temper + 1) * 0.15).coerceIn(0.0, 1.0)

        // Surface specific feelings from specific conditions.
        if (b.drives[DriveType.CONNECTION] < 30) e.feel(EmotionName.LONELINESS, 0.12)
        if (b.drives[DriveType.SECURITY] < 30 || world.weather == Weather.STORM) e.feel(EmotionName.FEAR, 0.1)
        if (b.drives[DriveType.HUNGER] < 20) e.feel(EmotionName.ANXIETY, 0.1)
        if (e.valence > 0.55) e.feel(EmotionName.CONTENTMENT, 0.12)
        if (e.valence < -0.5 && b.personality.optimism < 0) e.feel(EmotionName.DESPAIR, 0.1)
        val goalStuck = b.goal?.let { it.status == GoalStatus.ACTIVE && it.progress < 0.1 } ?: false
        if (goalStuck && rng.chance(0.02)) e.feel(EmotionName.FRUSTRATION, 0.15)

        // Distress accumulates from unmet lower needs and negative feeling.
        val deficitLoad = listOf(DriveType.HUNGER, DriveType.WARMTH, DriveType.THIRST, DriveType.CONNECTION)
            .sumOf { b.drives.deficit(it) }
        val negLoad = e.active.filter { !it.name.positive }.sumOf { it.intensity }
        e.distressLoad = (e.distressLoad + deficitLoad * 0.6 + negLoad * 0.8).coerceIn(0.0, 100.0)
        // Resilience helps carry it.
        e.distressLoad = (e.distressLoad - (b.personality.resilience + 1) * 0.3).coerceAtLeast(0.0)
    }

    // ---- decision loop ------------------------------------------------------

    private fun chooseAction(b: Being): ActionType {
        // Score every option -> weight it by who they are and what's around them ->
        // usually take the best, but leave a little room for surprise.
        val candidates = ActionType.entries.filter { it != ActionType.GRIEVE }
        val scores = DoubleArray(candidates.size) { i -> utility(b, candidates[i]) }
        // Low temperature: the best action usually wins, but a little chance keeps
        // lives from being perfectly predictable. An atypical mind runs a touch warmer,
        // so it surprises you more often — it doesn't just take the sensible option.
        val temperature = if (b.personality.isAtypical) 0.34 else 0.22
        val idx = rng.weightedChoice(scores, temperature = temperature)
        return candidates[idx]
    }

    /** utility = Σ (relief × urgency) × personalityWeight × contextModifier, plus goal bias. */
    private fun utility(b: Being, action: ActionType): Double {
        var u = 0.0
        for ((drive, relief) in action.reliefs) {
            u += (relief / 100.0) * b.drives.urgency(drive)
        }
        u *= personalityWeight(b, action)
        u *= contextModifier(b, action)
        u += goalBias(b, action)
        // A being scarred by hunger keeps foraging past need, building a store against it.
        if (action == ActionType.FORAGE) u += hoardingPull(b) * 0.4
        return u.coerceAtLeast(0.0)
    }

    private fun personalityWeight(b: Being, action: ActionType): Double {
        val p = b.personality
        return when (action) {
            ActionType.EXPLORE -> 1.0 + p.curiosity * 0.6 + p.boldness * 0.3
            ActionType.WANDER -> 1.0 + p.curiosity * 0.3 - p.industry * 0.2
            ActionType.SOCIALIZE -> 1.0 + p.warmth * 0.6
            ActionType.PLAY -> 1.0 + p.curiosity * 0.3 + (if (b.lifeStage.label == "child") 0.8 else 0.0)
            ActionType.BUILD -> 1.0 + p.industry * 0.6
            ActionType.TEND -> 1.0 + p.industry * 0.5 + p.curiosity * 0.2
            ActionType.FORAGE -> 1.0 + p.industry * 0.4
            ActionType.HUNT -> 1.0 + p.boldness * 0.6 // the bold hunt; the cautious forage
            ActionType.REFLECT -> 1.0 + (if (b.lifeStage.label == "elder") 0.6 else 0.0) - p.industry * 0.2
            ActionType.SEEK_WARMTH -> 1.0 + p.resilience.let { if (it < 0) 0.3 else 0.0 }
            else -> 1.0
        }.coerceAtLeast(0.1)
    }

    private fun contextModifier(b: Being, action: ActionType): Double = when (action) {
        ActionType.EAT -> if (b.foodStore > 1.0 || nearestFood(b) != null) 1.2 else 0.05
        ActionType.FORAGE -> if (nearestFood(b) != null) 1.1 else 0.1
        ActionType.DRINK -> if (nearestWater(b) != null) 1.2 else 0.05
        ActionType.SEEK_WARMTH -> if (world.coldness > 0.2) 1.2 else 0.2
        ActionType.REST -> if (world.isNight) 1.4 else 0.7
        ActionType.SOCIALIZE, ActionType.PLAY -> if (nearestOther(b) != null) 1.2 else 0.05
        ActionType.BUILD -> if (nearestMaterials(b) != null) 1.0 else 0.1
        // Only a being who's learned to cultivate reaches for it -> otherwise near-zero.
        ActionType.TEND -> if (b.skills[SkillType.CULTIVATION] > 0.0 && nearestGrowable(b) != null) 1.1 else 0.02
        // Hunting is grown-up, dangerous work; the young don't take it on.
        ActionType.HUNT -> if (b.lifeStage.label == "infant" || b.lifeStage.label == "child") 0.03 else 1.0
        else -> 1.0
    }

    private fun goalBias(b: Being, action: ActionType): Double {
        val g = b.goal ?: return 0.0
        if (g.status != GoalStatus.ACTIVE) return 0.0
        val fit = when (g.kind) {
            GoalKind.EXPLORE -> if (action == ActionType.EXPLORE) 1.0 else 0.0
            GoalKind.PROVIDE -> if (action == ActionType.FORAGE) 1.0 else 0.0
            GoalKind.MASTER_FORAGING -> if (action == ActionType.FORAGE) 1.0 else 0.0
            GoalKind.BUILD_SHELTER -> if (action == ActionType.BUILD) 1.0 else 0.0
            GoalKind.FAMILY, GoalKind.BELONG, GoalKind.PROTECT ->
                if (action == ActionType.SOCIALIZE) 1.0 else 0.0
        }
        return fit * g.intensity * 0.5
    }

    // ---- action resolution --------------------------------------------------

    private fun execute(action: ActionType, b: Being) {
        b.currentAction = action.label
        when (action) {
            ActionType.EAT -> {
                if (b.foodStore > 1.0) {
                    val amount = minOf(b.foodStore, 20.0)
                    b.foodStore -= amount
                    b.drives.change(DriveType.HUNGER, amount * 2.0)
                } else {
                    val tile = nearestFood(b)
                    if (tile != null && stepToward(b, tile.first, tile.second)) {
                        val t = world.tileAt(b.x, b.y)
                        val eaten = minOf(t.food, 22.0)
                        t.food -= eaten
                        b.drives.change(DriveType.HUNGER, eaten * 1.9)
                        record(b, MemoryKind.ATE, "a good meal by the ${t.terrain.label}", 0.3, 0.2)
                    }
                }
            }
            ActionType.FORAGE -> {
                val tile = nearestFood(b)
                if (tile != null && stepToward(b, tile.first, tile.second)) {
                    val t = world.tileAt(b.x, b.y)
                    // Life stage sets the body's capability; learned foraging skill sharpens it.
                    val skillBonus = 0.75 + 0.5 * b.skills[SkillType.FORAGING]
                    val gathered = minOf(t.food, 16.0 * forageSkill(b) * skillBonus * world.season.foodYield.coerceAtLeast(0.2))
                    t.food -= gathered
                    b.drives.change(DriveType.HUNGER, gathered * 0.7)
                    b.foodStore += gathered * 0.8
                    b.skills.practice(SkillType.FORAGING, 0.008) // you learn the ground by working it
                    record(b, MemoryKind.FORAGED, "foraging the ${t.terrain.label}", 0.15, 0.1)
                }
            }
            ActionType.HUNT -> hunt(b)
            ActionType.DRINK -> {
                val tile = nearestWater(b)
                if (tile != null && stepToward(b, tile.first, tile.second)) {
                    b.drives.change(DriveType.THIRST, 46.0)
                }
            }
            ActionType.REST -> {
                b.drives.change(DriveType.ENERGY, 22.0)
                b.drives.change(DriveType.HEALTH, 0.2)
            }
            ActionType.SEEK_WARMTH -> {
                val tile = nearestShelter(b)
                if (tile != null) stepToward(b, tile.first, tile.second)
                val q = world.tileAt(b.x, b.y).shelterQuality
                // Huddling and moving warms you anywhere; real shelter warms you far more.
                b.drives.change(DriveType.WARMTH, 20.0 + q * 30.0)
            }
            ActionType.SOCIALIZE -> socialize(b, warm = true)
            ActionType.PLAY -> {
                socialize(b, warm = true)
                b.drives.change(DriveType.CURIOSITY, 12.0)
                b.drives.change(DriveType.ENERGY, -3.0)
            }
            ActionType.WANDER -> {
                // Those with a home drift back toward it now and then; the rest just roam.
                if (b.hasHome && chebyshev(b.x, b.y, b.homeX, b.homeY) > 4 && rng.chance(0.5)) {
                    stepToward(b, b.homeX, b.homeY)
                } else {
                    stepRandom(b)
                }
                b.drives.change(DriveType.CURIOSITY, 6.0)
                b.drives.change(DriveType.AUTONOMY, 5.0)
            }
            ActionType.EXPLORE -> {
                stepOutward(b)
                b.drives.change(DriveType.CURIOSITY, 18.0)
                b.drives.change(DriveType.AUTONOMY, 9.0)
                b.hold(BeliefKind.THE_FAR_PLACES_CALL, 0.03, "the pull of ground never walked")
                if (rng.chance(0.05)) record(b, MemoryKind.EXPLORED, "ground no one I know has walked", 0.4, 0.3)
            }
            ActionType.BUILD -> {
                val tile = chooseBuildTarget(b)
                if (tile != null && stepToward(b, tile.first, tile.second)) {
                    val t = world.tileAt(b.x, b.y)
                    val used = minOf(t.materials, 8.0)
                    t.materials -= used
                    // A skilled builder gets more shelter from the same materials.
                    val skillBonus = 0.75 + 0.5 * b.skills[SkillType.BUILDING]
                    b.drives.change(DriveType.SECURITY, used * 2.0 * skillBonus)
                    b.skills.practice(SkillType.BUILDING, 0.012)
                    // The work now lasts: it raises a real, shared structure on this ground,
                    // one anyone can shelter in -> the making of a home (§3.5).
                    val before = t.built
                    t.built = (t.built + used * 0.03 * skillBonus).coerceAtMost(1.0)
                    if (before < 0.5 && t.built >= 0.5) {
                        record(b, MemoryKind.ACHIEVED, "raising a shelter to last", 0.6, 0.6)
                        b.hold(BeliefKind.HARD_WORK_PROVIDES, 0.06, "a shelter that stood where they built it")
                        chronicle.add(WorldEvent(world.tick, EventKind.MILESTONE,
                            "${b.name} raised a shelter that will stand.", b.id, significant = true))
                        // The place you build becomes the place you belong, if you had none.
                        if (!b.hasHome) { b.homeX = b.x; b.homeY = b.y }
                        regard(b, 0.05) // raising something that lasts is well thought of

                    }
                }
            }
            ActionType.TEND -> tend(b)
            ActionType.REFLECT -> {
                b.drives.change(DriveType.PURPOSE, 10.0)
                b.drives.change(DriveType.AUTONOMY, 6.0)
            }
            ActionType.GRIEVE -> {
                b.drives.change(DriveType.CONNECTION, 4.0)
                b.emotion.distressLoad = (b.emotion.distressLoad - 4.0).coerceAtLeast(0.0)
            }
        }
    }

    private fun forageSkill(b: Being): Double = when (b.lifeStage.label) {
        "infant" -> 0.2
        "child" -> 0.6
        "adolescent" -> 0.9
        "elder" -> 0.7
        else -> 1.0
    }

    private fun socialize(b: Being, warm: Boolean) {
        val other = nearestOther(b) ?: return
        stepToward(b, other.x, other.y)
        if (chebyshev(b.x, b.y, other.x, other.y) > 1) return

        val rel = b.relationshipWith(other.id)
        val otherRel = other.relationshipWith(b.id)

        // Estranged? Then this is no ordinary meeting -> it either thaws or stays cold,
        // and no bond warms while the rift stands (§4.8).
        if (rel.sentiment == Sentiment.RESENTMENT || rel.sentiment == Sentiment.RIVALRY) {
            maybeReconcile(b, other, rel, otherRel)
            return
        }

        // How warmly they take to each other is coloured by standing: a well-regarded
        // soul is met openly, an ill-regarded one warily (§4.8).
        val base = 2.0 + (b.personality.warmth + other.personality.warmth)
        val wasStranger = rel.sentiment == Sentiment.STRANGER
        rel.warm(base + other.reputation * 1.5)
        otherRel.warm(base + b.reputation * 1.5)
        b.drives.change(DriveType.CONNECTION, 24.0)
        b.drives.change(DriveType.INTIMACY, 10.0)
        other.drives.change(DriveType.CONNECTION, 18.0)

        if (wasStranger && rel.bond >= 20) {
            record(b, MemoryKind.BONDED, "met ${other.name}", 0.3, 0.3, other.id)
            b.moralLedger += 0.15 // reaching out and holding a bond weighs to the good
            chronicle.add(WorldEvent(world.tick, EventKind.BOND_FORMED, "${b.name} and ${other.name} began to know each other.", b.id, other.id))
        }
        if (rel.sentiment == Sentiment.FRIENDSHIP || rel.sentiment == Sentiment.LOVE) {
            record(b, MemoryKind.SOCIALIZED, "time with ${other.name}", 0.2, 0.25, other.id)
            b.hold(BeliefKind.OTHERS_CAN_BE_TRUSTED, 0.04, "being known and not turned away")
        }
        // Two who are grieving, sitting together, take some of the weight off each other.
        if (isGrieving(b) && isGrieving(other)) comfort(b, other)

        // A figure the group looks to steadies whoever's with them.
        sway(b, other); sway(other, b)

        // Sickness moves between the close — and so does the care that answers it.
        if (other.ailing) tendSick(b, other) else if (b.ailing) tendSick(other, b)
        maybeContagion(b, other)
        // The old show the young how, and pass on what they believe -> culture begins here (§9).
        teachIfElder(b, other)
        teachIfElder(other, b)
        maybeShareFood(b, other)
        maybeReproduce(b, other)
    }

    private fun isGrieving(b: Being): Boolean =
        b.emotion.active.any { it.name == EmotionName.GRIEF && it.intensity > 0.1 }

    /** Two who grieve, together: each takes some of the weight off the other. */
    private fun comfort(a: Being, b: Being) {
        for (m in listOf(a, b)) {
            m.emotion.active.firstOrNull { it.name == EmotionName.GRIEF }
                ?.let { it.intensity = (it.intensity - 0.12).coerceAtLeast(0.0) }
            m.emotion.distressLoad = (m.emotion.distressLoad - 8.0).coerceAtLeast(0.0)
            m.emotion.feel(EmotionName.GRATITUDE, 0.2)
        }
        a.relationshipWith(b.id).warm(3.0)
        b.relationshipWith(a.id).warm(3.0)
        a.hold(BeliefKind.WE_CARRY_EACH_OTHER, 0.05, "not being left alone with the grief")
        record(a, MemoryKind.SOCIALIZED, "sat with ${b.name} in the grief", 0.5, 0.2, b.id)
    }

    // ---- food and society (§3.5) --------------------------------------------

    /**
     * The one with more may feed a hungry, cared-for other -> a child, a mate, a close
     * friend. Generosity leans on warmth; a being scarred by past hunger hoards instead,
     * far slower to give away what might be needed later.
     */
    private fun maybeShareFood(a: Being, b: Being) {
        val giver = if (a.foodStore >= b.foodStore) a else b
        val taker = if (giver === a) b else a
        if (taker.drives[DriveType.HUNGER] >= 45) return // only the genuinely hungry
        val willingness = (0.25 + giver.personality.warmth * 0.35 - hoardingPull(giver)).coerceIn(0.0, 0.8)
        if (!rng.chance(willingness)) return
        shareFood(giver, taker)
    }

    /**
     * Hand food from one being to another if there's a surplus, a real hunger, and a bond
     * worth feeding. Returns whether anything was shared. Kept apart from the willingness
     * roll so the act itself is plain and testable.
     */
    internal fun shareFood(giver: Being, taker: Being): Boolean {
        if (giver.foodStore < 12.0) return false                 // nothing to spare
        if (taker.drives[DriveType.HUNGER] >= 45) return false    // no real hunger to answer
        val rel = giver.relationshipWith(taker.id)
        val cares = rel.bond > 35 || taker.id in giver.lineage.children || giver.lineage.mate == taker.id
        if (!cares) return false

        val amount = minOf(giver.foodStore * 0.4, 20.0)
        giver.foodStore -= amount
        taker.drives.change(DriveType.HUNGER, amount * 1.6)
        giver.relationshipWith(taker.id).warm(3.0)
        taker.relationshipWith(giver.id).warm(3.0)
        giver.moralLedger += 0.05 // feeding another weighs to the good
        regard(giver, 0.03) // and generosity is noticed
        taker.hold(BeliefKind.OTHERS_CAN_BE_TRUSTED, 0.05, "being fed when hungry")
        record(giver, MemoryKind.HELPED, "shared food with ${taker.name}", 0.3, 0.4, taker.id)
        record(taker, MemoryKind.COMFORTED, "fed by ${giver.name} when hungry", 0.3, 0.5, giver.id)
        return true
    }

    /** How strongly past starvation has turned a being toward hoarding: 0 (never hungry) .. 0.6. */
    private fun hoardingPull(b: Being): Double =
        (b.memory.events.count { it.kind == MemoryKind.STARVED } * 0.15).coerceAtMost(0.6)

    /**
     * Tend a patch of ground: raise how cultivated it is and coax a little food up now.
     * A cultivated plot holds more and comes back faster (§3.5), so a being who keeps one
     * near home leans less on the wild. Returns whether any ground was worked.
     */
    internal fun tend(b: Being): Boolean {
        val plot = nearestGrowable(b) ?: return false
        if (!stepToward(b, plot.first, plot.second)) return false
        val t = world.tileAt(b.x, b.y)
        if (t.foodCapacity <= 0.0) return false
        val gain = 0.04 + b.skills[SkillType.CULTIVATION] * 0.06
        t.cultivation = (t.cultivation + gain).coerceAtMost(1.0)
        t.food = (t.food + 6.0).coerceAtMost(t.effectiveFoodCapacity)
        b.skills.practice(SkillType.CULTIVATION, 0.01)
        b.hold(BeliefKind.HARD_WORK_PROVIDES, 0.02, "coaxing food from the ground")
        return true
    }

    /**
     * Cultivation is a genuine leap, not a given: a fed, safe, driven mind may hit on the
     * idea of tending the ground rather than only gathering from it (§3.5). It's rare, and
     * once it happens it spreads by teaching (§9). Discovery is a payoff beat -> it surfaces.
     */
    private fun maybeDiscoverCultivation(b: Being) {
        if (b.skills[SkillType.CULTIVATION] > 0.0) return
        val stage = b.lifeStage.label
        if (stage != "adult" && stage != "elder") return
        if (b.drives.lowerNeedsSatisfaction() < 0.55) return
        if (b.personality.industry * 0.5 + b.personality.curiosity * 0.5 < 0.3) return
        if (!rng.chance(0.0008)) return
        b.skills.practice(SkillType.CULTIVATION, 0.15)
        b.hold(BeliefKind.HARD_WORK_PROVIDES, 0.2, "learning to coax food from the ground")
        b.think("If I tend this ground, it might bear for me. I could keep it near.")
        chronicle.add(WorldEvent(world.tick, EventKind.MILESTONE, "${b.name} learned to coax food from the ground.", b.id, significant = true))
    }

    private fun nearestGrowable(b: Being): Pair<Int, Int>? = nearestTile(b) { it.foodCapacity > 0.0 }

    /**
     * Hunt game: the riskier, higher-yield way to eat (§3.5). Skill and boldness make a
     * catch more likely and larger; a miss can turn bad and leave a real wound, which is
     * one of the honest ways a life comes apart. Not everyone takes it up -- the cautious
     * stick to foraging. Learned by doing, and taught on like any other skill.
     */
    internal fun hunt(b: Being): Boolean {
        stepOutward(b) // range after game
        val skill = b.skills[SkillType.HUNTING]
        b.skills.practice(SkillType.HUNTING, 0.01)
        val prowess = (0.3 + skill * 0.45 + b.personality.boldness * 0.15).coerceIn(0.1, 0.9)
        if (rng.chance(prowess)) {
            val caught = 30.0 + skill * 24.0 // far more than a forage, when it lands
            b.drives.change(DriveType.HUNGER, caught * 0.9)
            b.foodStore += caught * 0.7
            b.hold(BeliefKind.HARD_WORK_PROVIDES, 0.02, "a hunt that fed them")
            record(b, MemoryKind.FORAGED, "brought down game", 0.35, 0.45)
            return true
        }
        // A miss, and now and then a bad turn -> a wound. Skill and nerve keep you safer.
        val hurtChance = (0.2 - skill * 0.14 - b.personality.boldness * 0.05).coerceIn(0.03, 0.25)
        if (rng.chance(hurtChance)) {
            b.drives.change(DriveType.HEALTH, -14.0)
            b.emotion.feel(EmotionName.FEAR, 0.3)
            record(b, MemoryKind.HURT, "hurt in a hunt gone wrong", 0.6, -0.6)
        }
        return false
    }

    /**
     * An elder near a child hands down what they know: a nudge toward their skill, and
     * a fainter version of their firmest conviction. Passed on weaker and imperfect,
     * a belief drifts as it travels a lineage -> the seed of shared story and myth (§9).
     */
    private fun teachIfElder(teacher: Being, student: Being) {
        val teaching = teacher.lifeStage.label == "adult" || teacher.lifeStage.label == "elder"
        val learning = student.lifeStage.label == "child" || student.lifeStage.label == "adolescent"
        if (!teaching || !learning) return
        if (!rng.chance(0.15)) return

        student.skills.learnFrom(teacher.skills[SkillType.FORAGING], SkillType.FORAGING)
        student.skills.learnFrom(teacher.skills[SkillType.BUILDING], SkillType.BUILDING)
        // Cultivation, once someone hits on it, travels down the generations by being shown.
        student.skills.learnFrom(teacher.skills[SkillType.CULTIVATION], SkillType.CULTIVATION)
        student.skills.learnFrom(teacher.skills[SkillType.HUNTING], SkillType.HUNTING)

        val belief = teacher.beliefs.maxByOrNull { it.strength } ?: return
        val isNew = student.beliefs.none { it.kind == belief.kind }
        // What a figure the group looks to holds carries further than an ordinary elder's word.
        student.hold(belief.kind, if (teacher.eminent) 0.3 else 0.15, "what an elder taught")
        if (isNew && rng.chance(0.3)) {
            chronicle.add(WorldEvent(world.tick, EventKind.BOND_FORMED, "${teacher.name} taught ${student.name} that ${belief.statement}.", teacher.id, student.id))
        }
    }

    /**
     * Being near a figure the group looks to steadies the ordinary: a little of their
     * conviction rubs off, and their presence eases the day (§9).
     */
    private fun sway(figure: Being, other: Being) {
        if (!figure.eminent || other.eminent) return
        other.emotion.distressLoad = (other.emotion.distressLoad - 4.0).coerceAtLeast(0.0)
        figure.beliefs.maxByOrNull { it.strength }?.let { other.hold(it.kind, 0.03, "being near ${figure.name}") }
    }

    // ---- goals --------------------------------------------------------------

    private fun maybeFormGoal(b: Being) {
        if (b.goal != null && b.goal!!.status == GoalStatus.ACTIVE) return
        if (b.lifeStage.label == "infant" || b.lifeStage.label == "child") return
        // Only reach for a dream once the floor is handled — the hierarchy at work.
        if (b.drives.lowerNeedsSatisfaction() < 0.55) return
        if (b.drives.urgency(DriveType.PURPOSE) < 0.25) return
        if (!rng.chance(0.03)) return

        val g = Goal.formFor(b.personality, b.memory) ?: return
        b.goal = g
        record(b, MemoryKind.ACHIEVED, "a purpose took shape: to ${g.target}", 0.4, 0.4)
        b.think("I want to ${g.target}. It came from ${g.bornFrom}.")
        chronicle.add(WorldEvent(world.tick, EventKind.GOAL_FORMED, "${b.name} set out to ${g.target}.", b.id, significant = true))
    }

    private fun advanceGoal(b: Being, action: ActionType) {
        val g = b.goal ?: return
        if (g.status != GoalStatus.ACTIVE) return
        if (goalBias(b, action) <= 0.0) return
        val reached = g.advance(0.01 + b.personality.industry.coerceAtLeast(0.0) * 0.01)
        if (reached != null) {
            b.drives.change(DriveType.PURPOSE, 22.0)
            b.emotion.feel(EmotionName.PRIDE, 0.4)
            record(b, MemoryKind.MILESTONE, reached.label, 0.5, 0.5)
            chronicle.add(WorldEvent(world.tick, EventKind.MILESTONE, "${b.name}: ${reached.label}.", b.id, significant = true))
        }
        if (g.status == GoalStatus.ACHIEVED) {
            b.drives.change(DriveType.PURPOSE, 40.0)
            b.emotion.feel(EmotionName.JOY, 0.6)
            // A life's work weighs the soul -> goals that lift others weigh most.
            b.moralLedger += when (g.kind) {
                GoalKind.FAMILY, GoalKind.PROTECT, GoalKind.BELONG -> 1.0
                else -> 0.3
            }
            if (g.kind == GoalKind.PROVIDE || g.kind == GoalKind.MASTER_FORAGING || g.kind == GoalKind.BUILD_SHELTER) {
                b.hold(BeliefKind.HARD_WORK_PROVIDES, 0.2, "reaching what they worked for")
            }
            record(b, MemoryKind.ACHIEVED, "did the thing they set out to: ${g.target}", 0.9, 0.8)
            chronicle.add(WorldEvent(world.tick, EventKind.GOAL_ACHIEVED, "${b.name} achieved it: ${g.target}.", b.id, significant = true))
        }
    }

    // ---- coping -------------------------------------------------------------

    private fun cope(b: Being) {
        if (b.emotion.distressLoad < 45) return
        // Personality and history choose the coping; relief is real, the cost comes later.
        val p = b.personality
        when {
            p.warmth < -0.3 -> { // withdrawal: eases strain now, deepens loneliness
                b.currentAction = "withdrawing"
                b.emotion.distressLoad -= 10
                b.drives.change(DriveType.CONNECTION, -6.0)
                b.emotion.feel(EmotionName.LONELINESS, 0.1)
                chronicle.add(WorldEvent(world.tick, EventKind.COPED, "${b.name} pulled away from the others.", b.id))
            }
            p.temper > 0.3 -> { // lashing out: vents, damages a bond
                val other = nearestOther(b)
                b.emotion.distressLoad -= 12
                if (other != null) {
                    b.relationshipWith(other.id).cool(6.0)
                    b.moralLedger -= 0.3 // venting the hurt onto others weighs against them
                    regard(b, -0.03) // and it's seen
                    b.emotion.feel(EmotionName.ANGER, 0.1)
                    chronicle.add(WorldEvent(world.tick, EventKind.COPED, "${b.name} lashed out at ${other.name}.", b.id, other.id))
                    maybeRift(b, other)
                }
            }
            else -> { // adaptive: grieve openly, seek comfort
                execute(ActionType.GRIEVE, b)
                b.emotion.feel(EmotionName.RELIEF, 0.1)
            }
        }
    }

    // ---- thoughts -----------------------------------------------------------

    private fun maybeThink(b: Being) {
        val salient = b.emotion.arousal > 0.5 || b.drives.urgency(b.drives.dominant()) > 0.5
        if (salient || rng.chance(0.06)) {
            b.think(Thoughts.surface(b, world, ::nameOf, rng))
        }
    }

    // ---- reproduction & mortality ------------------------------------------

    private fun maybeReproduce(a: Being, b: Being) {
        if (a.lifeStage.label != "adult" && a.lifeStage.label != "elder") return
        if (b.lifeStage.label != "adult" && b.lifeStage.label != "elder") return
        val rel = a.relationshipWith(b.id)
        if (rel.bond < 72) return
        if (a.drives[DriveType.HUNGER] < 45 || b.drives[DriveType.HUNGER] < 45) return
        if (beings.count { it.alive } >= 24) return
        val cd = World.DAYS_PER_SEASON * World.TICKS_PER_DAY * 2 // ~two seasons between children
        if (world.tick - (lastBirthTick[a.id] ?: -cd) < cd) return
        if (!rng.chance(0.02)) return

        a.relationshipWith(b.id).sentiment = Sentiment.LOVE
        a.lineage.mate = b.id
        b.lineage.mate = a.id
        lastBirthTick[a.id] = world.tick
        lastBirthTick[b.id] = world.tick

        // A child born to a grieving parent may carry a lost loved one's name forward.
        val namesake = if (rng.chance(0.5)) lostLovedName(a) ?: lostLovedName(b) else null
        val child = Being(
            id = nextId++,
            name = namesake ?: Names.random(rng),
            x = a.x,
            y = a.y,
            personality = Personality.inherit(a.personality, b.personality, rng, config.let { 0.15 }),
            generation = maxOf(a.generation, b.generation) + 1,
            ageYears = 0.0,
            birthTick = world.tick,
            appearanceSeed = rng.nextInt(360),
        )
        child.lineage.parents += a.id
        child.lineage.parents += b.id
        a.lineage.children += child.id
        b.lineage.children += child.id
        // A child belongs where its parents do -> home passes down a family (§4.8).
        val parentHome = a.takeIf { it.hasHome } ?: b.takeIf { it.hasHome }
        if (parentHome != null) { child.homeX = parentHome.homeX; child.homeY = parentHome.homeY }
        beings += child
        a.moralLedger += 0.4 // bringing up a life is one of the heavier good weights
        b.moralLedger += 0.4
        // A child inherits a faint trace of a parent's firmest belief -> culture carried forward (§9).
        (a.beliefs + b.beliefs).maxByOrNull { it.strength }?.let {
            child.hold(it.kind, 0.2, "something a parent believed")
        }
        record(a, MemoryKind.BORN, "a child, ${child.name}", 0.9, 0.9, child.id)
        record(b, MemoryKind.BORN, "a child, ${child.name}", 0.9, 0.9, child.id)
        if (namesake != null) {
            // Named for someone gone: the loss carried forward into a new life (§9, §10.7).
            a.hold(BeliefKind.WE_CARRY_EACH_OTHER, 0.08, "naming ${child.name} for one who was lost")
            chronicle.add(WorldEvent(world.tick, EventKind.BIRTH,
                "${child.name} was born to ${a.name} and ${b.name}, named for one they had lost.", child.id, significant = true))
        } else {
            chronicle.add(WorldEvent(world.tick, EventKind.BIRTH, "${child.name} was born to ${a.name} and ${b.name}.", child.id, significant = true))
        }
    }

    /**
     * The name of a loved one this being has lost, if any — the most-bonded of the dead
     * they still grieve. Used to carry a name forward into a newborn (remembrance).
     */
    internal fun lostLovedName(parent: Being): String? =
        parent.relationships.values
            .filter { it.sentiment == Sentiment.GRIEF && it.bond > 40 }
            .filter { byId(it.otherId)?.alive == false }
            .maxByOrNull { it.bond }
            ?.let { byId(it.otherId)?.name }

    private fun checkMortality(b: Being) {
        if (b.immortal) {
            // Held back from the edge: never quite empties, never dies.
            if (b.drives[DriveType.HEALTH] < 5.0) b.drives[DriveType.HEALTH] = 5.0
            return
        }
        if (b.drives[DriveType.HEALTH] > 0.0 && b.ageYears < 80) return
        val cause = when {
            b.drives[DriveType.HUNGER] < 10 -> "hunger"
            b.drives[DriveType.WARMTH] < 10 -> "the cold"
            b.ageYears >= 80 -> "old age"
            b.illness > 0.4 -> "the sickness"
            else -> "failing health"
        }
        die(b, cause)
    }

    private fun die(b: Being, cause: String) {
        b.alive = false
        b.deathCause = cause
        b.deathTick = world.tick
        b.currentAction = "gone"
        // A soul leaves a last reflection, is weighed, and settles into a realm (§10.7).
        b.finalThought = finalThoughtFor(b)
        val realm = Realm.sortFor(b.moralLedger)
        b.realm = realm
        settleSoul(b, realm)
        chronicle.add(WorldEvent(world.tick, EventKind.DEATH, "${b.name} died of $cause, and passed into ${realm.label}.", b.id, significant = true))
        // Death lands on everyone bonded to them.
        val mourners = beings.filter { it.alive && (it.relationships[b.id]?.bond ?: 0.0) > 30 }
        for (other in mourners) {
            val rel = other.relationships.getValue(b.id)
            rel.sentiment = Sentiment.GRIEF
            other.emotion.feel(EmotionName.GRIEF, (rel.bond / 100.0).coerceIn(0.2, 1.0))
            other.emotion.distressLoad += rel.bond * 0.4
            other.hold(BeliefKind.THE_WORLD_TAKES_WHAT_YOU_LOVE, 0.12, "losing ${b.name}")
            record(other, MemoryKind.LOST, "lost ${b.name}", 1.0, -0.9, b.id)
        }

        // Grief shared is grief made bearable: those who mourn the same loss are drawn
        // closer to each other, and come to hold it together -> the root of mourning (§9).
        if (mourners.size >= 2) {
            for (m in mourners) {
                mourners.forEach { n -> if (n.id != m.id) m.relationshipWith(n.id).warm(4.0) }
                m.hold(BeliefKind.WE_CARRY_EACH_OTHER, 0.1, "mourning ${b.name} together")
            }
            chronicle.add(WorldEvent(world.tick, EventKind.COPED,
                "Those who loved ${b.name} drew together in the loss.", mourners.first().id, significant = true))
        }

        // A last teaching: what the dying held most firmly passes, strengthened, to the
        // one who loved them best -> conviction outliving the life that formed it (§9).
        passOnConviction(b, mourners)

        // When a figure the group looked to dies, the whole group feels it, and someone
        // may rise to the space they leave (§9).
        if (b.eminent) succeed(b)
    }

    /** The death of a figure the group looked to: felt by all, and a space someone may fill. */
    private fun succeed(dead: Being) {
        for (m in living()) {
            m.emotion.feel(EmotionName.GRIEF, 0.2)
            m.hold(BeliefKind.THE_WORLD_TAKES_WHAT_YOU_LOVE, 0.05, "losing the one they looked to")
        }
        chronicle.add(WorldEvent(world.tick, EventKind.DEATH,
            "The one they looked to, ${dead.name}, is gone.", dead.id, significant = true))
        // The group's regard turns to the most-esteemed grown soul still living.
        val successor = living()
            .filter { it.lifeStage == LifeStage.ADULT || it.lifeStage == LifeStage.ELDER }
            .maxByOrNull { it.reputation }
        if (successor != null && successor.reputation > 0.2) {
            regard(successor, 0.25)
            record(successor, MemoryKind.REFLECTED, "the others turning to them, after ${dead.name}", 0.6, 0.2)
            chronicle.add(WorldEvent(world.tick, EventKind.MILESTONE,
                "With ${dead.name} gone, the others begin to look to ${successor.name}.", successor.id, significant = true))
        }
    }

    /** The firmest belief of the dead settles more deeply on their closest mourner. */
    private fun passOnConviction(dead: Being, mourners: List<Being>) {
        val belief = dead.beliefs.maxByOrNull { it.strength } ?: return
        if (belief.strength < 0.3) return // only a real conviction carries
        val heir = mourners.maxByOrNull { it.relationships.getValue(dead.id).bond } ?: return
        heir.hold(belief.kind, 0.2, "what ${dead.name} believed, left to them")
        record(heir, MemoryKind.REFLECTED, "holding to what ${dead.name} believed: ${belief.statement}", 0.6, 0.1)
        chronicle.add(WorldEvent(world.tick, EventKind.MILESTONE,
            "What ${dead.name} believed lives on in ${heir.name}.", heir.id, significant = true))
    }

    /** A last surfaced reflection at death, coloured by how the life weighed out. */
    private fun finalThoughtFor(b: Being): String {
        val pool = when {
            b.moralLedger >= 1.0 -> listOf(
                "So this is the end of it. I think I did right by them.",
                "Let them remember the good days. There were good days.",
                "I'm not as afraid as I thought I would be.",
            )
            b.moralLedger <= -1.0 -> listOf(
                "There was more I meant to make right. Too late now.",
                "I could have been softer. I see that now.",
                "I wonder if anyone will miss me.",
            )
            else -> listOf(
                "There was more I wanted. There always is.",
                "It goes quiet. Stranger than I imagined.",
                "I hope someone carries this on.",
            )
        }
        return rng.pick(pool)
    }

    /**
     * The dead don't tick; a soul sits in a fixed feeling, pinned by its realm.
     * Heaven is contentment, hell is heaviness, purgatory is an unfinished, hopeful
     * middle -> the same emotion model, its rules rewritten (§10.7).
     */
    private fun settleSoul(b: Being, realm: Realm) {
        b.emotion.active.clear()
        when (realm) {
            Realm.HEAVEN -> {
                b.emotion.valence = 0.9; b.emotion.arousal = 0.1; b.emotion.distressLoad = 0.0
                b.emotion.feel(EmotionName.CONTENTMENT, 0.8)
            }
            Realm.HELL -> {
                b.emotion.valence = -0.8; b.emotion.arousal = 0.3
                b.emotion.feel(EmotionName.GRIEF, 0.6)
            }
            Realm.PURGATORY -> {
                b.emotion.valence = 0.0; b.emotion.arousal = 0.2
                b.emotion.feel(EmotionName.HOPE, 0.3)
            }
        }
    }

    // ---- world upkeep -------------------------------------------------------

    private fun rollWeather() {
        if (world.hourOfDay != 6) return // reroll once a day, at dawn

        // A hard spell is a trial that runs for days: while it holds, the cold bites deep
        // and only shelter and stores carry you through (§3.5).
        if (world.inHarshSpell) {
            world.harshSpell--
            world.weather = Weather.COLD_SNAP
            if (world.harshSpell == 0) {
                chronicle.add(WorldEvent(world.tick, EventKind.WEATHER, "The hard spell broke, and the cold let go.", significant = true))
            }
            return
        }
        // Deep in the cold seasons, a hard spell can descend.
        if ((world.season == Season.WINTER || world.season == Season.AUTUMN) && rng.chance(0.06)) {
            world.harshSpell = 2 + rng.nextInt(3) // two to four days
            world.weather = Weather.COLD_SNAP
            chronicle.add(WorldEvent(world.tick, EventKind.WEATHER, "A hard spell set in -- the cold bit deep.", significant = true))
            return
        }

        val roll = rng.nextDouble()
        world.weather = when (world.season) {
            Season.WINTER -> when {
                roll < 0.45 -> Weather.SNOW
                roll < 0.65 -> Weather.COLD_SNAP
                roll < 0.8 -> Weather.OVERCAST
                else -> Weather.CLEAR
            }
            Season.SUMMER -> when {
                roll < 0.6 -> Weather.CLEAR
                roll < 0.8 -> Weather.OVERCAST
                else -> Weather.RAIN
            }
            else -> when {
                roll < 0.4 -> Weather.CLEAR
                roll < 0.6 -> Weather.OVERCAST
                roll < 0.8 -> Weather.RAIN
                else -> Weather.STORM
            }
        }
    }

    private fun regrowResources() {
        // Cheap: touch a slice of tiles each tick so a big map costs little.
        val total = world.tiles.size
        val slice = (total / World.TICKS_PER_DAY).coerceAtLeast(1)
        val start = ((world.tick % World.TICKS_PER_DAY) * slice).toInt() % total
        val yield = world.season.foodYield
        for (i in 0 until slice) {
            val t = world.tiles[(start + i) % total]
            if (t.foodCapacity > 0) {
                val cap = t.effectiveFoodCapacity // tended ground holds more...
                if (t.food < cap) {
                    // ...and comes back faster than the wild.
                    t.food = (t.food + cap * (0.02 + t.cultivation * 0.02) * yield).coerceAtMost(cap)
                }
                if (t.cultivation > 0.0) t.cultivation *= 0.999 // without tending, it creeps back to wild
            }
            if (t.materialsCapacity > 0 && t.materials < t.materialsCapacity) {
                t.materials = (t.materials + 0.3).coerceAtMost(t.materialsCapacity)
            }
            // A built shelter weathers, slowly, if no one keeps it up -> a home has to be
            // maintained, not just made once.
            if (t.built > 0.0) t.built = (t.built - 0.0006).coerceAtLeast(0.0)
        }
    }

    private fun endOfDay() {
        for (b in beings.filter { it.alive }) {
            b.memory.decay()
            // A store slowly spoils, so "provide" is a standing effort, not a one-off.
            b.foodStore = (b.foodStore * 0.98)
        }
        fadeTheDead()
    }

    /**
     * The dead don't drown the world in data (§10.7): as world-time passes, older souls
     * compress -> the sharp memory dulls, then a short epitaph forms, until the long-gone
     * are little more than a name and a line. This is exactly how memory works for us.
     */
    private fun fadeTheDead() {
        val year = World.DAYS_PER_SEASON * 4 * World.TICKS_PER_DAY
        val season = World.DAYS_PER_SEASON * World.TICKS_PER_DAY
        for (b in beings) {
            if (b.alive || b.reincarnated) continue
            val since = world.tick - (b.deathTick ?: continue)
            when {
                since > year -> {
                    if (b.epitaph == null) b.epitaph = epitaphFor(b)
                    b.memory.compressTo(1)
                }
                since > 2 * season -> {
                    if (b.epitaph == null) b.epitaph = epitaphFor(b)
                    b.memory.compressTo(3)
                }
                since > season -> b.memory.compressTo(8)
            }
        }
    }

    /** A one-line remembrance for a soul long gone: what the life is remembered for. */
    private fun epitaphFor(b: Being): String {
        val note = b.goal?.takeIf { it.status == GoalStatus.ACHIEVED }?.target
            ?: b.memory.events.filter { it.valenceAtTime > 0 }.maxByOrNull { it.emotionalWeight }?.detail
        return if (note != null) "${b.name} — ${note}" else "${b.name} — a life, now a name"
    }

    /**
     * Reincarnation (§10.7): a soul returns to the living world as a newborn, carrying a
     * trace of who it was -> a look, a temperament near the old one, and a faint pull it
     * can't name. The old soul has moved on, so it leaves the afterlife.
     */
    fun reincarnate(soul: Being): Being {
        val child = Being(
            id = nextId++,
            name = Names.random(rng),
            x = soul.x.coerceIn(0, world.width - 1),
            y = soul.y.coerceIn(0, world.height - 1),
            personality = Personality.inherit(soul.personality, soul.personality, rng, 0.2),
            generation = 1,
            ageYears = 0.0,
            birthTick = world.tick,
            appearanceSeed = soul.appearanceSeed, // an echo of the old look
        )
        child.memory.record(
            MemoryEvent(
                world.tick, MemoryKind.REFLECTED,
                "a pull toward something from before, with no name for it",
                subjectId = null, emotionalWeight = 0.5, valenceAtTime = 0.1, salience = 0.6,
            ),
        )
        soul.reincarnated = true
        beings += child
        chronicle.add(WorldEvent(world.tick, EventKind.BIRTH, "${soul.name} was reborn as ${child.name}.", child.id, significant = true))
        return child
    }

    // ---- perception helpers -------------------------------------------------

    private fun forageSearchRadius(): Int = 6

    private fun nearestFood(b: Being): Pair<Int, Int>? = nearestTile(b) { it.food > 3.0 }
    private fun nearestWater(b: Being): Pair<Int, Int>? = nearestTile(b) { it.terrain == Terrain.WATER }
    private fun nearestMaterials(b: Being): Pair<Int, Int>? = nearestTile(b) { it.materials > 2.0 }
    private fun nearestShelter(b: Being): Pair<Int, Int>? = nearestTile(b) { it.shelterQuality > 0.4 }

    /**
     * Where to put the next hour's building. Rather than scatter new half-shelters and
     * burn materials, a being tops up their own home first, then the nearest standing
     * shelter that can still be worked, and only starts fresh when there's nothing to
     * reuse (§3.5).
     */
    internal fun chooseBuildTarget(b: Being): Pair<Int, Int>? {
        if (b.hasHome) {
            val h = world.tileAt(b.homeX, b.homeY)
            if (h.built < 0.95 && h.materials > 2.0) return b.homeX to b.homeY
        }
        nearestTile(b) { it.shelterQuality in 0.2..0.95 && it.materials > 2.0 }?.let { return it }
        return nearestMaterials(b)
    }

    private inline fun nearestTile(b: Being, predicate: (world.larutan.engine.world.Tile) -> Boolean): Pair<Int, Int>? {
        val r = forageSearchRadius()
        var best: Pair<Int, Int>? = null
        var bestDist = Int.MAX_VALUE
        for (dy in -r..r) for (dx in -r..r) {
            val nx = b.x + dx
            val ny = b.y + dy
            if (!world.inBounds(nx, ny)) continue
            if (!predicate(world.tileAt(nx, ny))) continue
            val dist = chebyshev(b.x, b.y, nx, ny)
            if (dist < bestDist) { bestDist = dist; best = nx to ny }
        }
        return best
    }

    private fun nearestOther(b: Being): Being? =
        beings.filter { it.alive && it.id != b.id && chebyshev(b.x, b.y, it.x, it.y) <= 8 }
            .minByOrNull { chebyshev(b.x, b.y, it.x, it.y) }

    // ---- movement -----------------------------------------------------------

    /** Step one tile toward (tx,ty); returns true once adjacent-or-on it. */
    private fun stepToward(b: Being, tx: Int, ty: Int): Boolean {
        if (chebyshev(b.x, b.y, tx, ty) <= 0) return true
        val nx = b.x + (tx - b.x).coerceIn(-1, 1)
        val ny = b.y + (ty - b.y).coerceIn(-1, 1)
        if (world.inBounds(nx, ny)) { b.x = nx; b.y = ny }
        return chebyshev(b.x, b.y, tx, ty) <= 0
    }

    private fun stepRandom(b: Being) {
        val nx = (b.x + rng.nextIntRange(-1, 1)).coerceIn(0, world.width - 1)
        val ny = (b.y + rng.nextIntRange(-1, 1)).coerceIn(0, world.height - 1)
        b.x = nx; b.y = ny
    }

    private fun stepOutward(b: Being) {
        val cx = world.width / 2
        val cy = world.height / 2
        val dx = if (b.x == cx) rng.nextIntRange(-1, 1) else (b.x - cx).coerceIn(-1, 1)
        val dy = if (b.y == cy) rng.nextIntRange(-1, 1) else (b.y - cy).coerceIn(-1, 1)
        val nx = (b.x + dx).coerceIn(0, world.width - 1)
        val ny = (b.y + dy).coerceIn(0, world.height - 1)
        b.x = nx; b.y = ny
    }

    private fun chebyshev(x1: Int, y1: Int, x2: Int, y2: Int): Int =
        maxOf(kotlin.math.abs(x1 - x2), kotlin.math.abs(y1 - y2))

    // ---- memory helpers -----------------------------------------------------

    private fun record(b: Being, kind: MemoryKind, detail: String, weight: Double, valence: Double, subject: Int? = null) {
        b.memory.record(MemoryEvent(world.tick, kind, detail, subject, weight, valence, salience = 1.0))
    }

    /** Record a memory of this kind at most once per day, so pain doesn't spam the log. */
    private fun recordOnce(b: Being, kind: MemoryKind, detail: String, valence: Double, weight: Double) {
        val today = world.tick / World.TICKS_PER_DAY
        val already = b.memory.events.any { it.kind == kind && it.tick / World.TICKS_PER_DAY == today }
        if (!already) record(b, kind, detail, weight, valence)
    }
}
