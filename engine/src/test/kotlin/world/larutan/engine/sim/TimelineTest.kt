package world.larutan.engine.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Reversing time is restoring an earlier snapshot; the same serialization save/load uses (§10.5). */
class TimelineTest {

    private fun freshSim(seed: Long = 4L): Simulation {
        val config = WorldConfig(width = 22, height = 22, startingPopulation = 5, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun rewindRestoresTheWorldAsItWas() {
        val sim = freshSim()
        val timeline = Timeline(cadenceTicks = 1, maxPoints = 200)
        repeat(40) { timeline.maybeRecord(sim); sim.step() } // records ticks 0..39
        timeline.maybeRecord(sim)                            // ...and tick 40

        // Wander further into the future, then roll back.
        repeat(30) { sim.step() }
        assertEquals(70L, sim.world.tick)

        val json = timeline.rewindTo(15)
        assertNotNull(json, "the moment should still be held")
        val restored = Persistence.restore(Persistence.deserialize(json))
        assertEquals(15L, restored.world.tick, "rewind lands exactly where it was")
        assertTrue(restored.beings.isNotEmpty())

        // A plain rewind discards the future -> nothing later than 15 remains.
        assertTrue(timeline.moments().all { it.tick <= 15 })
    }

    @Test
    fun replayingFromARewindIsDeterministic() {
        val sim = freshSim(seed = 8L)
        val timeline = Timeline(cadenceTicks = 1, maxPoints = 200)
        repeat(25) { timeline.maybeRecord(sim); sim.step() }
        val json = timeline.rewindTo(10)!!

        // Two worlds restored from the same moment must run the same way forward
        // (the RNG state rides along in the snapshot).
        val a = Persistence.restore(Persistence.deserialize(json)).also { it.run(30) }
        val b = Persistence.restore(Persistence.deserialize(json)).also { it.run(30) }
        assertEquals(
            Persistence.serialize(Persistence.snapshot(a)),
            Persistence.serialize(Persistence.snapshot(b)),
            "replay from a rewind point is deterministic",
        )
    }
}
