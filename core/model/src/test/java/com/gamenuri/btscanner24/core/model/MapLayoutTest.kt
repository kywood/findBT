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
    fun `devices in one band never share a place`() {
        val entries = (1..12).map { MapEntry("AA:BB:CC:00:00:%02d".format(it), 3.0) }
        val spots = MapLayout.arrange(entries).values.toList()
        assertEquals(12, spots.size)
        for (i in spots.indices) {
            for (j in i + 1 until spots.size) {
                val apart = MapLayout.apart(spots[i].angleDegrees, spots[j].angleDegrees)
                assertTrue("only $apart° apart", apart >= 29f)
            }
        }
    }

    @Test
    fun `a measured direction is used as it is and the others keep clear of it`() {
        val home = MapLayout.homeDegrees("plain")
        val spots = MapLayout.arrange(listOf(MapEntry("plain", 3.0), MapEntry("measured", 3.5, home)))
        val measured = spots.getValue("measured")
        assertTrue(measured.measured)
        assertEquals(home, measured.angleDegrees, 0.001f)
        val plain = spots.getValue("plain")
        assertTrue(!plain.measured)
        assertTrue(MapLayout.apart(plain.angleDegrees, measured.angleDegrees) >= 14f)
    }

    @Test
    fun `no direction is made up as measured`() {
        val spots = MapLayout.arrange((1..20).map { MapEntry("d$it", it * 0.7) })
        assertTrue(spots.values.none { it.measured })
    }

    @Test
    fun `similar keys are spread round the middle`() {
        val angles = (0..7).map { MapLayout.homeDegrees("AA:BB:CC:DD:EE:0$it") }
        assertTrue(angles.max() - angles.min() > 90f)
        assertTrue(angles.all { it in 0f..360f })
    }
}
