package com.gamenuri.btscanner24.core.ui.arrow3d

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect

/** What the 3D find scene shows (#76). Angles are degrees, clockwise. */
@Immutable
data class FindSceneSpec(
    /** Direction relative to the top of the phone; used when [worldBearingDegrees] is null. */
    val relativeDegrees: Float = 0f,
    /**
     * Direction in the same frame as the pose heading. With it (and a [PoseFeed]) the arrow stays
     * fixed in the room while the phone turns, which is what makes it feel like AR.
     */
    val worldBearingDegrees: Float? = null,
    /** Still looking for a direction: the arrow turns by itself and the ring shows [progress]. */
    val spinning: Boolean = false,
    val progress: Float = 0f,
    /** Low confidence: wider arrow and sector. */
    val wide: Boolean = false,
    val locked: Boolean = false,
    /** Within reach: a mint puck with rings pulsing over the floor instead of the arrow. */
    val rightHere: Boolean = false,
    /** "Found it": only the check medal, rising from the floor ring. */
    val found: Boolean = false,
    /** Mint: the puck, its rings, the check and the found ring. */
    val accentColor: Color = Color(0xFF2EE6A0),
    val arrowColor: Color = Color.White,
    val floorColor: Color = Color.White,
    /** Animations are off in the system settings: no bobbing. */
    val reduceMotion: Boolean = false,
    /**
     * The scene is a layer across the screen (design find-flow-3d v1.1), not a box: the ring is
     * 0.30 of the width, the far rings and the device marker are drawn, and the floor's middle
     * sits at [centerY] of the layer's height (0 = top).
     */
    val fullBleed: Boolean = false,
    val centerY: Float = 0.5f,
    /** The mint marker on the far ring, where the arrow points (only with [fullBleed]). */
    val showTarget: Boolean = false,
)

/** The floor ring's radius as a share of the screen width in the full-bleed layout. */
private const val FULL_BLEED_RING_FRACTION = 0.30f

/**
 * Source of the phone's pose for the scene camera. The feature module backs it with core:sensors;
 * without one the camera stays at the design's fixed height.
 */
fun interface PoseFeed {
    /** [onPose] gets heading, elevation (90 = lying flat, 0 = upright) and roll in degrees, on the main thread. */
    fun start(onPose: (heading: Float, elevation: Float, roll: Float) -> Unit): AutoCloseable
}

/**
 * The find arrow as a real 3D object on a floor, drawn with OpenGL ES on a see-through layer.
 * [onUnavailable] is called when the phone cannot run it; draw the Canvas arrow instead then.
 */
@Composable
fun FindScene3d(
    spec: FindSceneSpec,
    modifier: Modifier = Modifier,
    poseFeed: PoseFeed? = null,
    onUnavailable: () -> Unit = {},
) {
    val unavailable by rememberUpdatedState(onUnavailable)
    var scene by remember { mutableStateOf<SceneTextureView?>(null) }
    AndroidView(
        factory = { context ->
            SceneTextureView(context).also {
                it.onUnavailable = { unavailable() }
                scene = it
            }
        },
        modifier = modifier,
        update = { view ->
            view.state.apply {
                relativeDegrees = spec.relativeDegrees
                worldBearingDegrees = spec.worldBearingDegrees ?: Float.NaN
                spinning = spec.spinning
                progress = spec.progress
                wide = spec.wide
                locked = spec.locked
                rightHere = spec.rightHere
                found = spec.found
                accentColor = spec.accentColor.toArgb()
                arrowColor = spec.arrowColor.toArgb()
                floorColor = spec.floorColor.toArgb()
                reduceMotion = spec.reduceMotion
                fullBleed = spec.fullBleed
                centerY = spec.centerY
                ringFraction = if (spec.fullBleed) FULL_BLEED_RING_FRACTION else SceneState.BOXED_RING_FRACTION
                showTarget = spec.showTarget
            }
        },
    )
    // Sensors only while the screen is visible.
    LifecycleStartEffect(poseFeed, scene) {
        val view = scene
        val feed = if (view != null) poseFeed?.start(view::setPose) else null
        onStopOrDispose { feed?.close() }
    }
}
