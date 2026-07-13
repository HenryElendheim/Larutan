package world.larutan.engine.being

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** An atypical mind is more than a flag: it names the one trait that sets it apart (§4.9). */
class PersonalityTest {

    private fun mild() = Personality(
        boldness = 0.1, warmth = -0.1, curiosity = 0.0,
        resilience = 0.2, industry = -0.2, temper = 0.1, optimism = 0.0,
    )

    @Test
    fun aMildMindHasNoSignature() {
        val p = mild()
        assertFalse(p.isAtypical, "no trait sits out in the tail")
        assertNull(p.signature, "so there's nothing to name")
    }

    @Test
    fun aTailTraitBecomesTheSignature() {
        val p = mild().copy(curiosity = 0.95)
        assertTrue(p.isAtypical)
        val sig = p.signature
        assertNotNull(sig)
        assertEquals("curiosity", sig.trait)
        assertTrue(sig.high, "it leans to the far end")
        assertTrue(sig.phrase.isNotBlank())
        assertTrue(sig.voice.isNotBlank())
    }

    @Test
    fun theSignatureIsTheFarthestTrait() {
        // Two traits out in the tail -> the more extreme one defines the mind.
        val p = mild().copy(temper = -0.85, warmth = 0.97)
        assertEquals("warmth", p.signature?.trait, "the farthest lean wins")
        assertEquals(true, p.signature?.high)
    }

    @Test
    fun theLowTailNamesADifferentVoiceThanTheHigh() {
        val high = mild().copy(boldness = 0.95).signature
        val low = mild().copy(boldness = -0.95).signature
        assertNotNull(high); assertNotNull(low)
        assertTrue(high.high && !low.high)
        assertTrue(high.voice != low.voice, "the two ends of a trait speak differently")
    }
}
