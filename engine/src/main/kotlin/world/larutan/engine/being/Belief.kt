package world.larutan.engine.being

import kotlinx.serialization.Serializable

/**
 * A simple generalization a being forms from what it has lived (§4.7): winters are
 * cruel, others can be trusted, hard work provides. Beliefs are taught to children
 * and inherited a little from parents, so over generations they become shared
 * conviction -> worldview and myth, echoing and drifting down a lineage (§9).
 */
@Serializable
enum class BeliefKind(val statement: String) {
    WINTERS_ARE_CRUEL("winters are cruel"),
    OTHERS_CAN_BE_TRUSTED("others can be trusted"),
    THE_WORLD_TAKES_WHAT_YOU_LOVE("the world takes what you love"),
    HARD_WORK_PROVIDES("hard work provides"),
    THE_FAR_PLACES_CALL("there is more beyond the edge"),
}

@Serializable
data class Belief(
    val kind: BeliefKind,
    var strength: Double = 0.4,  // 0..1 — how firmly it's held
    val bornFrom: String,        // what planted it: an experience, an elder, a parent
) {
    val statement: String get() = kind.statement
}
