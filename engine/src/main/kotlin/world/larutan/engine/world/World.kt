package world.larutan.engine.world

import kotlinx.serialization.Serializable

/** What a tile is made of. Drives what it can hold and offer. */
@Serializable
enum class Terrain(val label: String, val glyph: Char) {
    GRASS("grassland", '.'),
    FOREST("forest", '^'),
    WATER("water", '~'),
    ROCK("rock", '#'),
    SHELTER("shelter", 'n');
}

/** The four turns of the year. Abundance swings with them; winter is the crucible. */
@Serializable
enum class Season(val label: String) {
    SPRING("spring"),
    SUMMER("summer"),
    AUTUMN("autumn"),
    WINTER("winter");

    /** How generously tiles bear and regrow food this season, 0..1. */
    val foodYield: Double
        get() = when (this) {
            SPRING -> 0.75
            SUMMER -> 1.0
            AUTUMN -> 0.55
            WINTER -> 0.12
        }

    /** How cold it is, 0 (mild) .. 1 (harsh). Feeds the warmth drive. */
    val chill: Double
        get() = when (this) {
            SPRING -> 0.15
            SUMMER -> 0.0
            AUTUMN -> 0.3
            WINTER -> 0.75
        }
}

/** Rough part of the day, derived from the clock. Night is when beings sleep and dream. */
@Serializable
enum class TimeOfDay { DAWN, MORNING, MIDDAY, AFTERNOON, DUSK, NIGHT }

/** Passing conditions layered on top of the season. */
@Serializable
enum class Weather(val label: String) {
    CLEAR("clear"),
    OVERCAST("overcast"),
    RAIN("rain"),
    STORM("storm"),
    SNOW("snow"),
    COLD_SNAP("a cold snap");

    val extraChill: Double
        get() = when (this) {
            CLEAR -> 0.0
            OVERCAST -> 0.05
            RAIN -> 0.15
            STORM -> 0.3
            SNOW -> 0.35
            COLD_SNAP -> 0.5
        }
}

@Serializable
class Tile(
    var terrain: Terrain, // a god may reshape the land itself
    var food: Double,
    var water: Double,
    var materials: Double,
    // The cover the land itself offers, fixed by its terrain.
    val naturalShelter: Double = 0.0,
    // How tended this ground is, 0..1 -> a cultivated plot bears more and comes back
    // faster (§3.5). It creeps back toward wild if no one keeps tending it.
    var cultivation: Double = 0.0,
    // Shelter raised by hands on top of the natural cover: a lasting structure that
    // weathers back down if no one keeps it up (§3.5). This is what makes a home a place.
    var built: Double = 0.0,
) {
    /** Total protection here: the land's own cover plus whatever's been built on it. */
    val shelterQuality: Double get() = (naturalShelter + built).coerceIn(0.0, 1.0)
    // The most this tile can hold; regrowth climbs back toward it with the season.
    // Derived from terrain, so reshaping the land updates what it can bear.
    val foodCapacity: Double get() = when (terrain) {
        Terrain.GRASS -> 60.0
        Terrain.FOREST -> 100.0
        else -> 0.0
    }

    /** What a tended plot can hold: tending lifts the ceiling by up to four-fifths. */
    val effectiveFoodCapacity: Double get() = foodCapacity * (1.0 + cultivation * 0.8)
    val waterCapacity: Double get() = if (terrain == Terrain.WATER) 100.0 else 0.0
    val materialsCapacity: Double get() = when (terrain) {
        Terrain.FOREST -> 80.0
        Terrain.ROCK -> 100.0
        else -> 0.0
    }
}

/**
 * The stage. A grid of tiles plus the clock. Everything the beings live inside.
 *
 * Time is measured in ticks. One tick is one world-hour, so a day is 24 ticks —
 * which lines up with the "Reflect" speed of one real minute to one world hour.
 */
@Serializable
class World(
    val width: Int,
    val height: Int,
    val tiles: List<Tile>,
    var tick: Long = 0,
    var weather: Weather = Weather.CLEAR,
    var harshSpell: Int = 0, // days left of a severe cold spell -> a trial the shelter must weather
    var settlementName: String? = null, // the name the group gives its home ground, once it settles
    var foundingMyth: String? = null, // the group's origin story, retold and shaping what they believe
    var yearBounty: Double = 1.0, // how generous this year's land is -> a year of plenty or of want
) {
    val inHarshSpell: Boolean get() = harshSpell > 0

    fun tileAt(x: Int, y: Int): Tile = tiles[y * width + x]
    fun inBounds(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    val hourOfDay: Int get() = (tick % TICKS_PER_DAY).toInt()
    val day: Long get() = tick / TICKS_PER_DAY
    val season: Season get() = Season.entries[((day / DAYS_PER_SEASON) % 4).toInt()]
    val year: Long get() = day / (DAYS_PER_SEASON * 4)
    val dayOfSeason: Int get() = (day % DAYS_PER_SEASON).toInt()

    val timeOfDay: TimeOfDay
        get() = when (hourOfDay) {
            in 5..6 -> TimeOfDay.DAWN
            in 7..10 -> TimeOfDay.MORNING
            in 11..13 -> TimeOfDay.MIDDAY
            in 14..17 -> TimeOfDay.AFTERNOON
            in 18..20 -> TimeOfDay.DUSK
            else -> TimeOfDay.NIGHT
        }

    val isNight: Boolean get() = timeOfDay == TimeOfDay.NIGHT

    /** Combined cold pressure right now, 0..1, from season + weather + night. */
    val coldness: Double
        get() = (season.chill + weather.extraChill + if (isNight) 0.15 else 0.0).coerceIn(0.0, 1.0)

    companion object {
        const val TICKS_PER_DAY = 24
        const val DAYS_PER_SEASON = 12L
    }
}
