package world.larutan.engine.action

import world.larutan.engine.being.DriveType

/**
 * What a being can choose to do on a given tick. Each action lists the drives it
 * can relieve; the decision loop weighs that against how urgently each drive is
 * calling, then bends the score by personality and context (§5).
 */
enum class ActionType(val label: String, val reliefs: Map<DriveType, Double>) {
    EAT("eating", mapOf(DriveType.HUNGER to 42.0)),
    DRINK("drinking", mapOf(DriveType.THIRST to 46.0)),
    FORAGE("foraging", mapOf(DriveType.HUNGER to 14.0)),
    REST("resting", mapOf(DriveType.ENERGY to 40.0)),
    SEEK_WARMTH("seeking warmth", mapOf(DriveType.WARMTH to 36.0, DriveType.SECURITY to 6.0)),
    SOCIALIZE("with another", mapOf(DriveType.CONNECTION to 30.0, DriveType.INTIMACY to 14.0)),
    PLAY("playing", mapOf(DriveType.CURIOSITY to 16.0, DriveType.CONNECTION to 10.0)),
    WANDER("wandering", mapOf(DriveType.CURIOSITY to 10.0, DriveType.AUTONOMY to 8.0)),
    EXPLORE("exploring", mapOf(DriveType.CURIOSITY to 26.0, DriveType.AUTONOMY to 12.0)),
    BUILD("building", mapOf(DriveType.SECURITY to 26.0, DriveType.PURPOSE to 12.0)),
    TEND("tending the ground", mapOf(DriveType.PURPOSE to 12.0, DriveType.SECURITY to 8.0)),
    REFLECT("reflecting", mapOf(DriveType.PURPOSE to 14.0, DriveType.AUTONOMY to 10.0)),
    GRIEVE("grieving", emptyMap()),
}
