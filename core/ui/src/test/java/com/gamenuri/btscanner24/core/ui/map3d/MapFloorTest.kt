package com.gamenuri.btscanner24.core.ui.map3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapFloorTest {
    @Test
    fun `each band keeps its devices on its own part of the floor`() {
        for (band in 0 until MapFloor.BANDS) {
            for (across in listOf(0f, 0.5f, 0.99f)) {
                assertEquals(band, MapFloor.band(MapFloor.radius(band + across)))
            }
        }
    }

    @Test
    fun `further across a band is further from the middle`() {
        var last = 0f
        for (tenth in 0 until MapFloor.BANDS * 10) {
            val radius = MapFloor.radius(tenth / 10f)
            assertTrue("ring ${tenth / 10f} gives $radius after $last", radius > last)
            last = radius
        }
    }

    @Test
    fun `values outside the bands stay on the floor`() {
        assertEquals(MapFloor.radius(0f), MapFloor.radius(-2f), 0.0001f)
        assertEquals(MapFloor.BANDS - 1, MapFloor.band(MapFloor.radius(99f)))
    }

    @Test
    fun `every step lies lower than the one inside it`() {
        assertEquals(0f, MapFloor.height(1f), 0.0001f)
        assertEquals(-MapFloor.STEP_HEIGHT, MapFloor.height(3f), 0.0001f)
        assertEquals(-2 * MapFloor.STEP_HEIGHT, MapFloor.height(5f), 0.0001f)
        assertEquals(-3 * MapFloor.STEP_HEIGHT, MapFloor.height(8f), 0.0001f)
    }

    @Test
    fun `the camera never looks straight down and rises as the phone lies down`() {
        assertEquals(MapCamera.TYPICAL, MapCamera.elevationFor(MapCamera.TYPICAL_POSE), 0.001f)
        var last = 0f
        for (pose in 0..90 step 5) {
            val camera = MapCamera.elevationFor(pose.toFloat())
            assertTrue("pose $pose gives $camera", camera >= last && camera <= 50f)
            last = camera
        }
    }
}
