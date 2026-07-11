package world.larutan.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import world.larutan.app.ui.model.BeingDot
import world.larutan.app.ui.model.WorldInfo
import world.larutan.app.ui.theme.Ember
import world.larutan.app.ui.theme.Line
import kotlin.math.abs

/**
 * The map. Beings are simple dots — position and colour carry the information,
 * exactly as the plan intends: minimal now, richer later on the same hooks. Tap a
 * dot to choose whom to follow.
 */
@Composable
fun WorldView(
    world: WorldInfo,
    beings: List<BeingDot>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(2.dp)
            .pointerInput(world.width, world.height, beings) {
                detectTapGestures { tap ->
                    val cell = size.width.toFloat() / world.width
                    val gx = (tap.x / cell).toInt()
                    val gy = (tap.y / cell).toInt()
                    val hit = beings
                        .filter { it.alive }
                        .minByOrNull { abs(it.x - gx) + abs(it.y - gy) }
                    if (hit != null && abs(hit.x - gx) + abs(hit.y - gy) <= 2) onSelect(hit.id)
                }
            },
    ) {
        val cell = size.width / world.width
        val nightWash = if (world.isNight) 0.5f else 1f

        // A faint grid so the space reads as a place, not a void.
        val gridColor = Line.copy(alpha = 0.25f * nightWash)
        for (i in 0..world.width) {
            val p = i * cell
            drawLine(gridColor, Offset(p, 0f), Offset(p, size.height), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, p), Offset(size.width, p), strokeWidth = 1f)
        }

        for (b in beings) {
            val cx = (b.x + 0.5f) * cell
            val cy = (b.y + 0.5f) * cell
            if (!b.alive) {
                drawCircle(Color(0xFF3A3E4A).copy(alpha = 0.5f), radius = cell * 0.18f, center = Offset(cx, cy))
                continue
            }
            val color = beingColor(b.hue, b.valence).copy(alpha = nightWash.coerceAtLeast(0.7f))
            if (b.selected) {
                drawCircle(Ember, radius = cell * 0.62f, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = cell * 0.14f))
            }
            drawCircle(color, radius = cell * 0.34f, center = Offset(cx, cy))
        }
    }
}

/** Hue is identity (stable per being); brightness leans a little on how they feel. */
private fun beingColor(hue: Float, valence: Float): Color {
    val v = (0.68f + valence * 0.22f).coerceIn(0.45f, 0.95f)
    return Color.hsv(hue % 360f, 0.42f, v)
}
