package com.gamenuri.btscanner24.demo

import com.gamenuri.btscanner24.core.ble.proximity.ProximityBand
import com.gamenuri.btscanner24.core.ble.proximity.ProximityTrend
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.DeviceCategory
import com.gamenuri.btscanner24.core.model.DeviceLabel
import com.gamenuri.btscanner24.core.model.LastSeen
import com.gamenuri.btscanner24.core.model.NameSource
import com.gamenuri.btscanner24.core.model.PairedDevice
import com.gamenuri.btscanner24.core.model.SavedDevice
import com.gamenuri.btscanner24.feature.finder.AdvancedInfo
import com.gamenuri.btscanner24.feature.finder.ArrowConfidence
import com.gamenuri.btscanner24.feature.finder.FinderStage
import com.gamenuri.btscanner24.feature.finder.FinderUiState
import com.gamenuri.btscanner24.feature.finder.SoundState
import kotlin.math.ln
import kotlin.math.log10

/** Fixed demo content. Names are generic on purpose: no brands, no real people's devices. */
internal object DemoData {

    /** Korean demo names for the KO store set (#14); every other language keeps the English ones. */
    var korean: Boolean = false

    private val KOREAN = mapOf(
        "My Earbuds" to "내 이어폰",
        "Running Watch" to "러닝 워치",
        "Office Keyboard" to "사무실 키보드",
        "Bike Sensor" to "자전거 센서",
        "Keys Tag" to "열쇠 태그",
        "Studio Headphones" to "스튜디오 헤드폰",
        "Speaker" to "스피커",
        "Living Room TV" to "거실 TV",
        "Bathroom Scale" to "욕실 체중계",
        "My Car" to "내 차",
        "Work Laptop" to "업무용 노트북",
        "Gym Band" to "운동 밴드",
        "Wallet Tag" to "지갑 태그",
    )

    /** [name] in the demo's language. */
    fun local(name: String): String = if (korean) KOREAN[name] ?: name else name

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
        Fake("Speaker", DeviceCategory.SPEAKER, 12.0, "Lumen Audio"),
        // "Other devices" (#82): hidden by default, behind "N other devices hidden · Show".
        Fake(null, DeviceCategory.UNKNOWN, 4.2),
        Fake("Living Room TV", DeviceCategory.TV, 7.0, "Vista"),
        Fake("Bathroom Scale", DeviceCategory.HEALTH, 11.0),
        Fake(null, DeviceCategory.SENSOR, 13.0),
    )

    /** A saved device that is not around: My devices shows when it was last seen (#162). */
    fun savedAway(now: Long): SavedDevice = SavedDevice(
        address = SAVED_AWAY_ADDRESS,
        name = local("Wallet Tag"),
        category = DeviceCategory.TRACKER,
        advertisedName = null,
        savedAt = now - 3 * DAY_MS,
    )

    /** Heard two hours ago, with no place: the demo never shows a real location. */
    fun savedAwaySeen(now: Long): LastSeen = LastSeen(SAVED_AWAY_ADDRESS, now - 2 * HOUR_MS)

    private const val SAVED_AWAY_ADDRESS = "DE:00:00:00:00:E1"
    private const val HOUR_MS = 60 * 60 * 1_000L

    private fun address(index: Int) = "DE:00:00:00:00:%02X".format(index + 1)

    fun devices(now: Long): List<BleDevice> = fakes.mapIndexed { index, fake ->
        // Same path-loss model as the app (P1m -59 dBm, n 2.2), so bars and numbers agree.
        val rssi = -59.0 - 22.0 * log10(fake.meters)
        BleDevice(
            address = address(index),
            name = fake.name?.let(::local),
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

    val paired: List<PairedDevice> get() = listOf(
        PairedDevice(address(0), local("My Earbuds"), DeviceCategory.EARBUDS),
        PairedDevice(address(1), local("Running Watch"), DeviceCategory.WATCH),
        // Car and computer go into the collapsed "Other paired devices" group (design home-v3).
        PairedDevice("DE:00:00:00:00:F1", local("My Car"), DeviceCategory.CAR),
        PairedDevice("DE:00:00:00:00:F2", local("Work Laptop"), DeviceCategory.COMPUTER),
        PairedDevice("DE:00:00:00:00:F3", local("Gym Band"), DeviceCategory.FITNESS_BAND),
    )

    /** Every find state the design review asks for (#52): spin, walk, locked, far, close, very close, here, lost. */
    fun findState(name: String): FinderUiState {
        val base = FinderUiState(
            deviceLabel = DeviceLabel.Named(local("My Earbuds"), NameSource.ADVERTISED, suffix = "0001"),
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
            // A nameless Apple Find My signal: the ⋯ menu explains what it may be (#147).
            "apple" -> base.copy(
                deviceLabel = DeviceLabel.Generic("Apple", DeviceCategory.TRACKER, "0001", appleFindMy = true),
                category = DeviceCategory.TRACKER,
                stage = FinderStage.WALK,
            ).distance(3.0, ProximityBand.NEAR)
            // Connected to the phone and silent: the connected text and the sound through the earbuds (#119, #153).
            "connected" -> base.copy(stage = FinderStage.NOT_FOUND, isConnected = true, soundViaPhone = true)
            // The tone is playing in the earbuds: the big stop and "louder" (#153). A still picture, no audio.
            "ringing" -> base.copy(
                stage = FinderStage.NOT_FOUND,
                isConnected = true,
                soundViaPhone = true,
                sound = SoundState.PLAYING,
            )
            // Connected, but media plays elsewhere: the guide to switch the output (#153).
            "output" -> base.copy(
                stage = FinderStage.NOT_FOUND,
                isConnected = true,
                sound = SoundState.UNAVAILABLE,
                soundNeedsOutput = true,
            )
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
