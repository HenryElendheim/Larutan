package world.larutan.engine.sim

import world.larutan.engine.Rng

/**
 * Short, soft, invented names — plain sounds for a plain-spoken, pre-literate
 * world. No culture imported from ours; just names beings could call each other.
 */
object Names {
    private val syllablesA = listOf("Ru", "Bee", "Sa", "Ka", "Mo", "Ni", "Ta", "Lo", "Ve", "Ha", "Ish", "Ora", "Wen", "Fen", "Ael")
    private val syllablesB = listOf("na", "li", "ro", "sha", "mi", "th", "va", "no", "ka", "ren", "wa", "el", "ir", "un")

    // Endings that turn a sound into a place: soft, old, of the land.
    private val placeSuffix = listOf("mere", "hollow", "stead", "fell", "ford", "reach", "vale", "holt", "wick", "combe")

    fun random(rng: Rng): String {
        val a = rng.pick(syllablesA)
        return if (rng.chance(0.45)) a else a + rng.pick(syllablesB)
    }

    /** A name for a place the beings settle -- a sound plus an ending of the land. */
    fun place(rng: Rng): String = rng.pick(syllablesA) + rng.pick(placeSuffix)
}
