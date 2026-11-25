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

    /** Within reach: the arrow gives way to a puck with rings pulsing out over the floor. */
    @Volatile var rightHere: Boolean = false

    /** "Found it": the check medal rises from the floor; nothing else is shown. */
    @Volatile var found: Boolean = false

    /** Mint: the puck, its rings, the check and the found ring. */
    @Volatile var accentColor: Int = 0xFF2EE6A0.toInt()

    /**
     * The pulsing rings fade out between these two heights (pixels from the bottom of the view):
     * fully visible at [fadeFullY] and above, gone at [fadeClearY] and below, so that no ring
     * crosses the distance number or the buttons. Equal values: no fade.
     */
    @Volatile var fadeFullY: Float = 0f

    @Volatile var fadeClearY: Float = 0f

    /**
     * The scene is a layer across the whole screen instead of a box: the far floor rings and the
     * device marker are drawn too, and the floor runs on to the edges of the view.
     */
    @Volatile var fullBleed: Boolean = false

    /**
     * The far floor (rings, sector, marker, pulses) is gone above this share of the view's height
     * (0 = top edge) and whole a little below it, so it never lies under the state text. Negative: no fade.
     */
    @Volatile var topFade: Float = -1f

    /** Where the middle of the floor sits in the view, 0 = top edge, 1 = bottom edge. */
    @Volatile var centerY: Float = 0.5f

    /** The floor ring's radius as a share of the view's width. */
    @Volatile var ringFraction: Float = BOXED_RING_FRACTION

    /** The mint marker on the far ring, where the arrow points: shown once there is a direction. */
    @Volatile var showTarget: Boolean = false

    companion object {
        /** In its 280 dp box the ring is 108 dp: the same picture as a 30° field of view. */
        const val BOXED_RING_FRACTION = 0.3843f
    }

    @Volatile var arrowColor: Int = 0xFFFFFFFF.toInt()

    @Volatile var floorColor: Int = 0xFFFFFFFF.toInt()

    /** The system's "remove animations" setting: the arrow stops bobbing. */
    @Volatile var reduceMotion: Boolean = false

    @Volatile var hasPose: Boolean = false

    @Volatile var headingDegrees: Float = 0f

    /** The phone's pose (90 = lying flat, 0 = upright); [SceneCamera] turns it into a camera height. */
    @Volatile var elevationDegrees: Float = SceneCamera.TYPICAL_POSE

    @Volatile var rollDegrees: Float = 0f
}
