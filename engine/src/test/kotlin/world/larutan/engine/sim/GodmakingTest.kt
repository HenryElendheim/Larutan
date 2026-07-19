package world.larutan.engine.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A god's making and unmaking: call a new being into the world, or end one (§10). */
class GodmakingTest {

    private fun freshSim(seed: Long = 3L): Simulation {
        val config = WorldConfig(width = 20, height = 20, startingPopulation = 4, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun spawnCallsANewLivingBeingIntoTheWorld() {
        val sim = freshSim()
        val before = sim.living().size
        val b = sim.spawn()

        assertTrue(b.alive, "the new being is alive")
        assertEquals(before + 1, sim.living().size, "and joins the living")
        assertTrue(sim.byId(b.id) === b, "and can be found by id")
        assertTrue(b.ageYears > 0.0, "it arrives grown enough to act")
        assertTrue(b.name.isNotBlank())
    }

    @Test
    fun spawnCanPlaceAtAChosenSpot() {
        val sim = freshSim(seed = 7L)
        val b = sim.spawn(5, 8)
        assertEquals(5, b.x, "it appears where it was called")
        assertEquals(8, b.y)
    }

    @Test
    fun strikeDownEndsALife() {
        val sim = freshSim(seed = 4L)
        val victim = sim.beings.first()
        assertTrue(victim.alive)

        sim.strikeDown(victim.id)
        assertFalse(victim.alive, "the life is ended")
        assertNotNull(victim.realm, "and the soul is weighed and settled")
    }
}
