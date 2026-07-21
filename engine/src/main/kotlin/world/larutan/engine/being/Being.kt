package world.larutan.engine.being

import kotlinx.serialization.Serializable

/** The turns of a life. Each reshapes what a being wants and how it acts. */
@Serializable
enum class LifeStage(val label: String) {
    INFANT("infant"),
    CHILD("child"),
    ADOLESCENT("adolescent"),
    ADULT("adult"),
    ELDER("elder");

    companion object {
        fun forAge(years: Double): LifeStage = when {
            years < 2 -> INFANT
            years < 7 -> CHILD
            years < 13 -> ADOLESCENT
            years < 20 -> ADULT
            else -> ELDER
        }
    }
}

/** A being's family ties. */
@Serializable
data class Lineage(
    val parents: MutableList<Int> = mutableListOf(),
    val children: MutableList<Int> = mutableListOf(),
    var mate: Int? = null,
)

/**
 * A whole soul. A being is a bundle of state; everything else — behaviour,
 * thoughts, dreams, story — is produced *from* this state. There are no NPCs and
 * protagonists here, only beings, and you choose whom to follow.
 */
@Serializable
class Being(
    val id: Int,
    var name: String, // a god may rename them
    var x: Int,
    var y: Int,
    val personality: Personality,
    val drives: Drives = Drives(),
    val emotion: Emotion = Emotion(),
    val memory: MemoryLog = MemoryLog(),
    val relationships: MutableMap<Int, Relationship> = mutableMapOf(),
    val skills: Skills = Skills(),
    val beliefs: MutableList<Belief> = mutableListOf(),
    var goal: Goal? = null,
    val lineage: Lineage = Lineage(),
    var generation: Int = 1,
    var ageYears: Double = 20.0,
    var alive: Boolean = true,
    var immortal: Boolean = false, // a god's gift: halts aging and holds death away
    var birthTick: Long = 0,
    /** The being's dot colour in the 2D view (a hue seed) — a god may recolour them. */
    var appearanceSeed: Int = 0,
    /** How large the being's dot draws, 1.0 being ordinary — a god may resize them. */
    var size: Double = 1.0,
    // --- transient-ish state the panel reads ---
    var currentAction: String = "waking",
    var foodStore: Double = 0.0,
    var illness: Double = 0.0,           // 0 is well; climbs when sick, drains health, and can spread
    var homeX: Int = -1,                 // the place they've come to call home, or -1 for none
    var homeY: Int = -1,
    var reputation: Double = 0.0,        // how the group regards them, -1 (ill-regarded) .. +1 (well-loved)
    var eminent: Boolean = false,        // a figure the group has come to look to, from standing and years
    var deathCause: String? = null,
    // --- the afterlife (§10.7) ---
    var realm: Realm? = null,            // where the soul settled; null while alive
    var moralLedger: Double = 0.0,       // a quiet weighing of the life, which sets the realm at death
    var finalThought: String? = null,    // the last reflection, left behind for others to find
    var deathTick: Long? = null,         // when they died, so the memory of them can fade with time
    var epitaph: String? = null,         // what the long-gone become: a name and a line
    var reincarnated: Boolean = false,   // once reborn, the soul has moved on from the afterlife
    var lastThought: String? = null,
    val recentThoughts: MutableList<String> = mutableListOf(),
    var lastDream: String? = null,
    // --- coping and its costs (§4.4) ---
    /** How strongly each maladaptive coping style has taken hold, 0..1. */
    val habits: MutableMap<CopingHabit, Double> = mutableMapOf(),
    /** Dependency on numbing: relief shrinks as this climbs, so they reach for it more. */
    var tolerance: Double = 0.0,
) {
    val lifeStage: LifeStage get() = LifeStage.forAge(ageYears)

    /** The habit grown strong enough to read as a vice, if any. */
    val vice: CopingHabit? get() = habits.entries.filter { it.value >= VICE_THRESHOLD }.maxByOrNull { it.value }?.key

    /** How firmly a given coping style has taken hold, 0..1. */
    fun habitStrength(h: CopingHabit): Double = habits[h] ?: 0.0

    /** Noticeably unwell — enough for it to show and for others to want to tend them. */
    val ailing: Boolean get() = alive && illness > 0.15

    /** True once they've a place to call home. */
    val hasHome: Boolean get() = homeX >= 0 && homeY >= 0

    /** The conviction they hold most firmly, if it's strong — the seed of which camp they lean to. */
    val creed: BeliefKind? get() = beliefs.maxByOrNull { it.strength }?.takeIf { it.strength > 0.3 }?.kind

    fun relationshipWith(otherId: Int): Relationship =
        relationships.getOrPut(otherId) { Relationship(otherId) }

    fun think(text: String) {
        lastThought = text
        recentThoughts += text
        while (recentThoughts.size > 12) recentThoughts.removeAt(0)
    }

    /** Come to hold a belief, or hold an existing one a little more firmly. */
    fun hold(kind: BeliefKind, delta: Double, bornFrom: String) {
        val existing = beliefs.firstOrNull { it.kind == kind }
        if (existing != null) {
            existing.strength = (existing.strength + delta).coerceIn(0.0, 1.0)
        } else if (delta > 0.0) {
            beliefs += Belief(kind, delta.coerceIn(0.1, 1.0), bornFrom)
        }
    }

    /** A rough one-line read of how they're doing, for logs and the panel header. */
    fun statusLine(): String =
        "$name ($generation·${lifeStage.label}) — ${emotion.moodLabel()}, ${currentAction}"

    companion object {
        /** How firm a habit must be before it reads as a vice. */
        const val VICE_THRESHOLD = 0.5
    }
}
