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
    val name: String,
    var x: Int,
    var y: Int,
    val personality: Personality,
    val drives: Drives = Drives(),
    val emotion: Emotion = Emotion(),
    val memory: MemoryLog = MemoryLog(),
    val relationships: MutableMap<Int, Relationship> = mutableMapOf(),
    var goal: Goal? = null,
    val lineage: Lineage = Lineage(),
    var generation: Int = 1,
    var ageYears: Double = 20.0,
    var alive: Boolean = true,
    var birthTick: Long = 0,
    /** A stable seed for the being's dot colour in the 2D view. */
    val appearanceSeed: Int = 0,
    // --- transient-ish state the panel reads ---
    var currentAction: String = "waking",
    var foodStore: Double = 0.0,
    var deathCause: String? = null,
    var lastThought: String? = null,
    val recentThoughts: MutableList<String> = mutableListOf(),
    var lastDream: String? = null,
) {
    val lifeStage: LifeStage get() = LifeStage.forAge(ageYears)

    fun relationshipWith(otherId: Int): Relationship =
        relationships.getOrPut(otherId) { Relationship(otherId) }

    fun think(text: String) {
        lastThought = text
        recentThoughts += text
        while (recentThoughts.size > 12) recentThoughts.removeAt(0)
    }

    /** A rough one-line read of how they're doing, for logs and the panel header. */
    fun statusLine(): String =
        "$name ($generation·${lifeStage.label}) — ${emotion.moodLabel()}, ${currentAction}"
}
