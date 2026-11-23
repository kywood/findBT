package com.gamenuri.btscanner24.feature.finder

import com.gamenuri.btscanner24.core.ble.TargetTracker
import com.gamenuri.btscanner24.core.ble.proximity.ProximityBand
import com.gamenuri.btscanner24.core.ble.proximity.ProximityEstimator
import com.gamenuri.btscanner24.core.ble.proximity.ProximityReading
import com.gamenuri.btscanner24.core.ble.proximity.ProximityTrend
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.DeviceLabel
import com.gamenuri.btscanner24.core.model.DeviceNaming
import com.gamenuri.btscanner24.core.sensors.Angles
import com.gamenuri.btscanner24.core.sensors.BearingEstimator
import com.gamenuri.btscanner24.core.sensors.BearingEstimator.Confidence
import com.gamenuri.btscanner24.core.sensors.BearingEstimator.Phase
import com.gamenuri.btscanner24.core.sensors.MotionSample
import kotlin.math.abs

/**
 * Pure find-mode logic: follows the target through MAC rotations, smooths its signal into a
 * distance, and feeds heading + signal into the bearing estimator. The ViewModel only wires flows.
 *
 * Two clocks: sightings use wall time (same as [BleDevice.lastSeen]); motion uses the sensor clock.
 */
internal class FinderEngine(
    target: BleDevice,
    private val startMs: Long,
    /** The phone's name for a paired device; wins over anything advertised (#46). */
    private val pairedAlias: String? = null,
    private val hasCompass: Boolean,
    private val proximity: ProximityEstimator = ProximityEstimator(),
    private val bearing: BearingEstimator = BearingEstimator(),
) {
    private val tracker = TargetTracker(target)
    private var label: DeviceLabel = DeviceNaming.label(target, pairedAlias)
    private var reading: ProximityReading? = null
    private var lastSightingMs: Long? = null
    private var heading: Double? = null
    private var estimate: BearingEstimator.Estimate? = null
    private var hadDirection = false
    private var rightHereSinceMs: Long? = null

    /** @return true when the sighting belonged to the target. */
    fun onSighting(device: BleDevice, nowMs: Long): Boolean {
        if (!tracker.isTarget(device, nowMs)) return false
        // Keep a real name once seen; a later packet without one must not turn it back into a generic label.
        val next = DeviceNaming.label(device, pairedAlias)
        if (next is DeviceLabel.Named || label is DeviceLabel.Generic) label = next
        val newReading = proximity.add(device.rssi, nowMs, device.txPower)
        reading = newReading
        lastSightingMs = nowMs
        rightHereSinceMs = if (newReading.band == ProximityBand.RIGHT_HERE) rightHereSinceMs ?: nowMs else null
        return true
    }

    /** @return feedback to play, if the direction state changed. */
    fun onMotion(sample: MotionSample, nowMs: Long): FinderEvent? {
        heading = sample.headingDegrees
        val current = reading ?: return null
        if (signalLost(nowMs)) return null
        val previous = estimate
        val next = bearing.update(sample.timeMs, sample.headingDegrees, current.smoothedRssi, sample.walking)
        estimate = next
        if (next.phase != Phase.SWEEP) hadDirection = true
        return when {
            previous == null -> null
            previous.phase == Phase.SWEEP && next.phase == Phase.WALK -> FinderEvent.DIRECTION_FOUND
            previous.phase != Phase.LOCKED && next.phase == Phase.LOCKED -> FinderEvent.LOCKED
            previous.phase != Phase.SWEEP && next.phase == Phase.SWEEP -> FinderEvent.DIRECTION_CHANGED
            flipped(previous.bearingDegrees, next.bearingDegrees) -> FinderEvent.DIRECTION_CHANGED
            else -> null
        }
    }

    private fun flipped(before: Double?, after: Double?): Boolean =
        before != null && after != null && abs(Angles.delta(before, after)) > FLIP_DEGREES

    /** The "Turn once again" button. */
    fun restartSweep() {
        bearing.reset()
        estimate = null
        hadDirection = false
    }

    fun state(nowMs: Long, vibrationOn: Boolean): FinderUiState {
        val current = reading
        val est = estimate
        val relative = est?.bearingDegrees?.let { b -> heading?.let { Angles.delta(b, it) } }
        val stage = stageFor(nowMs, current, est, relative)
        val showArrow = stage == FinderStage.WALK || stage == FinderStage.LOCKED || stage == FinderStage.TURN_AROUND
        return FinderUiState(
            deviceLabel = label,
            stage = stage,
            arrowDegrees = relative?.takeIf { showArrow }?.toFloat(),
            confidence = if (showArrow) confidenceFor(est, current) else ArrowConfidence.NONE,
            sweepProgress = est?.sweepProgress ?: 0f,
            distanceMeters = current?.distanceMeters,
            band = current?.band,
            heat = current?.heat ?: 0f,
            trend = current?.trend ?: ProximityTrend.STEADY,
            secondsSinceSeen = lastSightingMs?.let { ((nowMs - it) / 1000).toInt() } ?: 0,
            vibrationOn = vibrationOn,
            advanced = AdvancedInfo(
                address = tracker.address,
                rawRssi = current?.rawRssi,
                smoothedRssi = current?.smoothedRssi,
                headingDegrees = heading,
                bearingDegrees = est?.bearingDegrees,
                addressSwitches = tracker.addressSwitches,
                elapsedSeconds = ((nowMs - startMs) / 1000).toInt(),
            ),
        )
    }

    private fun signalLost(nowMs: Long): Boolean = lastSightingMs?.let { nowMs - it > SIGNAL_LOST_MS } ?: false

    private fun stageFor(
        nowMs: Long,
        current: ProximityReading?,
        est: BearingEstimator.Estimate?,
        relative: Double?,
    ): FinderStage = when {
        current == null -> FinderStage.WAITING
        signalLost(nowMs) -> FinderStage.SIGNAL_LOST
        rightHereSinceMs?.let { nowMs - it >= RIGHT_HERE_HOLD_MS } == true -> FinderStage.RIGHT_HERE
        !hasCompass -> FinderStage.NO_COMPASS
        est == null || est.phase == Phase.SWEEP -> if (hadDirection) FinderStage.SPIN_AGAIN else FinderStage.SPIN
        relative != null && abs(relative) >= TURN_AROUND_DEGREES -> FinderStage.TURN_AROUND
        est.phase == Phase.LOCKED -> FinderStage.LOCKED
        else -> FinderStage.WALK
    }

    private fun confidenceFor(est: BearingEstimator.Estimate?, current: ProximityReading?): ArrowConfidence {
        val base = when (est?.confidence) {
            Confidence.HIGH -> ArrowConfidence.HIGH
            Confidence.MEDIUM -> ArrowConfidence.MEDIUM
            else -> ArrowConfidence.NONE
        }
        // Under 1.5 m the body barely blocks the signal, so direction is less reliable.
        val veryClose = current?.band == ProximityBand.VERY_CLOSE || current?.band == ProximityBand.RIGHT_HERE
        return if (veryClose && base == ArrowConfidence.HIGH) ArrowConfidence.MEDIUM else base
    }

    private companion object {
        const val SIGNAL_LOST_MS = 8_000L
        const val RIGHT_HERE_HOLD_MS = 2_000L
        const val TURN_AROUND_DEGREES = 135.0
        const val FLIP_DEGREES = 90.0
    }
}
