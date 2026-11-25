package com.gamenuri.btscanner24.core.ui.map3d

/**
 * The map's stepped floor (design bt-map-3d v2.1, #86): three round steps for "within 1 m", "1–5 m"
 * and "5–10 m", each a little lower than the one inside it, and the open floor beyond for everything
 * further. Lengths are scene units, the designer's own.
 */
internal object MapFloor {
    /** Outer radius of each step, from the middle outwards. */
    val STEP_RADII = floatArrayOf(1.7f, 4.0f, 6.6f)

    /** How much lower each step lies than the one inside it; also the thickness of its slab. */
    const val STEP_HEIGHT = 0.12f

    const val STEPS = 3

    /** Places to stand: the three steps and the floor beyond them. */
    const val BANDS = STEPS + 1

    // Where a device may stand in each band, round the design's 1.2, 2.9, 5.3 and 7.9: clear of the
    // "me" marker in the middle and of the rims.
    private val STAND_FROM = floatArrayOf(1.1f, 2.45f, 4.75f, 7.5f)
    private val STAND_TO = floatArrayOf(1.3f, 3.35f, 5.85f, 8.3f)

    /** Height of the floor's top at [radius] from the middle. */
    fun height(radius: Float): Float = -STEP_HEIGHT * band(radius)

    /** The band [radius] falls in: 0, 1 or 2 for the steps, 3 beyond them. */
    fun band(radius: Float): Int = STEP_RADII.count { radius > it }

    /**
     * Distance from the middle for a [MapPin.ring] value: the whole part picks the band (0 to 3) and
     * the rest how far across it the device stands.
     */
    fun radius(ring: Float): Float {
        val clamped = ring.coerceIn(0f, BANDS - RING_EPSILON)
        val band = clamped.toInt()
        val across = clamped - band
        return STAND_FROM[band] + (STAND_TO[band] - STAND_FROM[band]) * across
    }

    private const val RING_EPSILON = 0.001f
}
