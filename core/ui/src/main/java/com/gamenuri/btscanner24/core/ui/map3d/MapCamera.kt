package com.gamenuri.btscanner24.core.ui.map3d

/**
 * How high the map's camera sits for a given phone pose (design bt-map-3d v2). Like the find scene's
 * camera it never copies the phone one to one: lying flat it stops well short of a top view, where
 * the steps and the models would flatten into a 2D radar (#128).
 */
internal object MapCamera {
    /** Camera height with the phone upright: looking across the floor. */
    const val UPRIGHT = 13f

    /** Camera height of the design's usual hold; also used while the pose is unknown. */
    const val TYPICAL = 22f

    /** Camera height with the phone lying flat. */
    const val FLAT = 46f

    /** The phone pose (90 = lying flat, 0 = upright) that shows [TYPICAL]. */
    const val TYPICAL_POSE = 45f

    private const val FLAT_POSE = 90f

    /** Camera elevation in degrees for a phone [poseElevation] (90 = lying flat, 0 = upright). */
    fun elevationFor(poseElevation: Float): Float {
        val pose = poseElevation.coerceIn(0f, FLAT_POSE)
        return if (pose <= TYPICAL_POSE) {
            UPRIGHT + (TYPICAL - UPRIGHT) * (pose / TYPICAL_POSE)
        } else {
            TYPICAL + (FLAT - TYPICAL) * ((pose - TYPICAL_POSE) / (FLAT_POSE - TYPICAL_POSE))
        }
    }
}
