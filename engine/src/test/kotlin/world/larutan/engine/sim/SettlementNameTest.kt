package world.larutan.engine.sim

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** When shelters gather in one place, the settled ground earns a name that outlives them (§9). */
class SettlementNameTest {

    private fun freshSim(seed: Long = 4L): Simulation {
        val config = WorldConfig(width = 22, height = 22, startingPopulation = 4, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    private fun raiseShelter(sim: Simulation, x: Int, y: Int) {
        sim.world.tileAt(x, y).built = 0.6
    }

    @Test
    fun gatheredSheltersEarnAName() {
        val sim = freshSim()
        raiseShelter(sim, 10, 10)
        raiseShelter(sim, 11, 10)
        raiseShelter(sim, 10, 11)

        sim.maybeNameSettlement()
        assertNotNull(sim.world.settlementName, "a cluster of homes makes a settlement, and a settlement gets a name")
        assertTrue(sim.chronicle.recent(20).any { it.text.contains("came to be called") })
    }

    @Test
    fun scatteredSheltersAreNotASettlement() {
        val sim = freshSim(seed = 5L)
        raiseShelter(sim, 2, 2)
        raiseShelter(sim, 19, 2)
        raiseShelter(sim, 10, 19)

        sim.maybeNameSettlement()
        assertNull(sim.world.settlementName, "shelters scattered wide aren't a place yet")
    }
}
