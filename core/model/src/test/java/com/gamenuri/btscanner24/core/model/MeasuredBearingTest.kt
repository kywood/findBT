package com.gamenuri.btscanner24.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasuredBearingTest {
    private val now = 1_000_000L

    @Test
    fun `a direction is fresh for two minutes and never from the future`() {
        assertTrue(MeasuredBearing(90f, true, now - 119_000).isFresh(now))
        assertFalse(MeasuredBearing(90f, true, now - 121_000).isFresh(now))
        assertFalse(MeasuredBearing(90f, true, now + 1).isFresh(now))
    }

    @Test
    fun `only devices with a fresh direction get one on the map`() {
        val entries = MeasuredBearing.entries(
            distances = listOf("a" to 2.0, "b" to null, "c" to 7.5),
            bearings = mapOf(
                "a" to MeasuredBearing(45f, false, now - 10_000),
                "c" to MeasuredBearing(200f, true, now - 600_000),
                "gone" to MeasuredBearing(10f, true, now),
            ),
            now = now,
        )
        assertEquals(listOf(MapEntry("a", 2.0, 45f), MapEntry("b", null, null), MapEntry("c", 7.5, null)), entries)
        val spots = MapLayout.arrange(entries)
        assertTrue(spots.getValue("a").measured)
        assertEquals(45f, spots.getValue("a").angleDegrees)
        assertFalse(spots.getValue("c").measured)
    }
}
