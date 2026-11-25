package com.gamenuri.btscanner24.core.ui.arrow3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class FoundMeshesTest {
    private val stride = ObjMesh.FLOATS_PER_VERTEX

    private fun assertUnitNormals(mesh: FloatArray) {
        assertEquals(0, mesh.size % (stride * 3))
        for (vertex in 0 until mesh.size / stride) {
            val o = vertex * stride + 3
            val length = sqrt(mesh[o] * mesh[o] + mesh[o + 1] * mesh[o + 1] + mesh[o + 2] * mesh[o + 2])
            assertEquals(1f, length, 0.001f)
        }
    }

    @Test
    fun `disc has the size it was asked for`() {
        val mesh = DiscMesh.build(radius = 0.34f, thickness = 0.11f, bevel = 0.02f)
        assertUnitNormals(mesh)
        var widest = 0f
        var highest = 0f
        var lowest = 0f
        for (vertex in 0 until mesh.size / stride) {
            val o = vertex * stride
            widest = maxOf(widest, sqrt(mesh[o] * mesh[o] + mesh[o + 2] * mesh[o + 2]))
            highest = maxOf(highest, mesh[o + 1])
            lowest = minOf(lowest, mesh[o + 1])
        }
        assertEquals(0.34f, widest, 0.0001f)
        assertEquals(0.055f, highest, 0.0001f)
        assertEquals(-0.055f, lowest, 0.0001f)
    }

    @Test
    fun `disc faces point away from its middle`() {
        val mesh = DiscMesh.build(radius = 0.6f, thickness = 0.13f, bevel = 0.045f)
        for (vertex in 0 until mesh.size / stride) {
            val o = vertex * stride
            val outward = mesh[o] * mesh[o + 3] + mesh[o + 1] * mesh[o + 4] + mesh[o + 2] * mesh[o + 5]
            assertTrue(outward > 0f)
        }
    }

    @Test
    fun `check stands on the face and stays inside the medal`() {
        val base = 0.065f
        val mesh = CheckMesh.build(base)
        assertUnitNormals(mesh)
        for (vertex in 0 until mesh.size / stride) {
            val o = vertex * stride
            assertTrue(sqrt(mesh[o] * mesh[o] + mesh[o + 2] * mesh[o + 2]) < 0.45f)
            assertTrue(mesh[o + 1] <= base + CheckMesh.HEIGHT + 0.0001f)
        }
    }
}
