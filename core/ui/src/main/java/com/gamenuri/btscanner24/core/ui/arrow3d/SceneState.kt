package com.gamenuri.btscanner24.core.ui.arrow3d

/**
 * What the scene should show right now. Written from the main thread (UI state and sensor), read by
 * the GL thread every frame, which eases towards these targets itself; plain volatile fields are
 * enough because each value stands alone.
 */
internal class SceneState {
    /** Direction in the world (same frame as the heading), or NaN to use [relativeDegrees]. */
    @Volatile var worldBearingDegrees: Float = Float.NaN

    /** Direction relative to the top of the phone, clockwise; used when there is no world bearing. */
    @Volatile var relativeDegrees: Float = 0f

    /** The arrow turns by itself (looking for a direction) instead of pointing. */
    @Volatile var spinning: Boolean = false

    /** 0..1 arc on the floor ring while spinning. */
    @Volatile var progress: Float = 0f

    /** Low confidence: wider arrow and sector. */
    @Volatile var wide: Boolean = false

    /** Direction locked: the arrow settles with a small overshoot. */
    @Volatile var locked: Boolean = false

    @Volatile var arrowColor: Int = 0xFFFFFFFF.toInt()

    @Volatile var floorColor: Int = 0xFFFFFFFF.toInt()

    @Volatile var hasPose: Boolean = false

    @Volatile var headingDegrees: Float = 0f

    @Volatile var elevationDegrees: Float = DEFAULT_ELEVATION

    @Volatile var rollDegrees: Float = 0f

    companion object {
        /** The camera height of the design renders when the phone's pose is unknown. */
        const val DEFAULT_ELEVATION = 34f
    }
}
