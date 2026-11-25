package com.gamenuri.btscanner24.core.model

import kotlin.math.abs
import kotlin.math.roundToInt

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
    /** Degrees clockwise round the middle. */
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
 * direction. The distance picks the ring; the place on the ring is made from the device's key, so it
 * is the same in every frame and every run, and devices that would stand on top of each other move
 * to the next free place. Only a measured direction is used as it is.
 */
object MapLayout {
    const val BANDS = 4

    /** Places round each band, wide enough apart for two stands side by side. */
    private val SLOTS = intArrayOf(6, 12, 20, 28)

    /** Each band's places are turned by this share of a place, so devices do not line up in spokes. */
    private val TWIST = floatArrayOf(0.5f, 0.2f, 0.7f, 0.4f)
    private const val FULL_TURN = 360f
    private const val FAR_ACROSS = 0.5f
    private const val MAX_ACROSS = 0.999f

    // The same ranges as the list's distance texts ([DistanceDisplay]).
    private const val NEAR_METERS = 1.0
    private const val MID_METERS = 5.0
    private const val FAR_METERS = 10.0

    private const val HASH_MIX = -0x61c88647 // 0x9E3779B9, the golden-ratio hash multiplier.
    private const val HASH_SHIFT = 8
    private const val ANGLE_STEPS = 3600

    fun arrange(entries: List<MapEntry>): Map<String, MapSpot> {
        val spots = HashMap<String, MapSpot>(entries.size)
        val taken = Array(BANDS) { BooleanArray(SLOTS[it]) }
        // Measured directions first: they stand where they really are and keep that place free of others.
        for (entry in entries) {
            val degrees = entry.measuredDegrees ?: continue
            val ring = ring(entry.distanceMeters)
            val angle = normalize(degrees)
            taken[ring.toInt()][slot(angle, ring.toInt())] = true
            spots[entry.key] = MapSpot(angle, ring, measured = true)
        }
        // The rest in the order of their keys, so who gives way never depends on the order of the scan.
        for (entry in entries.filter { it.measuredDegrees == null }.sortedBy { it.key }) {
            if (entry.key in spots) continue
            val ring = ring(entry.distanceMeters)
            val band = ring.toInt()
            val slots = SLOTS[band]
            val free = freeSlot(taken[band], slot(homeDegrees(entry.key), band))
            taken[band][free] = true
            spots[entry.key] = MapSpot((free + TWIST[band]) * FULL_TURN / slots, ring, measured = false)
        }
        return spots
    }

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

    /** The place round the middle a key asks for: spread evenly, the same in every run. */
    fun homeDegrees(key: String): Float {
        val mixed = (key.hashCode() * HASH_MIX) ushr HASH_SHIFT
        return (mixed % ANGLE_STEPS) * FULL_TURN / ANGLE_STEPS
    }

    private fun slot(degrees: Float, band: Int): Int {
        val slots = SLOTS[band]
        return (((degrees / (FULL_TURN / slots) - TWIST[band]).roundToInt() % slots) + slots) % slots
    }

    /** [wanted] if it is free, else the nearest free one, looking right then left; [wanted] when all are taken. */
    private fun freeSlot(taken: BooleanArray, wanted: Int): Int {
        val slots = taken.size
        for (step in 0..slots / 2) {
            val right = (wanted + step) % slots
            if (!taken[right]) return right
            val left = (wanted - step + slots) % slots
            if (!taken[left]) return left
        }
        return wanted
    }

    private fun normalize(degrees: Float): Float = ((degrees % FULL_TURN) + FULL_TURN) % FULL_TURN

    /** The smallest turn between two directions, 0..180. */
    fun apart(a: Float, b: Float): Float {
        val turn = abs(normalize(a) - normalize(b))
        return if (turn > FULL_TURN / 2) FULL_TURN - turn else turn
    }
}
