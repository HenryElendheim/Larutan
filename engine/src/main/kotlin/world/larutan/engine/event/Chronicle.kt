package world.larutan.engine.event

import kotlinx.serialization.Serializable

/** The kind of notable thing the world recorded happening. */
@Serializable
enum class EventKind {
    BOND_FORMED, BOND_BROKEN, BIRTH, DEATH,
    GOAL_FORMED, MILESTONE, GOAL_ACHIEVED, GOAL_FAILED,
    HARDSHIP, WEATHER, SEASON_TURN, COPED,
}

/**
 * Everything meaningful emits an event — so thoughts, dreams, the chronicle, and
 * (later) art always have something to hook into. The core never renders these;
 * it just states, plainly, what happened.
 */
@Serializable
data class WorldEvent(
    val tick: Long,
    val kind: EventKind,
    val text: String,
    val beingId: Int? = null,
    val otherId: Int? = null,
    val significant: Boolean = false,
)

/**
 * A running text history of the world — births, deaths, hard winters, a bond
 * forming, a dream. You can read it back like the history of a place.
 */
@Serializable
class Chronicle(val entries: MutableList<WorldEvent> = mutableListOf()) {
    fun add(e: WorldEvent) {
        entries += e
        if (entries.size > 500) entries.removeAt(0)
    }

    fun recent(limit: Int = 20): List<WorldEvent> = entries.takeLast(limit)

    /** The moments worth surfacing to the watcher no matter how fast time runs. */
    fun significant(limit: Int = 20): List<WorldEvent> =
        entries.filter { it.significant }.takeLast(limit)
}
