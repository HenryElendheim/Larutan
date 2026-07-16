package world.larutan.engine.sim

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Standing, held by someone grown, makes a figure the group looks to -- and it can be lost (§9). */
class LeadershipTest {

    private fun freshSim(seed: Long = 3L): Simulation {
        val config = WorldConfig(width = 20, height = 20, startingPopulation = 4, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun highStandingInAGrownBeingMakesThemEminent() {
        val sim = freshSim()
        val b = sim.beings.first()
        b.ageYears = 30.0 // grown
        b.reputation = 0.7

        sim.recognizeEminence(b)
        assertTrue(b.eminent, "well-regarded and grown, they become one the group looks to")
        assertTrue(sim.chronicle.recent(20).any { it.text.contains("come to look to") })
    }

    @Test
    fun theYoungAreNotYetLookedTo() {
        val sim = freshSim(seed = 4L)
        val b = sim.beings.first()
        b.ageYears = 5.0 // a child
        b.reputation = 0.9

        sim.recognizeEminence(b)
        assertFalse(b.eminent, "standing alone isn't enough without the years")
    }

    @Test
    fun eminenceIsLostIfRegardFallsAway() {
        val sim = freshSim(seed = 5L)
        val b = sim.beings.first()
        b.ageYears = 30.0
        b.eminent = true
        b.reputation = 0.1 // fallen from regard

        sim.recognizeEminence(b)
        assertFalse(b.eminent, "regard gone, the standing goes with it")
    }
}
