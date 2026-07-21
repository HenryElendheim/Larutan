package world.larutan.engine.sim

import world.larutan.engine.being.Being
import world.larutan.engine.being.CopingHabit
import world.larutan.engine.being.Personality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sustained distress hardens into a habit and then a visible vice, with a real
 * dependency behind the numbing -- and none of it is destiny: a being can climb
 * back out once the weight lifts (§4.4).
 */
class CopingTest {

    private fun freshSim(seed: Long = 7L): Simulation {
        val config = WorldConfig(width = 22, height = 22, startingPopulation = 4, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    /** A being with a flat, unremarkable nature -- turns to numbing, the fallback way. */
    private fun neutralBeing(): Being =
        Being(id = 900, name = "Test", x = 1, y = 1, personality = Personality(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0))

    @Test
    fun distressHardensIntoAViceWithDependency() {
        val sim = freshSim()
        val b = neutralBeing()
        sim.beings.add(b)

        var tries = 0
        while (b.vice == null && tries < 300) {
            b.emotion.distressLoad = 90.0 // the weight keeps coming; they keep reaching for relief
            sim.cope(b)
            tries++
        }

        assertNotNull(b.vice, "a vice takes hold under sustained distress")
        assertEquals(CopingHabit.NUMBING, b.vice, "a flat nature falls back on numbing")
        assertTrue(b.tolerance > 0.0, "numbing builds a dependency -- it takes more each time")
        assertTrue(
            sim.chronicle.entries.any { it.text.contains("numbing") && it.significant },
            "the vice forming is set down in the chronicle",
        )
    }

    @Test
    fun aViceCanBeClimbedOutOf() {
        val sim = freshSim(seed = 11L)
        val b = neutralBeing()
        sim.beings.add(b)

        // First, let it take hold.
        var tries = 0
        while (b.vice == null && tries < 300) {
            b.emotion.distressLoad = 90.0
            sim.cope(b)
            tries++
        }
        assertNotNull(b.vice, "the vice is in place before we test the climb out")

        // Then the weight lifts, and the habit loosens its grip.
        tries = 0
        while (b.vice != null && tries < 500) {
            b.emotion.distressLoad = 0.0
            sim.cope(b)
            tries++
        }

        assertNull(b.vice, "the vice fades once the distress is gone -- coping isn't destiny")
        assertTrue(
            sim.chronicle.entries.any { it.text.contains("less and less") && it.significant },
            "the climb back out is chronicled too",
        )
    }
}
