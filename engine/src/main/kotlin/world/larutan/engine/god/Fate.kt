package world.larutan.engine.god

import kotlinx.serialization.Serializable

/**
 * A fate is a god's reach into the *future* instead of the present (§10.6): an
 * intention set on one being that waits, dormant, until their life meets a
 * condition — and then comes to pass on its own. You don't act; you arrange for
 * an act to arrive when it's needed.
 *
 * Fates are world state: they ride along in the snapshot, so they survive a save
 * and a rewind. Roll time back past the moment one was fulfilled and it arms
 * again, still waiting for the life to turn.
 */
@Serializable
data class Fate(
    val subjectId: Int,       // whose life this watches, and where the boon lands
    val trigger: FateTrigger, // the turn of a life that wakes it
    val boon: FateBoon,       // what arrives when it wakes
    var fulfilled: Boolean = false,
) {
    /** A human line for the panel: "When hunger bites deep, food will find them." */
    fun sentence(subjectName: String): String {
        val when0 = trigger.label.replaceFirstChar { it.uppercase() }
        return "$when0, ${boon.label.replace("them", subjectName)}."
    }
}

/** The condition in a being's life that a fate lies in wait for. */
@Serializable
enum class FateTrigger(val label: String) {
    HUNGER("when hunger bites deep"),
    LONELINESS("when they're too long alone"),
    COLD("when the cold gets into them"),
    DESPAIR("when hope runs out of them"),
    LIFES_END("as their years run short"),
}

/** What a fulfilled fate brings — the same reach as a blessing, only deferred. */
@Serializable
enum class FateBoon(val label: String) {
    PROVISION("food will find them"),
    EASE("an ease will settle on them"),
    WARMTH("warmth will reach them"),
    PURPOSE("a purpose will arrive for them"),
}
