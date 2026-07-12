package world.larutan.engine.sim

import world.larutan.engine.being.SkillType
import world.larutan.engine.being.Skills
import kotlin.test.Test
import kotlin.test.assertTrue

/** Hunting: the riskier, higher-yield food, learned by doing and taught on (§3.5, §9). */
class HuntingTest {

    private fun freshSim(seed: Long): Simulation {
        val config = WorldConfig(width = 24, height = 24, startingPopulation = 4, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun huntingIsLearnedByDoingAndCanFeed() {
        val sim = freshSim(2L)
        val b = sim.living().first()
        val storeBefore = b.foodStore

        repeat(50) { sim.hunt(b) }

        assertTrue(b.skills[SkillType.HUNTING] > 0.0, "hunting is learned by doing")
        assertTrue(b.foodStore > storeBefore, "a run of hunts brings food home at least some of the time")
    }

    @Test
    fun huntingSpreadsWhenItIsTaught() {
        val teacher = Skills()
        repeat(30) { teacher.practice(SkillType.HUNTING, 0.05) }
        val learner = Skills()
        learner.learnFrom(teacher[SkillType.HUNTING], SkillType.HUNTING)
        assertTrue(learner[SkillType.HUNTING] > 0.0, "an elder can pass the hunt to the young")
    }
}
