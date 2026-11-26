package com.gamenuri.btscanner24.core.model

import kotlin.math.roundToInt

/**
 * How an approximate distance is shown. Beyond 5 m one dB of noise moves the number by metres,
 * so far devices get a coarse range instead of a jumping value (PM decision, #21).
 */
sealed interface DistanceDisplay {
    /** "within 1 m" or "within 3 ft": below that RSSI cannot tell 0.3 m from 0.9 m, so no decimals (#52). */
    data object WithinOne : DistanceDisplay

    /** "~3 m" or "~10 ft": a whole number of [unit] between 1 and 5 m. */
    data class About(val amount: Int, val unit: DistanceUnit = DistanceUnit.METERS) : DistanceDisplay

    /** "~5–10 m" or "~15–30 ft". */
    data object FiveToTen : DistanceDisplay

    /** "10 m+" or "30 ft+". */
    data object OverTen : DistanceDisplay

    companion object {
        private const val FEET_PER_METER = 3.28084

        /**
         * The ranges are the same in both units (1, 5 and 10 m read as 3, 15 and 30 ft), so a device
         * sits in the same list filter whichever unit is shown (#83).
         */
        fun of(meters: Double, unit: DistanceUnit = DistanceUnit.METERS): DistanceDisplay = when {
            meters < 1.0 -> WithinOne
            meters < 5.0 -> when (unit) {
                DistanceUnit.METERS -> About(meters.roundToInt().coerceIn(1, 4), unit)
                DistanceUnit.FEET -> About((meters * FEET_PER_METER).roundToInt().coerceIn(3, 15), unit)
            }
            meters < 10.0 -> FiveToTen
            else -> OverTen
        }
    }
}
