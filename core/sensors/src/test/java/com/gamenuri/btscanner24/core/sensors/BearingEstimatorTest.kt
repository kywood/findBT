package com.gamenuri.btscanner24.core.sensors

import com.gamenuri.btscanner24.core.sensors.BearingEstimator.Confidence
import com.gamenuri.btscanner24.core.sensors.BearingEstimator.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos

class BearingEstimatorTest {

    private var time = 0L

    /** Body blocking model: strongest when facing the device, about 10 dB weaker when facing away. */
    private fun rssiFacing(heading: Double, deviceBearing: Double): Double =
        -70.0 + 5.0 * cos(Math.toRadians(Angles.delta(heading, deviceBearing)))

    private fun BearingEstimator.spin(deviceBearing: Double, from: Double = 0.0): BearingEstimator.Estimate {
        var last: BearingEstimator.Estimate? = null
        var heading = from
        while (heading < from + 360.0) {
            time += 100
            last = update(time, heading, rssiFacing(heading, deviceBearing), walking = false)
            if (last.phase != Phase.SWEEP) return last
            heading += 5.0
        }
        return last!!
    }

    /** Walks along [heading] for [seconds], changing RSSI by [dbPerSecond]. */
    private fun BearingEstimator.walk(
        heading: Double,
        startRssi: Double,
        dbPerSecond: Double,
        seconds: Int,
    ): BearingEstimator.Estimate {
        var last: BearingEstimator.Estimate? = null
        for (step in 0..seconds * 10) {
            time += 100
            last = update(time, heading, startRssi + dbPerSecond * step / 10.0, walking = true)
        }
        return last!!
    }

    @Test
    fun `full spin finds the device direction`() {
        val estimate = BearingEstimator().spin(deviceBearing = 100.0)

        assertEquals(Phase.WALK, estimate.phase)
        assertEquals(Confidence.HIGH, estimate.confidence)
        assertTrue("bearing ${estimate.bearingDegrees}", abs(Angles.delta(estimate.bearingDegrees!!, 100.0)) <= 20.0)
        assertEquals(1f, estimate.sweepProgress)
    }

    @Test
    fun `half spin keeps sweeping without an arrow`() {
        val estimator = BearingEstimator()
        var estimate: BearingEstimator.Estimate? = null
        var heading = 0.0
        while (heading < 180.0) {
            time += 100
            estimate = estimator.update(time, heading, rssiFacing(heading, 100.0), walking = false)
            heading += 5.0
        }

        assertEquals(Phase.SWEEP, estimate!!.phase)
        assertNull(estimate.bearingDegrees)
        assertTrue(estimate.sweepProgress in 0.5f..0.7f)
    }

    @Test
    fun `flat signal gives no direction`() {
        val estimator = BearingEstimator()
        var estimate: BearingEstimator.Estimate? = null
        var heading = 0.0
        while (heading < 360.0) {
            time += 100
            estimate = estimator.update(time, heading, -65.0, walking = false)
            heading += 5.0
        }

        assertEquals(Phase.SWEEP, estimate!!.phase)
        assertNull(estimate.bearingDegrees)
        assertEquals(Confidence.NONE, estimate.confidence)
    }

    @Test
    fun `rising signal while walking along the arrow locks it`() {
        val estimator = BearingEstimator()
        val swept = estimator.spin(deviceBearing = 100.0)

        val walked = estimator.walk(heading = swept.bearingDegrees!!, startRssi = -66.0, dbPerSecond = 1.5, seconds = 4)

        assertEquals(Phase.LOCKED, walked.phase)
        assertEquals(Confidence.HIGH, walked.confidence)
    }

    @Test
    fun `weakening signal along the arrow flips it behind`() {
        val estimator = BearingEstimator()
        val swept = estimator.spin(deviceBearing = 100.0)
        val first = swept.bearingDegrees!!

        val walked = estimator.walk(heading = first, startRssi = -66.0, dbPerSecond = -1.5, seconds = 3)

        assertEquals(Phase.WALK, walked.phase)
        assertTrue(abs(Angles.delta(walked.bearingDegrees!!, first + 180.0)) < 1.0)
    }

    @Test
    fun `signal falling for ten seconds goes back to spinning`() {
        val estimator = BearingEstimator()
        estimator.spin(deviceBearing = 100.0)

        // Walking off to the side (outside the cone) so only the lost rule applies.
        val walked = estimator.walk(heading = 250.0, startRssi = -66.0, dbPerSecond = -0.5, seconds = 11)

        assertEquals(Phase.SWEEP, walked.phase)
        assertNull(walked.bearingDegrees)
    }

    @Test
    fun `angles wrap around north`() {
        assertEquals(-20.0, Angles.delta(350.0, 10.0), 1e-9)
        assertEquals(350.0, Angles.normalize(-10.0), 1e-9)
        assertEquals(0.0, Angles.circularMean(listOf(350.0 to 1.0, 10.0 to 1.0))!!, 1e-6)
        assertNull(Angles.circularMean(listOf(0.0 to 1.0, 180.0 to 1.0)))
    }
}
