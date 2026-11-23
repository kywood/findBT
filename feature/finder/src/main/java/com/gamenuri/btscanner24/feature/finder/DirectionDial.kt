package com.gamenuri.btscanner24.feature.finder

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * The round area in the middle of the find screen: spinning needle while sweeping, a 3D arrow lying
 * on the floor once a direction is known, rings when the device is right here.
 *
 * The 3D look is two nested layers, no 3D engine: the inner layer turns the arrow in the floor
 * plane (rotationZ), the outer layer tilts that floor away from the viewer (rotationX 58°).
 */
@Composable
internal fun DirectionDial(state: FinderUiState, modifier: Modifier = Modifier) {
    // Up to 280 dp, square, shrinking with the space the screen gives it.
    Box(
        modifier = modifier
            .sizeIn(maxWidth = 280.dp, maxHeight = 280.dp)
            .aspectRatio(1f, matchHeightConstraintsFirst = true),
        contentAlignment = Alignment.Center,
    ) {
        val arrow = state.arrowDegrees
        when {
            state.stage == FinderStage.RIGHT_HERE -> RightHereRings()
            arrow != null -> FloorArrow(arrow, state.confidence, locked = state.stage == FinderStage.LOCKED)
            state.stage == FinderStage.SPIN ||
                state.stage == FinderStage.SPIN_AGAIN ||
                state.stage == FinderStage.WAITING -> SpinningNeedle(state.sweepProgress)
            else -> HeatRing(state.heat)
        }
    }
}

@Composable
private fun FloorArrow(degrees: Float, confidence: ArrowConfidence, locked: Boolean) {
    val target = rememberContinuousAngle(degrees)
    val angle by animateFloatAsState(
        targetValue = target,
        // Locking "settles" the needle with a small overshoot; otherwise a calm 0.3 s ease.
        animationSpec = if (locked) {
            spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)
        } else {
            tween(durationMillis = 300, easing = FastOutSlowInEasing)
        },
        label = "arrowAngle",
    )
    val wide = confidence != ArrowConfidence.HIGH
    val widthScale by animateFloatAsState(if (wide) 1.4f else 1f, label = "arrowWidth")
    val arrowAlpha by animateFloatAsState(if (wide) 0.75f else 1f, label = "arrowAlpha")
    val sectorHalf by animateFloatAsState(if (wide) 45f else 25f, label = "sector")

    val color = LocalContentColor.current
    val shade = lerp(color, Color.Black, 0.35f)
    val highlight = lerp(color, Color.White, 0.25f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationX = FLOOR_TILT_DEGREES
                cameraDistance = 8 * density
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = angle
                    alpha = arrowAlpha
                },
        ) {
            // "Somewhere this way": the sector widens as confidence drops.
            drawArc(
                color = color.copy(alpha = 0.16f),
                startAngle = -90f - sectorHalf,
                sweepAngle = sectorHalf * 2,
                useCenter = true,
            )
            drawOval(
                color = Color.Black.copy(alpha = 0.12f),
                topLeft = Offset(size.width * 0.3f, size.height * 0.47f),
                size = Size(size.width * 0.4f, size.height * 0.12f),
            )
            scale(scaleX = widthScale, scaleY = 1f, pivot = center) {
                val path = arrowPath()
                // Darker copy slightly below gives the arrow some thickness.
                translate(top = size.minDimension * 0.025f) {
                    drawRounded(path, Brush.linearGradient(listOf(shade, shade)))
                }
                drawRounded(
                    path,
                    Brush.verticalGradient(listOf(highlight, color), startY = 0f, endY = size.height),
                )
            }
        }
    }
}

@Composable
private fun SpinningNeedle(progress: Float) {
    val transition = rememberInfiniteTransition(label = "spin")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2_400, easing = LinearEasing)),
        label = "needle",
    )
    val color = LocalContentColor.current
    val track = LocalContentColor.current.copy(alpha = 0.25f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        progressRing(track, color, progress)
        val path = arrowPath(scale = 0.75f)
        // Three fading ghosts behind the needle read as motion.
        for (ghost in GHOSTS downTo 0) {
            rotate(rotation - ghost * GHOST_SPACING_DEGREES) {
                val ghostAlpha = if (ghost == 0) 1f else 0.12f * (GHOSTS + 1 - ghost)
                drawRounded(path, Brush.linearGradient(listOf(color, color)), alpha = ghostAlpha)
            }
        }
    }
}

@Composable
private fun HeatRing(heat: Float) {
    val color = LocalContentColor.current
    val track = LocalContentColor.current.copy(alpha = 0.25f)
    Canvas(modifier = Modifier.fillMaxSize()) { progressRing(track, color, heat) }
}

@Composable
private fun RightHereRings() {
    val transition = rememberInfiniteTransition(label = "rings")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_600, easing = LinearEasing)),
        label = "pulse",
    )
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.fillMaxSize()) {
        val maxRadius = size.minDimension / 2
        for (ring in 0 until RINGS) {
            val t = (pulse + ring.toFloat() / RINGS) % 1f
            drawCircle(color.copy(alpha = 0.5f * (1f - t)), radius = maxRadius * t, style = Stroke(3.dp.toPx()))
        }
        drawCircle(color, radius = maxRadius * 0.12f)
    }
}

private fun DrawScope.progressRing(track: Color, color: Color, progress: Float) {
    val stroke = 10.dp.toPx()
    val inset = stroke / 2
    val arcSize = Size(size.width - stroke, size.height - stroke)
    drawArc(track, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
    drawArc(
        color = color,
        startAngle = -90f,
        sweepAngle = 360f * progress.coerceIn(0f, 1f),
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = arcSize,
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
}

/** Nearo's own round-ended arrow, pointing up from the centre. */
private fun DrawScope.arrowPath(scale: Float = 1f): Path {
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

private fun DrawScope.drawRounded(path: Path, brush: Brush, alpha: Float = 1f) {
    drawPath(path, brush, alpha = alpha)
    // A round-joined stroke of the same paint rounds the corners.
    drawPath(path, brush, alpha = alpha, style = Stroke(width = 6.dp.toPx(), join = StrokeJoin.Round))
}

/**
 * Keeps the angle continuous so the arrow turns the short way across north (350° → 10° is +20°,
 * not −340°), and ignores changes under 5° to stop jitter.
 */
@Composable
private fun rememberContinuousAngle(degrees: Float): Float {
    val holder = remember { floatArrayOf(degrees) }
    val delta = ((degrees - holder[0]) % 360f + 540f) % 360f - 180f
    if (abs(delta) >= MIN_TURN_DEGREES) holder[0] += delta
    return holder[0]
}

private const val FLOOR_TILT_DEGREES = 58f
private const val MIN_TURN_DEGREES = 5f
private const val GHOSTS = 3
private const val GHOST_SPACING_DEGREES = 12f
private const val RINGS = 3
