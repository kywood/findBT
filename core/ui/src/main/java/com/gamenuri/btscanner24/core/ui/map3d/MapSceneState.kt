package com.gamenuri.btscanner24.core.ui.map3d

import android.graphics.Bitmap
import com.gamenuri.btscanner24.core.ui.arrow3d.DeviceModel
import java.util.concurrent.ConcurrentHashMap

/**
 * What the map scene should show right now. Written from the main thread, read by the GL thread
 * every frame, which eases towards these targets itself. Lists and maps are replaced whole, never
 * changed in place, so plain volatile fields are enough.
 */
internal class MapSceneState {
    @Volatile var pins: List<MapPin> = emptyList()

    @Volatile var selectedKey: String? = null

    /** The screen's colour under the layer: the floor is tinted from it and fades into it far out. */
    @Volatile var backgroundColor: Int = 0xFF2563FF.toInt()

    /** Mint: only the selected device's ring. */
    @Volatile var accentColor: Int = 0xFF2EE6A0.toInt()

    /** The system's "remove animations" setting: the view stands still and nothing bobs or pulses. */
    @Volatile var reduceMotion: Boolean = false

    /** Where the middle of the floor sits in the view, 0 = top edge, 1 = bottom edge. */
    @Volatile var centerY: Float = MapSceneSpec.DEFAULT_CENTER_Y

    /** Shares of the height (0 = top) outside which nothing is drawn: the bars and the sheet lie there. */
    @Volatile var clipTop: Float = 0f

    @Volatile var clipBottom: Float = 1f

    @Volatile var hasPose: Boolean = false

    @Volatile var headingDegrees: Float = 0f

    /** The phone's pose (90 = lying flat, 0 = upright); [MapCamera] turns it into a camera height. */
    @Volatile var elevationDegrees: Float = MapCamera.TYPICAL_POSE

    /** Device models that have been read, [DeviceModel.FLOATS_PER_VERTEX] floats per vertex. */
    val meshes = ConcurrentHashMap<DeviceModel, FloatArray>()

    /** The white "me" chevron (#123), in the same format; null until it is read. */
    @Volatile var meMesh: FloatArray? = null

    /** The name tags, by their text, drawn by the main thread. */
    @Volatile var labels: Map<String, Bitmap> = emptyMap()

    /** The distances written on the steps, from the middle outwards; fewer than three: none. */
    @Volatile var stepLabels: List<Bitmap> = emptyList()

    /** Written by the GL thread after every frame. */
    @Volatile var hits: MapHits = MapHits.NONE
}
