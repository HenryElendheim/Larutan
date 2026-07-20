package world.larutan.engine.sim

import world.larutan.engine.being.BeliefKind
import kotlin.test.Test
import kotlin.test.assertTrue

/** A divided people looks to two figures -- one for each camp of belief (§9). */
class CampFigureTest {

    private fun freshSim(seed: Long = 3L): Simulation {
        val config = WorldConfig(width = 24, height = 24, startingPopulation = 6, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun eachCampComesToLookToItsOwn() {
        val sim = freshSim()
        sim.world.divided = true
        val b = sim.beings
        // Two camps of three, each with one clearly-esteemed grown member.
        b.take(3).forEach { it.ageYears = 30.0; it.hold(BeliefKind.THE_FAR_PLACES_CALL, 0.6, "far ground") }
        b.drop(3).take(3).forEach { it.ageYears = 30.0; it.hold(BeliefKind.HARD_WORK_PROVIDES, 0.6, "a life of work") }
        b[0].reputation = 0.7
        b[3].reputation = 0.7

        sim.recognizeCampFigures()
        assertTrue(b[0].eminent, "the far-places camp looks to its own")
        assertTrue(b[3].eminent, "and the hard-work camp to its own")
        assertTrue(sim.chronicle.recent(20).count { it.text.contains("come to look to") } >= 2)
    }
}
