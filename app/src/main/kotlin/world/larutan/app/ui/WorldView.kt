package world.larutan.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import world.larutan.app.ui.model.BeingDot
import world.larutan.app.ui.model.WorldInfo
import world.larutan.app.ui.theme.Ember
import world.larutan.app.ui.theme.Line
import kotlin.math.abs

/**
 * The map. Beings are simple dots — position and colour carry the information,
 * exactly as the plan intends: minimal now, richer later on the same hooks. Tap a
 * dot to choose whom to follow; pinch or use the buttons to zoom in and out and
 * drag to move around once you're in close.
 */
@Composable
fun WorldView(
    world: WorldInfo,
    beings: List<BeingDot>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Zoom is a plain scale on the cell size; pan shifts the origin. Keeping the
    // maths here (rather than a graphics-layer transform) means a tap still lands
    // on the right tile at any zoom.
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var canvasPx by remember { mutableFloatStateOf(0f) }

    // Don't let the map be dragged off its own edges.
    fun clampPan(p: Offset, z: Float): Offset {
        val min = canvasPx * (1f - z) // 0 when not zoomed, negative once zoomed in
        return Offset(p.x.coerceIn(min, 0f), p.y.coerceIn(min, 0f))
    }

    fun setZoom(target: Float) {
        val z = target.coerceIn(1f, 5f)
        pan = clampPan(pan, z)
        zoom = z
    }

    Box(modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(2.dp)
                .onSizeChanged { canvasPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        val z = (zoom * zoomChange).coerceIn(1f, 5f)
                        pan = clampPan(pan + panChange, z)
                        zoom = z
                    }
                }
                .pointerInput(world.width, world.height, beings) {
                    detectTapGestures { tap ->
                        val cell = (canvasPx * zoom) / world.width
                        if (cell <= 0f) return@detectTapGestures
                        val gx = ((tap.x - pan.x) / cell).toInt()
                        val gy = ((tap.y - pan.y) / cell).toInt()
                        val hit = beings
                            .filter { it.alive }
                            .minByOrNull { abs(it.x - gx) + abs(it.y - gy) }
                        if (hit != null && abs(hit.x - gx) + abs(hit.y - gy) <= 2) onSelect(hit.id)
                    }
                },
        ) {
            val cell = (size.width / world.width) * zoom
            val ox = pan.x
            val oy = pan.y
            val nightWash = if (world.isNight) 0.5f else 1f

            // A faint grid so the space reads as a place, not a void.
            val gridColor = Line.copy(alpha = 0.25f * nightWash)
            for (i in 0..world.width) {
                val p = i * cell
                drawLine(gridColor, Offset(ox + p, oy), Offset(ox + p, oy + world.height * cell), strokeWidth = 1f)
                drawLine(gridColor, Offset(ox, oy + p), Offset(ox + world.width * cell, oy + p), strokeWidth = 1f)
            }

            for (b in beings) {
                val cx = ox + (b.x + 0.5f) * cell
                val cy = oy + (b.y + 0.5f) * cell
                if (!b.alive) {
                    drawCircle(Color(0xFF3A3E4A).copy(alpha = 0.5f), radius = cell * 0.18f, center = Offset(cx, cy))
                    continue
                }
                val color = beingColor(b.hue, b.valence).copy(alpha = nightWash.coerceAtLeast(0.7f))
                if (b.selected) {
                    drawCircle(Ember, radius = cell * 0.62f, center = Offset(cx, cy), style = Stroke(width = cell * 0.14f))
                }
                // A soft gold ring marks the ageless, so you can pick them out on the map.
                if (b.immortal) {
                    drawCircle(Color(0xFFE7D08A), radius = cell * 0.5f, center = Offset(cx, cy), style = Stroke(width = cell * 0.09f))
                }
                drawCircle(color, radius = cell * 0.34f, center = Offset(cx, cy))
            }
        }

        // Plain zoom controls, in case pinching isn't handy.
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ZoomButton("-") { setZoom(zoom / 1.4f) }
            ZoomButton("+") { setZoom(zoom * 1.4f) }
        }
    }
}

@Composable
private fun ZoomButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** Hue is identity (stable per being); brightness leans a little on how they feel. */
private fun beingColor(hue: Float, valence: Float): Color {
    val v = (0.68f + valence * 0.22f).coerceIn(0.45f, 0.95f)
    return Color.hsv(hue % 360f, 0.42f, v)
}
