package com.gamenuri.btscanner24.core.ui.arrow3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The "right here" marker of the 3D find scene (#76): a small ball that takes the arrow's place
 * above the floor once the device is within reach. Units are the floor ring's radius R, centred on
 * the origin. Smooth-shaded: every vertex carries the ball's own normal.
 */
internal object MarkerMesh {
    const val RADIUS = 0.17f
    private const val STACKS = 12
    private const val SLICES = 24

    /** Interleaved position + normal, [ObjMesh.FLOATS_PER_VERTEX] floats per vertex, as triangles. */
    fun build(): FloatArray {
        val out = FloatArray(STACKS * SLICES * 2 * 3 * ObjMesh.FLOATS_PER_VERTEX)
        var i = 0
        fun vertex(stack: Int, slice: Int) {
            val polar = PI * stack / STACKS
            val around = 2 * PI * slice / SLICES
            val x = (sin(polar) * cos(around)).toFloat()
            val y = cos(polar).toFloat()
            val z = (sin(polar) * sin(around)).toFloat()
            out[i++] = x * RADIUS
            out[i++] = y * RADIUS
            out[i++] = z * RADIUS
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
