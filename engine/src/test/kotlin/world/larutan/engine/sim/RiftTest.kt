package world.larutan.engine.sim

import world.larutan.engine.being.Sentiment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Bonds can curdle into a lasting rift, and a forgiving heart can mend one (§4.8). */
class RiftTest {

    private fun freshSim(seed: Long = 5L): Simulation {
        val config = WorldConfig(width = 22, height = 22, startingPopulation = 5, seed = seed)
        val (world, beings) = WorldGen.create(config)
        return Simulation(config, world, beings)
    }

    @Test
    fun hurtCanHardenIntoAMutualRift() {
        val sim = freshSim()
        val a = sim.beings[0]
        val b = sim.beings[1]
        // A worn-down bond is what curdles.
        a.relationshipWith(b.id).bond = 6.0

        var tries = 0
        while (a.relationshipWith(b.id).sentiment != Sentiment.RESENTMENT && tries < 100) {
            sim.maybeRift(a, b); tries++
        }
        assertEquals(Sentiment.RESENTMENT, a.relationshipWith(b.id).sentiment, "the rift takes on their side")
        assertEquals(Sentiment.RESENTMENT, b.relationshipWith(a.id).sentiment, "and on the other's too")
        assertTrue(
            sim.chronicle.recent(30).any { it.text.contains("A rift opened") },
            "the rift is chronicled",
        )
    }

    @Test
    fun aRiftCanBeMended() {
        val sim = freshSim(seed = 9L)
        val a = sim.beings[0]
        val b = sim.beings[1]
        // Start them estranged.
        a.relationshipWith(b.id).apply { sentiment = Sentiment.RESENTMENT; bond = 8.0 }
        b.relationshipWith(a.id).apply { sentiment = Sentiment.RESENTMENT; bond = 8.0 }
        val ledgerBefore = a.moralLedger

        var tries = 0
        while (a.relationshipWith(b.id).sentiment == Sentiment.RESENTMENT && tries < 500) {
            sim.maybeReconcile(a, b, a.relationshipWith(b.id), b.relationshipWith(a.id)); tries++
        }
        assertTrue(a.relationshipWith(b.id).sentiment != Sentiment.RESENTMENT, "the rift is mended, not frozen forever")
        assertTrue(a.moralLedger > ledgerBefore, "making peace weighs to the good")
        assertTrue(
            sim.chronicle.recent(30).any { it.text.contains("made their peace") },
            "the peace is chronicled",
        )
    }
}
