package world.larutan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import world.larutan.app.ui.model.DriveBar
import world.larutan.app.ui.model.FollowedBeing
import world.larutan.app.ui.model.GoalView
import world.larutan.app.ui.model.GodAction
import world.larutan.app.ui.model.RelationView
import world.larutan.app.ui.theme.Clay
import world.larutan.app.ui.theme.Ember
import world.larutan.app.ui.theme.Moss
import world.larutan.app.ui.theme.Tide

/**
 * The person. When you follow a being, this is what you read: what's pressing,
 * how they feel, what they're reaching for, what they think and dream and carry.
 * The 2D view is the map; this panel is the product.
 */
@Composable
fun InnerLifePanel(
    being: FollowedBeing,
    onGod: (GodAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Header(being)
        if (being.alive) {
            // A living being: the full inner life, and the powers to reach into it.
            GodTouch(onGod, immortal = being.immortal)
            if (being.emotions.isNotEmpty()) Emotions(being.emotions)
            being.lastThought?.let { Utterance(label = "Thinking", text = it) }
            being.goal?.let { Goal(it) }
            Drives(being.drives)
            being.lastDream?.let { Utterance(label = "Last night's dream", text = it, dream = true) }
            if (being.relationships.isNotEmpty()) Relationships(being.relationships)
            if (being.memories.isNotEmpty()) Memories(being.memories)
        } else {
            // A soul at rest: no drives to tend, but a last word and the thoughts they carried.
            being.finalThought?.let { Utterance(label = "Final thought", text = it) }
            if (being.pastThoughts.isNotEmpty()) PastThoughts(being.pastThoughts)
            if (being.relationships.isNotEmpty()) Relationships(being.relationships)
            if (being.memories.isNotEmpty()) Memories(being.memories)
        }
    }
}

@Composable
private fun Header(b: FollowedBeing) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            if (b.atypical) {
                Text("  a mind apart", style = MaterialTheme.typography.labelSmall, color = Tide)
            }
            // A plain, always-visible mark of agelessness, so you can tell at a glance.
            if (b.immortal) {
                Text("  ageless", style = MaterialTheme.typography.labelSmall, color = Ember)
            }
        }
        Text(
            if (b.alive) "generation ${b.generation} · ${b.lifeStage} · ${b.ageYears} years"
            else "generation ${b.generation} · gone to ${b.deathCause ?: "the world"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (b.alive) "${b.mood.replaceFirstChar { it.uppercase() }} — ${b.action}"
            else "At rest in ${b.realm ?: "the world"} — ${b.mood}",
            style = MaterialTheme.typography.titleMedium,
            color = moodColor(b.valence),
        )
    }
}

@Composable
private fun GodTouch(onGod: (GodAction) -> Unit, immortal: Boolean) {
    // Reach in. Powerful and free -- the being's own systems metabolise what you do.
    // The agelessness power is a toggle, and it shows whether it's on or off.
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GodAction.entries.forEach { action ->
            val isAgeless = action == GodAction.IMMORTALITY
            val on = isAgeless && immortal
            val label = if (isAgeless) (if (immortal) "Ageless: on" else "Ageless: off") else action.label
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                // A filled, highlighted chip means the toggle is on; hollow means off.
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .clickable { onGod(action) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PastThoughts(thoughts: List<String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel("What they were thinking")
        thoughts.forEach { t ->
            Text(
                "“$t”",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
            )
        }
    }
}

@Composable
private fun Emotions(emotions: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        emotions.forEach { name ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Utterance(label: String, text: String, dream: Boolean = false) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SectionLabel(label)
        Text(
            text = if (dream) text else "“$text”",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontStyle = if (dream) FontStyle.Italic else FontStyle.Normal,
        )
    }
}

@Composable
private fun Goal(g: GoalView) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel("What they're reaching for")
        Text(
            g.target.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Meter(g.progress, Ember)
        g.milestones.forEach { (label, reached) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // A small dot for each step on the path: filled once reached, hollow while ahead.
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (reached) Moss else MaterialTheme.colorScheme.surfaceVariant),
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (reached) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text("Born from ${g.bornFrom}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Drives(drives: List<DriveBar>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        SectionLabel("What's pressing")
        drives.forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    d.label.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(96.dp),
                )
                Meter(d.value, driveColor(d.value), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Relationships(rels: List<RelationView>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel("Who they hold")
        rels.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(120.dp)) {
                    Text(r.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                    Text(r.sentiment, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Meter(r.bond, Tide, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Memories(memories: List<String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel("What stays with them")
        memories.forEach { m ->
            Text("— $m", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- small pieces -----------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun Meter(value: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(value.coerceIn(0f, 1f))
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
    }
}

private fun driveColor(v: Float): Color = when {
    v < 0.25f -> Clay
    v < 0.55f -> Ember
    else -> Moss
}

private fun moodColor(valence: Float): Color = when {
    valence > 0.3f -> Moss
    valence < -0.3f -> Clay
    else -> Ember
}
