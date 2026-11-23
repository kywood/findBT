package com.gamenuri.btscanner24.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gamenuri.btscanner24.core.model.DistanceDisplay

/** "within 1 m", "~3 m", "~5–10 m" or "10 m+": the same wording on the list, My devices and find screen. */
@Composable
fun distanceText(meters: Double): String = when (val display = DistanceDisplay.of(meters)) {
    DistanceDisplay.WithinOne -> stringResource(R.string.device_distance_within_1)
    is DistanceDisplay.About -> stringResource(R.string.device_distance, display.meters.toString())
    DistanceDisplay.FiveToTen -> stringResource(R.string.device_distance_5_to_10)
    DistanceDisplay.OverTen -> stringResource(R.string.device_distance_over_10)
}
