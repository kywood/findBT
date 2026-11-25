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
 * below the fade. The middle of the floor follows [slotCenterY], the middle (in root pixels) of
 * the place the screen's layout keeps free for it; [scene] gets that as a share of the layer's height.
 */
@Composable
internal fun BoxScope.FindSceneLayer(
    slotCenterY: Float,
    modifier: Modifier = Modifier,
    scene: @Composable (centerY: Float) -> Unit,
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
        val center = if (slotCenterY.isNaN() || height <= 0f) {
            DEFAULT_CENTER
        } else {
            ((slotCenterY - top) / height).coerceIn(MIN_CENTER, MAX_CENTER)
        }
        scene(center)
    }
}

/** Reports the middle of this place (root pixels) for [FindSceneLayer]. */
internal fun Modifier.sceneSlot(onPlaced: (Float) -> Unit): Modifier =
    onGloballyPositioned { onPlaced(it.boundsInRoot().center.y) }

private const val SCENE_LAYER_HEIGHT = 0.62f
private const val DEFAULT_CENTER = 0.7f
private const val MIN_CENTER = 0.35f
private const val MAX_CENTER = 0.9f
