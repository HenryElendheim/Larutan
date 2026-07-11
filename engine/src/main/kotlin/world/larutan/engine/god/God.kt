package world.larutan.engine.god

import world.larutan.engine.being.Being
import world.larutan.engine.being.DriveType
import world.larutan.engine.being.EmotionName
import world.larutan.engine.being.Goal
import world.larutan.engine.being.GoalKind
import world.larutan.engine.being.GoalStatus
import world.larutan.engine.being.MemoryEvent
import world.larutan.engine.being.MemoryKind
import world.larutan.engine.being.Sentiment
import world.larutan.engine.event.EventKind
import world.larutan.engine.event.WorldEvent
import world.larutan.engine.sim.Simulation

/**
 * Your reach into the world. Every power here is just an operation on the state
 * the simulation already holds -- not magic, but authored reach. Observation is
 * the default; this is when you decide to touch a life.
 *
 * To the living, your voice arrives as a whisper they read through their own
 * nature: one is lifted by it, one is unsettled. You author the cause; their own
 * systems author what it means.
 */
class God(private val sim: Simulation) {

    private val world get() = sim.world

    // ---- provision ----------------------------------------------------------

    /** Give food into a being's store, food that seems to come from nowhere. */
    fun provide(id: Int, amount: Double = 40.0) {
        val b = alive(id) ?: return
        b.foodStore += amount
        b.drives.change(DriveType.HUNGER, amount * 0.5)
        remember(b, MemoryKind.STORED, "food, come from nowhere", 0.4, 0.5)
        log("Something provided for ${b.name}.", b.id)
    }

    /** Take food away -- scarcity by decree. */
    fun deprive(id: Int, amount: Double = 40.0) {
        val b = alive(id) ?: return
        b.foodStore = (b.foodStore - amount).coerceAtLeast(0.0)
        b.drives.change(DriveType.HUNGER, -amount * 0.4)
        log("The world went bare for ${b.name}.", b.id)
    }

    // ---- bless / afflict ----------------------------------------------------

    /** Grant vigour: fill the pressing needs and lift the mood. */
    fun bless(id: Int) {
        val b = alive(id) ?: return
        listOf(DriveType.HUNGER, DriveType.THIRST, DriveType.WARMTH, DriveType.ENERGY, DriveType.HEALTH)
            .forEach { b.drives[it] = (b.drives[it] + 40.0).coerceAtMost(100.0) }
        b.emotion.valence = (b.emotion.valence + 0.4).coerceAtMost(1.0)
        b.emotion.feel(EmotionName.RELIEF, 0.5)
        b.emotion.distressLoad = (b.emotion.distressLoad - 25).coerceAtLeast(0.0)
        remember(b, MemoryKind.RESTED, "a sudden, unearned ease", 0.5, 0.6)
        log("A blessing settled on ${b.name}.", b.id)
    }

    /** Send hardship: drain health and press distress down on them. */
    fun afflict(id: Int) {
        val b = alive(id) ?: return
        b.drives.change(DriveType.HEALTH, -30.0)
        b.emotion.valence = (b.emotion.valence - 0.4).coerceAtLeast(-1.0)
        b.emotion.feel(EmotionName.FEAR, 0.5)
        b.emotion.distressLoad = (b.emotion.distressLoad + 30).coerceAtMost(100.0)
        remember(b, MemoryKind.HURT, "a sudden, sourceless suffering", 0.7, -0.7)
        log("Hardship fell on ${b.name}.", b.id, kind = EventKind.HARDSHIP)
    }

    /** Warm one being to the bone, whatever the season. */
    fun warm(id: Int) {
        val b = alive(id) ?: return
        b.drives[DriveType.WARMTH] = 100.0
        log("Warmth reached ${b.name}.", b.id)
    }

    // ---- inspire / whisper --------------------------------------------------

    /**
     * Plant an aspiration. A push toward becoming something -- the being's own
     * systems still do the climbing. If no kind is named, one is chosen to fit them.
     */
    fun inspire(id: Int, kind: GoalKind? = null) {
        val b = alive(id) ?: return
        val goal = if (kind != null) {
            Goal(kind, Goal.milestonesForPublic(kind), bornFrom = "a longing that arrived from outside", intensity = 0.7)
        } else {
            Goal.formFor(b.personality, b.memory) ?: return
        }
        goal.status = GoalStatus.ACTIVE
        b.goal = goal
        b.drives.change(DriveType.PURPOSE, 20.0)
        b.think("I want to ${goal.target}. I don't know where the wanting came from.")
        remember(b, MemoryKind.ACHIEVED, "a purpose that arrived from nowhere: to ${goal.target}", 0.5, 0.5)
        log("${b.name} was moved to ${goal.target}.", b.id, kind = EventKind.GOAL_FORMED)
    }

    /**
     * Speak into a being's inner life. How they take it depends on who they are:
     * a hopeful soul feels touched by something holy; a fearful one is unsettled.
     */
    fun whisper(id: Int, words: String) {
        val b = alive(id) ?: return
        b.think(words)
        val hopeful = b.personality.optimism > 0.15 && b.personality.temper < 0.4
        if (hopeful) {
            b.emotion.feel(EmotionName.AWE, 0.4)
            b.emotion.valence = (b.emotion.valence + 0.2).coerceAtMost(1.0)
            remember(b, MemoryKind.REFLECTED, "a voice in the quiet, and it felt like grace", 0.6, 0.6)
        } else {
            b.emotion.feel(EmotionName.FEAR, 0.3)
            remember(b, MemoryKind.FEARED, "a voice in the quiet, and it frightened them", 0.6, -0.4)
        }
        log("A voice reached ${b.name}.", b.id)
    }

    // ---- longevity ----------------------------------------------------------

    fun grantImmortality(id: Int) {
        val b = alive(id) ?: return
        b.immortal = true
        remember(b, MemoryKind.ACHIEVED, "time stopped touching them", 0.8, 0.3)
        log("${b.name} was made ageless.", b.id, significant = true)
    }

    fun revokeImmortality(id: Int) {
        alive(id)?.let { it.immortal = false; log("Time found ${it.name} again.", it.id) }
    }

    // ---- bonds --------------------------------------------------------------

    fun mendBond(aId: Int, bId: Int, amount: Double = 25.0) {
        val a = alive(aId) ?: return
        val b = alive(bId) ?: return
        a.relationshipWith(bId).warm(amount)
        b.relationshipWith(aId).warm(amount)
        log("Something drew ${a.name} and ${b.name} closer.", a.id, b.id)
    }

    fun severBond(aId: Int, bId: Int, amount: Double = 25.0) {
        val a = alive(aId) ?: return
        val b = alive(bId) ?: return
        a.relationshipWith(bId).cool(amount)
        b.relationshipWith(aId).cool(amount)
        a.relationshipWith(bId).sentiment = Sentiment.RESENTMENT
        log("Something came between ${a.name} and ${b.name}.", a.id, b.id)
    }

    // ---- helpers ------------------------------------------------------------

    private fun alive(id: Int): Being? = sim.byId(id)?.takeIf { it.alive }

    private fun remember(b: Being, kind: MemoryKind, detail: String, weight: Double, valence: Double) {
        b.memory.record(MemoryEvent(world.tick, kind, detail, null, weight, valence, salience = 1.0))
    }

    private fun log(text: String, beingId: Int, otherId: Int? = null, kind: EventKind = EventKind.COPED, significant: Boolean = false) {
        sim.chronicle.add(WorldEvent(world.tick, kind, text, beingId, otherId, significant))
    }
}
