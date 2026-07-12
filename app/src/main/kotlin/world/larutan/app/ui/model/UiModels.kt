package world.larutan.app.ui.model

/** Immutable snapshots the engine hands to Compose each tick. */

enum class Speed(val label: String, val tickDelayMillis: Long) {
    PAUSED("Paused", Long.MAX_VALUE),
    DWELL("Dwell", 3200),      // slower than slow — linger on a single moment
    REFLECT("Reflect", 1400),  // the intimate baseline — sit with one life
    WATCH("Watch", 380),       // follow arcs across days
    DRIFT("Drift", 80);        // let a long stretch of life run
}

/** Whether the roster shows the living or the dead, and — for the dead — which realm. */
enum class RosterFilter(val label: String) {
    LIVING("Living"),
    DEAD("The dead"),
}

data class UiState(
    val world: WorldInfo = WorldInfo(),
    val beings: List<BeingDot> = emptyList(),
    val roster: List<RosterEntry> = emptyList(),
    val rosterFilter: RosterFilter = RosterFilter.LIVING,
    val realmFilter: String? = null, // null means every realm; otherwise a realm label
    val followed: FollowedBeing? = null,
    val speed: Speed = Speed.PAUSED,
    val timeline: List<TimelineMomentView> = emptyList(),
    val chronicle: List<String> = emptyList(),
)

/** One moment you can roll the world back to. */
data class TimelineMomentView(
    val tick: Long,
    val label: String,
    val isNow: Boolean, // the moment the world is sitting at right now
)

/** One row in the who-to-follow picker. */
data class RosterEntry(
    val id: Int,
    val name: String,
    val alive: Boolean,
    val selected: Boolean,
    val note: String,     // life stage, or how they died
    val realm: String? = null, // where the soul settled, for the dead
)

/** The powers you can reach in with, aimed at the being you're following. */
enum class GodAction(val label: String) {
    PROVIDE("Provide"),
    WARM("Warm"),
    BLESS("Bless"),
    INSPIRE("Inspire"),
    IMMORTALITY("Make ageless"), // a toggle: grants agelessness, or takes it back
}

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
    val immortal: Boolean = false, // drawn with a ring so the ageless read at a glance
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
    val alive: Boolean,
    val immortal: Boolean,
    val realm: String?,        // set once they've died
    val deathCause: String?,
    val finalThought: String?, // their last reflection
    val epitaph: String?,      // what the long-gone become: a name and a line
    val valence: Float,
    val emotions: List<String>,
    val drives: List<DriveBar>,
    val goal: GoalView?,
    val lastThought: String?,
    val lastDream: String?,
    val pastThoughts: List<String>, // the thoughts they carried, readable after they're gone
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
