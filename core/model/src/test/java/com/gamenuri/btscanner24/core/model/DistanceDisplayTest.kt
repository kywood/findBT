package com.gamenuri.btscanner24.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceDisplayTest {

    @Test
    fun `under 1 m reads within 1 m, no false decimals`() {
        assertEquals(DistanceDisplay.WithinOne, DistanceDisplay.of(0.83))
        assertEquals(DistanceDisplay.WithinOne, DistanceDisplay.of(0.02))
    }

    @Test
    fun `1 to 5 m rounds to whole metres`() {
        assertEquals(DistanceDisplay.About(2), DistanceDisplay.of(2.4))
        assertEquals(DistanceDisplay.About(1), DistanceDisplay.of(1.2))
        assertEquals(DistanceDisplay.About(4), DistanceDisplay.of(4.8))
    }

    @Test
    fun `beyond 5 m shows a coarse range so the number does not jump`() {
        assertEquals(DistanceDisplay.FiveToTen, DistanceDisplay.of(7.0))
        assertEquals(DistanceDisplay.FiveToTen, DistanceDisplay.of(9.9))
        assertEquals(DistanceDisplay.OverTen, DistanceDisplay.of(13.0))
    }

    @Test
    fun `feet show whole feet between 3 and 15 ft`() {
        assertEquals(DistanceDisplay.WithinOne, DistanceDisplay.of(0.83, DistanceUnit.FEET))
        assertEquals(DistanceDisplay.About(3, DistanceUnit.FEET), DistanceDisplay.of(1.0, DistanceUnit.FEET))
        assertEquals(DistanceDisplay.About(8, DistanceUnit.FEET), DistanceDisplay.of(2.4, DistanceUnit.FEET))
        assertEquals(DistanceDisplay.About(15, DistanceUnit.FEET), DistanceDisplay.of(4.9, DistanceUnit.FEET))
    }

    @Test
    fun `feet keep the same ranges as metres`() {
        assertEquals(DistanceDisplay.FiveToTen, DistanceDisplay.of(7.0, DistanceUnit.FEET))
        assertEquals(DistanceDisplay.OverTen, DistanceDisplay.of(13.0, DistanceUnit.FEET))
    }
}
