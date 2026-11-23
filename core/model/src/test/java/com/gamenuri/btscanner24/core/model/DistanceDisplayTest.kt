package com.gamenuri.btscanner24.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceDisplayTest {

    @Test
    fun `under 1 m rounds to 0_1 m and never shows zero`() {
        assertEquals(DistanceDisplay.About(0.8), DistanceDisplay.of(0.83))
        assertEquals(DistanceDisplay.About(0.1), DistanceDisplay.of(0.02))
    }

    @Test
    fun `1 to 5 m rounds to half metres`() {
        assertEquals(DistanceDisplay.About(2.5), DistanceDisplay.of(2.4))
        assertEquals(DistanceDisplay.About(3.0), DistanceDisplay.of(3.2))
    }

    @Test
    fun `beyond 5 m shows a coarse range so the number does not jump`() {
        assertEquals(DistanceDisplay.FiveToTen, DistanceDisplay.of(7.0))
        assertEquals(DistanceDisplay.FiveToTen, DistanceDisplay.of(9.9))
        assertEquals(DistanceDisplay.OverTen, DistanceDisplay.of(13.0))
    }
}
