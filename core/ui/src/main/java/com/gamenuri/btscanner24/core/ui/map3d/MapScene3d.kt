package com.gamenuri.btscanner24.core.ui.map3d

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
import com.gamenuri.btscanner24.core.ui.arrow3d.DeviceModel
import com.gamenuri.btscanner24.core.ui.arrow3d.PoseFeed

/** One device on the map (#86). */
@Immutable
data class MapPin(
    /** Stable for as long as the device is listed; taps report it back. */
    val key: String,
    val model: DeviceModel,
    /**
     * Where round the middle it stands, degrees clockwise from the top of the screen. When [measured]
     * it is in the frame of the pose heading instead, and the device stays put in the room while the
     * phone turns.
     */
    val angleDegrees: Float,
    /**
     * How far out: the whole part is the band (0 = within 1 m, 1 = up to 5 m, 2 = up to 10 m,
     * 3 = further) and the rest how far across that band, so 1.5 stands in the middle of the second one.
     */
    val ring: Float,
    /** The name tag over it, e.g. "My Earbuds · 2 m"; empty for none. Drawn on the phone only (#19). */
    val label: String = "",
    /**
     * [angleDegrees] is a direction that was really measured (the find screen's turn-around scan), not
     * just a tidy place on its ring: a white beam runs to it from the middle and it wears a second ring.
     */
    val measured: Boolean = false,
)

/** What the 3D device map shows. */
@Immutable
data class MapSceneSpec(
    val pins: List<MapPin>,
    /** The device that is picked out: mint ring, lifted off its stand. */
    val selectedKey: String? = null,
    /** The screen's colour behind the layer. */
    val backgroundColor: Color = Color(0xFF2563FF),
    val accentColor: Color = Color(0xFF2EE6A0),
    /** Animations are off in the system settings: fixed view, nothing bobs. */
    val reduceMotion: Boolean = false,
    /** Where the middle of the floor sits in the layer's height (0 = top). */
    val centerY: Float = DEFAULT_CENTER_Y,
    /** Nothing is drawn above this share of the layer's height: the top bar and the chips under it. */
    val clipTop: Float = 0f,
    /** Nothing is drawn below this share of the layer's height: the top edge of the list sheet. */
    val clipBottom: Float = 1f,
    /**
     * The distances written on the three steps, from the middle outwards, in the user's unit
     * ("1 m", "5 m", "10 m+" or "3 ft", "15 ft", "30 ft+"); empty for none.
     */
    val stepLabels: List<String> = emptyList(),
) {
    companion object {
        /** As in the design, where a list sheet covers the lower third. */
        const val DEFAULT_CENTER_Y = 0.41f
    }
}

/**
 * The device map as a real 3D space, drawn with the find scene's OpenGL ES layer: a stepped floor
 * with "me" in the middle and every device as its model on a stand. With a [poseFeed] the view
 * follows how the phone is held and turned. [onTap] gets the key of the device that was tapped, or
 * null for a tap on the empty floor. [onUnavailable] is called when the phone cannot run it.
 */
@Composable
fun MapScene3d(
    spec: MapSceneSpec,
    modifier: Modifier = Modifier,
    poseFeed: PoseFeed? = null,
    onTap: (key: String?) -> Unit = {},
    onUnavailable: () -> Unit = {},
) {
    val unavailable by rememberUpdatedState(onUnavailable)
    val tap by rememberUpdatedState(onTap)
    var scene by remember { mutableStateOf<MapSceneView?>(null) }
    AndroidView(
        factory = { context ->
            MapSceneView(context).also {
                it.onUnavailable = { unavailable() }
                it.onTap = { key -> tap(key) }
                scene = it
            }
        },
        modifier = modifier,
        update = { view ->
            view.state.apply {
                selectedKey = spec.selectedKey
                backgroundColor = spec.backgroundColor.toArgb()
                accentColor = spec.accentColor.toArgb()
                reduceMotion = spec.reduceMotion
                centerY = spec.centerY
                clipTop = spec.clipTop
                clipBottom = spec.clipBottom
            }
            view.setPins(spec.pins)
            view.setStepLabels(spec.stepLabels)
        },
    )
    // Sensors only while the screen is visible.
    LifecycleStartEffect(poseFeed, scene) {
        val view = scene
        val feed = if (view != null) poseFeed?.start(view::setPose) else null
        onStopOrDispose { feed?.close() }
    }
}
