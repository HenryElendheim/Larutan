package world.larutan.engine.being

import world.larutan.engine.god.God
import world.larutan.engine.sim.Simulation
import world.larutan.engine.sim.WorldConfig
import world.larutan.engine.sim.WorldGen
import world.larutan.engine.world.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    @Test
    fun aSoulCanBeRebornCarryingAnEcho() {
        val sim = freshSim(seed = 3L)
        val soul = sim.living().first()
        soul.ageYears = 85.0
        sim.run(60)
        assertTrue(!soul.alive)

        val livingBefore = sim.living().size
        val newId = God(sim).reincarnate(soul.id)
        assertNotNull(newId, "a dead soul can be reborn")

        val reborn = sim.byId(newId)!!
        assertTrue(reborn.alive)
        assertEquals("infant", reborn.lifeStage.label, "reborn as a newborn")
        assertEquals(soul.appearanceSeed, reborn.appearanceSeed, "the look echoes the life before")
        assertTrue(reborn.memory.events.isNotEmpty(), "a faint pull carries over")
        assertTrue(soul.reincarnated, "the old soul has moved on from the afterlife")
        assertEquals(livingBefore + 1, sim.living().size)
        assertNull(God(sim).reincarnate(soul.id), "a soul that already moved on can't be reborn again")
    }

    @Test
    fun theLongDeadFadeToLegend() {
        val sim = freshSim(seed = 7L)
        val soul = sim.living().first()
        repeat(12) {
            soul.memory.record(MemoryEvent(0, MemoryKind.SOCIALIZED, "a day, number $it", null, 0.3, 0.5, 1.0))
        }
        soul.moralLedger = 5.0
        soul.ageYears = 85.0
        sim.run(30)
        assertTrue(!soul.alive)
        val richCount = soul.memory.events.size

        // Let more than a year of world-time pass over the grave.
        sim.run(World.DAYS_PER_SEASON.toInt() * 4 * World.TICKS_PER_DAY + World.TICKS_PER_DAY)

        assertNotNull(soul.epitaph, "the long-gone become a name and a line")
        assertTrue(soul.memory.events.size < richCount, "the sharp memory fades")
        assertTrue(soul.memory.events.size <= 1, "until barely anything is left")
    }
}
