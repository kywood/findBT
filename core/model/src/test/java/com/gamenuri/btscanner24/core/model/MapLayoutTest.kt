package com.gamenuri.btscanner24.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLayoutTest {
    @Test
    fun `the distance picks the band like the list's distance texts`() {
        assertEquals(0, MapLayout.ring(0.4).toInt())
        assertEquals(1, MapLayout.ring(1.0).toInt())
        assertEquals(1, MapLayout.ring(4.9).toInt())
        assertEquals(2, MapLayout.ring(5.0).toInt())
        assertEquals(2, MapLayout.ring(9.9).toInt())
        assertEquals(3, MapLayout.ring(10.0).toInt())
        assertEquals(3, MapLayout.ring(80.0).toInt())
        assertEquals(3, MapLayout.ring(null).toInt())
    }

    @Test
    fun `further away is further across its band`() {
        assertTrue(MapLayout.ring(0.2) < MapLayout.ring(0.8))
        assertTrue(MapLayout.ring(2.0) < MapLayout.ring(4.0))
        assertEquals(1.5f, MapLayout.ring(3.0), 0.001f)
    }

    @Test
    fun `a device stands in the same place whatever else is found and in whatever order`() {
        val alone = MapLayout.arrange(listOf(MapEntry("AA:01", 3.0)))
        val later = MapLayout.arrange(listOf(MapEntry("ZZ:99", 7.0), MapEntry("AA:01", 3.0)))
        assertEquals(alone["AA:01"], later["AA:01"])
        val entries = (1..9).map { MapEntry("device-$it", it.toDouble()) }
        assertEquals(MapLayout.arrange(entries), MapLayout.arrange(entries.reversed()))
    }

    @Test
    fun `devices in one band never share a place while there is room`() {
        for (band in 0 until MapLayout.BANDS) {
            val meters = listOf(0.5, 3.0, 7.0, 20.0)[band]
            val entries = (1..MapLayout.places(band)).map { MapEntry("AA:BB:CC:00:00:%02d".format(it), meters) }
            val angles = MapLayout.arrange(entries).values.map { it.angleDegrees }
            assertEquals(entries.size, angles.toSet().size)
        }
    }

    @Test
    fun `beyond the first ring every place is on the far side or straight below, never off to the side`() {
        val entries = (1..40).map { MapEntry("device-$it", 1.0 + it * 0.5) }
        for (spot in MapLayout.arrange(entries).values) {
            val angle = spot.angleDegrees
            val far = angle <= 60f || angle >= 300f
            val near = angle in 144f..216f
            assertTrue("a place at $angle°", far || (near && spot.ring.toInt() == 1))
        }
    }

    @Test
    fun `a measured direction is used as it is`() {
        val spots = MapLayout.arrange(listOf(MapEntry("plain", 3.0), MapEntry("measured", 3.5, -90f)))
        val measured = spots.getValue("measured")
        assertTrue(measured.measured)
        assertEquals(270f, measured.angleDegrees, 0.001f)
        assertEquals(MapLayout.arrange(listOf(MapEntry("plain", 3.0))).getValue("plain"), spots.getValue("plain"))
    }

    @Test
    fun `no direction is made up as measured`() {
        val spots = MapLayout.arrange((1..20).map { MapEntry("d$it", it * 0.7) })
        assertTrue(spots.values.none { it.measured })
    }

    @Test
    fun `similar keys ask for different places`() {
        val places = (0..7).map { MapLayout.homePlace("AA:BB:CC:DD:EE:0$it", 1) }
        assertTrue(places.toSet().size >= 4)
    }
}
