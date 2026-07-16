package world.larutan.engine.sim

import kotlin.test.Test
import kotlin.test.assertTrue

/** When a figure the group looked to dies, the group feels it and someone may rise (§9). */
class SuccessionTest {

    private fun freshSim(seed: Long = 6L): Simulation {
        val config = WorldConfig(width = 22, height = 22, startingPopulation = 5, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun theDeathOfAFigureTurnsTheGroupToAnother() {
        val sim = freshSim()
        val figure = sim.beings[0]
        val successor = sim.beings[1]

        figure.eminent = true
        figure.reputation = 0.7 // a figure's standing holds them eminent to the end
        figure.ageYears = 85.0 // out of time
        // A well-regarded survivor, and someone the figure was bonded to so they mourn.
        successor.ageYears = 30.0
        successor.reputation = 0.35
        successor.relationshipWith(figure.id).warm(60.0)
        val repBefore = successor.reputation

        sim.run(3)

        assertTrue(!figure.alive, "the figure has died")
        assertTrue(successor.reputation > repBefore, "the group's regard turns toward the successor")
        assertTrue(
            sim.chronicle.recent(40).any { it.text.contains("begin to look to") },
            "and the turning is chronicled",
        )
    }
}
