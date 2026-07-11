package world.larutan.engine.being

import kotlinx.serialization.Serializable

/** The loose hierarchy a drive sits in. Lower tiers shout louder when unmet. */
enum class DriveTier(val urgencyWeight: Double) {
    PHYSIOLOGICAL(1.6),
    SAFETY(1.2),
    SOCIAL(0.9),
    GROWTH(0.6),
}

/**
 * The needs that motivate everything a being does. Each is a *satisfaction*
 * level, 0..100 — 100 is fully met, 0 is desperate. Left alone they drift down;
 * acting to meet them pushes them back up.
 */
enum class DriveType(
    val label: String,
    val tier: DriveTier,
    /** How fast satisfaction bleeds away per hour when untended. */
    val driftPerHour: Double,
) {
    HUNGER("hunger", DriveTier.PHYSIOLOGICAL, 2.0),
    THIRST("thirst", DriveTier.PHYSIOLOGICAL, 2.2),
    ENERGY("energy", DriveTier.PHYSIOLOGICAL, 2.4),
    WARMTH("warmth", DriveTier.PHYSIOLOGICAL, 0.0), // handled by the exposure model, not flat drift
    HEALTH("health", DriveTier.PHYSIOLOGICAL, 0.0), // moves only via hunger/cold/injury
    SECURITY("security", DriveTier.SAFETY, 0.8),
    CONNECTION("connection", DriveTier.SOCIAL, 1.1),
    INTIMACY("intimacy", DriveTier.SOCIAL, 0.7),
    PURPOSE("purpose", DriveTier.GROWTH, 0.9),
    CURIOSITY("curiosity", DriveTier.GROWTH, 1.0),
    AUTONOMY("autonomy", DriveTier.GROWTH, 0.6);
}

@Serializable
class Drives(
    val values: MutableMap<DriveType, Double> = DriveType.entries.associateWith { 70.0 }.toMutableMap(),
) {
    operator fun get(d: DriveType): Double = values.getValue(d)

    operator fun set(d: DriveType, v: Double) {
        values[d] = v.coerceIn(0.0, 100.0)
    }

    fun change(d: DriveType, delta: Double) {
        this[d] = this[d] + delta
    }

    /** The deficit on a drive, 0 (met) .. 1 (empty). */
    fun deficit(d: DriveType): Double = (100.0 - this[d]) / 100.0

    /**
     * How loudly this drive is calling right now. Physiological deficits dominate;
     * growth drives stay quiet until the lower tiers are reasonably handled — which
     * is what turns "finding purpose" into an earned, emergent arc rather than noise.
     */
    fun urgency(d: DriveType): Double {
        val base = deficit(d) * d.tier.urgencyWeight
        if (d.tier == DriveTier.GROWTH || d.tier == DriveTier.SOCIAL) {
            return base * lowerNeedsSatisfaction()
        }
        return base
    }

    /** 0..1 — how well the physiological floor is covered. Gates the higher drives. */
    fun lowerNeedsSatisfaction(): Double {
        val floor = listOf(DriveType.HUNGER, DriveType.THIRST, DriveType.WARMTH, DriveType.ENERGY)
        return floor.minOf { this[it] } / 100.0
    }

    fun dominant(): DriveType = DriveType.entries.maxBy { urgency(it) }

    fun copy(): Drives = Drives(HashMap(values))
}
