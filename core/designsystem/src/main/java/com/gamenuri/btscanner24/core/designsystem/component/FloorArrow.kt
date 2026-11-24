package com.gamenuri.btscanner24.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/** The floor is tilted away from the viewer by this much; the find screen and its pictures share it. */
const val FLOOR_TILT_DEGREES = 58f

/**
 * Nearo's 3D arrow lying on the floor, pointing at [degrees] (0 = straight ahead). A still picture of
 * the find screen's arrow, for illustrations such as the onboarding hero (#72).
 */
@Composable
fun FloorArrowIllustration(color: Color, modifier: Modifier = Modifier, degrees: Float = 0f) {
    Box(
        modifier = modifier.graphicsLayer {
            rotationX = FLOOR_TILT_DEGREES
            cameraDistance = 8 * density
        },
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = degrees }) { drawFloorArrow(color) }
    }
}

/** Shadow, a darker copy for thickness, then the arrow with a light top. Draw inside a tilted layer. */
fun DrawScope.drawFloorArrow(color: Color, alpha: Float = 1f) {
    val shade = lerp(color, Color.Black, 0.35f)
    val highlight = lerp(color, Color.White, 0.25f)
    drawOval(
        color = Color.Black.copy(alpha = 0.12f * alpha),
        topLeft = Offset(size.width * 0.3f, size.height * 0.47f),
        size = Size(size.width * 0.4f, size.height * 0.12f),
    )
    val path = nearoArrowPath()
    translate(top = size.minDimension * 0.025f) {
        drawRoundedArrow(path, Brush.linearGradient(listOf(shade, shade)), alpha)
    }
    drawRoundedArrow(path, Brush.verticalGradient(listOf(highlight, color), startY = 0f, endY = size.height), alpha)
}

/** Nearo's own round-ended arrow, pointing up from the centre. */
fun DrawScope.nearoArrowPath(scale: Float = 1f): Path {
    val s = size.minDimension * scale
    val cx = center.x
    val cy = center.y
    return Path().apply {
        moveTo(cx, cy - 0.40f * s)
        lineTo(cx + 0.20f * s, cy - 0.08f * s)
        lineTo(cx + 0.07f * s, cy - 0.11f * s)
        lineTo(cx + 0.07f * s, cy + 0.28f * s)
        lineTo(cx - 0.07f * s, cy + 0.28f * s)
        lineTo(cx - 0.07f * s, cy - 0.11f * s)
        lineTo(cx - 0.20f * s, cy - 0.08f * s)
        close()
    }
}

/** Fills [path] and strokes it with round joins in the same paint, which rounds the corners. */
fun DrawScope.drawRoundedArrow(path: Path, brush: Brush, alpha: Float = 1f) {
    drawPath(path, brush, alpha = alpha)
    drawPath(path, brush, alpha = alpha, style = Stroke(width = 6.dp.toPx(), join = StrokeJoin.Round))
}
