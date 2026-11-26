package com.gamenuri.btscanner24.core.ble.proximity

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/** Coarse distance shown with the approximate number. Edges come from the plan (0.5 / 1.5 / 5 m). */
enum class ProximityBand { RIGHT_HERE, VERY_CLOSE, NEAR, FAR }

enum class ProximityTrend { CLOSER, FARTHER, STEADY }

data class ProximityReading(
    /** Kalman-smoothed RSSI in dBm. */
    val smoothedRssi: Double,
    /** Rough distance in metres. Always shown as "approximate": walls, bodies and pockets move it a lot. */
    val distanceMeters: Double,
    val band: ProximityBand,
    /** 0.0 = cold (far) to 1.0 = hot (right here), for the hot-cold gauge. */
    val heat: Float,
    val trend: ProximityTrend,
    /** Last raw RSSI, for the Advanced section. */
    val rawRssi: Int,
)

data class ProximityConfig(
    /** Kalman process noise: how fast the true signal is allowed to change between samples. */
    val processNoise: Double = 0.6,
    /** Kalman measurement noise: typical RSSI variance indoors (dB²). */
    val measurementNoise: Double = 9.0,
    /** Heavier smoothing beyond [nearMeters], where small RSSI changes swing the distance by metres. */
    val farMeasurementNoise: Double = 25.0,
    val medianWindow: Int = 5,
    val outlierDb: Int = 15,
    /** RSSI at 1 m when the device does not advertise TX power. */
    val defaultMeasuredPower: Int = -59,
    /** Advertised TX power outside this range (dBm) is ignored as implausible. */
    val trustedTxPowerRange: IntRange = -10..20,
    /** Path-loss exponent: 2 in free space, ~2-3 indoors. */
    val pathLossExponent: Double = 2.2,
    val rightHereMeters: Double = 0.5,
    val veryCloseMeters: Double = 1.5,
    val nearMeters: Double = 5.0,
    val hysteresisMeters: Double = 0.3,
    val trendWindowMs: Long = 3_000,
    val trendDb: Double = 3.0,
    val hotMeters: Double = 0.3,
    val coldMeters: Double = 15.0,
)

/**
 * Turns noisy RSSI samples into a stable proximity reading for the find screen.
 *
 * Per sample: drop outliers against a short median window, smooth with a 1-D Kalman filter,
 * convert to distance with the log-distance path-loss model (the same model family the AltBeacon
 * library uses), then map to a band with hysteresis so it does not flicker at the edges.
 * Not thread safe: feed it from one coroutine.
 */
class ProximityEstimator(private val config: ProximityConfig = ProximityConfig()) {

    private val recentRaw = ArrayDeque<Int>()
    private val history = ArrayDeque<Pair<Long, Double>>()
    private var estimate: Double? = null
    private var errorCovariance = 1.0
    private var band: ProximityBand? = null

    fun reset() {
        recentRaw.clear()
        history.clear()
        estimate = null
        errorCovariance = 1.0
        band = null
    }

    fun add(rssi: Int, timeMs: Long, txPower: Int? = null): ProximityReading {
        val median = median()
        val accepted = median == null || recentRaw.size < config.medianWindow || abs(rssi - median) <= config.outlierDb
        recentRaw.addLast(rssi)
        if (recentRaw.size > config.medianWindow) recentRaw.removeFirst()

        val smoothed = if (accepted) kalman(rssi.toDouble(), txPower) else estimate ?: rssi.toDouble()

        history.addLast(timeMs to smoothed)
        while (history.isNotEmpty() && timeMs - history.first().first > 2 * config.trendWindowMs) history.removeFirst()

        val distance = distanceFor(smoothed, txPower)
        val newBand = bandFor(distance, band)
        band = newBand
        return ProximityReading(
            smoothedRssi = smoothed,
            distanceMeters = distance,
            band = newBand,
            heat = heatFor(distance),
            trend = trend(timeMs),
            rawRssi = rssi,
        )
    }

    private fun kalman(measurement: Double, txPower: Int?): Double {
        val previous = estimate ?: return measurement.also { estimate = it }
        val predictedCovariance = errorCovariance + config.processNoise
        val far = distanceFor(previous, txPower) > config.nearMeters
        val noise = if (far) config.farMeasurementNoise else config.measurementNoise
        val gain = predictedCovariance / (predictedCovariance + noise)
        val next = previous + gain * (measurement - previous)
        errorCovariance = (1 - gain) * predictedCovariance
        estimate = next
        return next
    }

    private fun median(): Double? {
        if (recentRaw.isEmpty()) return null
        val sorted = recentRaw.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid].toDouble() else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    /** Log-distance path loss: d = 10 ^ ((P1m - RSSI) / (10 n)). Advertised TX power is at 0 m, ~41 dB above 1 m. */
    fun distanceFor(rssi: Double, txPower: Int?): Double {
        // Some devices put a very low or bogus value in the TX Power field (seen on an LG device
        // advertising about -26 dBm, which turned -60 dBm into "right here, 0.5 m"). Trust it only in
        // the range real radios use; otherwise fall back to the typical 1 m reading.
        val trusted = txPower?.takeIf { it in config.trustedTxPowerRange }
        val measuredPower = trusted?.let { it - TX_TO_ONE_METRE_DB } ?: config.defaultMeasuredPower
        val meters = 10.0.pow((measuredPower - rssi) / (10 * config.pathLossExponent))
        return meters.coerceIn(0.1, 50.0)
    }

    internal fun bandFor(meters: Double, current: ProximityBand?): ProximityBand {
        val edges = listOf(config.rightHereMeters, config.veryCloseMeters, config.nearMeters)
        fun plain(d: Double) = when {
            d < edges[0] -> ProximityBand.RIGHT_HERE
            d < edges[1] -> ProximityBand.VERY_CLOSE
            d < edges[2] -> ProximityBand.NEAR
            else -> ProximityBand.FAR
        }
        if (current == null) return plain(meters)
        val candidate = plain(meters)
        if (candidate == current) return current
        // Only leave the current band once the distance is past its edge by the hysteresis margin.
        val h = config.hysteresisMeters
        val stays = when (current) {
            ProximityBand.RIGHT_HERE -> meters < edges[0] + h
            ProximityBand.VERY_CLOSE -> meters >= edges[0] - h && meters < edges[1] + h
            ProximityBand.NEAR -> meters >= edges[1] - h && meters < edges[2] + h
            ProximityBand.FAR -> meters >= edges[2] - h
        }
        return if (stays) current else candidate
    }

    private fun heatFor(meters: Double): Float {
        val span = ln(config.coldMeters / config.hotMeters)
        return (1 - ln(meters / config.hotMeters) / span).coerceIn(0.0, 1.0).toFloat()
    }

    /** Compares the average of the last [ProximityConfig.trendWindowMs] with the window before it. */
    private fun trend(now: Long): ProximityTrend {
        val window = config.trendWindowMs
        val recent = history.filter { now - it.first < window }.map { it.second }
        val before = history.filter { now - it.first in window until 2 * window }.map { it.second }
        if (recent.isEmpty() || before.isEmpty()) return ProximityTrend.STEADY
        val delta = recent.average() - before.average()
        return when {
            delta >= config.trendDb -> ProximityTrend.CLOSER
            delta <= -config.trendDb -> ProximityTrend.FARTHER
            else -> ProximityTrend.STEADY
        }
    }

    private companion object {
        /** TX power is radiated power at the antenna; about 41 dB is lost by 1 m (iBeacon convention). */
        const val TX_TO_ONE_METRE_DB = 41
    }
}
