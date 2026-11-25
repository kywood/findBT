package com.gamenuri.btscanner24.core.ui.arrow3d

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class MarkerMeshTest {
    private val mesh = MarkerMesh.build()
    private val stride = ObjMesh.FLOATS_PER_VERTEX

    @Test
    fun `is whole triangles`() {
        assertEquals(0, mesh.size % (stride * 3))
    }

    @Test
    fun `every vertex is on the ball and its normal points straight out`() {
        for (vertex in 0 until mesh.size / stride) {
            val o = vertex * stride
            val radius = sqrt(mesh[o] * mesh[o] + mesh[o + 1] * mesh[o + 1] + mesh[o + 2] * mesh[o + 2])
            assertEquals(MarkerMesh.RADIUS, radius, 0.0001f)
            for (axis in 0 until 3) {
                assertEquals(mesh[o + axis] / MarkerMesh.RADIUS, mesh[o + 3 + axis], 0.0001f)
            }
        }
    }
}
