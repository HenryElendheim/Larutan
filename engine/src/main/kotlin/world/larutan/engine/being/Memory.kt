package world.larutan.engine.being

import kotlinx.serialization.Serializable

/** The sort of thing that happened, as a being remembers it. */
@Serializable
enum class MemoryKind {
    ATE, DRANK, RESTED, WARMED, FORAGED, STORED,
    WANDERED, EXPLORED, REFLECTED, PLAYED,
    BONDED, SOCIALIZED, COMFORTED, HELPED, REJECTED, LASHED_OUT,
    LOST, GRIEVED, FEARED, HURT, STARVED, FROZE,
    ACHIEVED, MILESTONE, FAILED, BORN,
}

/**
 * One remembered moment. This is what turns a state machine into someone with a
 * past — memories feed decisions, thoughts, dreams, and grief. Salience decays
 * unless reinforced, but a heavy emotional weight keeps a memory for a lifetime.
 */
@Serializable
data class MemoryEvent(
    val tick: Long,
    val kind: MemoryKind,
    val detail: String,
    val subjectId: Int? = null,   // another being, if one was involved
    val emotionalWeight: Double,  // 0..1, how hard it landed
    val valenceAtTime: Double,    // -1..+1, good or bad
    var salience: Double,         // 0..1, how present it still is
)

@Serializable
class MemoryLog(val events: MutableList<MemoryEvent> = mutableListOf()) {

    fun record(e: MemoryEvent) {
        events += e
        // Keep the log bounded: faint, old memories fall out on their own.
        if (events.size > 240) {
            events.sortByDescending { it.salience + it.emotionalWeight }
            while (events.size > 200) events.removeAt(events.size - 1)
        }
    }

    /** Salience ebbs each day; heavy memories resist forgetting. */
    fun decay() {
        for (e in events) {
            val floor = e.emotionalWeight * 0.6 // the loss of a mate is never fully forgotten
            e.salience = (e.salience - 0.01).coerceAtLeast(floor * e.salience.coerceAtMost(1.0)).coerceAtLeast(0.0)
            if (e.emotionalWeight > 0.7) e.salience = e.salience.coerceAtLeast(0.35)
        }
        events.removeAll { it.salience < 0.03 && it.emotionalWeight < 0.5 }
    }

    /** The memories most present right now — recent, salient, heavy. */
    fun salient(limit: Int = 5): List<MemoryEvent> =
        events.sortedByDescending { it.salience * 0.6 + it.emotionalWeight * 0.4 }.take(limit)

    /** Keep only the most present memories, letting the rest go. This is how the dead fade to legend (§10.7). */
    fun compressTo(limit: Int) {
        if (events.size <= limit) return
        val keep = salient(limit).toHashSet()
        events.retainAll { it in keep }
    }

    fun involving(otherId: Int, limit: Int = 4): List<MemoryEvent> =
        events.filter { it.subjectId == otherId }
            .sortedByDescending { it.salience + it.emotionalWeight }
            .take(limit)

    fun heaviest(): MemoryEvent? = events.maxByOrNull { it.emotionalWeight * it.salience }
}
