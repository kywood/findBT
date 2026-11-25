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
import com.gamenuri.btscanner24.feature.finder.SoundState
import kotlin.math.ln
import kotlin.math.log10

/** Fixed demo content. Names are generic on purpose: no brands, no real people's devices. */
internal object DemoData {

    /** Makers are made up as well, so the filter's maker section has something to show (#100). */
    private class Fake(val name: String?, val category: DeviceCategory, val meters: Double, val maker: String? = null)

    private val fakes = listOf(
        Fake("My Earbuds", DeviceCategory.EARBUDS, 0.7, "Lumen Audio"),
        Fake("Running Watch", DeviceCategory.WATCH, 2.2, "Orbit Wear"),
        Fake("Office Keyboard", DeviceCategory.KEYBOARD, 1.4, "Keyline"),
        Fake("Bike Sensor", DeviceCategory.FITNESS_BAND, 3.1, "Orbit Wear"),
        Fake("Keys Tag", DeviceCategory.TRACKER, 4.0, "Tagly"),
        Fake(null, DeviceCategory.TRACKER, 6.5, "Tagly"),
        Fake("Studio Headphones", DeviceCategory.HEADPHONES, 7.5, "Lumen Audio"),
        Fake("Living Room Speaker", DeviceCategory.SPEAKER, 12.0, "Lumen Audio"),
        // "Other devices" (#82): hidden by default, behind "N other devices hidden · Show".
        Fake(null, DeviceCategory.UNKNOWN, 4.2),
        Fake("Living Room TV", DeviceCategory.TV, 7.0, "Vista"),
        Fake("Bathroom Scale", DeviceCategory.HEALTH, 11.0),
        Fake(null, DeviceCategory.SENSOR, 13.0),
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
            manufacturer = fake.maker,
        )
    }

    val paired: List<PairedDevice> = listOf(
        PairedDevice(address(0), "My Earbuds", DeviceCategory.EARBUDS),
        PairedDevice(address(1), "Running Watch", DeviceCategory.WATCH),
        // Car and computer go into the collapsed "Other paired devices" group (design home-v3).
        PairedDevice("DE:00:00:00:00:F1", "My Car", DeviceCategory.CAR),
        PairedDevice("DE:00:00:00:00:F2", "Work Laptop", DeviceCategory.COMPUTER),
        PairedDevice("DE:00:00:00:00:F3", "Gym Band", DeviceCategory.FITNESS_BAND),
    )

    /** Every find state the design review asks for (#52): spin, walk, locked, far, close, very close, here, lost. */
    fun findState(name: String): FinderUiState {
        val base = FinderUiState(
            deviceLabel = DeviceLabel.Named("My Earbuds", NameSource.ADVERTISED, suffix = "0001"),
            category = DeviceCategory.EARBUDS,
            advanced = AdvancedInfo(address = address(0)),
            // The demo earbuds can ring, so every in-range state shows the same bottom bar (#28, #60).
            sound = SoundState.READY,
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
            "close", "sound" -> base.copy(
                stage = FinderStage.WALK,
                arrowDegrees = 12f,
                confidence = ArrowConfidence.HIGH,
                trend = ProximityTrend.CLOSER,
                sound = if (name == "sound") SoundState.PLAYING else base.sound,
            ).distance(3.0, ProximityBand.NEAR)
            // World-anchored for the 3D scene (#76): the arrow keeps its place in the room as the phone turns.
            "ar" -> base.copy(
                stage = FinderStage.WALK,
                arrowDegrees = 0f,
                bearingDegrees = 0f,
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
                showConfirmHint = true,
            ).distance(1.2, ProximityBand.VERY_CLOSE)
            "here" -> base.copy(stage = FinderStage.RIGHT_HERE).distance(0.3, ProximityBand.RIGHT_HERE)
            "not_found" -> base.copy(stage = FinderStage.NOT_FOUND)
            "lost" -> base.copy(stage = FinderStage.SIGNAL_LOST, secondsSinceSeen = 12).distance(6.0, ProximityBand.FAR)
            else -> base
        }
    }

    private fun FinderUiState.distance(meters: Double, band: ProximityBand): FinderUiState =
        copy(distanceMeters = meters, band = band, heat = heatFor(meters))

    /** Same curve as ProximityEstimator: 0.3 m is hot, 15 m is cold. */
    private fun heatFor(meters: Double): Float = (1 - ln(meters / 0.3) / ln(15.0 / 0.3)).coerceIn(0.0, 1.0).toFloat()

    private const val DAY_MS = 86_400_000L
}
