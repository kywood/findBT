package com.gamenuri.btscanner24.feature.finder

import com.gamenuri.btscanner24.core.ble.ScanFailure
import com.gamenuri.btscanner24.core.ble.proximity.ProximityBand
import com.gamenuri.btscanner24.core.ble.proximity.ProximityTrend
import com.gamenuri.btscanner24.core.model.DeviceCategory
import com.gamenuri.btscanner24.core.model.DeviceLabel

/** What the find screen is asking the user to do. Mirrors the states in design find-screen v3.3. */
enum class FinderStage {
    /** No advertisement from the target yet. */
    WAITING,

    /** Still none after a while: say so and offer to try again or go back, instead of waiting forever (#117). */
    NOT_FOUND,

    /** Turn a full circle; the needle spins and the ring shows progress. */
    SPIN,

    /** Direction found during the spin, then lost: spin again. */
    SPIN_AGAIN,

    /** Direction estimated: walk toward the arrow. */
    WALK,

    /** Signal rose while walking along the arrow. */
    LOCKED,

    /** The device is behind the user (arrow more than 135° off). */
    TURN_AROUND,

    /** Under 0.5 m for 2 s: dot and rings instead of an arrow. */
    RIGHT_HERE,

    /** No advertisement for a while. */
    SIGNAL_LOST,

    /** Phone has no rotation sensor: distance and trend only. */
    NO_COMPASS,
}

enum class ArrowConfidence { HIGH, MEDIUM, NONE }

data class FinderUiState(
    val deviceLabel: DeviceLabel = DeviceLabel.Generic(maker = null, category = DeviceCategory.UNKNOWN, suffix = ""),
    val stage: FinderStage = FinderStage.WAITING,
    /** Arrow angle relative to the top of the phone, clockwise. Null hides the arrow. */
    val arrowDegrees: Float? = null,
    val confidence: ArrowConfidence = ArrowConfidence.NONE,
    val sweepProgress: Float = 0f,
    val distanceMeters: Double? = null,
    val band: ProximityBand? = null,
    val heat: Float = 0f,
    val trend: ProximityTrend = ProximityTrend.STEADY,
    val secondsSinceSeen: Int = 0,
    /** Very close for a while without reaching "right here": suggest confirming by touch (design review #55). */
    val showConfirmHint: Boolean = false,
    val vibrationOn: Boolean = true,
    /** "Play sound" button (#28); hidden while [SoundState.UNAVAILABLE]. */
    val sound: SoundState = SoundState.UNAVAILABLE,
    val advanced: AdvancedInfo = AdvancedInfo(),
    /**
     * The scan itself cannot run (Bluetooth or Location off, permission missing): the screen says that
     * instead of "no signal", in the [FinderStage.NOT_FOUND] layout with its retry.
     */
    val scanFailure: ScanFailure? = null,
)

/** Immediate Alert (0x1802) "Play sound" state (#28). */
enum class SoundState {
    /** Not checked yet, not supported, or no BLUETOOTH_CONNECT permission: no button. */
    UNAVAILABLE,

    /** The device can ring. */
    READY,

    /** Connecting and sending "High Alert". */
    CONNECTING,

    /** Ringing; the button stops it. */
    PLAYING,

    /** The last attempt failed (out of range, busy); the button retries. */
    FAILED,

    /** A tap showed the device has no Immediate Alert service: a short note instead of the button. */
    NOT_SUPPORTED,
}

/** Raw numbers for the Advanced section and for the prototype measurements. */
data class AdvancedInfo(
    val address: String = "",
    val rawRssi: Int? = null,
    val smoothedRssi: Double? = null,
    val headingDegrees: Double? = null,
    val bearingDegrees: Double? = null,
    val addressSwitches: Int = 0,
    val elapsedSeconds: Int = 0,
)

/** One-off feedback for the screen (vibration patterns). */
enum class FinderEvent {
    /** Spin finished and an arrow appeared: one short vibration. */
    DIRECTION_FOUND,

    /** Direction locked: two short vibrations. */
    LOCKED,

    /** Arrow flipped behind the user, or direction lost. */
    DIRECTION_CHANGED,
}
