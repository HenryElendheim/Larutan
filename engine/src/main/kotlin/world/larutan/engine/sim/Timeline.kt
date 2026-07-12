package world.larutan.engine.sim

import world.larutan.engine.world.Season
import world.larutan.engine.world.World

/** A moment the player can roll back to: when it was and how to name it. */
data class TimelineMoment(val tick: Long, val label: String)

/**
 * A rolling history of world snapshots, so time can be reversed (§10.5). It runs on
 * the same complete serialization save/load use, so a rewind is just restoring one.
 *
 * History is tiered so a long span fits in bounded memory: recent time is kept dense
 * (a snapshot every few world-hours), older time thins to one a day, and older still
 * to one a season -> you can roll back finely in the recent past and coarsely across
 * years. Each point is its own serialized string, a true independent copy. The
 * history lives in memory for the session; it isn't part of the saved world.
 */
class Timeline(
    private val recentCadence: Int = 6, // a dense snapshot every quarter-day...
    private val recentCap: Int = 24,    // ...for the last several days
    private val dailyCap: Int = 40,     // one a day, going back weeks
    private val seasonalCap: Int = 20,  // one a season, going back years
) {
    private class Point(val tick: Long, val json: String)

    private val recent = ArrayDeque<Point>()
    private val daily = ArrayDeque<Point>()
    private val seasonal = ArrayDeque<Point>()

    /** Record into whichever tiers this tick belongs to. */
    fun maybeRecord(sim: Simulation) {
        val w = sim.world
        if (recent.isEmpty() || w.tick % recentCadence == 0L) push(recent, recentCap, sim)
        if (w.hourOfDay == 0) push(daily, dailyCap, sim)
        if (w.hourOfDay == 0 && w.dayOfSeason == 0) push(seasonal, seasonalCap, sim)
    }

    fun record(sim: Simulation) = push(recent, recentCap, sim)

    private fun push(buf: ArrayDeque<Point>, cap: Int, sim: Simulation) {
        val tick = sim.world.tick
        if (buf.lastOrNull()?.tick == tick) return // don't record the same moment twice in a tier
        buf.addLast(Point(tick, Persistence.serialize(Persistence.snapshot(sim))))
        while (buf.size > cap) buf.removeFirst()
    }

    /** Every distinct moment held, across all tiers, oldest first. */
    fun moments(): List<TimelineMoment> {
        val ticks = sortedSetOf<Long>()
        seasonal.forEach { ticks.add(it.tick) }
        daily.forEach { ticks.add(it.tick) }
        recent.forEach { ticks.add(it.tick) }
        return ticks.map { TimelineMoment(it, labelFor(it)) }
    }

    fun isEmpty(): Boolean = recent.isEmpty() && daily.isEmpty() && seasonal.isEmpty()

    /**
     * Roll back to a held moment and discard everything after it across every tier
     * (a plain rewind lets the future go). Null if that moment is no longer held.
     */
    fun rewindTo(tick: Long): String? {
        val json = find(recent, tick) ?: find(daily, tick) ?: find(seasonal, tick) ?: return null
        recent.removeAll { it.tick > tick }
        daily.removeAll { it.tick > tick }
        seasonal.removeAll { it.tick > tick }
        return json
    }

    fun clear() {
        recent.clear(); daily.clear(); seasonal.clear()
    }

    private fun find(buf: ArrayDeque<Point>, tick: Long): String? = buf.firstOrNull { it.tick == tick }?.json

    private fun labelFor(tick: Long): String {
        val day = tick / World.TICKS_PER_DAY
        val hour = (tick % World.TICKS_PER_DAY).toInt()
        val year = day / (World.DAYS_PER_SEASON * 4)
        val season = Season.entries[((day / World.DAYS_PER_SEASON) % 4).toInt()]
        val dayOfSeason = (day % World.DAYS_PER_SEASON).toInt()
        return "Y$year ${season.label} d${dayOfSeason + 1} ${hour.toString().padStart(2, '0')}h"
    }
}
