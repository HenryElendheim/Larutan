package world.larutan.engine

import world.larutan.engine.being.DriveType
import world.larutan.engine.god.God
import world.larutan.engine.sim.Persistence
import world.larutan.engine.sim.Simulation
import world.larutan.engine.sim.WorldConfig
import world.larutan.engine.sim.WorldGen
import world.larutan.engine.world.World
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Walks the exact path the Android app drives through the engine -- run, follow,
 * reach in with every power, and save/load to a real file -- so the substance the
 * UI sits on is proven even though the Compose layer can't be built in this
 * sandbox (Google's servers are firewalled here).
 */
class AppFlowTest {

    private fun appSim(): Simulation {
        // Same shape the app builds on launch.
        val config = WorldConfig(width = 32, height = 32, startingPopulation = 5)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun theWholeAppLoopHolds() {
        var sim = appSim()
        val followedId = 0

        // The tick loop the ViewModel runs.
        sim.run(4 * World.TICKS_PER_DAY)

        // Everything the inner-life panel reads must be present and sane.
        val followed = sim.byId(followedId)
        assertNotNull(followed)
        assertTrue(followed.name.isNotBlank())
        assertTrue(followed.emotion.moodLabel().isNotBlank())
        assertTrue(followed.lifeStage.label.isNotBlank())
        for (d in DriveType.entries) assertTrue(followed.drives[d] in 0.0..100.0)
        // Relationship mapping the panel uses must resolve names for known others.
        followed.relationships.keys.forEach { assertNotNull(sim.nameOf(it)) }

        // The roster the picker shows: living sorted ahead of the lost.
        val roster = sim.beings.sortedWith(
            compareByDescending<world.larutan.engine.being.Being> { it.alive }.thenByDescending { it.generation }
        )
        val firstDeadIndex = roster.indexOfFirst { !it.alive }
        if (firstDeadIndex >= 0) {
            assertTrue(roster.take(firstDeadIndex).all { it.alive }, "a lost being sorted ahead of a living one")
        }
    }

    @Test
    fun everyGodPowerLandsOnTheFollowedBeing() {
        val sim = appSim()
        val god = God(sim)
        val b = sim.beings.first()

        b.foodStore = 0.0
        god.provide(b.id)
        assertTrue(b.foodStore > 0.0, "provide did nothing")

        b.drives[DriveType.WARMTH] = 5.0
        god.warm(b.id)
        assertEquals(100.0, b.drives[DriveType.WARMTH], 0.001)

        b.drives[DriveType.HEALTH] = 10.0
        god.bless(b.id)
        assertTrue(b.drives[DriveType.HEALTH] > 10.0, "bless did not restore")

        god.inspire(b.id)
        assertNotNull(b.goal, "inspire planted no goal")

        god.grantImmortality(b.id)
        assertTrue(b.immortal)
    }

    @Test
    fun saveAndLoadToDiskRestoresTheSameWorld() {
        val sim = appSim()
        sim.run(3 * World.TICKS_PER_DAY)
        val originalTick = sim.world.tick
        val originalNames = sim.beings.map { it.name }

        // Exactly what the app does: write JSON to a file, read it back, restore.
        val file = File.createTempFile("larutan-world", ".json")
        try {
            file.writeText(Persistence.serialize(Persistence.snapshot(sim)))
            val restored = Persistence.restore(Persistence.deserialize(file.readText()))

            assertEquals(originalTick, restored.world.tick)
            assertEquals(originalNames, restored.beings.map { it.name })

            // And it keeps running after a load, like reopening the app.
            restored.run(World.TICKS_PER_DAY)
            assertTrue(restored.world.tick > originalTick)
        } finally {
            file.delete()
        }
    }
}
