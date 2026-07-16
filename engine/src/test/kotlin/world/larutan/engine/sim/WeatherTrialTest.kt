package world.larutan.engine.sim

import world.larutan.engine.being.DriveType
import world.larutan.engine.world.Terrain
import kotlin.test.Test
import kotlin.test.assertTrue

/** A hard spell is a trial: shelter and stores carry you through it, exposure doesn't (§3.5). */
class WeatherTrialTest {

    private fun freshSim(seed: Long = 7L): Simulation {
        val config = WorldConfig(width = 24, height = 24, startingPopulation = 4, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    private fun placeOnGrass(sim: Simulation, b: world.larutan.engine.being.Being) {
        // Put them on open ground with no natural cover.
        for (y in 0 until sim.world.height) for (x in 0 until sim.world.width) {
            val t = sim.world.tileAt(x, y)
            if (t.terrain == Terrain.GRASS) { b.x = x; b.y = y; return }
        }
    }

    @Test
    fun theExposedSufferAHardSpell() {
        val sim = freshSim()
        val b = sim.beings.first()
        placeOnGrass(sim, b)
        sim.world.harshSpell = 3
        b.foodStore = 0.0
        b.drives[DriveType.HUNGER] = 30.0
        b.drives[DriveType.WARMTH] = 30.0
        b.drives[DriveType.HEALTH] = 80.0

        sim.healthEffects(b)
        assertTrue(b.drives[DriveType.HEALTH] < 80.0, "out in it with nothing, they lose ground")
    }

    @Test
    fun theShelteredAndStockedRideItOut() {
        val sim = freshSim(seed = 12L)
        val b = sim.beings.first()
        placeOnGrass(sim, b)
        sim.world.tileAt(b.x, b.y).built = 0.7 // a good roof over them
        sim.world.harshSpell = 3
        b.foodStore = 6.0
        b.drives[DriveType.HUNGER] = 90.0
        b.drives[DriveType.WARMTH] = 90.0
        b.drives[DriveType.HEALTH] = 80.0

        sim.healthEffects(b)
        assertTrue(b.drives[DriveType.HEALTH] >= 80.0, "safe and provided, the same spell doesn't touch them")
    }
}
