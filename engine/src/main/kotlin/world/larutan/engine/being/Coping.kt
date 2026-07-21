package world.larutan.engine.being

import kotlinx.serialization.Serializable

/**
 * The maladaptive ways a being carries what hurts (§4.4) -- the ones that ease the
 * weight now but cost later. Reached for again and again, one of these hardens into
 * a habit, and a habit grown strong enough reads as a visible vice: a mark of what
 * life did to them, not a moral failing. None of it is destiny -- habits fade, and a
 * being can climb back out.
 */
@Serializable
enum class CopingHabit(
    val label: String,     // the habit, in a word or two
    val mark: String,      // how it reads on the follower's panel
    val fallInto: String,  // the chronicle line when it hardens into a vice
    val climbOut: String,  // the chronicle line when they work their way back out
) {
    WITHDRAWAL(
        "withdrawal", "caught in withdrawal",
        "has fallen into pulling away from everyone.",
        "is letting the others back in again.",
    ),
    TEMPER(
        "a short fuse", "quick to lash out",
        "has fallen into lashing out at those around them.",
        "is keeping their temper again.",
    ),
    NUMBING(
        "numbing", "leaning on numbing",
        "has fallen into numbing the hurt, and it takes more each time.",
        "is reaching for the numbing less and less.",
    ),
    OVERWORK(
        "overwork", "lost in overwork",
        "has fallen into burying everything in relentless work.",
        "is easing off the endless work.",
    ),
    RUMINATION(
        "brooding", "caught in brooding",
        "has fallen into turning the same hurt over and over.",
        "is climbing up out of the brooding.",
    ),
}
