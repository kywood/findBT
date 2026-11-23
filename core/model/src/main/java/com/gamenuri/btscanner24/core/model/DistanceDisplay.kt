package com.gamenuri.btscanner24.core.model

import kotlin.math.roundToInt

/**
 * How an approximate distance is shown. Beyond 5 m one dB of noise moves the number by metres,
 * so far devices get a coarse range instead of a jumping value (PM decision, #21).
 */
sealed interface DistanceDisplay {
    /** "~0.8 m", "~2.5 m": 0.1 m steps under 1 m, 0.5 m steps up to 5 m. */
    data class About(val meters: Double) : DistanceDisplay

    /** "~5–10 m". */
    data object FiveToTen : DistanceDisplay

    /** "10 m+". */
    data object OverTen : DistanceDisplay

    companion object {
        fun of(meters: Double): DistanceDisplay = when {
            meters < 1.0 -> About(((meters * 10).roundToInt() / 10.0).coerceAtLeast(0.1))
            meters < 5.0 -> About((meters * 2).roundToInt() / 2.0)
            meters < 10.0 -> FiveToTen
            else -> OverTen
        }
    }
}
