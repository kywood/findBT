package com.gamenuri.btscanner24.demo

import com.gamenuri.btscanner24.core.ble.proximity.ProximityBand
import com.gamenuri.btscanner24.core.ble.proximity.ProximityTrend
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.DeviceCategory
import com.gamenuri.btscanner24.core.model.DeviceLabel
import com.gamenuri.btscanner24.core.model.NameSource
import com.gamenuri.btscanner24.core.model.PairedDevice
import com.gamenuri.btscanner24.feature.finder.AdvancedInfo
import com.gamenuri.btscanner24.feature.finder.ArrowConfidence
import com.gamenuri.btscanner24.feature.finder.FinderStage
import com.gamenuri.btscanner24.feature.finder.FinderUiState
import kotlin.math.ln
import kotlin.math.log10

/** Fixed demo content. Names are generic on purpose: no brands, no real people's devices. */
internal object DemoData {

    private class Fake(val name: String?, val category: DeviceCategory, val meters: Double)

    private val fakes = listOf(
        Fake("My Earbuds", DeviceCategory.EARBUDS, 0.7),
        Fake("Running Watch", DeviceCategory.WATCH, 2.2),
        Fake("Keyboard", DeviceCategory.KEYBOARD, 1.4),
        Fake("Bike Sensor", DeviceCategory.FITNESS_BAND, 3.1),
        Fake("Keys Tag", DeviceCategory.TRACKER, 4.0),
        Fake(null, DeviceCategory.TRACKER, 6.5),
        Fake("Headphones", DeviceCategory.HEADPHONES, 7.5),
        Fake("Living Room Speaker", DeviceCategory.SPEAKER, 12.0),
    )

    private fun address(index: Int) = "DE:00:00:00:00:%02X".format(index + 1)

    fun devices(now: Long): List<BleDevice> = fakes.mapIndexed { index, fake ->
        // Same path-loss model as the app (P1m -59 dBm, n 2.2), so bars and numbers agree.
        val rssi = -59.0 - 22.0 * log10(fake.meters)
        BleDevice(
            address = address(index),
            name = fake.name,
            rssi = rssi.toInt(),
            smoothedRssi = rssi,
            distanceMeters = fake.meters,
            firstSeen = now - index * 1_000L,
            // Far in the future so rows never turn "gone" while screenshots are taken.
            lastSeen = now + DAY_MS,
            category = fake.category,
        )
    }

    val paired: List<PairedDevice> = listOf(
        PairedDevice(address(0), "My Earbuds", DeviceCategory.EARBUDS),
        PairedDevice(address(1), "Running Watch", DeviceCategory.WATCH),
        PairedDevice("DE:00:00:00:00:F1", "Car Audio", DeviceCategory.SPEAKER),
    )

    /** Every find state the design review asks for (#52): spin, walk, locked, far, close, very close, here, lost. */
    fun findState(name: String): FinderUiState {
        val base = FinderUiState(
            deviceLabel = DeviceLabel.Named("My Earbuds", NameSource.ADVERTISED, suffix = "0001"),
            advanced = AdvancedInfo(address = address(0)),
        )
        return when (name) {
            "waiting" -> base
            "spin" -> base.copy(stage = FinderStage.SPIN, sweepProgress = 0.35f).distance(8.0, ProximityBand.FAR)
            "walk", "far" -> base.copy(
                stage = FinderStage.WALK,
                arrowDegrees = 28f,
                confidence = ArrowConfidence.MEDIUM,
                trend = ProximityTrend.STEADY,
            ).distance(8.0, ProximityBand.FAR)
            "close" -> base.copy(
                stage = FinderStage.WALK,
                arrowDegrees = 12f,
                confidence = ArrowConfidence.HIGH,
                trend = ProximityTrend.CLOSER,
            ).distance(3.0, ProximityBand.NEAR)
            "locked" -> base.copy(
                stage = FinderStage.LOCKED,
                arrowDegrees = 0f,
                confidence = ArrowConfidence.HIGH,
                trend = ProximityTrend.CLOSER,
            ).distance(2.4, ProximityBand.NEAR)
            "turn_around" -> base.copy(
                stage = FinderStage.TURN_AROUND,
                arrowDegrees = 170f,
                confidence = ArrowConfidence.MEDIUM,
                trend = ProximityTrend.FARTHER,
            ).distance(6.0, ProximityBand.FAR)
            "very_close" -> base.copy(
                stage = FinderStage.WALK,
                arrowDegrees = 8f,
                confidence = ArrowConfidence.MEDIUM,
                trend = ProximityTrend.CLOSER,
            ).distance(1.2, ProximityBand.VERY_CLOSE)
            "here" -> base.copy(stage = FinderStage.RIGHT_HERE).distance(0.3, ProximityBand.RIGHT_HERE)
            "lost" -> base.copy(stage = FinderStage.SIGNAL_LOST, secondsSinceSeen = 12).distance(6.0, ProximityBand.FAR)
            else -> base
        }
    }

    private fun FinderUiState.distance(meters: Double, band: ProximityBand): FinderUiState =
        copy(distanceMeters = meters, band = band, heat = heatFor(meters))

    /** Same curve as ProximityEstimator: 0.3 m is hot, 15 m is cold. */
    private fun heatFor(meters: Double): Float =
        (1 - ln(meters / 0.3) / ln(15.0 / 0.3)).coerceIn(0.0, 1.0).toFloat()

    private const val DAY_MS = 86_400_000L
}
