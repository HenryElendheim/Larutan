package world.larutan.engine.narrative

import world.larutan.engine.Rng
import world.larutan.engine.being.Being
import world.larutan.engine.being.DriveType
import world.larutan.engine.being.EmotionName
import world.larutan.engine.world.World

/**
 * A thought is text generated from state — the window that makes interiority
 * visible. Never random: drawn from the dominant drive, the current mood, a
 * salient memory or relationship, and the being's personality "voice". This is
 * the always-on, offline core; an LLM can enrich the followed being later without
 * changing a line of this.
 */
object Thoughts {

    /** Surface a thought for a being, given the world and a name-lookup for others. */
    fun surface(being: Being, world: World, nameOf: (Int) -> String, rng: Rng): String {
        val dominant = being.drives.dominant()
        val valence = being.emotion.valence
        val season = world.season.label
        val place = placeLabel(world, being.x, being.y)
        val dominantEmotion = being.emotion.dominant()

        // A raw grief or fear overrides the drive-based selection — feeling comes first.
        when (dominantEmotion) {
            EmotionName.GRIEF -> {
                val lost = being.memory.events.firstOrNull { it.subjectId != null && it.valenceAtTime < -0.4 }?.subjectId
                val who = lost?.let(nameOf) ?: "them"
                return pick(rng, listOf(
                    "I keep thinking about $who. The $place feels emptier since.",
                    "Some mornings I forget, and then I remember $who is gone.",
                    "Grief doesn't leave. It just learns to sit quieter.",
                ))
            }
            EmotionName.LONELINESS -> return pick(rng, listOf(
                "It's a long day with no one to share it.",
                "I wish someone would come this way.",
                "I've been on my own too long. It starts to feel normal, and that's the worst part.",
            ))
            EmotionName.FEAR, EmotionName.ANXIETY -> return pick(rng, listOf(
                "Something about the $place isn't right. I should be careful.",
                "The $season puts an edge on everything.",
                "I don't feel safe here. Not really.",
            ))
            else -> {}
        }

        // An atypical mind falls into its own voice now and then, when nothing urgent
        // is pulling at it — the tail trait speaking plainly. Raw feeling still comes first
        // (handled above); this only colours the ordinary moments.
        being.personality.signature?.let { sig ->
            if (rng.chance(0.22)) return sig.voice
        }

        return when (dominant) {
            DriveType.HUNGER -> if (valence < 0) pick(rng, listOf(
                "My stomach won't be quiet. I need to find something to eat.",
                "Hunger again. There's never quite enough in $season.",
            )) else pick(rng, listOf(
                "A good meal. For a little while, nothing else matters.",
            ))
            DriveType.THIRST -> pick(rng, listOf(
                "My throat is dry. Water first, then everything else.",
                "I can hear the river from here. Good.",
                "Thirsty. Funny how it's always the small needs that nag loudest.",
            ))
            DriveType.WARMTH -> pick(rng, listOf(
                "The cold gets into your bones out here. I need shelter.",
                "$season bites. I keep thinking of somewhere warm.",
                "If I can just stay warm through this, I'll be all right.",
            ))
            DriveType.ENERGY -> pick(rng, listOf(
                "I'm worn through. I could sleep where I stand.",
                "Everything is heavier when you're this tired.",
            ))
            DriveType.CONNECTION -> pick(rng, listOf(
                "I miss the sound of another voice.",
                "I should find the others. It's been too quiet.",
            ))
            DriveType.PURPOSE -> {
                val g = being.goal
                if (g != null) pick(rng, listOf(
                    "I want to ${g.target}. I think about it more than I say.",
                    "Every day I get a little closer to what I'm after.",
                )) else pick(rng, listOf(
                    "There has to be more to a life than getting through the day.",
                    "I don't know what I'm reaching for yet. But I'm reaching.",
                ))
            }
            DriveType.CURIOSITY -> pick(rng, listOf(
                "I wonder what's past the far tiles. No one I know has been.",
                "The $place still has corners I haven't seen.",
            ))
            else -> if (valence > 0.3) pick(rng, listOf(
                "It's a fair day. I'll take it.",
                "Fed, warm, no one gone. That's enough, some days.",
                "The $place is quiet in a good way today.",
                "I don't need much. A day like this is close to it.",
            )) else pick(rng, listOf(
                "Another day in the $place. It passes.",
                "Some days you just put one foot after the other.",
                "I keep waiting to feel like myself again.",
            ))
        }
    }

    /** A short dream: unmet desire + a heavy memory + a touch of the surreal. */
    fun dream(being: Being, nameOf: (Int) -> String, rng: Rng): String {
        val dominant = being.drives.dominant()
        val lostId = being.memory.events.firstOrNull { it.valenceAtTime < -0.5 && it.subjectId != null }?.subjectId

        if (lostId != null && rng.chance(0.6)) {
            val who = nameOf(lostId)
            return pick(rng, listOf(
                "I dreamed $who was here again, and neither of us said a word about the winter.",
                "In the dream $who was young, and so was I, and the cold had never come.",
            ))
        }
        return when (dominant) {
            DriveType.HUNGER -> pick(rng, listOf(
                "I dreamed of a store that never ran empty, food heaped past the shelter walls.",
                "In the dream the whole ground bore fruit, and I ate until I was warm.",
            ))
            DriveType.WARMTH -> pick(rng, listOf(
                "I dreamed of a fire that didn't need feeding, and no season could reach it.",
            ))
            DriveType.CONNECTION -> pick(rng, listOf(
                "I dreamed the tiles were full of people, all of them turning to look at me and smile.",
            ))
            else -> pick(rng, listOf(
                "A strange one — the river ran uphill, and the sky was underfoot.",
                "I dreamed of walking somewhere I've never been, sure I'd been there before.",
                "I dreamed the whole world was one long summer, and no one was ever cold.",
            ))
        }
    }

    private fun placeLabel(world: World, x: Int, y: Int): String =
        world.tileAt(x, y).terrain.label

    private fun pick(rng: Rng, options: List<String>): String = rng.pick(options)
}
