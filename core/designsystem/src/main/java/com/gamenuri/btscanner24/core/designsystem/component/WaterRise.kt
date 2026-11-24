package com.gamenuri.btscanner24.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** The rise itself (#90). */
const val WATER_RISE_MS = 700

/** Row tap to find screen: a 120 ms press, then the rise. */
const val WATER_PRESS_MS = 120
const val WATER_TOTAL_MS = WATER_PRESS_MS + WATER_RISE_MS

/**
 * Matte "water rising" reveal of design motion v2 (#90, anim2.py): this content shows only below a
 * wavy waterline that rises from the bottom edge to above the top. [progress] is linear 0..1 over
 * [pressMs] + [WATER_RISE_MS] (nothing shows during the press); run it backwards to drain. Only a
 * path clip, fills and strokes, so every API level (the Android 11 A50 too) looks the same.
 *
 * While the level rises, [cover] (the screen's own background) hides the content and fades out
 * between [contentFrom] and 1, so the content appears once the water has settled.
 */
fun Modifier.waterRise(
    progress: () -> Float,
    cover: Brush? = null,
    contentFrom: Float = 0.55f,
    glint: Float = 1f,
    pressMs: Int = WATER_PRESS_MS,
): Modifier = drawWithContent {
    val scope = this
    val t = ((progress() * (pressMs + WATER_RISE_MS) - pressMs) / WATER_RISE_MS).coerceIn(0f, 1f)
    when {
        t >= 1f -> drawContent()
        t <= 0f -> Unit
        else -> {
            val scale = size.width / DESIGN_WIDTH
            val line = waterline(size, t, scale)
            val body = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            clipPath(body) {
                scope.drawContent()
                val k = smoothstep(contentFrom, 1f, t)
                if (cover != null && k < 1f) drawRect(cover, alpha = 1f - k)
                // Soft lighter band just under the surface: two strokes, the outer half is clipped away.
                val band = BAND_ALPHA * glint / 2
                drawPath(line, Color.White.copy(alpha = band), style = Stroke(width = 28f * scale))
                drawPath(line, Color.White.copy(alpha = band), style = Stroke(width = 12f * scale))
            }
        }
    }
}

/** y(x) of the surface at rise time [t] (0..1), sampled every 24 design px. */
private fun waterline(size: Size, t: Float, scale: Float): Path {
    val w = size.width
    val h = size.height
    val p = easeOutCubic(min(1f, t / 0.86f))
    val level = (h + 50f * scale) - p * (h + 110f * scale)
    val amplitude = (30f * (1f - p).pow(0.7f) + 4f) * scale
    val phase = t * 0.7f
    val slosh = 46f * scale * sin(2f * PI.toFloat() * 1.6f * t) * (1f - t).pow(1.2f)
    val step = max(1f, 24f * scale)
    val path = Path()
    var x = 0f
    while (true) {
        val u = x / w
        val waves = sin(TAU * 1.15f * u + 9f * phase) +
            0.55f * sin(TAU * 2.3f * u - 14f * phase + 1.7f) +
            0.25f * sin(TAU * 4.1f * u + 23f * phase + 0.4f)
        val y = level + amplitude * waves / 1.8f + slosh * (u - 0.5f)
        if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
        if (x >= w) break
        x = min(w, x + step)
    }
    return path
}

private fun easeOutCubic(t: Float): Float = 1f - (1f - t).pow(3)

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val u = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return u * u * (3 - 2 * u)
}

private const val DESIGN_WIDTH = 540f
private const val BAND_ALPHA = 0.10f
private val TAU = (2 * PI).toFloat()
