package com.gamenuri.btscanner24.core.ble.proximity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityEstimatorTest {

    @Test
    fun `first sample maps straight to a band`() {
        assertEquals(ProximityBand.VERY_CLOSE, ProximityEstimator().add(-50).band)
        assertEquals(ProximityBand.NEAR, ProximityEstimator().add(-65).band)
        assertEquals(ProximityBand.FAR, ProximityEstimator().add(-85).band)
    }

    @Test
    fun `single outlier spike is ignored`() {
        val estimator = ProximityEstimator()
        repeat(5) { estimator.add(-80) }
        val reading = estimator.add(-40)
        assertEquals(-80.0, reading.smoothedRssi, 0.01)
        assertEquals(ProximityBand.FAR, reading.band)
    }

    @Test
    fun `band does not flicker around the threshold`() {
        val estimator = ProximityEstimator(alpha = 1.0)
        estimator.add(-73) // FAR
        // -71 is past the -72 threshold but inside the 3 dB hysteresis.
        assertEquals(ProximityBand.FAR, estimator.add(-71).band)
        assertEquals(ProximityBand.NEAR, estimator.add(-68).band)
    }

    @Test
    fun `walking toward the device reports CLOSER and higher heat`() {
        val estimator = ProximityEstimator(alpha = 0.5)
        var last = estimator.add(-90)
        listOf(-88, -86, -84, -82, -80, -78, -76).forEach { last = estimator.add(it) }
        assertEquals(ProximityTrend.CLOSER, last.trend)
        assertTrue(last.heat > 0.2f)
    }

    @Test
    fun `heat is clamped between 0 and 1`() {
        assertEquals(0f, ProximityEstimator().add(-120).heat)
        assertEquals(1f, ProximityEstimator().add(-20).heat)
    }
}
