package com.gamenuri.btscanner24.core.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/** Whether a device has the Immediate Alert service (0x1802). */
enum class AlertSupport { SUPPORTED, NOT_SUPPORTED, UNREACHABLE }

/** How a [ImmediateAlert.ring] call ended. */
enum class RingResult { DONE, NOT_SUPPORTED, FAILED }

/**
 * "Play sound" for trackers with the Bluetooth SIG Immediate Alert service (#28). The alert level
 * resets to "No Alert" when the link drops, so [ring] keeps the connection open while it rings.
 * Nothing about the device leaves the phone. Open for the debug demo mode.
 */
open class ImmediateAlert(context: Context) {

    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter

    /** Android 12+ needs BLUETOOTH_CONNECT to connect; older versions only the install-time BLUETOOTH. */
    open fun hasPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

    /** Connects briefly to look for the service, then disconnects so the device keeps advertising. */
    open suspend fun probe(address: String): AlertSupport {
        val session = open(address) ?: return AlertSupport.UNREACHABLE
        return try {
            when {
                !session.connectAndDiscover() -> AlertSupport.UNREACHABLE
                session.alertLevel() == null -> AlertSupport.NOT_SUPPORTED
                else -> AlertSupport.SUPPORTED
            }
        } finally {
            session.close()
        }
    }

    /**
     * Rings for [durationMs] (or until the caller is cancelled), then writes "No Alert" and
     * disconnects. [onStarted] runs once the "High Alert" write went out.
     */
    open suspend fun ring(address: String, durationMs: Long, onStarted: () -> Unit): RingResult {
        val session = open(address) ?: return RingResult.FAILED
        try {
            if (!session.connectAndDiscover()) return RingResult.FAILED
            val level = session.alertLevel() ?: return RingResult.NOT_SUPPORTED
            if (!session.write(level, HIGH_ALERT)) return RingResult.FAILED
            onStarted()
            delay(durationMs)
            return RingResult.DONE
        } finally {
            // Also runs when the user taps "Stop sound" (cancellation): silence it before hanging up.
            withContext(NonCancellable) {
                session.alertLevel()?.let { session.write(it, NO_ALERT) }
                session.close()
            }
        }
    }

    @SuppressLint("MissingPermission") // checked by hasPermission()
    private fun open(address: String): GattSession? {
        if (!hasPermission()) return null
        val device: BluetoothDevice = try {
            adapter?.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            null
        } ?: return null
        val session = GattSession()
        session.gatt = try {
            device.connectGatt(appContext, false, session.callback, BluetoothDevice.TRANSPORT_LE)
        } catch (_: SecurityException) {
            null
        } ?: return null
        return session
    }

    /** One GATT connection; callbacks arrive on a binder thread and are handed over through a channel. */
    @SuppressLint("MissingPermission") // only created by open(), after hasPermission()
    private class GattSession {
        var gatt: BluetoothGatt? = null
        private val events = Channel<Event>(Channel.UNLIMITED)

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                val ok = status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED
                events.trySend(if (ok) Event.Connected else Event.Disconnected)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                events.trySend(Event.Discovered(status == BluetoothGatt.GATT_SUCCESS))
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                events.trySend(Event.Written(status == BluetoothGatt.GATT_SUCCESS))
            }
        }

        suspend fun connectAndDiscover(): Boolean = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            if (events.receive() != Event.Connected) return@withTimeoutOrNull false
            if (gatt?.discoverServices() != true) return@withTimeoutOrNull false
            (events.receive() as? Event.Discovered)?.ok == true
        } == true

        fun alertLevel(): BluetoothGattCharacteristic? =
            gatt?.getService(IMMEDIATE_ALERT_SERVICE)?.getCharacteristic(ALERT_LEVEL)

        suspend fun write(characteristic: BluetoothGattCharacteristic, level: Byte): Boolean {
            val g = gatt ?: return false
            // The spec makes Alert Level write-without-response; a few clones only accept a normal write.
            val type = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            val value = byteArrayOf(level)
            val queued = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeCharacteristic(characteristic, value, type) == BluetoothGatt.GATT_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.writeType = type
                    @Suppress("DEPRECATION")
                    characteristic.value = value
                    @Suppress("DEPRECATION")
                    g.writeCharacteristic(characteristic)
                }
            } catch (_: SecurityException) {
                false
            }
            if (!queued) return false
            // Android reports even a no-response write once it leaves the stack; don't hang if it doesn't.
            val written = withTimeoutOrNull(WRITE_TIMEOUT_MS) {
                var event = events.receive()
                while (event !is Event.Written && event != Event.Disconnected) event = events.receive()
                event
            }
            return when (written) {
                is Event.Written -> written.ok
                Event.Disconnected -> false
                else -> type == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
        }

        fun close() {
            try {
                gatt?.disconnect()
                gatt?.close()
            } catch (_: SecurityException) {
                // Permission revoked mid-session; the system drops the link anyway.
            }
            gatt = null
            events.close()
        }

        sealed interface Event {
            data object Connected : Event
            data object Disconnected : Event
            data class Discovered(val ok: Boolean) : Event
            data class Written(val ok: Boolean) : Event
        }
    }

    companion object {
        /** 16-bit UUID advertised by devices that can ring; also used to show "Play sound" before probing. */
        const val SERVICE_UUID_16 = 0x1802

        private val IMMEDIATE_ALERT_SERVICE: UUID = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb")
        private val ALERT_LEVEL: UUID = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb")
        private const val NO_ALERT: Byte = 0
        private const val HIGH_ALERT: Byte = 2
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val WRITE_TIMEOUT_MS = 2_000L
    }
}
