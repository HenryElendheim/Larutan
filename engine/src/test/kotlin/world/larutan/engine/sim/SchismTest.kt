package world.larutan.engine.sim

import world.larutan.engine.being.BeliefKind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A people split in belief may one day split in fact -- a camp breaking away (§9). */
class SchismTest {

    private fun freshSim(seed: Long = 3L): Simulation {
        val config = WorldConfig(width = 24, height = 24, startingPopulation = 6, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun aDividedPeopleCanBreakApart() {
        val sim = freshSim()
        sim.world.foundingMyth = "they gathered and made a place their own"
        val beings = sim.beings
        beings.take(3).forEach { it.hold(BeliefKind.THE_FAR_PLACES_CALL, 0.6, "far ground") }
        beings.drop(3).take(3).forEach { it.hold(BeliefKind.HARD_WORK_PROVIDES, 0.6, "a life of work") }

        sim.recognizeDivision()
        assertTrue(sim.world.divided)

        // The breaking-away is an occasional thing; run the chance until it happens.
        var tries = 0
        while (!sim.world.schismed && tries < 400) { sim.maybeSchism(); tries++ }

        assertTrue(sim.world.schismed, "a camp breaks away")
        assertNotNull(sim.world.breakawaySettlement, "and founds a place of its own")
        assertTrue(
            sim.chronicle.recent(20).any { it.text.contains("broke away") },
            "and the parting is set down",
        )
    }

    @Test
    fun aPartingCanHealWhenTheyDriftBackTogether() {
        val sim = freshSim(seed = 5L)
        // Force a schism state directly.
        sim.world.divided = true
        sim.world.schismed = true
        sim.world.breakawaySettlement = "Somewhere"
        // The living wander back within sight of one another.
        sim.beings.forEach { it.x = 10; it.y = 10 }

        sim.maybeReunite()
        assertFalse(sim.world.schismed, "back in reach, they become one again")
        assertTrue(sim.chronicle.recent(20).any { it.text.contains("became one again") })
    }
}
