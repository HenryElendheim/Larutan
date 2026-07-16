package world.larutan.engine.sim

import world.larutan.engine.being.DriveType
import world.larutan.engine.being.EmotionName
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A being forms a bond to a place: home is its own good, and its loss is felt (§4.8). */
class HomeTest {

    private fun freshSim(seed: Long = 6L): Simulation {
        val config = WorldConfig(width = 20, height = 20, startingPopulation = 4, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun beingHomeIsAQuietGood() {
        val sim = freshSim()
        val b = sim.beings.first()
        // A standing home right where they are.
        b.homeX = b.x; b.homeY = b.y
        sim.world.tileAt(b.x, b.y).built = 0.6
        b.drives[DriveType.SECURITY] = 40.0

        sim.feelForHome(b)
        assertTrue(b.drives[DriveType.SECURITY] > 40.0, "being home settles them")
        assertTrue(b.hasHome, "and they still have it")
    }

    @Test
    fun aHomeWeatheredAwayIsLostAndFelt() {
        val sim = freshSim(seed = 11L)
        val b = sim.beings.first()
        b.homeX = b.x; b.homeY = b.y
        sim.world.tileAt(b.x, b.y).built = 0.05 // gone back to the ground

        sim.feelForHome(b)
        assertFalse(b.hasHome, "the lost home is let go")
        assertTrue(b.emotion.active.any { it.name == EmotionName.GRIEF }, "and its loss is felt")
        assertTrue(sim.chronicle.recent(20).any { it.text.contains("home has weathered away") })
    }
}
