package world.larutan.engine.being

import kotlinx.serialization.Serializable
import world.larutan.engine.Rng

/**
 * Who a being is, underneath. Seven trait axes, each roughly -1..+1, that colour
 * every decision, feeling, thought, and dream. Inherited from parents with a
 * little mutation, so families feel related but distinct and lineages drift in
 * character over generations.
 */
@Serializable
data class Personality(
    val boldness: Double,     // cautious .. risk-taking
    val warmth: Double,       // aloof .. affectionate
    val curiosity: Double,    // content .. seeking
    val resilience: Double,   // fragile .. hardy
    val industry: Double,     // idle .. driven (sets life tempo)
    val temper: Double,       // calm .. volatile
    val optimism: Double,     // despairing .. hopeful
) {
    /** True when one or more traits sit far out in the tail — a mind unlike its peers (not lesser, different). */
    val isAtypical: Boolean
        get() = axes().any { kotlin.math.abs(it.second) > ATYPICAL_EDGE }

    /**
     * The one trait that leans farthest out, if this is an atypical mind — the shape of
     * how it differs, in a word, a phrase, and a private voice. Null for a typical mind.
     * This is what makes an outlier legible: not "a mind apart", but *how* it is apart.
     */
    val signature: Signature?
        get() {
            val (trait, value) = axes().maxByOrNull { kotlin.math.abs(it.second) } ?: return null
            if (kotlin.math.abs(value) <= ATYPICAL_EDGE) return null
            return Signature.of(trait, value > 0.0)
        }

    private fun axes(): List<Pair<String, Double>> = listOf(
        "boldness" to boldness, "warmth" to warmth, "curiosity" to curiosity,
        "resilience" to resilience, "industry" to industry, "temper" to temper,
        "optimism" to optimism,
    )

    companion object {
        /** How far out a trait must sit to mark a mind as atypical. */
        const val ATYPICAL_EDGE = 0.82

        /** A being drawn from the species norm — most traits mild, occasionally an outlier. */
        fun random(rng: Rng, atypicalChance: Double = 0.12): Personality {
            fun axis(): Double {
                // Sum of a few uniforms clusters around 0 (roughly bell-shaped).
                val n = (rng.nextDouble() + rng.nextDouble() + rng.nextDouble()) / 3.0
                return ((n - 0.5) * 2.0).coerceIn(-1.0, 1.0)
            }

            var p = Personality(axis(), axis(), axis(), axis(), axis(), axis(), axis())
            if (rng.chance(atypicalChance)) {
                // Push one axis out to the far tail: an edge of what the species can be.
                val extreme = if (rng.chance(0.5)) 1.0 else -1.0
                val far = (extreme * rng.nextDoubleRange(0.85, 1.0))
                p = when (rng.nextInt(7)) {
                    0 -> p.copy(boldness = far)
                    1 -> p.copy(warmth = far)
                    2 -> p.copy(curiosity = far)
                    3 -> p.copy(resilience = far)
                    4 -> p.copy(industry = far)
                    5 -> p.copy(temper = far)
                    else -> p.copy(optimism = far)
                }
            }
            return p
        }

        /** A child's nature: the blend of both parents plus small mutation. */
        fun inherit(a: Personality, b: Personality, rng: Rng, mutationRate: Double = 0.15): Personality {
            fun blend(x: Double, y: Double): Double {
                val mid = (x + y) / 2.0
                val mutation = (rng.nextDouble() - 0.5) * 2.0 * mutationRate
                return (mid + mutation).coerceIn(-1.0, 1.0)
            }
            return Personality(
                boldness = blend(a.boldness, b.boldness),
                warmth = blend(a.warmth, b.warmth),
                curiosity = blend(a.curiosity, b.curiosity),
                resilience = blend(a.resilience, b.resilience),
                industry = blend(a.industry, b.industry),
                temper = blend(a.temper, b.temper),
                optimism = blend(a.optimism, b.optimism),
            )
        }
    }
}

/**
 * The defining lean of an atypical mind. Names the trait, which way it runs, a phrase
 * for the panel ("a hunger to know that never settles"), and a private voice the being
 * falls into now and then — the thing they'd say that no typical mind would.
 */
@Serializable
data class Signature(
    val trait: String,   // which axis: "curiosity", "temper", ...
    val high: Boolean,   // the far end or the near end of it
    val phrase: String,  // a one-line read for the follower
    val voice: String,   // a thought only this kind of mind would think
) {
    companion object {
        fun of(trait: String, high: Boolean): Signature = when (trait to high) {
            "boldness" to true -> Signature(trait, high,
                "a recklessness that doesn't flinch",
                "The others hang back and weigh it. I've already gone.")
            "boldness" to false -> Signature(trait, high,
                "a caution that sees every edge",
                "Everyone calls it fear. I call it seeing the drop before I reach it.")
            "warmth" to true -> Signature(trait, high,
                "a tenderness that spills over",
                "I feel the others' weather as if it were my own sky.")
            "warmth" to false -> Signature(trait, high,
                "a distance others find hard to cross",
                "People tire me in a way the empty tiles never do.")
            "curiosity" to true -> Signature(trait, high,
                "a hunger to know that never settles",
                "There's always one more thing past the edge, and I have to see it.")
            "curiosity" to false -> Signature(trait, high,
                "a deep contentment with what is",
                "I don't need to know what's over the hill. Here is enough.")
            "resilience" to true -> Signature(trait, high,
                "a hardness the world can't seem to dent",
                "It hit me, and I got up. That's all there ever is to it.")
            "resilience" to false -> Signature(trait, high,
                "a rawness that feels everything keenly",
                "Small things land hard on me. I've stopped pretending they don't.")
            "industry" to true -> Signature(trait, high,
                "a drive that never rests",
                "Rest feels like something coming undone. I'd rather be making.")
            "industry" to false -> Signature(trait, high,
                "a stillness the busy can't understand",
                "They rush at the day. I let it come to me, and it does.")
            "temper" to true -> Signature(trait, high,
                "a fire close to the surface",
                "It rises fast in me. I've hurt people I loved before it cooled.")
            "temper" to false -> Signature(trait, high,
                "a calm that nothing seems to shake",
                "The storm passes over me. I've never quite known why it doesn't land.")
            "optimism" to true -> Signature(trait, high,
                "a light that won't go out",
                "Even in the lean season, some part of me is sure it turns out well.")
            else -> Signature(trait, high,
                "an eye that finds the dark first",
                "I see how it ends before it starts. Being right is no comfort.")
        }
    }
}
