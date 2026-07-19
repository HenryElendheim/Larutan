package world.larutan.engine.sim

import world.larutan.engine.world.World
import kotlin.test.Test
import kotlin.test.assertTrue

/** Some years the land is generous, some lean, and it shifts what the ground bears (§3.5). */
class BountyTest {

    private fun freshSim(seed: Long = 2L): Simulation {
        val config = WorldConfig(width = 22, height = 22, startingPopulation = 4, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun eachYearsFortuneIsCastFresh() {
        val sim = freshSim()
        val seen = HashSet<Double>()
        repeat(40) {
            sim.rollYearBounty()
            assertTrue(sim.world.yearBounty in 0.6..1.4, "fortune stays within its bounds")
            seen += sim.world.yearBounty
        }
        assertTrue(seen.size > 1, "the years differ in their fortune")
    }

    @Test
    fun aPlentifulYearBearsMoreThanALeanOne() {
        // Regrow a day's worth on the same land, with no one eating, at two fortunes.
        fun regrownOverADay(bounty: Double): Double {
            val sim = freshSim()
            sim.world.yearBounty = bounty
            sim.world.tiles.forEach { it.food = 0.0 }
            repeat(World.TICKS_PER_DAY + 1) {
                sim.regrowResources()
                sim.world.tick++
            }
            return sim.world.tiles.sumOf { it.food }
        }
        assertTrue(regrownOverADay(1.4) > regrownOverADay(0.6), "a generous year grows more food than a lean one")
    }
}
