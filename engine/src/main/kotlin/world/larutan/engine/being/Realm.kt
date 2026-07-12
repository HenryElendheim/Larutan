package world.larutan.engine.being

import kotlinx.serialization.Serializable

/**
 * Where a soul settles after death (§10.7). A realm isn't a place the sim keeps
 * ticking -> it's an override on how a being feels, pinned for the dead. Which one
 * a being lands in falls out of the life they lived, weighed in their moral ledger.
 *
 * The names are the plain ones: heaven for a life that did right, hell for one that
 * did real harm, purgatory for the middle -> a place of reckoning with a way up.
 */
@Serializable
enum class Realm(val label: String) {
    HEAVEN("Heaven"),
    HELL("Hell"),
    PURGATORY("Purgatory");

    companion object {
        /** Sort a soul by the weight of its life. Most lives are mixed, so most land in purgatory. */
        fun sortFor(ledger: Double): Realm = when {
            ledger >= 1.0 -> HEAVEN
            ledger <= -1.0 -> HELL
            else -> PURGATORY
        }
    }
}
