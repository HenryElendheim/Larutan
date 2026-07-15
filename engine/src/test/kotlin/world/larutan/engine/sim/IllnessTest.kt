package world.larutan.engine.sim

import world.larutan.engine.being.DriveType
import kotlin.test.Test
import kotlin.test.assertTrue

/** Sickness runs a course: a kept body fights it off, a run-down one lets it deepen (§3.5, §4). */
class IllnessTest {

    private fun freshSim(seed: Long = 2L): Simulation {
        val config = WorldConfig(width = 22, height = 22, startingPopulation = 4, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun aFedAndWarmBodyFightsSicknessOff() {
        val sim = freshSim()
        val b = sim.beings.first()
        b.illness = 0.5
        // Keep them well-provided so the body can push back, tick after tick.
        repeat(12) {
            b.drives[DriveType.HUNGER] = 95.0
            b.drives[DriveType.WARMTH] = 95.0
            b.drives[DriveType.HEALTH] = 90.0
            sim.step()
        }
        assertTrue(b.illness < 0.5, "a fed, warm body drives the sickness back")
    }

    @Test
    fun aRunDownBodyLetsSicknessDeepen() {
        val sim = freshSim(seed = 5L)
        val b = sim.beings.first()
        b.illness = 0.30
        b.drives[DriveType.HUNGER] = 12.0
        b.drives[DriveType.WARMTH] = 12.0
        b.drives[DriveType.HEALTH] = 90.0
        sim.step()
        assertTrue(b.illness > 0.30, "hungry and cold, the body loses ground to it")
    }
}
