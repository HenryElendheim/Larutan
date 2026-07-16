package world.larutan.engine.sim

import kotlin.test.Test
import kotlin.test.assertEquals

/** Beings top up a home they already have rather than scatter new half-shelters (§3.5). */
class BuildReuseTest {

    private fun freshSim(seed: Long = 8L): Simulation {
        val config = WorldConfig(width = 22, height = 22, startingPopulation = 4, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun buildingReinforcesAnExistingHome() {
        val sim = freshSim()
        val b = sim.beings.first()
        // A home that isn't finished and still has materials to work with.
        val hx = b.x
        val hy = b.y
        sim.world.tileAt(hx, hy).apply { materials = 10.0; built = 0.5 }
        b.homeX = hx
        b.homeY = hy

        assertEquals(hx to hy, sim.chooseBuildTarget(b), "they go back to their home to build it up, not start anew")
    }
}
