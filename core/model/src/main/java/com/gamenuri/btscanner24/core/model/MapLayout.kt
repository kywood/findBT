package com.gamenuri.btscanner24.core.model

/** A device to put on the map (#86): what the scan knows about where it is. */
data class MapEntry(
    /** Stable for the device; its place round the middle is made from it. */
    val key: String,
    /** The approximate distance from the smoothed signal, or null when there is none yet. */
    val distanceMeters: Double?,
    /**
     * A direction that was really measured (the find screen's turn-around scan), degrees clockwise in
     * the heading's frame; null for nearly every device, as a signal's strength says nothing about it.
     */
    val measuredDegrees: Float? = null,
)

/** Where a device stands on the map. */
data class MapSpot(
    /**
     * Degrees clockwise round the middle: from the top of the screen for a tidy place, in the heading's
     * frame when [measured].
     */
    val angleDegrees: Float,
    /**
     * The whole part is the distance band (0 = within 1 m, 1 = up to 5 m, 2 = up to 10 m, 3 = further)
     * and the rest how far across that band.
     */
    val ring: Float,
    /** [angleDegrees] is a measured direction, not just a tidy place on the ring. */
    val measured: Boolean,
)

/**
 * Arranges devices round "me" by distance (PM decision on #86): the map never pretends to know a
 * direction. The distance picks the ring; the place on the ring is one of a few fixed places that can
 * all be seen at once, chosen from the device's key, so it is the same in every frame and every run
 * and nobody has to turn the phone to "find" a device whose direction is not known. Devices that
 * would stand on top of each other move to the next free place. Only a measured direction is used as
 * it is, and only such a device is fixed in the room.
 */
object MapLayout {
    const val BANDS = 4

    /**
     * The places of each band, degrees clockwise from straight ahead (away from the viewer), wide
     * enough apart for two stands side by side. The nearest ring is seen all round. Further out only
     * the far side is in view (the list sheet covers the near side), and the further the ring, the
     * narrower the part of it that fits the screen's width.
     */
    private val PLACES = arrayOf(
        floatArrayOf(30f, 90f, 150f, 210f, 270f, 330f),
        floatArrayOf(300f, 324f, 348f, 12f, 36f, 60f, 144f, 180f, 216f),
        floatArrayOf(328f, 341f, 354f, 6f, 19f, 32f),
        floatArrayOf(333f, 342f, 351f, 0f, 9f, 18f, 27f),
    )
    private const val FULL_TURN = 360f
    private const val FAR_ACROSS = 0.5f
    private const val MAX_ACROSS = 0.999f

    // The same ranges as the list's distance texts ([DistanceDisplay]).
    private const val NEAR_METERS = 1.0
    private const val MID_METERS = 5.0
    private const val FAR_METERS = 10.0

    private const val HASH_MIX = -0x61c88647 // 0x9E3779B9, the golden-ratio hash multiplier.
    private const val HASH_SHIFT = 8

    fun arrange(entries: List<MapEntry>): Map<String, MapSpot> {
        val spots = HashMap<String, MapSpot>(entries.size)
        val taken = Array(BANDS) { BooleanArray(PLACES[it].size) }
        // The order of the keys, so who gives way never depends on the order of the scan.
        for (entry in entries.sortedBy { it.key }) {
            if (entry.key in spots) continue
            val ring = ring(entry.distanceMeters)
            val measured = entry.measuredDegrees
            if (measured != null) {
                spots[entry.key] = MapSpot(normalize(measured), ring, measured = true)
                continue
            }
            val band = ring.toInt()
            val free = freePlace(taken[band], homePlace(entry.key, band))
            taken[band][free] = true
            spots[entry.key] = MapSpot(PLACES[band][free], ring, measured = false)
        }
        return spots
    }

    /** How many devices a band holds before two have to share a place. */
    fun places(band: Int): Int = PLACES[band].size

    /** The band and the place across it for a distance; no distance yet counts as far. */
    fun ring(distanceMeters: Double?): Float {
        val meters = distanceMeters ?: return (BANDS - 1) + FAR_ACROSS
        val across = when {
            meters < NEAR_METERS -> meters / NEAR_METERS
            meters < MID_METERS -> 1 + (meters - NEAR_METERS) / (MID_METERS - NEAR_METERS)
            meters < FAR_METERS -> 2 + (meters - MID_METERS) / (FAR_METERS - MID_METERS)
            else -> return (BANDS - 1) + FAR_ACROSS
        }
        val band = across.toInt()
        return band + (across - band).toFloat().coerceIn(0f, MAX_ACROSS)
    }

    /** The place in [band] a key asks for: spread evenly over the places, the same in every run. */
    fun homePlace(key: String, band: Int): Int {
        val mixed = (key.hashCode() * HASH_MIX) ushr HASH_SHIFT
        return mixed % PLACES[band].size
    }

    /** [wanted] if it is free, else the nearest free one, looking right then left; [wanted] when all are taken. */
    private fun freePlace(taken: BooleanArray, wanted: Int): Int {
        val places = taken.size
        for (step in 0..places / 2) {
            val right = (wanted + step) % places
            if (!taken[right]) return right
            val left = (wanted - step + places) % places
            if (!taken[left]) return left
        }
        return wanted
    }

    private fun normalize(degrees: Float): Float = ((degrees % FULL_TURN) + FULL_TURN) % FULL_TURN
}
