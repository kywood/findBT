package com.gamenuri.btscanner24.core.sensors

import org.junit.Assert.assertEquals
import org.junit.Test

class DevicePoseTest {
    /** Row-major device → world matrix from the world directions of the phone's X, Y and Z axes. */
    private fun matrix(x: FloatArray, y: FloatArray, z: FloatArray) =
        floatArrayOf(x[0], y[0], z[0], x[1], y[1], z[1], x[2], y[2], z[2])

    private val east = floatArrayOf(1f, 0f, 0f)
    private val north = floatArrayOf(0f, 1f, 0f)
    private val south = floatArrayOf(0f, -1f, 0f)
    private val up = floatArrayOf(0f, 0f, 1f)

    @Test
    fun `flat on a table, top edge north`() {
        val pose = DevicePose.fromRotationMatrix(matrix(x = east, y = north, z = up))
        assertEquals(0.0, pose.headingDegrees, 0.01)
        assertEquals(90.0, pose.elevationDegrees, 0.01)
        assertEquals(0.0, pose.rollDegrees, 0.01)
    }

    @Test
    fun `flat, top edge east`() {
        val pose = DevicePose.fromRotationMatrix(matrix(x = south, y = east, z = up))
        assertEquals(90.0, pose.headingDegrees, 0.01)
    }

    @Test
    fun `upright, back of the phone north`() {
        // The screen faces the user in the south.
        val pose = DevicePose.fromRotationMatrix(matrix(x = east, y = up, z = south))
        assertEquals(0.0, pose.headingDegrees, 0.01)
        assertEquals(0.0, pose.elevationDegrees, 0.01)
    }

    @Test
    fun `upright, back of the phone west`() {
        val pose = DevicePose.fromRotationMatrix(matrix(x = north, y = up, z = east))
        assertEquals(270.0, pose.headingDegrees, 0.01)
    }

    @Test
    fun `tilting between flat and upright keeps the heading`() {
        val s = 0.70710677f
        // 45°: top edge points north and up, the screen normal up and south.
        val top = floatArrayOf(0f, s, s)
        val normal = floatArrayOf(0f, -s, s)
        val pose = DevicePose.fromRotationMatrix(matrix(x = east, y = top, z = normal))
        assertEquals(0.0, pose.headingDegrees, 0.01)
        assertEquals(45.0, pose.elevationDegrees, 0.01)
    }
}
