package world.larutan.engine.sim

import world.larutan.engine.being.BeliefKind
import kotlin.test.Test
import kotlin.test.assertTrue

/** When spring comes round, the people gather to mark the year's turn (§9). */
class FestivalTest {

    private fun freshSim(seed: Long = 4L): Simulation {
        val config = WorldConfig(width = 20, height = 20, startingPopulation = 4, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun springsReturnBringsAGathering() {
        val sim = freshSim()
        // Hold everyone through the winter so the gathering has a crowd.
        sim.beings.forEach { it.immortal = true }
        // Sit just before the year's turn (winter's last hours), then let it come round.
        sim.world.tick = 1150L
        sim.run(4)

        assertTrue(
            sim.chronicle.recent(40).any { it.text.contains("gathered to mark the year's turn") },
            "the festival is chronicled as spring returns",
        )
        assertTrue(
            sim.living().any { b -> b.beliefs.any { it.kind == BeliefKind.THERE_IS_JOY_IN_GATHERING } },
            "and those who gathered come to hold that there is joy in it",
        )
    }
}
