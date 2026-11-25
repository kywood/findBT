package com.gamenuri.btscanner24.core.ui.arrow3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneCameraTest {
    @Test
    fun `usual hold shows the design view`() {
        assertEquals(SceneCamera.TYPICAL, SceneCamera.elevationFor(SceneCamera.TYPICAL_POSE), 0.001f)
    }

    @Test
    fun `usual hand angles never look top-down`() {
        for (pose in 30..60) {
            val camera = SceneCamera.elevationFor(pose.toFloat())
            assertTrue("pose $pose gives $camera", camera in 25f..45f)
        }
    }

    @Test
    fun `lying flat keeps depth`() {
        assertEquals(SceneCamera.FLAT, SceneCamera.elevationFor(90f), 0.001f)
        assertEquals(SceneCamera.FLAT, SceneCamera.elevationFor(120f), 0.001f)
        assertTrue(SceneCamera.FLAT < 65f)
    }

    @Test
    fun `upright is the lowest view and the camera only rises as the phone lies down`() {
        assertEquals(SceneCamera.UPRIGHT, SceneCamera.elevationFor(0f), 0.001f)
        assertEquals(SceneCamera.UPRIGHT, SceneCamera.elevationFor(-20f), 0.001f)
        var last = SceneCamera.elevationFor(0f)
        for (pose in 1..90) {
            val camera = SceneCamera.elevationFor(pose.toFloat())
            assertTrue(camera > last)
            last = camera
        }
    }
}
