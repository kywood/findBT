package com.gamenuri.btscanner24.core.ui.map3d

import com.gamenuri.btscanner24.core.ui.arrow3d.ObjMesh

/**
 * The plain solids of the map's "direction measured" beam (design bt-map-3d v2.1): a box and an
 * arrowhead. Interleaved position + normal, [ObjMesh.FLOATS_PER_VERTEX] floats per vertex, as triangles.
 */
internal object MapMeshes {
    private const val SIN_120 = 0.8660254f

    /** A cube of side 1 round the origin; scaled into the beam. */
    fun box(): FloatArray {
        val out = ArrayList<Float>()
        val h = 0.5f
        // Each face: its normal, then four corners going round it.
        face(out, floatArrayOf(0f, 1f, 0f), floatArrayOf(-h, h, -h, -h, h, h, h, h, h, h, h, -h))
        face(out, floatArrayOf(0f, -1f, 0f), floatArrayOf(-h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h))
        face(out, floatArrayOf(1f, 0f, 0f), floatArrayOf(h, -h, -h, h, h, -h, h, h, h, h, -h, h))
        face(out, floatArrayOf(-1f, 0f, 0f), floatArrayOf(-h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h))
        face(out, floatArrayOf(0f, 0f, 1f), floatArrayOf(-h, -h, h, h, -h, h, h, h, h, -h, h, h))
        face(out, floatArrayOf(0f, 0f, -1f), floatArrayOf(-h, -h, -h, -h, h, -h, h, h, -h, h, -h, -h))
        return out.toFloatArray()
    }

    /**
     * A flat three-sided arrowhead lying in the floor plane: corners 1 from the origin, its tip
     * straight ahead (−Z), 1 thick round y = 0.
     */
    fun arrowhead(): FloatArray {
        val out = ArrayList<Float>()
        val h = 0.5f
        val xs = floatArrayOf(0f, SIN_120, -SIN_120)
        val zs = floatArrayOf(-1f, 0.5f, 0.5f)
        val up = floatArrayOf(0f, 1f, 0f)
        val down = floatArrayOf(0f, -1f, 0f)
        triangle(out, up, floatArrayOf(xs[0], h, zs[0], xs[1], h, zs[1], xs[2], h, zs[2]))
        triangle(out, down, floatArrayOf(xs[0], -h, zs[0], xs[2], -h, zs[2], xs[1], -h, zs[1]))
        for (side in 0..2) {
            val next = (side + 1) % 3
            // Outward: away from the corner opposite this side.
            val other = (side + 2) % 3
            val corners = floatArrayOf(
                xs[side], -h, zs[side],
                xs[next], -h, zs[next],
                xs[next], h, zs[next],
                xs[side], h, zs[side],
            )
            face(out, floatArrayOf(-xs[other], 0f, -zs[other]), corners)
        }
        return out.toFloatArray()
    }

    /** Four [corners] (xyz each, going round) as two triangles with one [normal]. */
    private fun face(out: MutableList<Float>, normal: FloatArray, corners: FloatArray) {
        triangle(out, normal, corners.copyOfRange(0, 9))
        triangle(out, normal, corners.copyOfRange(0, 3) + corners.copyOfRange(6, 12))
    }

    /** Three [corners] (xyz each) with one [normal]. */
    private fun triangle(out: MutableList<Float>, normal: FloatArray, corners: FloatArray) {
        for (corner in 0..2) {
            for (axis in 0..2) out += corners[corner * 3 + axis]
            for (axis in 0..2) out += normal[axis]
        }
    }
}
