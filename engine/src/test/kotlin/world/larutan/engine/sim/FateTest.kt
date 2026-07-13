package world.larutan.engine.sim

import world.larutan.engine.being.DriveType
import world.larutan.engine.god.Fate
import world.larutan.engine.god.FateBoon
import world.larutan.engine.god.FateTrigger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A fate is a god's reach into the future: it waits for a life to turn, then comes to pass (§10.6). */
class FateTest {

    private fun freshSim(seed: Long = 3L): Simulation {
        val config = WorldConfig(width = 20, height = 20, startingPopulation = 4, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun aFateWaitsUntilItsTriggerIsMet() {
        val sim = freshSim()
        val subject = sim.beings.first()
        // Keep them fed so the trigger can't fire yet.
        subject.drives[DriveType.HUNGER] = 90.0
        sim.decree(Fate(subject.id, FateTrigger.HUNGER, FateBoon.PROVISION))

        sim.step()
        assertFalse(sim.fates.first().fulfilled, "well-fed, the fate stays dormant")

        // Now let hunger bite, and the fate should come to pass on the next tick.
        subject.drives[DriveType.HUNGER] = 10.0
        sim.step()
        val fate = sim.fates.first()
        assertTrue(fate.fulfilled, "the trigger is met, so the fate fires")
        assertTrue(subject.foodStore > 0.0, "and the boon arrives — food in their store")
    }

    @Test
    fun aFulfilledFateSurvivesSaveAndLoad() {
        val sim = freshSim(seed = 6L)
        val subject = sim.beings.first()
        subject.drives[DriveType.HUNGER] = 5.0
        sim.decree(Fate(subject.id, FateTrigger.HUNGER, FateBoon.EASE))
        sim.step()
        assertTrue(sim.fates.first().fulfilled)

        val restored = Persistence.restore(Persistence.deserialize(Persistence.serialize(Persistence.snapshot(sim))))
        assertEquals(1, restored.fates.size, "the fate rides along in the snapshot")
        assertTrue(restored.fates.first().fulfilled, "and remembers it was fulfilled")
    }

    @Test
    fun rewindingPastAFulfilledFateArmsItAgain() {
        val sim = freshSim(seed = 9L)
        val timeline = Timeline(recentCadence = 1, recentCap = 300)
        val subject = sim.beings.first()

        // Snapshot an early, well-fed moment where the fate is still waiting.
        subject.drives[DriveType.HUNGER] = 95.0
        sim.decree(Fate(subject.id, FateTrigger.COLD, FateBoon.WARMTH))
        timeline.maybeRecord(sim)
        val armedTick = sim.world.tick

        // Force the trigger and step so the fate fires.
        subject.drives[DriveType.WARMTH] = 5.0
        sim.step()
        assertTrue(sim.fates.first().fulfilled, "the fate has come to pass")

        // Roll back to before it fired -> the past holds it dormant again.
        val json = timeline.rewindTo(armedTick)!!
        val past = Persistence.restore(Persistence.deserialize(json))
        assertEquals(1, past.fates.size)
        assertFalse(past.fates.first().fulfilled, "rewound past its moment, the fate waits once more")
    }
}
