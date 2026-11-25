package com.gamenuri.btscanner24.core.ui.arrow3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A raised rim: a ring with an upright inner wall, a flat top and an upright outer wall, standing
 * on the floor plane (y = 0) around the origin. The lit edges of the stepped find floor (design
 * find-flow-3d v1.2, #132). Units are the floor ring's radius R.
 */
internal object RimMesh {
    private const val SLICES = 96

    /** Interleaved position + normal, [ObjMesh.FLOATS_PER_VERTEX] floats per vertex, as triangles. */
    fun build(outer: Float, width: Float, height: Float): FloatArray {
        val inner = outer - width
        // Up the inner wall, across the top, down the outer wall; each with its own flat normal.
        val profile = floatArrayOf(inner, 0f, inner, height, outer, height, outer, 0f)
        val normals = floatArrayOf(-1f, 0f, 0f, 1f, 1f, 0f)
        val bands = profile.size / 2 - 1
        val out = FloatArray(bands * SLICES * 2 * 3 * ObjMesh.FLOATS_PER_VERTEX)
        var i = 0
        for (band in 0 until bands) {
            val r0 = profile[band * 2]
            val y0 = profile[band * 2 + 1]
            val r1 = profile[band * 2 + 2]
            val y1 = profile[band * 2 + 3]
            val normalOut = normals[band * 2]
            val normalUp = normals[band * 2 + 1]
            fun vertex(r: Float, y: Float, slice: Int) {
                val around = 2 * PI * slice / SLICES
                val x = cos(around).toFloat()
                val z = sin(around).toFloat()
                out[i++] = x * r
                out[i++] = y
                out[i++] = z * r
                out[i++] = x * normalOut
                out[i++] = normalUp
                out[i++] = z * normalOut
            }
            for (slice in 0 until SLICES) {
                vertex(r0, y0, slice)
                vertex(r1, y1, slice)
                vertex(r1, y1, slice + 1)
                vertex(r0, y0, slice)
                vertex(r1, y1, slice + 1)
                vertex(r0, y0, slice + 1)
            }
        }
        return out
    }
}
