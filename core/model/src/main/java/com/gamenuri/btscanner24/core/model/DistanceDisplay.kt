package com.gamenuri.btscanner24.core.model

import kotlin.math.roundToInt

/**
 * How an approximate distance is shown. Beyond 5 m one dB of noise moves the number by metres,
 * so far devices get a coarse range instead of a jumping value (PM decision, #21).
 */
sealed interface DistanceDisplay {
    /** "within 1 m": below that RSSI cannot tell 0.3 m from 0.9 m, so no decimals (design review, #52). */
    data object WithinOne : DistanceDisplay

    /** "~3 m": whole metres from 1 to 5 m. */
    data class About(val meters: Int) : DistanceDisplay

    /** "~5–10 m". */
    data object FiveToTen : DistanceDisplay

    /** "10 m+". */
    data object OverTen : DistanceDisplay

    companion object {
        fun of(meters: Double): DistanceDisplay = when {
            meters < 1.0 -> WithinOne
            meters < 5.0 -> About(meters.roundToInt().coerceIn(1, 4))
            meters < 10.0 -> FiveToTen
            else -> OverTen
        }
    }
}
