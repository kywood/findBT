package com.gamenuri.btscanner24.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MapBandHoldTest {
    private fun band(hold: MapBandHold, meters: Double?, now: Long): Int =
        MapLayout.ring(hold.hold(listOf(MapEntry("tv", meters)), now).single().distanceMeters).toInt()

    @Test
    fun firstSightingTakesItsBandAtOnce() {
        assertEquals(2, band(MapBandHold(), 7.0, 0L))
    }

    @Test
    fun wobbleAcrossAnEdgeStaysOnTheRing() {
        val hold = MapBandHold()
        assertEquals(1, band(hold, 4.0, 0L))
        var now = 0L
        repeat(10) {
            now += 500L
            assertEquals(1, band(hold, if (it % 2 == 0) 7.0 else 4.0, now))
        }
    }

    @Test
    fun justPastTheEdgeNeverMoves() {
        val hold = MapBandHold()
        band(hold, 4.0, 0L)
        assertEquals(1, band(hold, 5.3, 1_000L))
        assertEquals(1, band(hold, 5.3, 10_000L))
    }

    @Test
    fun clearlyInAnotherBandMovesAfterTheDwell() {
        val hold = MapBandHold()
        band(hold, 4.0, 0L)
        assertEquals(1, band(hold, 7.0, 1_000L))
        assertEquals(1, band(hold, 7.0, 2_500L))
        assertEquals(2, band(hold, 7.0, 3_000L))
        assertEquals(2, band(hold, 4.9, 4_000L))
    }

    @Test
    fun comingCloserNeedsTheMarginToo() {
        val hold = MapBandHold()
        band(hold, 7.0, 0L)
        assertEquals(2, band(hold, 4.5, 1_000L))
        assertEquals(2, band(hold, 4.5, 5_000L))
        assertEquals(2, band(hold, 4.0, 6_000L))
        assertEquals(1, band(hold, 4.0, 8_000L))
    }
}
