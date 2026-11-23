package com.gamenuri.btscanner24.core.sensors

/** Phone heading and walking state at one moment. */
data class MotionSample(
    val timeMs: Long,
    /** 0..360 clockwise. With [MotionSensor.Source.GAME] this is relative to an arbitrary start, not north. */
    val headingDegrees: Double,
    val walking: Boolean,
)
