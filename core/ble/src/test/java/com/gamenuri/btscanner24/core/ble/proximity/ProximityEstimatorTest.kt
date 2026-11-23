package com.gamenuri.btscanner24.core.ble.proximity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityEstimatorTest {

    @Test
    fun `distance follows the path loss model`() {
        val estimator = ProximityEstimator()
        assertEquals(1.0, estimator.distanceFor(-59.0, txPower = null), 0.01)
        // 22 dB weaker with n = 2.2 is 10x farther.
        assertEquals(10.0, estimator.distanceFor(-81.0, txPower = null), 0.1)
        // Advertised TX power -12 dBm means about -53 dBm at 1 m.
        assertEquals(1.0, estimator.distanceFor(-53.0, txPower = -12), 0.01)
    }

    @Test
    fun `first sample maps straight to a band`() {
        assertEquals(ProximityBand.RIGHT_HERE, ProximityEstimator().add(-45, 0).band)
        assertEquals(ProximityBand.NEAR, ProximityEstimator().add(-70, 0).band)
        assertEquals(ProximityBand.FAR, ProximityEstimator().add(-85, 0).band)
    }

    @Test
    fun `single outlier spike is ignored`() {
        val estimator = ProximityEstimator()
        var t = 0L
        repeat(5) { estimator.add(-80, t++) }
        val reading = estimator.add(-40, t)
        assertEquals(-80.0, reading.smoothedRssi, 0.5)
        assertEquals(ProximityBand.FAR, reading.band)
    }

    @Test
    fun `band does not flicker around an edge`() {
        val estimator = ProximityEstimator()
        // 5.2 m is past the 5 m edge but inside the 0.3 m hysteresis, so NEAR stays NEAR.
        assertEquals(ProximityBand.NEAR, estimator.bandFor(5.2, ProximityBand.NEAR))
        assertEquals(ProximityBand.FAR, estimator.bandFor(5.4, ProximityBand.NEAR))
        assertEquals(ProximityBand.FAR, estimator.bandFor(4.8, ProximityBand.FAR))
        assertEquals(ProximityBand.NEAR, estimator.bandFor(4.6, ProximityBand.FAR))
    }

    @Test
    fun `walking toward the device reports CLOSER and more heat`() {
        val estimator = ProximityEstimator()
        var reading = estimator.add(-85, 0)
        var rssi = -85
        for (t in 250L..7_000L step 250L) {
            if (t % 1_000L == 0L) rssi += 2
            reading = estimator.add(rssi, t)
        }
        assertEquals(ProximityTrend.CLOSER, reading.trend)
        assertTrue(reading.heat > 0.25f)
    }

    @Test
    fun `heat is clamped between 0 and 1`() {
        assertEquals(0f, ProximityEstimator().add(-120, 0).heat)
        assertEquals(1f, ProximityEstimator().add(-20, 0).heat)
    }
}
