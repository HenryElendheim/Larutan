package world.larutan.engine.sim

import world.larutan.engine.being.BeliefKind
import world.larutan.engine.being.DriveType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Food meets society (§3.5): the fed feed the hungry they care for -- or hoard against a remembered scarcity. */
class FoodTest {

    private fun freshSim(seed: Long): Simulation {
        val config = WorldConfig(width = 20, height = 20, startingPopulation = 4, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun aBeingFeedsAHungryOneItCaresFor() {
        val sim = freshSim(1L)
        val (giver, taker) = sim.living().let { it[0] to it[1] }
        giver.foodStore = 50.0
        taker.drives[DriveType.HUNGER] = 20.0
        giver.relationshipWith(taker.id).warm(60.0) // a bond worth feeding

        val storeBefore = giver.foodStore
        val hungerBefore = taker.drives[DriveType.HUNGER]

        assertTrue(sim.shareFood(giver, taker), "with surplus, hunger, and a bond, they share")
        assertTrue(giver.foodStore < storeBefore, "the giver gives up part of their store")
        assertTrue(taker.drives[DriveType.HUNGER] > hungerBefore, "the hungry one is fed")
        assertTrue(
            taker.beliefs.any { it.kind == BeliefKind.OTHERS_CAN_BE_TRUSTED },
            "being fed teaches that others can be trusted",
        )
    }

    @Test
    fun noSharingWithoutSurplusHungerOrABond() {
        val sim = freshSim(2L)
        val living = sim.living()
        val a = living[0]
        val b = living[1]
        val stranger = living[2]

        // No surplus to give.
        a.foodStore = 5.0
        b.drives[DriveType.HUNGER] = 10.0
        a.relationshipWith(b.id).warm(60.0)
        assertFalse(sim.shareFood(a, b))

        // Surplus, and a bond, but the other isn't hungry.
        a.foodStore = 50.0
        b.drives[DriveType.HUNGER] = 90.0
        assertFalse(sim.shareFood(a, b))

        // Surplus and hunger, but no bond to a stranger.
        stranger.drives[DriveType.HUNGER] = 10.0
        assertFalse(sim.shareFood(a, stranger))
    }
}
