package com.gamenuri.btscanner24.core.ui.arrow3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A round slab with bevelled edges, lying in the floor plane and centred on the origin: the mint
 * "right here" puck and the body of the "Found it" medal (#76). Units are the floor ring's radius R.
 */
internal object DiscMesh {
    private const val SLICES = 48

    /** Interleaved position + normal, [ObjMesh.FLOATS_PER_VERTEX] floats per vertex, as triangles. */
    fun build(radius: Float, thickness: Float, bevel: Float): FloatArray {
        val half = thickness / 2
        // The outline from the middle of the top face, round the edge, to the middle of the bottom.
        val profile = floatArrayOf(
            0f, half,
            radius - bevel, half,
            radius, half - bevel,
            radius, bevel - half,
            radius - bevel, -half,
            0f, -half,
        )
        val bands = profile.size / 2 - 1
        val out = FloatArray(bands * SLICES * 2 * 3 * ObjMesh.FLOATS_PER_VERTEX)
        var i = 0
        for (band in 0 until bands) {
            val r0 = profile[band * 2]
            val y0 = profile[band * 2 + 1]
            val r1 = profile[band * 2 + 2]
            val y1 = profile[band * 2 + 3]
            // Outward normal of this stretch of the outline: its direction turned a quarter.
            val length = sqrt((r1 - r0) * (r1 - r0) + (y1 - y0) * (y1 - y0))
            val normalOut = (y0 - y1) / length
            val normalUp = (r1 - r0) / length
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
