package com.gamenuri.btscanner24.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.res.stringResource
import com.gamenuri.btscanner24.core.model.DistanceDisplay
import com.gamenuri.btscanner24.core.model.DistanceUnit

/** Metres or feet for every distance on screen; set by `:app` from the region or the Settings choice (#83). */
val LocalDistanceUnit = compositionLocalOf { DistanceUnit.METERS }

/** "within 1 m", "~3 m", "~5–10 m" or "10 m+": the same wording on the list, My devices and find screen. */
@Composable
fun distanceText(meters: Double): String {
    val feet = LocalDistanceUnit.current == DistanceUnit.FEET
    return when (val display = DistanceDisplay.of(meters, LocalDistanceUnit.current)) {
        DistanceDisplay.WithinOne ->
            stringResource(if (feet) R.string.device_distance_within_3_ft else R.string.device_distance_within_1)

        is DistanceDisplay.About -> stringResource(
            if (feet) R.string.device_distance_ft else R.string.device_distance,
            display.amount.toString(),
        )

        DistanceDisplay.FiveToTen ->
            stringResource(if (feet) R.string.device_distance_15_to_30_ft else R.string.device_distance_5_to_10)

        DistanceDisplay.OverTen ->
            stringResource(if (feet) R.string.device_distance_over_30_ft else R.string.device_distance_over_10)
    }
}
