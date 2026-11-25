package com.gamenuri.btscanner24.core.ui.arrow3d

import com.gamenuri.btscanner24.core.model.DeviceCategory
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceModelTest {
    @Test
    fun `every kind of device has a model and kinds without their own get the plain one`() {
        for (category in DeviceCategory.entries) assertNotNull(DeviceModel.of(category))
        assertEquals(DeviceModel.EARBUDS, DeviceModel.of(DeviceCategory.HEARING_AID))
        assertEquals(DeviceModel.LAPTOP, DeviceModel.of(DeviceCategory.COMPUTER))
        assertEquals(DeviceModel.OTHER, DeviceModel.of(DeviceCategory.UNKNOWN))
        assertEquals(DeviceModel.OTHER, DeviceModel.of(DeviceCategory.GAMEPAD))
    }

    @Test
    fun `baked models stand on the floor and are one unit long`() {
        // Unit tests run in the module folder.
        val folder = File("src/main/assets/models")
        for (model in DeviceModel.entries) {
            val mesh = DeviceModel.parse(File(folder, "${model.asset}.mesh").readBytes())
            assertNotNull(model.name, mesh)
            val stride = DeviceModel.FLOATS_PER_VERTEX
            val low = FloatArray(3) { Float.MAX_VALUE }
            val high = FloatArray(3) { -Float.MAX_VALUE }
            for (vertex in 0 until mesh!!.size / stride) {
                for (axis in 0 until 3) {
                    low[axis] = minOf(low[axis], mesh[vertex * stride + axis])
                    high[axis] = maxOf(high[axis], mesh[vertex * stride + axis])
                }
                for (channel in 6 until 9) assertTrue(mesh[vertex * stride + channel] in 0f..1f)
            }
            assertEquals(model.name, 0f, low[1], 0.0001f)
            assertEquals(model.name, 1f, (0 until 3).maxOf { high[it] - low[it] }, 0.0001f)
        }
    }

    @Test
    fun `a broken file is not a model`() {
        assertNull(DeviceModel.parse(ByteArray(2)))
        val short = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(3).putFloat(1f).array()
        assertNull(DeviceModel.parse(short))
    }
}
