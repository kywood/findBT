package com.gamenuri.btscanner24.core.sensors

import kotlin.math.sqrt

/**
 * Tells walking from standing or spinning in place using acceleration magnitude variance.
 *
 * Steps make the gravity-free acceleration swing by roughly 1–3 m/s² at about 2 Hz; turning in
 * place or holding still stays well under that. Using variance avoids the step detector sensor,
 * which needs the ACTIVITY_RECOGNITION runtime permission.
 */
class WalkingDetector(
    private val windowMs: Long = 1_500,
    private val startStdDev: Double = 0.9,
    private val stopStdDev: Double = 0.5,
) {
    private val samples = ArrayDeque<Pair<Long, Double>>()

    var walking: Boolean = false
        private set

    /** @param magnitude length of the linear (gravity-free) acceleration vector in m/s². */
    fun add(timeMs: Long, magnitude: Double): Boolean {
        samples.addLast(timeMs to magnitude)
        while (samples.isNotEmpty() && timeMs - samples.first().first > windowMs) samples.removeFirst()
        if (samples.size < MIN_SAMPLES) return walking

        val mean = samples.sumOf { it.second } / samples.size
        val stdDev = sqrt(samples.sumOf { (it.second - mean) * (it.second - mean) } / samples.size)
        // Two thresholds so the state does not flicker between steps.
        walking = if (walking) stdDev >= stopStdDev else stdDev >= startStdDev
        return walking
    }

    fun reset() {
        samples.clear()
        walking = false
    }

    private companion object {
        const val MIN_SAMPLES = 10
    }
}
