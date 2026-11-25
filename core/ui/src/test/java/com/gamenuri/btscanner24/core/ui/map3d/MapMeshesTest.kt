package com.gamenuri.btscanner24.core.ui.map3d

import com.gamenuri.btscanner24.core.ui.arrow3d.ObjMesh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class MapMeshesTest {
    @Test
    fun `the box is twelve triangles of side one`() {
        val box = MapMeshes.box()
        assertEquals(12 * 3 * ObjMesh.FLOATS_PER_VERTEX, box.size)
        for (vertex in box.indices step ObjMesh.FLOATS_PER_VERTEX) {
            for (axis in 0..2) assertEquals(0.5f, abs(box[vertex + axis]), 0.0001f)
        }
    }

    @Test
    fun `the arrowhead points ahead and is eight triangles`() {
        val head = MapMeshes.arrowhead()
        assertEquals(8 * 3 * ObjMesh.FLOATS_PER_VERTEX, head.size)
        var ahead = 0f
        for (vertex in head.indices step ObjMesh.FLOATS_PER_VERTEX) ahead = minOf(ahead, head[vertex + 2])
        assertEquals(-1f, ahead, 0.0001f)
    }

    @Test
    fun `every normal has length one and faces out`() {
        for (mesh in listOf(MapMeshes.box(), MapMeshes.arrowhead())) {
            for (vertex in mesh.indices step ObjMesh.FLOATS_PER_VERTEX) {
                val nx = mesh[vertex + 3]
                val ny = mesh[vertex + 4]
                val nz = mesh[vertex + 5]
                assertEquals(1f, sqrt(nx * nx + ny * ny + nz * nz), 0.001f)
                val out = mesh[vertex] * nx + mesh[vertex + 1] * ny + mesh[vertex + 2] * nz
                assertTrue("normal faces in at vertex ${vertex / ObjMesh.FLOATS_PER_VERTEX}", out > 0f)
            }
        }
    }
}
