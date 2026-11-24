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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gamenuri.btscanner24.core.designsystem.component.FLOOR_TILT_DEGREES
import com.gamenuri.btscanner24.core.designsystem.component.drawFloorArrow
import com.gamenuri.btscanner24.core.designsystem.component.drawRoundedArrow
import com.gamenuri.btscanner24.core.designsystem.component.nearoArrowPath
import com.gamenuri.btscanner24.core.sensors.PoseSensor
import com.gamenuri.btscanner24.core.ui.arrow3d.FindScene3d
import com.gamenuri.btscanner24.core.ui.arrow3d.FindSceneSpec
import com.gamenuri.btscanner24.core.ui.arrow3d.PoseFeed
import kotlin.math.abs

/**
 * The round area in the middle of the find screen: the 3D arrow scene (#76) turning by itself while
 * sweeping and pointing once a direction is known, rings when the device is right here.
 *
 * Where OpenGL is not usable the Canvas drawings stand in: two nested layers, the inner one turns
 * the arrow in the floor plane (rotationZ), the outer one tilts that floor (rotationX 58°).
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
        val spinning = state.stage == FinderStage.SPIN ||
            state.stage == FinderStage.SPIN_AGAIN ||
            state.stage == FinderStage.WAITING
        // The 3D scene (#76) needs working GL; without it the Canvas arrow below is drawn.
        var sceneFailed by remember { mutableStateOf(false) }
        val scene3d = LocalArFindScene.current && !sceneFailed
        when {
            state.stage == FinderStage.RIGHT_HERE -> RightHereRings()
            scene3d && (arrow != null || spinning) ->
                ArrowScene(state, spinning, onUnavailable = { sceneFailed = true })
            arrow != null -> FloorArrow(arrow, state.confidence, locked = state.stage == FinderStage.LOCKED)
            spinning -> SpinningNeedle(state.sweepProgress)
            else -> HeatRing(state.heat)
        }
    }
}

/**
 * The 3D, AR-style arrow scene (#76) for everything below. On by default; debug builds can show
 * the Canvas fallback with the `--ez demo_ar false` launch extra.
 */
val LocalArFindScene = staticCompositionLocalOf { true }

/** The real 3D arrow on its floor; the camera follows how the phone is held. */
@Composable
private fun ArrowScene(state: FinderUiState, spinning: Boolean, onUnavailable: () -> Unit) {
    val context = LocalContext.current
    val poseFeed = remember(context) {
        val sensor = PoseSensor(context.applicationContext)
        PoseFeed { onPose ->
            sensor.start { pose ->
                onPose(
                    pose.headingDegrees.toFloat(),
                    pose.elevationDegrees.toFloat(),
                    pose.rollDegrees.toFloat(),
                )
            }
        }
    }
    val reduceMotion = remember(context) { context.animationsOff() }
    val color = LocalContentColor.current
    FindScene3d(
        spec = FindSceneSpec(
            relativeDegrees = state.arrowDegrees ?: 0f,
            worldBearingDegrees = state.bearingDegrees,
            spinning = spinning && state.arrowDegrees == null,
            progress = state.sweepProgress,
            wide = state.confidence != ArrowConfidence.HIGH,
            locked = state.stage == FinderStage.LOCKED,
            arrowColor = color,
            floorColor = color,
            reduceMotion = reduceMotion,
        ),
        modifier = Modifier.fillMaxSize(),
        poseFeed = poseFeed,
        onUnavailable = onUnavailable,
    )
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
    val sectorHalf by animateFloatAsState(
        targetValue = when {
            locked -> 12f
            wide -> 35f
            else -> 20f
        },
        label = "sector",
    )

    val color = LocalContentColor.current

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
            // The same floor as the 3D scene: ring R, a fainter ring at 0.62 R, the sector inside R.
            val ring = size.minDimension * FALLBACK_RING
            drawCircle(color.copy(alpha = 0.35f), radius = ring, style = Stroke(2.dp.toPx()))
            drawCircle(color.copy(alpha = 0.18f), radius = ring * 0.62f, style = Stroke(1.dp.toPx()))
            // "Somewhere this way": the sector widens as confidence drops.
            drawArc(
                color = color.copy(alpha = 0.16f),
                startAngle = -90f - sectorHalf,
                sweepAngle = sectorHalf * 2,
                useCenter = true,
                topLeft = Offset(center.x - ring, center.y - ring),
                size = Size(ring * 2, ring * 2),
            )
            scale(scaleX = widthScale, scaleY = 1f, pivot = center) { drawFloorArrow(color) }
        }
    }
}

@Composable
private fun SpinningNeedle(progress: Float) {
    // Holds still while water moves over the screen (#106).
    val rotation = if (LocalFindMotionPaused.current) {
        remember { mutableFloatStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "spin").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 2_400, easing = LinearEasing)),
            label = "needle",
        )
    }
    val color = LocalContentColor.current
    val track = LocalContentColor.current.copy(alpha = 0.25f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        progressRing(track, color, progress)
        // One arrow, no ghost trail (#106); the angle is read here, so only drawing is redone.
        rotate(rotation.value) {
            drawRoundedArrow(nearoArrowPath(scale = 0.75f), Brush.linearGradient(listOf(color, color)))
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
    val pulse = if (LocalFindMotionPaused.current) {
        remember { mutableFloatStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "rings").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1_600, easing = LinearEasing)),
            label = "pulse",
        )
    }
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.fillMaxSize()) {
        val maxRadius = size.minDimension / 2
        for (ring in 0 until RINGS) {
            val t = (pulse.value + ring.toFloat() / RINGS) % 1f
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

private const val MIN_TURN_DEGREES = 5f
private const val FALLBACK_RING = 0.385f
private const val RINGS = 3
