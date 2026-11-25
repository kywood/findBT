package com.gamenuri.btscanner24.feature.finder

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import com.gamenuri.btscanner24.core.sensors.PoseSensor
import kotlin.math.tan

/**
 * True while water is moving over the find screen (#106): continuous animations below hold still
 * until it has settled, so the transition gets the whole frame budget.
 */
internal val LocalFindMotionPaused = compositionLocalOf { false }

/** The system's "remove animations" setting. */
internal fun Context.animationsOff(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * tan(roll) of the phone, low-pass filtered, for the water surface (#107): the water stays level in
 * the room when the phone leans sideways. Reads the sensor only while the screen is visible.
 */
@Composable
internal fun rememberWaterSlope(): () -> Float {
    val context = LocalContext.current
    val slope = remember { FloatArray(1) }
    LifecycleStartEffect(context) {
        val feed = PoseSensor(context.applicationContext).start { pose ->
            val roll = pose.rollDegrees.coerceIn(-MAX_ROLL_DEGREES, MAX_ROLL_DEGREES)
            slope[0] += (tan(Math.toRadians(roll)).toFloat() - slope[0]) * SLOPE_SMOOTHING
        }
        onStopOrDispose { feed.close() }
    }
    return remember { { slope[0] } }
}

private const val MAX_ROLL_DEGREES = 24.0
private const val SLOPE_SMOOTHING = 0.12f
