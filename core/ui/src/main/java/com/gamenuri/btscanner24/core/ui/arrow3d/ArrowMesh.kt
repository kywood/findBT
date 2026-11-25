package com.gamenuri.btscanner24.core.ui.arrow3d

import kotlin.math.sqrt

/**
 * The find arrow built from the design's numbers (#76) instead of a modelled file, so its
 * proportions are exact. Units are the floor ring's radius R: 1.5 R long, head 0.78 R wide and
 * 0.62 R long, shaft 0.30 R wide, 0.14 R thick, edges bevelled by 0.03 R. It lies in the floor
 * plane, centred on the origin, pointing at −Z. Flat-shaded: every triangle carries its own normal.
 */
internal object ArrowMesh {
    const val LENGTH = 1.5f
    const val HEAD_WIDTH = 0.78f
    const val HEAD_LENGTH = 0.62f
    const val SHAFT_WIDTH = 0.30f
    const val THICKNESS = 0.14f
    const val BEVEL = 0.03f

    /** Interleaved position + normal, [ObjMesh.FLOATS_PER_VERTEX] floats per vertex, as triangles. */
    fun build(): FloatArray {
        val tip = -LENGTH / 2
        val neck = tip + HEAD_LENGTH
        val tail = LENGTH / 2
        val head = HEAD_WIDTH / 2
        val shaft = SHAFT_WIDTH / 2
        // Outline as (x, z) pairs: tip, then round the right side to the tail and back up the left.
        val outline = floatArrayOf(
            0f, tip,
            head, neck,
            shaft, neck,
            shaft, tail,
            -shaft, tail,
            -shaft, neck,
            -head, neck,
        )
        val count = outline.size / 2
        val normals = edgeNormals(outline)
        val inner = inset(outline, normals)
        val top = THICKNESS / 2
        val wall = top - BEVEL
        val out = ArrayList<Float>(TRIANGLES * 3 * ObjMesh.FLOATS_PER_VERTEX)

        fun point(ring: FloatArray, i: Int, y: Float) = floatArrayOf(ring[i * 2], y, ring[i * 2 + 1])
        for (side in floatArrayOf(1f, -1f)) {
            // Top and bottom faces (the inset outline).
            for (face in FACES) {
                triangle(
                    out,
                    point(inner, face[0], top * side),
                    point(inner, face[1], top * side),
                    point(inner, face[2], top * side),
                    floatArrayOf(0f, side, 0f),
                )
            }
            // Bevel between that face and the wall.
            for (i in 0 until count) {
                val j = (i + 1) % count
                val towards = floatArrayOf(normals[i * 2], side, normals[i * 2 + 1])
                val a = point(inner, i, top * side)
                val b = point(inner, j, top * side)
                val c = point(outline, j, wall * side)
                val d = point(outline, i, wall * side)
                triangle(out, a, b, c, towards)
                triangle(out, a, c, d, towards)
            }
        }
        // Walls.
        for (i in 0 until count) {
            val j = (i + 1) % count
            val towards = floatArrayOf(normals[i * 2], 0f, normals[i * 2 + 1])
            val a = point(outline, i, wall)
            val b = point(outline, j, wall)
            val c = point(outline, j, -wall)
            val d = point(outline, i, -wall)
            triangle(out, a, b, c, towards)
            triangle(out, a, c, d, towards)
        }
        return out.toFloatArray()
    }

    /** Unit normal of each outline edge i → i+1, pointing out of the shape, as (x, z) pairs. */
    private fun edgeNormals(outline: FloatArray): FloatArray {
        val count = outline.size / 2
        var doubleArea = 0f
        for (i in 0 until count) {
            val j = (i + 1) % count
            doubleArea += outline[i * 2] * outline[j * 2 + 1] - outline[j * 2] * outline[i * 2 + 1]
        }
        val sign = if (doubleArea > 0f) 1f else -1f
        val normals = FloatArray(outline.size)
        for (i in 0 until count) {
            val j = (i + 1) % count
            val dx = outline[j * 2] - outline[i * 2]
            val dz = outline[j * 2 + 1] - outline[i * 2 + 1]
            val length = sqrt(dx * dx + dz * dz)
            normals[i * 2] = sign * dz / length
            normals[i * 2 + 1] = -sign * dx / length
        }
        return normals
    }

    /** The outline moved in by [BEVEL] on every edge (mitred corners). */
    private fun inset(outline: FloatArray, normals: FloatArray): FloatArray {
        val count = outline.size / 2
        val inner = FloatArray(outline.size)
        for (i in 0 until count) {
            val before = (i + count - 1) % count
            val ax = normals[before * 2]
            val az = normals[before * 2 + 1]
            val bx = normals[i * 2]
            val bz = normals[i * 2 + 1]
            val miter = BEVEL / (1f + ax * bx + az * bz)
            inner[i * 2] = outline[i * 2] - (ax + bx) * miter
            inner[i * 2 + 1] = outline[i * 2 + 1] - (az + bz) * miter
        }
        return inner
    }

    /** Adds one triangle with its face normal, turned to the side [towards] points at. */
    private fun triangle(out: ArrayList<Float>, a: FloatArray, b: FloatArray, c: FloatArray, towards: FloatArray) {
        val ux = b[0] - a[0]
        val uy = b[1] - a[1]
        val uz = b[2] - a[2]
        val vx = c[0] - a[0]
        val vy = c[1] - a[1]
        val vz = c[2] - a[2]
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val length = sqrt(nx * nx + ny * ny + nz * nz)
        val flip = if (nx * towards[0] + ny * towards[1] + nz * towards[2] < 0f) -1f else 1f
        nx *= flip / length
        ny *= flip / length
        nz *= flip / length
        for (corner in arrayOf(a, b, c)) {
            out += corner[0]
            out += corner[1]
            out += corner[2]
            out += nx
            out += ny
            out += nz
        }
    }

    /** The outline as triangles: a fan from the tip over the head, then the shaft. */
    private val FACES = arrayOf(
        intArrayOf(0, 1, 2),
        intArrayOf(0, 2, 5),
        intArrayOf(0, 5, 6),
        intArrayOf(2, 3, 4),
        intArrayOf(2, 4, 5),
    )

    /** 5 per face × 2, 14 per bevel × 2, 14 for the walls. */
    private const val TRIANGLES = 52
}
