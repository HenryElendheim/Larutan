package world.larutan.engine.sim

import world.larutan.engine.being.BeliefKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Belief becomes a social axis: a firmest conviction, and camps that can divide a people (§9). */
class FactionTest {

    private fun freshSim(seed: Long = 3L): Simulation {
        val config = WorldConfig(width = 24, height = 24, startingPopulation = 6, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun creedIsTheFirmestStrongBelief() {
        val b = freshSim().beings.first()
        assertNull(b.creed, "a fresh mind holds nothing firmly yet")
        b.hold(BeliefKind.WINTERS_ARE_CRUEL, 0.5, "a hard winter")
        assertEquals(BeliefKind.WINTERS_ARE_CRUEL, b.creed, "the firmest strong belief is their creed")
    }

    @Test
    fun twoCampsOfBeliefDivideAPeople() {
        val sim = freshSim()
        sim.world.foundingMyth = "they gathered and made a place their own" // a settled culture
        assertFalse(sim.world.divided)

        val beings = sim.beings
        beings.take(3).forEach { it.hold(BeliefKind.THE_FAR_PLACES_CALL, 0.6, "the pull of far ground") }
        beings.drop(3).take(3).forEach { it.hold(BeliefKind.HARD_WORK_PROVIDES, 0.6, "a life of work") }

        sim.recognizeDivision()
        assertTrue(sim.world.divided, "two real camps split the people")
        assertTrue(sim.chronicle.recent(20).any { it.text.contains("two ways") })
    }
}
