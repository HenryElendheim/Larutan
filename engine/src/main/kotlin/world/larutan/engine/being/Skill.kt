package world.larutan.engine.being

import kotlinx.serialization.Serializable

/** Learned capabilities that improve with practice and can be taught on (§4.7). */
@Serializable
enum class SkillType(val label: String) {
    FORAGING("foraging"),
    BUILDING("building"),
    CAREGIVING("caregiving"),
    CULTIVATION("cultivation"),
}

/**
 * What a being has learned to do well. Each skill is a 0..1 level that climbs with
 * practice (fast at first, then slowly) and can be passed to someone who knows less
 * -> knowledge as inheritance, the thread culture is woven from (§9).
 */
@Serializable
class Skills(val levels: MutableMap<SkillType, Double> = mutableMapOf()) {

    operator fun get(s: SkillType): Double = levels[s] ?: 0.0

    /** Practice: climbs toward mastery with diminishing returns, so no one is instantly expert. */
    fun practice(s: SkillType, amount: Double = 0.01) {
        val cur = get(s)
        levels[s] = (cur + amount * (1.0 - cur)).coerceIn(0.0, 1.0)
    }

    /** Learn from someone who knows more: move part of the way toward their level. */
    fun learnFrom(teacherLevel: Double, s: SkillType, rate: Double = 0.3) {
        val cur = get(s)
        if (teacherLevel > cur) levels[s] = (cur + (teacherLevel - cur) * rate).coerceIn(0.0, 1.0)
    }

    fun copy(): Skills = Skills(HashMap(levels))
}
