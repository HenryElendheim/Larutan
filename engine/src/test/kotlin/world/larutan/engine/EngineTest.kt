package world.larutan.engine

import world.larutan.engine.being.DriveType
import world.larutan.engine.being.GoalKind
import world.larutan.engine.being.Personality
import world.larutan.engine.god.God
import world.larutan.engine.sim.Persistence
import world.larutan.engine.sim.Simulation
import world.larutan.engine.sim.WorldConfig
import world.larutan.engine.sim.WorldGen
import world.larutan.engine.world.Season
import world.larutan.engine.world.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EngineTest {

    private fun freshSim(pop: Int = 5, seed: Long = 12345L): Simulation {
        val config = WorldConfig(width = 24, height = 24, startingPopulation = pop, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun clockDerivesSeasonsAndNight() {
        val sim = freshSim()
        val w = sim.world
        assertEquals(0L, w.day)
        assertEquals(Season.SPRING, w.season)
        // Advance to the depths of night and confirm the flag flips.
        w.tick = 2 // hour 2 is night
        assertTrue(w.isNight)
        w.tick = 12 // midday
        assertTrue(!w.isNight)
        // A full year of days should land back on spring.
        w.tick = World.DAYS_PER_SEASON * 4 * World.TICKS_PER_DAY
        assertEquals(Season.SPRING, w.season)
        assertEquals(1L, w.year)
    }

    @Test
    fun beingsActAndStayComputable() {
        val sim = freshSim()
        // Run a full season and make sure nothing corrupts: drives stay in range,
        // and the world keeps at least some beings alive through a mild spring.
        sim.run(World.DAYS_PER_SEASON.toInt() * World.TICKS_PER_DAY)
        for (b in sim.beings) {
            for (d in DriveType.entries) {
                val v = b.drives[d]
                assertTrue(v in 0.0..100.0, "drive $d out of range: $v")
            }
        }
        assertTrue(sim.living().isNotEmpty(), "the whole group died in a single mild spring")
    }

    @Test
    fun worldRoundTripsThroughSerialization() {
        val sim = freshSim()
        sim.run(3 * World.TICKS_PER_DAY)
        val text = Persistence.serialize(Persistence.snapshot(sim))
        val restored = Persistence.restore(Persistence.deserialize(text))
        assertEquals(sim.world.tick, restored.world.tick)
        assertEquals(sim.beings.size, restored.beings.size)
        assertEquals(sim.beings.first().name, restored.beings.first().name)
        // The RNG state carries too, so a rewind replays deterministically.
        assertEquals(sim.rng.state, restored.rng.state)
    }

    @Test
    fun inheritanceStaysBetweenParents() {
        val rng = Rng(99)
        val a = Personality.random(rng)
        val b = Personality.random(rng)
        repeat(50) {
            val child = Personality.inherit(a, b, rng, mutationRate = 0.15)
            val lo = minOf(a.warmth, b.warmth) - 0.16
            val hi = maxOf(a.warmth, b.warmth) + 0.16
            assertTrue(child.warmth in lo..hi, "child warmth ${child.warmth} outside [$lo,$hi]")
            assertTrue(child.boldness in -1.0..1.0)
        }
    }

    @Test
    fun godProvidesAndBlesses() {
        val sim = freshSim()
        val god = God(sim)
        val b = sim.beings.first()
        val storeBefore = b.foodStore
        god.provide(b.id, 50.0)
        assertTrue(b.foodStore >= storeBefore + 40.0)

        b.drives[DriveType.HEALTH] = 20.0
        god.bless(b.id)
        assertTrue(b.drives[DriveType.HEALTH] > 20.0)
    }

    @Test
    fun immortalityHoldsDeathAway() {
        val sim = freshSim(pop = 2)
        val god = God(sim)
        val mortal = sim.beings[0]
        val blessed = sim.beings[1]

        mortal.ageYears = 85.0
        blessed.ageYears = 85.0
        god.grantImmortality(blessed.id)

        sim.step()
        assertTrue(!mortal.alive, "an 85-year-old should die of old age")
        assertTrue(blessed.alive, "an immortal should not die of age")
        assertTrue(blessed.immortal)
    }

    @Test
    fun inspirePlantsTheNamedGoal() {
        val sim = freshSim()
        val god = God(sim)
        val b = sim.beings.first()
        god.inspire(b.id, GoalKind.EXPLORE)
        assertNotNull(b.goal)
        assertEquals(GoalKind.EXPLORE, b.goal!!.kind)
    }
}
