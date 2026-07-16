package world.larutan.engine.sim

import world.larutan.engine.being.DriveType
import kotlin.test.Test
import kotlin.test.assertTrue

/** How the group regards a being is shaped by what they're seen to do (§4.8). */
class ReputationTest {

    private fun freshSim(seed: Long = 4L): Simulation {
        val config = WorldConfig(width = 20, height = 20, startingPopulation = 4, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun generosityRaisesStanding() {
        val sim = freshSim()
        val giver = sim.beings[0]
        val taker = sim.beings[1]
        // A giver with surplus, a hungry friend to give to.
        giver.foodStore = 40.0
        taker.drives[DriveType.HUNGER] = 20.0
        giver.relationshipWith(taker.id).warm(50.0)
        val before = giver.reputation

        assertTrue(sim.shareFood(giver, taker), "the sharing goes through")
        assertTrue(giver.reputation > before, "and it lifts how they're regarded")
    }

    @Test
    fun standingIsBounded() {
        val sim = freshSim(seed = 5L)
        val giver = sim.beings[0]
        val taker = sim.beings[1]
        giver.relationshipWith(taker.id).warm(50.0)
        // Share over and over; standing climbs but never past full regard.
        repeat(200) {
            giver.foodStore = 40.0
            taker.drives[DriveType.HUNGER] = 10.0
            sim.shareFood(giver, taker)
        }
        assertTrue(giver.reputation in 0.0..1.0, "regard is held within its bounds")
        assertTrue(giver.reputation > 0.3, "and a long habit of giving builds real standing")
    }
}
