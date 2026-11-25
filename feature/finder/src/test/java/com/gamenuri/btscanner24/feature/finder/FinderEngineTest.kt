package com.gamenuri.btscanner24.feature.finder

import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.sensors.MotionSample
import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinderEngineTest {

    private val buds = BleDevice(
        address = "AA:AA:AA:AA:AA:01",
        name = "Galaxy Buds2 Pro",
        rssi = -70,
        firstSeen = 0,
        lastSeen = 0,
        companyId = 0x0075,
    )

    private fun sighting(rssi: Int, now: Long, address: String = buds.address) =
        buds.copy(address = address, rssi = rssi, lastSeen = now)

    /** Spins a full turn with the device at [deviceBearing]; returns the events that fired. */
    private fun FinderEngine.spin(deviceBearing: Double, startMs: Long): Pair<Long, List<FinderEvent>> {
        val events = mutableListOf<FinderEvent>()
        var now = startMs
        var heading = 0.0
        while (heading < 360.0) {
            now += 100
            val rssi = -72 + 6 * cos(Math.toRadians(heading - deviceBearing))
            onSighting(sighting(rssi.toInt(), now), now)
            onMotion(MotionSample(now, heading, walking = false), now)?.let(events::add)
            heading += 3.0
        }
        return now to events
    }

    @Test
    fun `starts waiting for a signal`() {
        val engine = FinderEngine(buds, startMs = 0, hasCompass = true)
        assertEquals(FinderStage.WAITING, engine.state(0, vibrationOn = true).stage)
    }

    @Test
    fun `no signal for ten seconds is said plainly instead of waiting forever`() {
        val engine = FinderEngine(buds, startMs = 0, hasCompass = true)
        assertEquals(FinderStage.WAITING, engine.state(10_000, vibrationOn = true).stage)
        assertEquals(FinderStage.NOT_FOUND, engine.state(10_001, vibrationOn = true).stage)
    }

    @Test
    fun `trying again waits afresh, and a signal ends the not-found state`() {
        val engine = FinderEngine(buds, startMs = 0, hasCompass = true)
        engine.restartWaiting(20_000)
        assertEquals(FinderStage.WAITING, engine.state(25_000, vibrationOn = true).stage)
        assertEquals(FinderStage.NOT_FOUND, engine.state(31_000, vibrationOn = true).stage)
        engine.onSighting(sighting(-70, 31_500), 31_500)
        assertEquals(FinderStage.SPIN, engine.state(31_500, vibrationOn = true).stage)
    }

    @Test
    fun `first signal asks to spin, full turn shows an arrow`() {
        val engine = FinderEngine(buds, startMs = 0, hasCompass = true)
        engine.onSighting(sighting(-70, 100), 100)
        engine.onMotion(MotionSample(100, 0.0, walking = false), 100)
        assertEquals(FinderStage.SPIN, engine.state(100, true).stage)

        val (now, events) = engine.spin(deviceBearing = 120.0, startMs = 100)
        val state = engine.state(now, true)

        assertTrue(events.contains(FinderEvent.DIRECTION_FOUND))
        assertTrue(state.stage == FinderStage.WALK || state.stage == FinderStage.TURN_AROUND)
        assertNotNull(state.advanced.bearingDegrees)
    }

    @Test
    fun `device behind the phone says turn around`() {
        val engine = FinderEngine(buds, startMs = 0, hasCompass = true)
        val (now, _) = engine.spin(deviceBearing = 120.0, startMs = 0)
        val bearing = engine.state(now, true).advanced.bearingDegrees!!

        engine.onMotion(MotionSample(now + 100, (bearing + 180.0) % 360.0, walking = false), now + 100)
        val state = engine.state(now + 100, true)

        assertEquals(FinderStage.TURN_AROUND, state.stage)
        assertEquals(180f, kotlin.math.abs(state.arrowDegrees!!), 1f)
    }

    @Test
    fun `no compass shows distance only`() {
        val engine = FinderEngine(buds, startMs = 0, hasCompass = false)
        engine.onSighting(sighting(-60, 100), 100)
        val state = engine.state(100, true)

        assertEquals(FinderStage.NO_COMPASS, state.stage)
        assertNull(state.arrowDegrees)
        assertNotNull(state.distanceMeters)
    }

    @Test
    fun `silence for eight seconds is signal lost`() {
        val engine = FinderEngine(buds, startMs = 0, hasCompass = true)
        engine.onSighting(sighting(-60, 1_000), 1_000)

        val state = engine.state(10_000, true)

        assertEquals(FinderStage.SIGNAL_LOST, state.stage)
        assertEquals(9, state.secondsSinceSeen)
    }

    @Test
    fun `follows the target after a MAC change`() {
        val engine = FinderEngine(buds, startMs = 0, hasCompass = true)
        engine.onSighting(sighting(-60, 1_000), 1_000)

        val switched = engine.onSighting(sighting(-61, 5_000, address = "BB:BB:BB:BB:BB:02"), 5_000)

        assertTrue(switched)
        assertEquals("BB:BB:BB:BB:BB:02", engine.state(5_000, true).advanced.address)
        assertEquals(1, engine.state(5_000, true).advanced.addressSwitches)
    }

    @Test
    fun `holding very close for two seconds is right here`() {
        val engine = FinderEngine(buds, startMs = 0, hasCompass = true)
        var now = 0L
        repeat(40) {
            now += 100
            engine.onSighting(sighting(-35, now), now)
        }
        assertEquals(FinderStage.RIGHT_HERE, engine.state(now, true).stage)
    }

    @Test
    fun `confirm hint appears only after 15 s very close`() {
        val engine = FinderEngine(buds, startMs = 0, hasCompass = true)
        // -60 dBm is about 1.1 m with the default 1 m power: very close, not right here.
        var now = 0L
        while (now <= 5_000) {
            engine.onSighting(sighting(-60, now), now)
            now += 200
        }
        assertFalse(engine.state(now, true).showConfirmHint)
        while (now <= 16_000) {
            engine.onSighting(sighting(-60, now), now)
            now += 200
        }
        assertTrue(engine.state(now, true).showConfirmHint)
    }

    @Test
    fun `confirm hint never shows when far`() {
        val engine = FinderEngine(buds, startMs = 0, hasCompass = true)
        var now = 0L
        while (now <= 20_000) {
            engine.onSighting(sighting(-85, now), now)
            now += 200
        }
        assertFalse(engine.state(now, true).showConfirmHint)
    }
}
