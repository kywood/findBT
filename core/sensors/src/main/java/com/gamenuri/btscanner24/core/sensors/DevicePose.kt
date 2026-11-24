package com.gamenuri.btscanner24.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.asin
import kotlin.math.atan2

/**
 * How the phone is held, for the AR-style find arrow (#76).
 *
 * @property headingDegrees where the phone points, 0..360 clockwise (same frame as [MotionSample]).
 * @property elevationDegrees how far the screen looks down at the floor: 90 lying flat face up, 0 upright.
 * @property rollDegrees sideways tilt; positive when the right edge is higher than the left.
 */
data class DevicePose(val headingDegrees: Double, val elevationDegrees: Double, val rollDegrees: Double) {
    companion object {
        /**
         * From a 3×3 rotation matrix of [SensorManager.getRotationMatrixFromVector] (device → world,
         * world X east, Y north, Z up).
         *
         * The heading is where "top of the phone minus screen normal" points on the ground. Lying
         * flat that is the top edge (the usual compass azimuth); held upright it is the back of the
         * phone. The plain azimuth of getOrientation() breaks down when the phone is upright.
         */
        fun fromRotationMatrix(r: FloatArray): DevicePose {
            val east = (r[1] - r[2]).toDouble()
            val north = (r[4] - r[5]).toDouble()
            val heading = Angles.normalize(Math.toDegrees(atan2(east, north)))
            val elevation = Math.toDegrees(asin(r[8].toDouble().coerceIn(-1.0, 1.0)))
            val roll = Math.toDegrees(asin(r[6].toDouble().coerceIn(-1.0, 1.0)))
            return DevicePose(heading, elevation, roll)
        }
    }
}

/**
 * Pose updates at game rate (about 50 Hz) for drawing only; nothing is stored or sent anywhere.
 * Uses the same sensor as [MotionSensor], so headings agree with the direction estimate.
 */
class PoseSensor(context: Context) {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val rotationSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    /** Calls [onPose] on the main thread until the returned handle is closed. */
    fun start(onPose: (DevicePose) -> Unit): AutoCloseable {
        val manager = sensorManager
        val sensor = rotationSensor
        if (manager == null || sensor == null) return AutoCloseable { }
        val matrix = FloatArray(9)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                onPose(DevicePose.fromRotationMatrix(matrix))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        return AutoCloseable { manager.unregisterListener(listener) }
    }
}
