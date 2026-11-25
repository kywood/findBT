package com.gamenuri.btscanner24.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Heading from the rotation vector sensor plus walking state from linear acceleration.
 *
 * The find screen only needs headings that are consistent during one search, so when there is no
 * magnetometer the game rotation vector (gyro + accelerometer, no north) is used instead.
 */
class MotionSensor(context: Context) {

    enum class Source { ROTATION_VECTOR, GAME, NONE }

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val rotationSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val accelSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    val source: Source = when (rotationSensor?.type) {
        Sensor.TYPE_ROTATION_VECTOR -> Source.ROTATION_VECTOR
        Sensor.TYPE_GAME_ROTATION_VECTOR -> Source.GAME
        else -> Source.NONE
    }

    /** Emits on every rotation update (about 50 Hz); collectors should sample or conflate. */
    fun samples(): Flow<MotionSample> = callbackFlow {
        val manager = sensorManager
        val rotation = rotationSensor
        if (manager == null || rotation == null) {
            close()
            return@callbackFlow
        }
        val walkingDetector = WalkingDetector()
        val matrix = FloatArray(9)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = event.timestamp / 1_000_000
                when (event.sensor.type) {
                    Sensor.TYPE_LINEAR_ACCELERATION -> {
                        val (x, y, z) = event.values
                        walkingDetector.add(now, sqrt((x * x + y * y + z * z).toDouble()))
                    }
                    else -> {
                        SensorManager.getRotationMatrixFromVector(matrix, event.values)
                        // Stable whether the phone lies flat or is held upright (#76).
                        val heading = DevicePose.fromRotationMatrix(matrix).headingDegrees
                        trySend(MotionSample(now, heading, walkingDetector.walking))
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        manager.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_GAME)
        accelSensor?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        awaitClose { manager.unregisterListener(listener) }
    }.conflate()
}
