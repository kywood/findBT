package com.gamenuri.btscanner24.feature.finder

import com.gamenuri.btscanner24.core.ble.proximity.ProximityBand
import com.gamenuri.btscanner24.core.ble.proximity.ProximityTrend

/** What the find screen is asking the user to do. Mirrors the states in design find-screen v3.3. */
enum class FinderStage {
    /** No advertisement from the target yet. */
    WAITING,

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
    val deviceName: String = "",
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
    val vibrationOn: Boolean = true,
    val advanced: AdvancedInfo = AdvancedInfo(),
)

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
