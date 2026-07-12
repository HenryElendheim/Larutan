package world.larutan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import world.larutan.app.ui.model.Speed

/**
 * Time is yours: pause to observe, or let it run at the pace you want. Step
 * advances a single world-hour for close watching.
 */
@Composable
fun TimeControls(
    current: Speed,
    onSpeed: (Speed) -> Unit,
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        // Swipe through the speeds the same way you swipe the roster -> they don't all fit.
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Speed.entries.forEach { speed ->
            val selected = speed == current
            Text(
                text = speed.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSpeed(speed) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        Icon(
            imageVector = stepIcon,
            contentDescription = "Step one hour",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onStep() }
                .padding(8.dp),
        )
    }
}

// A hand-drawn "step" glyph -> a play-triangle with a bar at its edge. Rolling our
// own keeps us off the huge material-icons-extended artifact for a single icon,
// which is what kept the release build honest and quick. Icon() tints it for us,
// so the fill colour here is just a placeholder.
private val stepIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "StepOneHour",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 6f)
            lineTo(14.5f, 12f)
            lineTo(6f, 18f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(16f, 6f)
            lineTo(18f, 6f)
            lineTo(18f, 18f)
            lineTo(16f, 18f)
            close()
        }
    }.build()
}
