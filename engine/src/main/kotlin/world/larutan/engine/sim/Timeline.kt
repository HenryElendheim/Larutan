package world.larutan.engine.sim

import world.larutan.engine.world.World

/** A moment the player can roll back to: when it was and how to name it. */
data class TimelineMoment(val tick: Long, val label: String)

/**
 * A rolling history of world snapshots, so time can be reversed (§10.5). It runs on
 * the exact same complete serialization as save/load -> a rewind is just restoring
 * an earlier snapshot. Recent history is kept and capped; the oldest falls away.
 *
 * Each point is stored as its own serialized string, so it's a true independent copy
 * -> the world can keep changing without disturbing the moments already recorded. The
 * history lives in memory for the session; it isn't part of the saved world, so a
 * fresh launch starts a fresh timeline from wherever the world was left.
 */
class Timeline(
    private val cadenceTicks: Int = 6,   // a snapshot every quarter of a world-day
    private val maxPoints: Int = 48,     // ...capped at a couple of weeks of history
) {
    private data class Point(val tick: Long, val label: String, val json: String)

    private val points = ArrayDeque<Point>()

    /** Record a snapshot when this tick falls on the cadence (and always the first one). */
    fun maybeRecord(sim: Simulation) {
        if (points.isEmpty() || sim.world.tick % cadenceTicks == 0L) record(sim)
    }

    fun record(sim: Simulation) {
        val tick = sim.world.tick
        if (points.lastOrNull()?.tick == tick) return // don't record the same moment twice
        points.addLast(Point(tick, labelFor(sim.world), Persistence.serialize(Persistence.snapshot(sim))))
        while (points.size > maxPoints) points.removeFirst()
    }

    /** The moments held, oldest first — what the rewind strip shows. */
    fun moments(): List<TimelineMoment> = points.map { TimelineMoment(it.tick, it.label) }

    fun isEmpty(): Boolean = points.isEmpty()

    /**
     * Roll back to a held moment: return its serialized world and drop everything after
     * it, since a plain rewind discards the future. Null if that moment is no longer held.
     */
    fun rewindTo(tick: Long): String? {
        val idx = points.indexOfLast { it.tick <= tick }
        if (idx < 0) return null
        while (points.size > idx + 1) points.removeLast()
        return points.last().json
    }

    fun clear() = points.clear()

    private fun labelFor(w: World): String =
        "Y${w.year} ${w.season.label} d${w.dayOfSeason + 1} ${w.hourOfDay.toString().padStart(2, '0')}h"
}
