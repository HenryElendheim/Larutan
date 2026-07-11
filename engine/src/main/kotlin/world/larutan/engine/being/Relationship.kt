package world.larutan.engine.being

import kotlinx.serialization.Serializable

/** What one being is to another, in feeling. */
@Serializable
enum class Sentiment { STRANGER, ACQUAINTANCE, FRIENDSHIP, LOVE, RIVALRY, RESENTMENT, GRIEF }

/**
 * A tie to one other being: how strong the bond is, how it's felt, how well they
 * know each other, and a short running summary that carries the relationship's
 * history forward without re-storing every exchange (this is what a conversation
 * prompt would lean on so a bond accumulates across many talks).
 */
@Serializable
data class Relationship(
    val otherId: Int,
    var bond: Double = 10.0,          // 0..100
    var sentiment: Sentiment = Sentiment.STRANGER,
    var familiarity: Double = 0.0,    // 0..100
    var summary: String = "",
) {
    fun warm(amount: Double) {
        bond = (bond + amount).coerceIn(0.0, 100.0)
        familiarity = (familiarity + amount * 0.6).coerceIn(0.0, 100.0)
        reclassify()
    }

    fun cool(amount: Double) {
        bond = (bond - amount).coerceIn(0.0, 100.0)
        reclassify()
    }

    private fun reclassify() {
        // Grief and rivalry/resentment are set explicitly by events; don't overwrite them here.
        if (sentiment == Sentiment.GRIEF || sentiment == Sentiment.RIVALRY || sentiment == Sentiment.RESENTMENT) return
        sentiment = when {
            bond >= 75 -> Sentiment.LOVE
            bond >= 45 -> Sentiment.FRIENDSHIP
            bond >= 20 -> Sentiment.ACQUAINTANCE
            else -> Sentiment.STRANGER
        }
    }
}
