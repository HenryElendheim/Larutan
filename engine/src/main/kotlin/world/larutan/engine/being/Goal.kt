package world.larutan.engine.being

import kotlinx.serialization.Serializable

/** The shape of an aspiration. A being selects one by fit, not by assignment. */
@Serializable
enum class GoalKind(val label: String) {
    EXPLORE("see the far edge of the world"),
    PROVIDE("store enough to never go hungry again"),
    MASTER_FORAGING("become the finest forager"),
    BUILD_SHELTER("raise the finest shelter that ever stood"),
    FAMILY("make a family and keep it safe"),
    BELONG("be understood and belong"),
    PROTECT("protect those who cannot protect themselves"),
}

@Serializable
data class Milestone(val label: String, var reached: Boolean = false)

@Serializable
enum class GoalStatus { FORMING, ACTIVE, ACHIEVED, FAILED, ABANDONED, EVOLVED }

/**
 * The arc of a life. Drives keep a being alive day to day; a goal gives them
 * something to *become* and a path to climb. It biases the utility loop toward
 * progress, and reaching a milestone writes a proud memory. Goals are formed,
 * revised, and remade — the churn is the story.
 */
@Serializable
data class Goal(
    val kind: GoalKind,
    val milestones: MutableList<Milestone>,
    var progress: Double = 0.0,          // 0..1 across the whole path
    val bornFrom: String,                // the memory / trait that sparked it
    var intensity: Double = 0.5,         // how strongly it drives them
    var status: GoalStatus = GoalStatus.ACTIVE,
) {
    val target: String get() = kind.label

    fun nextMilestone(): Milestone? = milestones.firstOrNull { !it.reached }

    /** Advance the path; returns the milestone just reached, if any. */
    fun advance(amount: Double): Milestone? {
        if (status != GoalStatus.ACTIVE) return null
        progress = (progress + amount).coerceIn(0.0, 1.0)
        val step = 1.0 / milestones.size
        val shouldBeReached = (progress / step).toInt().coerceAtMost(milestones.size)
        var justReached: Milestone? = null
        for (i in 0 until shouldBeReached) {
            if (!milestones[i].reached) {
                milestones[i].reached = true
                justReached = milestones[i]
            }
        }
        if (milestones.all { it.reached }) status = GoalStatus.ACHIEVED
        return justReached
    }

    companion object {
        /**
         * Choose an aspiration that fits who the being is and what they've lived.
         * Only called once the lower drives are handled — a starving being has no
         * dream beyond the next meal.
         */
        fun formFor(personality: Personality, memory: MemoryLog): Goal? {
            val nearlyStarved = memory.events.any { it.kind == MemoryKind.STARVED || it.kind == MemoryKind.FROZE }
            val lostSomeone = memory.events.any { it.kind == MemoryKind.LOST }

            val scored = GoalKind.entries.map { kind ->
                var fit = 0.2
                when (kind) {
                    GoalKind.EXPLORE -> fit += personality.curiosity + personality.boldness * 0.4
                    GoalKind.PROVIDE -> fit += personality.industry * 0.6 + if (nearlyStarved) 0.5 else 0.0
                    GoalKind.MASTER_FORAGING -> fit += personality.industry + personality.curiosity * 0.3
                    GoalKind.BUILD_SHELTER -> fit += personality.industry * 0.8 + if (nearlyStarved) 0.3 else 0.0
                    GoalKind.FAMILY -> fit += personality.warmth
                    GoalKind.BELONG -> fit += (0.5 - personality.warmth) * 0.6 + if (personality.isAtypical) 0.6 else 0.0
                    GoalKind.PROTECT -> fit += personality.warmth * 0.6 + if (lostSomeone) 0.8 else 0.0
                }
                // A small, stable per-being lean so two similar lives still reach for
                // different things instead of everyone wanting the same one.
                fit += jitter(kind, personality)
                kind to fit
            }
            val best = scored.maxByOrNull { it.second } ?: return null
            if (best.second < 0.35) return null

            val bornFrom = when {
                best.first == GoalKind.PROVIDE && nearlyStarved -> "a winter that nearly took them"
                best.first == GoalKind.PROTECT && lostSomeone -> "someone they could not save"
                best.first == GoalKind.BELONG && personality.isAtypical -> "always feeling a step apart"
                else -> "the kind of being they are"
            }
            return Goal(
                kind = best.first,
                milestones = milestonesFor(best.first),
                bornFrom = bornFrom,
                intensity = (0.4 + best.second * 0.2).coerceIn(0.3, 1.0),
            )
        }

        /** A deterministic, small [0, 0.3) lean per (being, goal) drawn from their traits. */
        private fun jitter(kind: GoalKind, p: Personality): Double {
            val seed = (p.boldness * 31 + p.warmth * 17 + p.curiosity * 13 + p.industry * 7 +
                p.optimism * 5 + p.temper * 3 + p.resilience * 2) * 1000 + kind.ordinal * 97
            val h = (seed.toLong() * -0x61c8864680b583ebL)
            return ((h ushr 40).toDouble() / (1L shl 24)) * 0.3
        }

        /** The path for a goal kind. Public so the god layer can plant a goal directly. */
        fun milestonesForPublic(kind: GoalKind): MutableList<Milestone> = milestonesFor(kind)

        private fun milestonesFor(kind: GoalKind): MutableList<Milestone> = when (kind) {
            GoalKind.EXPLORE -> mutableListOf(
                Milestone("wander past the home tiles"),
                Milestone("cross into unknown ground"),
                Milestone("reach the far edge"),
            )
            GoalKind.PROVIDE -> mutableListOf(
                Milestone("gather a first surplus"),
                Milestone("build a real store"),
                Milestone("carry a store through winter"),
            )
            GoalKind.MASTER_FORAGING -> mutableListOf(
                Milestone("learn the good ground"),
                Milestone("forage well even when it's thin"),
                Milestone("never come back empty-handed"),
            )
            GoalKind.BUILD_SHELTER -> mutableListOf(
                Milestone("gather materials"),
                Milestone("raise a rough shelter"),
                Milestone("make it warm and lasting"),
            )
            GoalKind.FAMILY -> mutableListOf(
                Milestone("find someone to trust"),
                Milestone("form a lasting bond"),
                Milestone("keep them safe through a hard season"),
            )
            GoalKind.BELONG -> mutableListOf(
                Milestone("be seen by another"),
                Milestone("be trusted by the group"),
                Milestone("find a place that fits"),
            )
            GoalKind.PROTECT -> mutableListOf(
                Milestone("watch over one other"),
                Milestone("shield someone from harm"),
                Milestone("be the one others lean on"),
            )
        }
    }
}
