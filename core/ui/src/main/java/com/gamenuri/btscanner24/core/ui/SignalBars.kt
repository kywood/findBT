package com.gamenuri.btscanner24.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.core.model.SignalLevel

/** Three rising bars: 3 lit = strong, 2 = medium, 1 = weak; grey when the device is gone. */
@Composable
fun SignalBars(level: SignalLevel, label: String, modifier: Modifier = Modifier, inactive: Boolean = false) {
    val colors = NearoTheme.colors
    val lit = when (level) {
        SignalLevel.STRONG -> 3
        SignalLevel.MEDIUM -> 2
        SignalLevel.WEAK -> 1
    }
    val on = when {
        inactive -> colors.signalInactive
        level == SignalLevel.STRONG -> colors.signalStrong
        level == SignalLevel.MEDIUM -> colors.signalMedium
        else -> colors.signalWeak
    }
    val off = colors.signalInactive
    Canvas(modifier = modifier.size(16.dp, 14.dp).semantics { contentDescription = label }) {
        val gap = 2.dp.toPx()
        val barWidth = (size.width - gap * 2) / 3
        for (i in 0 until 3) {
            val barHeight = size.height * (i + 1) / 3
            drawRoundRect(
                color = if (i < lit) on else off,
                topLeft = Offset(i * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
        }
    }
}
