package world.larutan.engine.sim

import world.larutan.engine.being.BeliefKind
import world.larutan.engine.being.EmotionName
import kotlin.test.Test
import kotlin.test.assertTrue

/** A death is felt together: those who mourn the same loss are drawn closer (§9). */
class MourningTest {

    private fun freshSim(seed: Long = 7L): Simulation {
        val config = WorldConfig(width = 24, height = 24, startingPopulation = 5, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun thoseWhoShareALossAreDrawnTogether() {
        val sim = freshSim()
        val victim = sim.beings[0]
        val a = sim.beings[1]
        val b = sim.beings[2]

        // Both a and b loved the victim, but barely know each other yet.
        a.relationshipWith(victim.id).warm(70.0)
        b.relationshipWith(victim.id).warm(70.0)
        val bondBefore = a.relationshipWith(b.id).bond

        // The victim is out of time, so the next stretch takes them.
        victim.ageYears = 85.0
        sim.run(3)

        assertTrue(!victim.alive, "the victim has died")
        assertTrue(
            a.emotion.active.any { it.name == EmotionName.GRIEF },
            "a mourner feels the grief",
        )
        assertTrue(
            a.relationshipWith(b.id).bond > bondBefore,
            "and the shared loss draws the mourners closer to each other",
        )
        assertTrue(
            a.beliefs.any { it.kind == BeliefKind.WE_CARRY_EACH_OTHER },
            "from mourning together, they come to hold that they carry each other",
        )
    }
}
