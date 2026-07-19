package world.larutan.engine.sim

import world.larutan.engine.world.Terrain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A god reshapes the land itself: grows food, opens water, raises shelter (§10). */
class LandshapingTest {

    private fun freshSim(seed: Long = 2L): Simulation {
        val config = WorldConfig(width = 20, height = 20, startingPopulation = 4, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun growingFoodMakesBarrenGroundBear() {
        val sim = freshSim()
        // Start from rock, which grows nothing.
        val t = sim.world.tileAt(3, 3)
        t.terrain = Terrain.ROCK
        assertEquals(0.0, t.foodCapacity, "rock bears no food")

        sim.growFoodAt(3, 3)
        assertTrue(t.foodCapacity > 0.0, "the ground can bear now")
        assertTrue(t.food > 0.0, "and there's food on it")
    }

    @Test
    fun makingWaterOpensADrinkingSpot() {
        val sim = freshSim(seed = 3L)
        sim.makeWaterAt(5, 5)
        val t = sim.world.tileAt(5, 5)
        assertEquals(Terrain.WATER, t.terrain)
        assertTrue(t.water > 0.0, "there's water to drink")
    }

    @Test
    fun raisingShelterBuildsItUp() {
        val sim = freshSim(seed = 4L)
        val t = sim.world.tileAt(7, 7)
        val before = t.built
        sim.raiseShelterAt(7, 7)
        assertTrue(t.built > before, "a shelter stands where there was none")
    }
}
