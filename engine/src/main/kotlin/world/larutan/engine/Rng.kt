package world.larutan.engine

import kotlinx.serialization.Serializable

/**
 * A small, seeded, fully serializable random source (SplitMix64).
 *
 * We roll our own instead of leaning on kotlin.random.Random because the whole
 * point of the rewind system is that the world's state — RNG included — can be
 * snapshotted and restored exactly. This holds its entire state in one Long, so
 * it serializes for free alongside everything else.
 */
@Serializable
class Rng(var state: Long) {

    fun nextLong(): Long {
        state += -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** Uniform in [0, 1). */
    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))

    /** Uniform in [0, bound). */
    fun nextInt(bound: Int): Int {
        require(bound > 0)
        return (nextDouble() * bound).toInt().coerceIn(0, bound - 1)
    }

    /** Uniform in [min, max]. */
    fun nextIntRange(min: Int, max: Int): Int = min + nextInt(max - min + 1)

    /** Uniform in [min, max). */
    fun nextDoubleRange(min: Double, max: Double): Double = min + nextDouble() * (max - min)

    /** True with the given probability. */
    fun chance(p: Double): Boolean = nextDouble() < p

    fun <T> pick(items: List<T>): T = items[nextInt(items.size)]

    /**
     * Weighted softmax-style choice: picks an index with probability
     * proportional to exp(score / temperature). Higher temperature = more random,
     * which is exactly the "dash of randomness so lives surprise you" the decision
     * loop wants.
     */
    fun weightedChoice(scores: DoubleArray, temperature: Double = 0.4): Int {
        if (scores.isEmpty()) return -1
        val max = scores.max()
        var sum = 0.0
        val weights = DoubleArray(scores.size) { i ->
            val w = Math.exp((scores[i] - max) / temperature)
            sum += w
            w
        }
        var roll = nextDouble() * sum
        for (i in weights.indices) {
            roll -= weights[i]
            if (roll <= 0.0) return i
        }
        return weights.size - 1
    }

    /** A fresh independent stream, derived deterministically from this one. */
    fun fork(): Rng = Rng(nextLong())
}
