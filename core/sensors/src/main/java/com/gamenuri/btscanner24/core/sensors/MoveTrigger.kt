package com.gamenuri.btscanner24.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener

/**
 * Tells when the phone starts to move with its owner: walking, cycling, riding in a car (#163). It
 * is the platform's significant-motion sensor, which wakes the phone by itself, costs next to no
 * battery, needs no permission and reports nothing but "moved". Phones without it report
 * [isAvailable] false and never call back.
 */
class MoveTrigger(context: Context) {
    private val sensors = context.applicationContext.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
    private var listener: TriggerEventListener? = null

    val isAvailable: Boolean get() = sensor != null

    /** Calls [onMoved] each time the phone starts moving, until [stop]. Safe to call again. */
    fun start(onMoved: () -> Unit) {
        val motion = sensor ?: return
        stop()
        val next = object : TriggerEventListener() {
            override fun onTrigger(event: TriggerEvent?) {
                // The sensor fires once and switches itself off: ask again while still wanted.
                if (listener !== this) return
                onMoved()
                sensors?.requestTriggerSensor(this, motion)
            }
        }
        listener = next
        sensors?.requestTriggerSensor(next, motion)
    }

    fun stop() {
        val current = listener ?: return
        listener = null
        sensor?.let { sensors?.cancelTriggerSensor(current, it) }
    }
}
