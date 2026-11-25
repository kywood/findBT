package com.gamenuri.btscanner24.core.ui.arrow3d

import kotlin.math.sqrt

/**
 * The raised check mark on the "Found it" medal (#76): two bars meeting at the bottom corner,
 * standing [HEIGHT] proud of a face at height [base]. It lies in the floor plane like [DiscMesh];
 * −Z is the top of the mark, +X its right. Units are the floor ring's radius R.
 */
internal object CheckMesh {
    const val HEIGHT = 0.045f
    private const val HALF_WIDTH = 0.055f
    private const val SINK = 0.005f

    // Start of the short stroke, the corner, the end of the long stroke.
    private const val START_X = -0.27f
    private const val START_Z = 0.03f
    private const val CORNER_X = -0.08f
    private const val CORNER_Z = 0.22f
    private const val END_X = 0.29f
    private const val END_Z = -0.22f

    private const val FACES_PER_BAR = 5
    private const val FLOATS_PER_BAR = FACES_PER_BAR * 2 * 3 * ObjMesh.FLOATS_PER_VERTEX

    /** Interleaved position + normal, [ObjMesh.FLOATS_PER_VERTEX] floats per vertex, as triangles. */
    fun build(base: Float): FloatArray {
        val out = FloatArray(2 * FLOATS_PER_BAR)
        val bottom = base - SINK
        val top = base + HEIGHT
        bar(out, 0, START_X, START_Z, CORNER_X, CORNER_Z, bottom, top)
        bar(out, FLOATS_PER_BAR, CORNER_X, CORNER_Z, END_X, END_Z, bottom, top)
        return out
    }

    /** A box along the line a → b, [HALF_WIDTH] longer at both ends; the hidden underside is left out. */
    private fun bar(
        out: FloatArray,
        offset: Int,
        ax: Float,
        az: Float,
        bx: Float,
        bz: Float,
        bottom: Float,
        top: Float,
    ) {
        val length = sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az))
        val dx = (bx - ax) / length
        val dz = (bz - az) / length
        val sx = -dz
        val sz = dx
        // Corners, clockwise seen from above: a-left, b-left, b-right, a-right.
        val xs = floatArrayOf(
            ax - (dx - sx) * HALF_WIDTH,
            bx + (dx + sx) * HALF_WIDTH,
            bx + (dx - sx) * HALF_WIDTH,
            ax - (dx + sx) * HALF_WIDTH,
        )
        val zs = floatArrayOf(
            az - (dz - sz) * HALF_WIDTH,
            bz + (dz + sz) * HALF_WIDTH,
            bz + (dz - sz) * HALF_WIDTH,
            az - (dz + sz) * HALF_WIDTH,
        )
        var i = offset
        fun vertex(corner: Int, y: Float, nx: Float, ny: Float, nz: Float) {
            out[i++] = xs[corner]
            out[i++] = y
            out[i++] = zs[corner]
            out[i++] = nx
            out[i++] = ny
            out[i++] = nz
        }
        // Top face.
        for (corner in intArrayOf(0, 1, 2, 0, 2, 3)) vertex(corner, top, 0f, 1f, 0f)
        // Four walls; wall n runs from corner n to the next one.
        val normals = floatArrayOf(sx, sz, dx, dz, -sx, -sz, -dx, -dz)
        for (wall in 0 until 4) {
            val next = (wall + 1) % 4
            val nx = normals[wall * 2]
            val nz = normals[wall * 2 + 1]
            vertex(wall, bottom, nx, 0f, nz)
            vertex(next, bottom, nx, 0f, nz)
            vertex(next, top, nx, 0f, nz)
            vertex(wall, bottom, nx, 0f, nz)
            vertex(next, top, nx, 0f, nz)
            vertex(wall, top, nx, 0f, nz)
        }
    }
}
