package world.larutan.engine.sim

import world.larutan.engine.world.World
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Coming through the first hard winter becomes the group's founding story (§9). */
class FoundingMythTest {

    private fun freshSim(seed: Long = 6L): Simulation {
        val config = WorldConfig(width = 22, height = 22, startingPopulation = 5, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun survivingTheFirstHardSpellFoundsAMyth() {
        val sim = freshSim()
        // Hold them safe through it, and set the hard spell to break at the next dawn.
        sim.beings.forEach { it.immortal = true }
        sim.world.harshSpell = 1
        // Land on a dawn (hour 6), when the weather is rerolled.
        sim.world.tick = 6L
        sim.step() // dawn: the spell ticks down to 0 and breaks

        assertNotNull(sim.world.foundingMyth, "the winter they survived becomes their origin story")
        assertTrue(
            sim.chronicle.recent(30).any { it.text.contains("A story took hold") },
            "and it's marked as a story taking hold",
        )
    }
}
