package com.gamenuri.btscanner24.core.ui.arrow3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A smooth ball centred on the origin: the device marker that stands on the far step of the find
 * floor (design find-flow-3d v1.2, #132). Units are the floor ring's radius R.
 */
internal object BallMesh {
    private const val SLICES = 32
    private const val STACKS = 16

    /** Interleaved position + normal, [ObjMesh.FLOATS_PER_VERTEX] floats per vertex, as triangles. */
    fun build(radius: Float): FloatArray {
        val out = FloatArray(STACKS * SLICES * 2 * 3 * ObjMesh.FLOATS_PER_VERTEX)
        var i = 0
        fun vertex(stack: Int, slice: Int) {
            // From the top (stack 0) down to the bottom; on a ball the normal is the direction itself.
            val down = PI * stack / STACKS
            val around = 2 * PI * slice / SLICES
            val x = (sin(down) * cos(around)).toFloat()
            val y = cos(down).toFloat()
            val z = (sin(down) * sin(around)).toFloat()
            out[i++] = x * radius
            out[i++] = y * radius
            out[i++] = z * radius
            out[i++] = x
            out[i++] = y
            out[i++] = z
        }
        for (stack in 0 until STACKS) {
            for (slice in 0 until SLICES) {
                vertex(stack, slice)
                vertex(stack + 1, slice)
                vertex(stack + 1, slice + 1)
                vertex(stack, slice)
                vertex(stack + 1, slice + 1)
                vertex(stack, slice + 1)
            }
        }
        return out
    }
}
