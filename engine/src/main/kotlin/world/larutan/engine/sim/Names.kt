package world.larutan.engine.sim

import world.larutan.engine.Rng

/**
 * Short, soft, invented names — plain sounds for a plain-spoken, pre-literate
 * world. No culture imported from ours; just names beings could call each other.
 */
object Names {
    private val syllablesA = listOf("Ru", "Bee", "Sa", "Ka", "Mo", "Ni", "Ta", "Lo", "Ve", "Ha", "Ish", "Ora", "Wen", "Fen", "Ael")
    private val syllablesB = listOf("na", "li", "ro", "sha", "mi", "th", "va", "no", "ka", "ren", "wa", "el", "ir", "un")

    fun random(rng: Rng): String {
        val a = rng.pick(syllablesA)
        return if (rng.chance(0.45)) a else a + rng.pick(syllablesB)
    }
}
