package com.gamenuri.btscanner24.core.sensors

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Degree helpers. Bearings are 0..360, clockwise from magnetic north. */
object Angles {
    fun normalize(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0

    /** Signed smallest difference a - b in -180..180. */
    fun delta(a: Double, b: Double): Double {
        val d = normalize(a - b)
        return if (d > 180.0) d - 360.0 else d
    }

    /** Weighted circular mean; returns null when the weights cancel out (no clear direction). */
    fun circularMean(samples: List<Pair<Double, Double>>): Double? {
        var x = 0.0
        var y = 0.0
        var total = 0.0
        for ((deg, weight) in samples) {
            val r = Math.toRadians(deg)
            x += cos(r) * weight
            y += sin(r) * weight
            total += weight
        }
        if (total <= 0.0 || x * x + y * y < 1e-9) return null
        return normalize(Math.toDegrees(atan2(y, x)))
    }
}
