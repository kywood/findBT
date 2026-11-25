package com.gamenuri.btscanner24.core.ui.arrow3d

/**
 * How high the scene camera sits for a given phone pose (#76).
 *
 * The camera does not copy the phone's angle one to one: held the usual way (screen tilted about
 * 30–60° towards the face) that would look almost straight down and the floor, the perspective and
 * the shadow would flatten into a 2D picture. The usual hold shows the view of the design renders
 * instead, and even lying flat on a table the scene keeps its depth.
 */
internal object SceneCamera {
    /** Camera height with the phone upright. */
    const val UPRIGHT = 14f

    /** Camera height of the design renders; also used while the pose is unknown. */
    const val TYPICAL = 34f

    /**
     * Camera height with the phone lying flat. Capped well below top-down (#130): higher than this the
     * floor rings turn into circles and the arrow's sides and shadow hide under it, so it reads as 2D.
     */
    const val FLAT = 48f

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
