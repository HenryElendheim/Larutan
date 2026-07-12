package world.larutan.engine.sim

import world.larutan.engine.being.SkillType
import world.larutan.engine.being.Skills
import kotlin.test.Test
import kotlin.test.assertTrue

/** Cultivation: a learned leap that tends the ground, holds more, and spreads by teaching (§3.5, §9). */
class CultivationTest {

    private fun freshSim(seed: Long): Simulation {
        val config = WorldConfig(width = 24, height = 24, startingPopulation = 4, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun tendingWorksThePlotAndBuildsTheSkill() {
        val sim = freshSim(2L)
        val b = sim.living().first()

        // Stand them on a patch that can bear food, so there's ground to tend.
        val idx = sim.world.tiles.indexOfFirst { it.foodCapacity > 0.0 }
        b.x = idx % sim.world.width
        b.y = idx / sim.world.width

        val skillBefore = b.skills[SkillType.CULTIVATION]
        val tended = sim.tend(b)

        assertTrue(tended, "there was ground to work")
        assertTrue(b.skills[SkillType.CULTIVATION] > skillBefore, "working the ground teaches it")
        assertTrue(sim.world.tileAt(b.x, b.y).cultivation > 0.0, "the plot is now tended")
    }

    @Test
    fun aTendedPlotHoldsMoreThanWildGround() {
        val (world, _) = WorldGen.create(WorldConfig(seed = 1L))
        val plot = world.tiles.first { it.foodCapacity > 0.0 }
        val wild = plot.effectiveFoodCapacity
        plot.cultivation = 1.0
        assertTrue(plot.effectiveFoodCapacity > wild, "tending lifts what the ground can hold")
    }

    @Test
    fun cultivationSpreadsWhenItIsTaught() {
        val teacher = Skills()
        repeat(30) { teacher.practice(SkillType.CULTIVATION, 0.05) }
        val learner = Skills()
        learner.learnFrom(teacher[SkillType.CULTIVATION], SkillType.CULTIVATION)
        assertTrue(learner[SkillType.CULTIVATION] > 0.0, "a learner picks up cultivation from one who knows it")
    }
}
