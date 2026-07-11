package world.larutan.engine.being

import kotlinx.serialization.Serializable

/** The specific feelings surfaced on top of the valence/arousal core. They stack. */
@Serializable
enum class EmotionName(val label: String, val positive: Boolean) {
    JOY("joy", true),
    CONTENTMENT("contentment", true),
    LOVE("love", true),
    AFFECTION("affection", true),
    HOPE("hope", true),
    PRIDE("pride", true),
    CURIOSITY("curiosity", true),
    RELIEF("relief", true),
    GRATITUDE("gratitude", true),
    AWE("awe", true),
    FEAR("fear", false),
    ANXIETY("anxiety", false),
    GRIEF("grief", false),
    LONELINESS("loneliness", false),
    ANGER("anger", false),
    FRUSTRATION("frustration", false),
    SHAME("shame", false),
    GUILT("guilt", false),
    JEALOUSY("jealousy", false),
    BOREDOM("boredom", false),
    DESPAIR("despair", false),
    RESENTMENT("resentment", false),
}

@Serializable
data class ActiveEmotion(val name: EmotionName, var intensity: Double)

/**
 * A being's feeling-state. A simple computable core — valence (good/bad) and
 * arousal (activated) — with the named emotions layered on top as texture, plus
 * the accumulated distress that unmet needs and hard events pile up. High distress
 * is what forces a being to cope (§4.4).
 */
@Serializable
class Emotion(
    var valence: Double = 0.2,   // -1 (awful) .. +1 (wonderful)
    var arousal: Double = 0.3,   // 0 (flat) .. 1 (activated)
    val active: MutableList<ActiveEmotion> = mutableListOf(),
    var distressLoad: Double = 0.0, // 0 (light) .. 100 (crushed)
) {
    fun dominant(): EmotionName? = active.maxByOrNull { it.intensity }?.name

    /** A plain-language mood label for the panel and thoughts. */
    fun moodLabel(): String {
        val d = dominant()
        if (d != null) return d.label
        return when {
            valence > 0.5 -> "content"
            valence > 0.1 -> "at ease"
            valence > -0.1 -> "flat"
            valence > -0.5 -> "low"
            else -> "wretched"
        }
    }

    fun feel(name: EmotionName, intensity: Double) {
        val existing = active.firstOrNull { it.name == name }
        if (existing != null) {
            existing.intensity = (existing.intensity + intensity).coerceIn(0.0, 1.0)
        } else if (intensity > 0.05) {
            active += ActiveEmotion(name, intensity.coerceIn(0.0, 1.0))
        }
    }

    /** Feelings fade over time unless something keeps them lit. */
    fun decay(rate: Double = 0.06) {
        active.forEach { it.intensity -= rate }
        active.removeAll { it.intensity <= 0.05 }
        distressLoad = (distressLoad - 0.4).coerceAtLeast(0.0)
    }
}
