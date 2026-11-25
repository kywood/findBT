package com.gamenuri.btscanner24.core.ui.arrow3d

import android.content.Context
import com.gamenuri.btscanner24.core.model.DeviceCategory
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

/**
 * The 3D model that stands for a kind of device in the find scene (design find-flow-3d v1.3, #133)
 * and on the map (#86). The files are baked from the designer's glTF by
 * `scripts/bake-device-meshes.py`: longest side 1, centred, standing on y = 0.
 */
enum class DeviceModel(internal val asset: String) {
    EARBUDS("nearo_earbuds"),
    HEADPHONES("nearo_headphones"),
    SPEAKER("nearo_speaker"),
    WATCH("nearo_watch"),
    BAND("nearo_band"),
    TRACKER("nearo_tracker"),
    PHONE("nearo_phone"),
    TABLET("nearo_tablet"),
    LAPTOP("nearo_laptop"),
    KEYBOARD("nearo_keyboard"),
    MOUSE("nearo_mouse"),
    TV("nearo_tv"),
    CAR("nearo_car"),

    /** A plain Bluetooth device, for every kind without a model of its own. */
    OTHER("nearo_other"),
    ;

    companion object {
        /** Floats per vertex in a baked model: position, normal, colour. */
        internal const val FLOATS_PER_VERTEX = 9
        private const val HEADER_BYTES = 4
        private val cache = ConcurrentHashMap<DeviceModel, FloatArray>()

        /** Same groups as the list icons; a kind without its own model gets [OTHER]. */
        fun of(category: DeviceCategory): DeviceModel = when (category) {
            DeviceCategory.EARBUDS, DeviceCategory.HEARING_AID -> EARBUDS
            DeviceCategory.HEADPHONES -> HEADPHONES
            DeviceCategory.SPEAKER -> SPEAKER
            DeviceCategory.WATCH -> WATCH
            DeviceCategory.FITNESS_BAND -> BAND
            DeviceCategory.TRACKER -> TRACKER
            DeviceCategory.PHONE -> PHONE
            DeviceCategory.TABLET -> TABLET
            DeviceCategory.COMPUTER -> LAPTOP
            DeviceCategory.KEYBOARD -> KEYBOARD
            DeviceCategory.MOUSE -> MOUSE
            DeviceCategory.TV -> TV
            DeviceCategory.CAR -> CAR
            DeviceCategory.GAMEPAD, DeviceCategory.HEALTH, DeviceCategory.SENSOR, DeviceCategory.UNKNOWN -> OTHER
        }

        /**
         * The model's triangles, [FLOATS_PER_VERTEX] floats per vertex, or null when the file cannot
         * be read (the scene then keeps its plain marker). Reads a file: call off the main thread.
         */
        internal fun load(context: Context, model: DeviceModel): FloatArray? {
            cache[model]?.let { return it }
            val vertices = try {
                context.assets.open("models/${model.asset}.mesh").use { parse(it.readBytes()) }
            } catch (_: IOException) {
                null
            }
            return vertices?.also { cache[model] = it }
        }

        /** Little-endian: an int vertex count, then the floats. Null when the size does not add up. */
        internal fun parse(bytes: ByteArray): FloatArray? {
            if (bytes.size < HEADER_BYTES) return null
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val count = buffer.int
            val floats = count * FLOATS_PER_VERTEX
            if (count <= 0 || count % 3 != 0 || bytes.size != HEADER_BYTES + floats * Float.SIZE_BYTES) return null
            return FloatArray(floats).also { buffer.asFloatBuffer().get(it) }
        }
    }
}
