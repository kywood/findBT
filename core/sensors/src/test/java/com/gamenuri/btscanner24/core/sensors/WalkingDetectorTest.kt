package com.gamenuri.btscanner24.core.sensors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class WalkingDetectorTest {

    private fun WalkingDetector.feed(fromMs: Long, toMs: Long, signal: (Long) -> Double): Boolean {
        var t = fromMs
        var result = false
        while (t <= toMs) {
            result = add(t, signal(t))
            t += 20
        }
        return result
    }

    @Test
    fun `standing still is not walking`() {
        val detector = WalkingDetector()
        assertFalse(detector.feed(0, 3_000) { 0.1 + 0.05 * sin(it / 50.0) })
    }

    @Test
    fun `steps at two hertz are walking`() {
        val detector = WalkingDetector()
        assertTrue(detector.feed(0, 3_000) { 1.5 + 2.0 * sin(2 * PI * 2 * it / 1000.0) })
    }

    @Test
    fun `stopping ends walking after the window`() {
        val detector = WalkingDetector()
        detector.feed(0, 3_000) { 1.5 + 2.0 * sin(2 * PI * 2 * it / 1000.0) }
        assertFalse(detector.feed(3_020, 6_000) { 0.1 })
    }
}
