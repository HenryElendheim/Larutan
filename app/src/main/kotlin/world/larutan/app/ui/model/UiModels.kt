package world.larutan.app.ui.model

/** Immutable snapshots the engine hands to Compose each tick. */

enum class Speed(val label: String, val tickDelayMillis: Long) {
    PAUSED("Paused", Long.MAX_VALUE),
    REFLECT("Reflect", 1400),  // the intimate baseline — sit with one life
    WATCH("Watch", 380),       // follow arcs across days
    DRIFT("Drift", 80);        // let a long stretch of life run
}

data class UiState(
    val world: WorldInfo = WorldInfo(),
    val beings: List<BeingDot> = emptyList(),
    val followed: FollowedBeing? = null,
    val speed: Speed = Speed.PAUSED,
    val chronicle: List<String> = emptyList(),
)

data class WorldInfo(
    val width: Int = 32,
    val height: Int = 32,
    val year: Long = 0,
    val season: String = "spring",
    val dayOfSeason: Int = 1,
    val daysPerSeason: Int = 12,
    val timeOfDay: String = "morning",
    val weather: String = "clear",
    val isNight: Boolean = false,
    val population: Int = 0,
)

data class BeingDot(
    val id: Int,
    val x: Int,
    val y: Int,
    val hue: Float,
    val valence: Float,
    val alive: Boolean,
    val selected: Boolean,
)

data class FollowedBeing(
    val id: Int,
    val name: String,
    val generation: Int,
    val lifeStage: String,
    val ageYears: Int,
    val action: String,
    val mood: String,
    val atypical: Boolean,
    val valence: Float,
    val emotions: List<String>,
    val drives: List<DriveBar>,
    val goal: GoalView?,
    val lastThought: String?,
    val lastDream: String?,
    val memories: List<String>,
    val relationships: List<RelationView>,
)

data class DriveBar(val label: String, val value: Float)

data class GoalView(
    val target: String,
    val progress: Float,
    val milestones: List<Pair<String, Boolean>>,
    val bornFrom: String,
)

data class RelationView(val name: String, val sentiment: String, val bond: Float)
