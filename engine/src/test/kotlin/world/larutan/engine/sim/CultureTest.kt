package world.larutan.engine.sim

import world.larutan.engine.being.BeliefKind
import world.larutan.engine.being.SkillType
import world.larutan.engine.being.Skills
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Skills that grow and are taught, beliefs formed from life -> the seeds of culture (§4.7, §9). */
class CultureTest {

    private fun freshSim(seed: Long): Simulation {
        val config = WorldConfig(width = 26, height = 26, startingPopulation = 5, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun skillsGrowWithPracticeAndCanBeLearned() {
        val master = Skills()
        assertEquals(0.0, master[SkillType.FORAGING])
        repeat(60) { master.practice(SkillType.FORAGING, 0.02) }
        assertTrue(master[SkillType.FORAGING] > 0.3, "practice builds skill")
        assertTrue(master[SkillType.FORAGING] < 1.0, "with diminishing returns, never instantly expert")

        val learner = Skills()
        learner.learnFrom(master[SkillType.FORAGING], SkillType.FORAGING)
        assertTrue(learner[SkillType.FORAGING] > 0.0, "a learner picks some up from someone who knows more")
        assertTrue(learner[SkillType.FORAGING] < master[SkillType.FORAGING], "but not all of it at once")
    }

    @Test
    fun beliefsFormAndThenHoldMoreFirmly() {
        val sim = freshSim(1L)
        val b = sim.living().first()
        assertTrue(b.beliefs.isEmpty())

        b.hold(BeliefKind.WINTERS_ARE_CRUEL, 0.3, "a hard winter")
        assertEquals(1, b.beliefs.size)
        val before = b.beliefs.first().strength

        b.hold(BeliefKind.WINTERS_ARE_CRUEL, 0.3, "another hard winter")
        assertEquals(1, b.beliefs.size, "the same belief strengthens rather than duplicating")
        assertTrue(b.beliefs.first().strength > before)
    }

    @Test
    fun beingsLearnTheGroundByWorkingIt() {
        val sim = freshSim(3L)
        sim.run(400)
        assertTrue(
            sim.beings.any { it.skills[SkillType.FORAGING] > 0.0 },
            "a being that forages should get better at it",
        )
    }
}
