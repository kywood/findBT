package com.gamenuri.btscanner24.core.ble.proximity

import kotlin.math.abs

/** Coarse distance shown to the user. No metres: TX power is often missing, so numbers would mislead. */
enum class ProximityBand { VERY_CLOSE, NEAR, FAR }

enum class ProximityTrend { CLOSER, FARTHER, STEADY }

data class ProximityReading(
    /** Smoothed RSSI in dBm. */
    val smoothedRssi: Double,
    val band: ProximityBand,
    /** 0.0 = cold (far) to 1.0 = hot (very close), for the hot-cold meter. */
    val heat: Float,
    val trend: ProximityTrend,
    /** Last raw RSSI, for the Advanced section. */
    val rawRssi: Int,
)

/**
 * Turns noisy RSSI samples into a stable proximity reading.
 *
 * Steps per sample: drop outliers against a short median window, smooth with an
 * exponential moving average, then map to a band with hysteresis so the band does
 * not flicker at the edges. Not thread safe: feed it from one coroutine.
 */
class ProximityEstimator(
    private val alpha: Double = 0.25,
    private val medianWindow: Int = 5,
    private val outlierDb: Int = 15,
    private val veryCloseDbm: Double = -55.0,
    private val nearDbm: Double = -72.0,
    private val hysteresisDb: Double = 3.0,
    private val trendDb: Double = 2.0,
    private val trendWindow: Int = 6,
    private val coldDbm: Double = -95.0,
    private val hotDbm: Double = -40.0,
) {
    private val recentRaw = ArrayDeque<Int>()
    private val recentSmoothed = ArrayDeque<Double>()
    private var smoothed: Double? = null
    private var band: ProximityBand? = null

    fun reset() {
        recentRaw.clear()
        recentSmoothed.clear()
        smoothed = null
        band = null
    }

    fun add(rssi: Int): ProximityReading {
        val median = median()
        val accepted = median == null || abs(rssi - median) <= outlierDb || recentRaw.size < medianWindow
        recentRaw.addLast(rssi)
        if (recentRaw.size > medianWindow) recentRaw.removeFirst()

        val previous = smoothed
        val next = when {
            previous == null -> rssi.toDouble()
            accepted -> previous + alpha * (rssi - previous)
            else -> previous
        }
        smoothed = next

        recentSmoothed.addLast(next)
        if (recentSmoothed.size > trendWindow) recentSmoothed.removeFirst()

        val newBand = bandFor(next, band)
        band = newBand
        return ProximityReading(
            smoothedRssi = next,
            band = newBand,
            heat = heatFor(next),
            trend = trend(),
            rawRssi = rssi,
        )
    }

    private fun median(): Double? {
        if (recentRaw.isEmpty()) return null
        val sorted = recentRaw.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid].toDouble() else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    internal fun bandFor(rssi: Double, current: ProximityBand?): ProximityBand {
        // Moving to a closer band needs to pass the threshold by hysteresisDb, and so does moving away.
        val h = if (current == null) 0.0 else hysteresisDb
        return when (current) {
            ProximityBand.VERY_CLOSE -> when {
                rssi >= veryCloseDbm - h -> ProximityBand.VERY_CLOSE
                rssi >= nearDbm - h -> ProximityBand.NEAR
                else -> ProximityBand.FAR
            }
            ProximityBand.NEAR -> when {
                rssi >= veryCloseDbm + h -> ProximityBand.VERY_CLOSE
                rssi >= nearDbm - h -> ProximityBand.NEAR
                else -> ProximityBand.FAR
            }
            ProximityBand.FAR -> when {
                rssi >= veryCloseDbm + h -> ProximityBand.VERY_CLOSE
                rssi >= nearDbm + h -> ProximityBand.NEAR
                else -> ProximityBand.FAR
            }
            null -> when {
                rssi >= veryCloseDbm -> ProximityBand.VERY_CLOSE
                rssi >= nearDbm -> ProximityBand.NEAR
                else -> ProximityBand.FAR
            }
        }
    }

    private fun heatFor(rssi: Double): Float =
        ((rssi - coldDbm) / (hotDbm - coldDbm)).coerceIn(0.0, 1.0).toFloat()

    private fun trend(): ProximityTrend {
        if (recentSmoothed.size < trendWindow) return ProximityTrend.STEADY
        val delta = recentSmoothed.last() - recentSmoothed.first()
        return when {
            delta >= trendDb -> ProximityTrend.CLOSER
            delta <= -trendDb -> ProximityTrend.FARTHER
            else -> ProximityTrend.STEADY
        }
    }
}
