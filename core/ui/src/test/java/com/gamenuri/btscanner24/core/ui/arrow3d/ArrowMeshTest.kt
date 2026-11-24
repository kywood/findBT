package com.gamenuri.btscanner24.core.ui.arrow3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class ArrowMeshTest {
    private val mesh = ArrowMesh.build()
    private val stride = ObjMesh.FLOATS_PER_VERTEX
    private val vertices = mesh.size / stride

    private fun range(axis: Int): Pair<Float, Float> {
        val values = (0 until vertices).map { mesh[it * stride + axis] }
        return values.min() to values.max()
    }

    @Test
    fun `is whole triangles`() {
        assertEquals(0, mesh.size % (stride * 3))
        assertEquals(52 * 3, vertices)
    }

    @Test
    fun `has the design's size, centred, pointing at minus Z`() {
        assertEquals(-ArrowMesh.HEAD_WIDTH / 2 to ArrowMesh.HEAD_WIDTH / 2, range(0))
        assertEquals(-ArrowMesh.THICKNESS / 2 to ArrowMesh.THICKNESS / 2, range(1))
        assertEquals(-ArrowMesh.LENGTH / 2 to ArrowMesh.LENGTH / 2, range(2))
        // The widest point (the head) is in the front half.
        val widest = (0 until vertices).maxBy { mesh[it * stride] }
        assertTrue(mesh[widest * stride + 2] < 0f)
    }

    @Test
    fun `normals are unit length and point out of the shape`() {
        for (i in 0 until vertices) {
            val o = i * stride
            val (nx, ny, nz) = Triple(mesh[o + 3], mesh[o + 4], mesh[o + 5])
            assertEquals(1f, sqrt(nx * nx + ny * ny + nz * nz), 1e-4f)
            // Top half faces never look down, bottom half never up.
            if (mesh[o + 1] == ArrowMesh.THICKNESS / 2) assertTrue(ny > 0f)
            if (mesh[o + 1] == -ArrowMesh.THICKNESS / 2) assertTrue(ny < 0f)
        }
    }

    @Test
    fun `top face is flat and lit from above`() {
        // The first triangle is the top face's tip.
        assertEquals(0f, mesh[3], 0f)
        assertEquals(1f, mesh[4], 0f)
        assertEquals(0f, mesh[5], 0f)
    }
}
