package com.gamenuri.btscanner24.core.model

/**
 * A direction to a device that was really measured: the find screen's turn-around scan (#174).
 * It holds for where the user stood then, so it is only used while [isFresh].
 */
data class MeasuredBearing(
    /** Degrees clockwise from north, the compass heading the signal was strongest at. */
    val degrees: Float,
    /** The find screen had locked on, not just found a first direction. */
    val certain: Boolean,
    val measuredAt: Long,
) {
    /** A person has not walked far in this time; after it the direction is anyone's guess again. */
    fun isFresh(now: Long): Boolean = now - measuredAt in 0..FRESH_MS

    companion object {
        const val FRESH_MS = 2 * 60_000L

        /** The devices to place on the map, with the measured direction of those that have a fresh one. */
        fun entries(
            distances: List<Pair<String, Double?>>,
            bearings: Map<String, MeasuredBearing>,
            now: Long,
        ): List<MapEntry> = distances.map { (key, meters) ->
            MapEntry(key, meters, bearings[key]?.takeIf { it.isFresh(now) }?.degrees)
        }
    }
}
