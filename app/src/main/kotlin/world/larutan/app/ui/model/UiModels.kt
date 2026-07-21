package world.larutan.app.ui.model

/** Immutable snapshots the engine hands to Compose each tick. */

enum class Speed(val label: String, val tickDelayMillis: Long) {
    PAUSED("Paused", Long.MAX_VALUE),
    DWELL("Dwell", 3200),      // slower than slow — linger on a single moment
    REFLECT("Reflect", 1400),  // the intimate baseline — sit with one life
    WATCH("Watch", 380),       // follow arcs across days
    DRIFT("Drift", 80);        // let a long stretch of life run
}

/** What a long-press on the map drops: a being, or a reshaping of the land itself. */
enum class PlaceMode(val label: String) {
    BEING("a being"),
    FOOD("food"),
    WATER("water"),
    SHELTER("shelter"),
}

/** Whether the roster shows the living or the dead, and — for the dead — which realm. */
enum class RosterFilter(val label: String) {
    LIVING("Living"),
    DEAD("The dead"),
}

/**
 * Player preferences: how the app reads and how much it interrupts. Kept small and
 * plain -- accessibility, and a way to blitz without the world easing off for you.
 */
data class Settings(
    val slowForMoments: Boolean = true,   // ease the speed on a big moment; off = blitz straight past
    val showMomentBanners: Boolean = true,// surface the moment banner at all
    val largerText: Boolean = false,      // scale text up for easier reading
)

/** One tile worth drawing on the map, with how much of the thing is there (0..1). */
data class MapCell(val x: Int, val y: Int, val amount: Float)

/** The land under the beings: where water sits, where food grows, and the shelters raised. */
data class MapView(
    val water: List<MapCell> = emptyList(),
    val food: List<MapCell> = emptyList(),
    val shelters: List<MapCell> = emptyList(),
)

data class UiState(
    val world: WorldInfo = WorldInfo(),
    val map: MapView = MapView(),
    val settings: Settings = Settings(),
    val beings: List<BeingDot> = emptyList(),
    val roster: List<RosterEntry> = emptyList(),
    val rosterFilter: RosterFilter = RosterFilter.LIVING,
    val realmFilter: String? = null, // null means every realm; otherwise a realm label
    val followed: FollowedBeing? = null,
    val speed: Speed = Speed.PAUSED,
    val moment: MomentView? = null,
    val timeline: List<TimelineMomentView> = emptyList(),
    val chronicle: List<String> = emptyList(),
    val canUndo: Boolean = false, // whether there's a god act to take back
    val canRedo: Boolean = false, // whether an undone act can be put back
)

/** A meaningful thing that just happened, surfaced so you don't miss it (§10.4). */
data class MomentView(
    val text: String,
    val beingId: Int?, // who it happened to, if anyone — tapping follows them
)

/**
 * One moment you can roll the world back to, broken down so the rewind picker can
 * drill from years, to months (seasons), to weeks, to days, to the time of day.
 */
data class TimelineMomentView(
    val tick: Long,
    val year: Long,
    val monthIndex: Int,    // which season, 0..3
    val monthLabel: String, // spring / summer / autumn / winter
    val week: Int,          // which week within the season, 0..2
    val dayOfSeason: Int,   // 0..11
    val timeLabel: String,  // the time of day, e.g. "morning · 08h"
    val isNow: Boolean,     // the moment the world is sitting at right now
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
    val harshSpell: Boolean = false, // a severe cold spell is on -> shown as a trial
    val settlementName: String? = null, // the name of the group's home ground, once settled
    val foundingMyth: String? = null,   // the origin story the group tells about itself
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
    val size: Float = 1f,          // how large the dot draws, a god may change it
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
    val atypicalSignature: String?, // for an atypical mind, how it differs, in a phrase
    val hue: Float,                 // their dot colour, for the god editor
    val size: Float,                // their dot size, for the god editor
    val foodStore: Int,        // what they're carrying put by, so hoarding and sharing read
    val alive: Boolean,
    val ailing: Boolean,       // unwell right now, so the panel can show it
    val atHome: Boolean,       // currently at the place they call home
    val standing: String?,     // how the group regards them, in a word, or null if unremarkable
    val vice: String?,         // a maladaptive habit grown into a vice, or null
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
    val skills: List<DriveBar>,     // what they've learned to do well (reuses the labelled-bar shape)
    val beliefs: List<String>,      // what they've come to believe from what they've lived
    val fates: List<String>,        // intentions set on their future, still waiting to come to pass
)

/** The two halves of a fate the player composes: the moment it waits for, and what it brings. */
data class FateOption(val id: String, val label: String)

/** The choices offered when setting a fate, mirrored from the engine so the UI stays declarative. */
object FateChoices {
    val triggers = listOf(
        FateOption("HUNGER", "when hunger bites"),
        FateOption("LONELINESS", "when they're alone too long"),
        FateOption("COLD", "when the cold gets in"),
        FateOption("DESPAIR", "when hope runs out"),
        FateOption("LIFES_END", "as their years run short"),
    )
    val boons = listOf(
        FateOption("PROVISION", "food will find them"),
        FateOption("EASE", "an ease will settle"),
        FateOption("WARMTH", "warmth will reach them"),
        FateOption("PURPOSE", "a purpose will arrive"),
    )
}

data class DriveBar(val label: String, val value: Float)

data class GoalView(
    val target: String,
    val progress: Float,
    val milestones: List<Pair<String, Boolean>>,
    val bornFrom: String,
)

data class RelationView(val name: String, val sentiment: String, val bond: Float)
