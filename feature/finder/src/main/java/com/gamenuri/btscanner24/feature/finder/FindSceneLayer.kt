package com.gamenuri.btscanner24.feature.finder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * The 3D scene as a layer behind a find-flow screen (design find-flow-3d v1.1, #76): the full
 * width, from the top of the screen down to a little past the middle, where its floor has faded
 * out. The title and the state text lie on top of it; the distance number and the buttons sit
 * below the fade. The middle of the floor follows the middle of [slot], the place the screen's
 * layout keeps free for it, and the far floor fades out above the slot's top and below its
 * bottom when [fadeOutsideSlot] (text sits there); [scene] gets all three as shares of the layer's
 * height (the fades negative when off).
 */
@Composable
internal fun BoxScope.FindSceneLayer(
    slot: SceneSlot,
    modifier: Modifier = Modifier,
    fadeOutsideSlot: Boolean = false,
    scene: @Composable (centerY: Float, topFade: Float, bottomFade: Float) -> Unit,
) {
    var top by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(0f) }
    Box(
        modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .fillMaxHeight(SCENE_LAYER_HEIGHT)
            .onGloballyPositioned {
                val bounds = it.boundsInRoot()
                top = bounds.top
                height = bounds.height
            },
    ) {
        val placed = !slot.centerY.isNaN() && height > 0f
        val center = if (placed) ((slot.centerY - top) / height).coerceIn(MIN_CENTER, MAX_CENTER) else DEFAULT_CENTER
        val topFade = if (placed && fadeOutsideSlot) ((slot.topY - top) / height).coerceIn(0f, 1f) else NO_FADE
        val bottomFade = if (placed && fadeOutsideSlot) ((slot.bottomY - top) / height).coerceIn(0f, 1f) else NO_FADE
        scene(center, topFade, bottomFade)
    }
}

/** Where the layout keeps a place free for the scene, in root pixels; NaN until it is laid out. */
internal class SceneSlot {
    var centerY by mutableFloatStateOf(Float.NaN)
    var topY by mutableFloatStateOf(Float.NaN)
    var bottomY by mutableFloatStateOf(Float.NaN)
}

/** Reports this place to [slot] for [FindSceneLayer]. */
internal fun Modifier.sceneSlot(slot: SceneSlot): Modifier = onGloballyPositioned {
    val bounds = it.boundsInRoot()
    slot.centerY = bounds.center.y
    slot.topY = bounds.top
    slot.bottomY = bounds.bottom
}

private const val SCENE_LAYER_HEIGHT = 0.62f
private const val DEFAULT_CENTER = 0.7f
private const val NO_FADE = -1f
private const val MIN_CENTER = 0.35f
private const val MAX_CENTER = 0.9f
