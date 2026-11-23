package com.gamenuri.btscanner24.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gamenuri.btscanner24.core.model.DistanceDisplay
import java.util.Locale
import kotlin.math.roundToInt

/** "~0.8 m", "~2.5 m", "~5–10 m" or "10 m+": the same wording on the list, My devices and find screen. */
@Composable
fun distanceText(meters: Double): String = when (val display = DistanceDisplay.of(meters)) {
    is DistanceDisplay.About -> stringResource(R.string.device_distance, formatMeters(display.meters))
    DistanceDisplay.FiveToTen -> stringResource(R.string.device_distance_5_to_10)
    DistanceDisplay.OverTen -> stringResource(R.string.device_distance_over_10)
}

/** "0.8", "2.5", "3": no trailing ".0". */
internal fun formatMeters(meters: Double): String = if (meters == meters.roundToInt().toDouble()) {
    meters.roundToInt().toString()
} else {
    String.format(Locale.getDefault(), "%.1f", meters)
}
