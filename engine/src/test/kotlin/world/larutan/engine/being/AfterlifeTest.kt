package world.larutan.engine.being

import world.larutan.engine.sim.Simulation
import world.larutan.engine.sim.WorldConfig
import world.larutan.engine.sim.WorldGen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The dead get their own state: a realm, a final thought, and the thoughts they carried (§10.7). */
class AfterlifeTest {

    private fun freshSim(seed: Long = 5L): Simulation {
        val config = WorldConfig(width = 20, height = 20, startingPopulation = 4, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun theLedgerWeighsTheLifeIntoARealm() {
        assertEquals(Realm.HEAVEN, Realm.sortFor(2.0))
        assertEquals(Realm.HELL, Realm.sortFor(-2.0))
        assertEquals(Realm.PURGATORY, Realm.sortFor(0.0))
    }

    @Test
    fun deathSettlesASoulAndKeepsItsThoughts() {
        val sim = freshSim()
        val b = sim.living().first()
        b.think("I keep thinking about the cold.")
        b.moralLedger = 5.0     // a life that did right
        b.ageYears = 85.0       // and one that is out of time
        sim.run(60)

        assertTrue(!b.alive, "old age should take them")
        assertEquals(Realm.HEAVEN, b.realm, "a good life settles in heaven")
        assertNotNull(b.finalThought, "a soul leaves a last reflection")
        assertTrue(b.recentThoughts.isNotEmpty(), "the dead keep the thoughts they had")
        assertTrue(b.emotion.valence > 0.5, "a soul in heaven rests content")
    }

    @Test
    fun aHarmfulLifeSettlesLower() {
        val sim = freshSim(seed = 11L)
        val b = sim.living().first()
        b.moralLedger = -5.0
        b.ageYears = 85.0
        sim.run(60)

        assertTrue(!b.alive)
        assertEquals(Realm.HELL, b.realm, "a life that did harm settles in hell")
    }
}
