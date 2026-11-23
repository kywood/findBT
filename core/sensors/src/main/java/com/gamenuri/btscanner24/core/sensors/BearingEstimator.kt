package com.gamenuri.btscanner24.core.sensors

import kotlin.math.abs

/**
 * Estimates the compass bearing toward a BLE device from phone heading + signal strength.
 *
 * BLE has no angle information, so this uses two physical effects:
 * 1. **Sweep** (spin in place): the user's body blocks the signal, so RSSI is highest when the
 *    phone faces the device. Phase 1 records RSSI per 30° heading sector over a full turn.
 * 2. **Walk**: walking toward the device makes RSSI rise. Phase 2 confirms (locks) the bearing
 *    when RSSI rises for [Config.lockRiseMs] while walking along it, and falls back to phase 1
 *    when RSSI keeps falling for [Config.lostFallMs].
 *
 * Output is a world-frame bearing plus a confidence tier. The arrow on screen is
 * `bearing - currentHeading`. Pure Kotlin, single-threaded, driven by timestamps so it is testable.
 */
class BearingEstimator(private val config: Config = Config()) {

    data class Config(
        val sectorDegrees: Int = 30,
        /** Sectors that must hold samples before the sweep counts as a full turn (10 of 12 = 300°). */
        val sweepSectorsRequired: Int = 10,
        /** Strongest sector must beat the weakest (body-blocked) sector by this much to point anywhere. */
        val minContrastDb: Double = 4.0,
        val highContrastDb: Double = 8.0,
        val lockRiseMs: Long = 3_000,
        val lockRiseDb: Double = 3.0,
        val lostFallMs: Long = 10_000,
        val lostFallDb: Double = 3.0,
        /** Walking heading must be within this of the estimate for a rise to confirm it. */
        val walkConeDegrees: Double = 45.0,
    )

    enum class Phase {
        /** Spin in place: collecting RSSI per heading. */
        SWEEP,

        /** Sweep done: ask the user to walk in the arrow direction. */
        WALK,

        /** RSSI rose while walking along the estimate. */
        LOCKED,
    }

    enum class Confidence {
        /** Arrow drawn with a narrow sector (about ±25°). */
        HIGH,

        /** Arrow drawn with a wide sector (about ±45°). */
        MEDIUM,

        /** Arrow hidden; distance and trend only. */
        NONE,
    }

    data class Estimate(
        val phase: Phase,
        /** World-frame bearing to the device, or null while unknown. */
        val bearingDegrees: Double?,
        val confidence: Confidence,
        /** 0..1 share of the turn completed, for the sweep ring. */
        val sweepProgress: Float,
    )

    private val sectors = DoubleArray(360 / config.sectorDegrees) { Double.NaN }
    private var phase = Phase.SWEEP
    private var bearing: Double? = null
    private var confidence = Confidence.NONE

    private var walkStart: Pair<Long, Double>? = null
    private var fallStart: Pair<Long, Double>? = null

    fun reset() {
        sectors.fill(Double.NaN)
        phase = Phase.SWEEP
        bearing = null
        confidence = Confidence.NONE
        walkStart = null
        fallStart = null
    }

    /**
     * @param headingDegrees current phone heading (rotation vector azimuth).
     * @param smoothedRssi current smoothed RSSI of the target.
     * @param walking whether the user is walking (accelerometer), as opposed to spinning in place.
     */
    fun update(timeMs: Long, headingDegrees: Double, smoothedRssi: Double, walking: Boolean): Estimate {
        when (phase) {
            Phase.SWEEP -> sweep(headingDegrees, smoothedRssi)
            Phase.WALK, Phase.LOCKED -> walk(timeMs, headingDegrees, smoothedRssi, walking)
        }
        return Estimate(phase, bearing, confidence, sweepProgress())
    }

    private fun sweep(heading: Double, rssi: Double) {
        val index = (Angles.normalize(heading) / config.sectorDegrees).toInt() % sectors.size
        // Keep the strongest reading per sector: the moment the phone pointed most directly at the device.
        sectors[index] = if (sectors[index].isNaN()) rssi else maxOf(sectors[index], rssi)

        if (filledSectors() < config.sweepSectorsRequired) return

        val values = sectors.filter { !it.isNaN() }.sorted()
        val best = values.last()
        val contrast = best - values.first()
        if (contrast < config.minContrastDb) {
            // No direction stands out (device very close, or reflections). Keep sweeping data fresh.
            confidence = Confidence.NONE
            bearing = null
            sectors.fill(Double.NaN)
            return
        }
        // Weighted mean of sectors within 3 dB of the best handles a peak split across sectors.
        val floor = best - PEAK_WINDOW_DB
        val peak = sectors.indices
            .filter { !sectors[it].isNaN() && sectors[it] >= floor }
            .map { centerOf(it) to (sectors[it] - floor + 0.5) }
        bearing = Angles.circularMean(peak)
            ?: centerOf(sectors.indices.maxBy { sectors[it].takeUnless(Double::isNaN) ?: -999.0 })
        confidence = if (contrast >= config.highContrastDb) Confidence.HIGH else Confidence.MEDIUM
        phase = Phase.WALK
        walkStart = null
        fallStart = null
    }

    private fun walk(time: Long, heading: Double, rssi: Double, walking: Boolean) {
        val estimate = bearing ?: return backToSweep()

        // Lost: RSSI kept falling for a long time, whichever way the user walks.
        val fall = fallStart
        if (fall == null || rssi > fall.second) {
            fallStart = time to rssi
        } else if (fall.second - rssi >= config.lostFallDb && time - fall.first >= config.lostFallMs) {
            return backToSweep()
        }

        if (!walking) {
            walkStart = null
            return
        }
        val alongEstimate = abs(Angles.delta(heading, estimate)) <= config.walkConeDegrees
        val start = walkStart
        if (start == null || !alongEstimate) {
            walkStart = if (alongEstimate) time to rssi else null
            return
        }
        if (time - start.first >= config.lockRiseMs) {
            if (rssi - start.second >= config.lockRiseDb) {
                // Walking this way works: lock, and pull the estimate toward the walking heading.
                phase = Phase.LOCKED
                confidence = Confidence.HIGH
                bearing = Angles.circularMean(listOf(estimate to 0.6, heading to 0.4))
            } else if (start.second - rssi >= config.lockRiseDb) {
                // Getting weaker along the estimate: the device is likely behind ("turn around").
                bearing = Angles.normalize(estimate + 180.0)
                confidence = Confidence.MEDIUM
                phase = Phase.WALK
            }
            walkStart = time to rssi
        }
    }

    private fun backToSweep() {
        reset()
    }

    private fun filledSectors() = sectors.count { !it.isNaN() }

    private fun sweepProgress(): Float =
        if (phase == Phase.SWEEP) filledSectors().toFloat() / config.sweepSectorsRequired else 1f

    private fun centerOf(index: Int): Double = index * config.sectorDegrees + config.sectorDegrees / 2.0

    private companion object {
        const val PEAK_WINDOW_DB = 3.0
    }
}
