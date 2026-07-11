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
        get() = listOf(boldness, warmth, curiosity, resilience, industry, temper, optimism)
            .any { kotlin.math.abs(it) > 0.82 }

    companion object {
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
