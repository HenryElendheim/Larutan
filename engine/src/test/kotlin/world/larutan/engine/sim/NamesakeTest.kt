package world.larutan.engine.sim

import world.larutan.engine.being.Sentiment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A lost loved one's name can be carried forward into a newborn — remembrance (§9, §10.7). */
class NamesakeTest {

    private fun freshSim(seed: Long = 1L): Simulation {
        val config = WorldConfig(width = 20, height = 20, startingPopulation = 4, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun aGrievedDeadLovedOneOffersTheirName() {
        val sim = freshSim()
        val parent = sim.beings[0]
        val lost = sim.beings[1]
        parent.relationshipWith(lost.id).warm(80.0)
        parent.relationshipWith(lost.id).sentiment = Sentiment.GRIEF
        lost.alive = false

        assertEquals(lost.name, sim.lostLovedName(parent), "the beloved dead offer their name to carry on")
    }

    @Test
    fun noLossMeansNoNameToCarry() {
        val sim = freshSim(seed = 2L)
        val parent = sim.beings[0]
        val friend = sim.beings[1]
        // A living, loved friend is not a loss -> no name to pass on.
        parent.relationshipWith(friend.id).warm(80.0)

        assertNull(sim.lostLovedName(parent))
    }

    @Test
    fun theLivingDeadNotCounted() {
        // A strong bond, but the other is still alive -> nothing to carry forward.
        val sim = freshSim(seed = 3L)
        val parent = sim.beings[0]
        val other = sim.beings[1]
        parent.relationshipWith(other.id).warm(80.0)
        parent.relationshipWith(other.id).sentiment = Sentiment.GRIEF // grieving, but they live yet
        assertNull(sim.lostLovedName(parent))
    }
}
