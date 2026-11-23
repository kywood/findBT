package com.gamenuri.btscanner24.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Line illustrations from design screens-rest-v3 (24 grid, 2 px stroke). Drawn black and tinted by
 * Icon(), like Material icons.
 */
object NearoIcons {
    /** Pin with a signal arc: onboarding "Find lost earbuds…". */
    val PinSignal: ImageVector by lazy {
        stroked(
            "PinSignal",
            "M12 2.5c-3.6 0-6.4 2.8-6.4 6.3 0 4.6 6.4 12.7 6.4 12.7s6.4-8.1 6.4-12.7c0-3.5-2.8-6.3-6.4-6.3z",
            "M10 8.3a2.8 2.8 0 0 1 4 0",
            circle(12f, 10.5f, 1f),
        )
    }

    /** Dot with radio waves: "Allow Nearby devices", "Permission needed". */
    val NearbySignal: ImageVector by lazy {
        stroked(
            "NearbySignal",
            circle(12f, 12f, 2f),
            "M8 8a5.6 5.6 0 0 0 0 8M16 8a5.6 5.6 0 0 1 0 8M5 5a9.9 9.9 0 0 0 0 14M19 5a9.9 9.9 0 0 1 0 14",
        )
    }

    /** Struck-through Bluetooth rune: "Bluetooth is off". */
    val BluetoothOff: ImageVector by lazy {
        stroked("BluetoothOff", "M7 7l10 10-5 4V12M12 8V3l5 4-2.5 2M3 3l18 18")
    }

    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun stroked(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        paths.forEach { data ->
            builder.addPath(
                pathData = addPathNodes(data),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
