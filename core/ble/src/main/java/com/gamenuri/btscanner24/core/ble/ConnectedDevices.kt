package com.gamenuri.btscanner24.core.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.gamenuri.btscanner24.core.model.PairedDevice
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The user's own devices that are connected to this phone right now (#119). Connected earbuds and
 * watches usually stop advertising, so the scan alone shows them as "not seen" while they are in
 * someone's ears. Open for the debug demo mode.
 */
open class ConnectedDevices(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(BluetoothManager::class.java)
    private val proxies = ConcurrentHashMap<Int, BluetoothProfile>()
    private var proxiesAsked = false

    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            proxies[profile] = proxy
        }

        override fun onServiceDisconnected(profile: Int) {
            proxies.remove(profile)
        }
    }

    /** Android 12+ needs BLUETOOTH_CONNECT to read connections; older versions only BLUETOOTH. */
    open fun hasPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

    /**
     * Addresses connected for audio (A2DP, hands-free) or over GATT. The audio profiles answer a
     * moment after the first call, so the first result can miss them; callers ask again every few seconds.
     */
    @SuppressLint("MissingPermission") // checked by hasPermission()
    open fun addresses(): Set<String> {
        val adapter = manager?.adapter ?: return emptySet()
        if (!hasPermission() || !adapter.isEnabled) return emptySet()
        val found = HashSet<String>()
        try {
            if (!proxiesAsked) {
                proxiesAsked = true
                AUDIO_PROFILES.forEach { adapter.getProfileProxy(appContext, listener, it) }
            }
            proxies.values.forEach { proxy -> proxy.connectedDevices.mapTo(found) { it.address } }
            manager.getConnectedDevices(BluetoothProfile.GATT).mapTo(found) { it.address }
        } catch (_: SecurityException) {
            return emptySet()
        }
        return found
    }

    /**
     * Signal strength of the link to a connected device, about once a second. Opens a GATT client
     * on the link and reads its RSSI; ends when that cannot be done (many earbuds only connect over
     * classic Bluetooth, which has no public RSSI) or when the device disconnects. Only ever called
     * for the user's own paired devices (#63).
     */
    @SuppressLint("MissingPermission") // checked by hasPermission()
    open fun readings(address: String): Flow<Int> = callbackFlow {
        var lastReadingMs = SystemClock.elapsedRealtime()
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_DISCONNECTED) close()
            }

            override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) return
                lastReadingMs = SystemClock.elapsedRealtime()
                trySend(rssi)
            }
        }
        val gatt = try {
            manager?.adapter?.takeIf { hasPermission() }
                ?.getRemoteDevice(address)
                ?.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        if (gatt == null) {
            close()
        } else {
            launch {
                while (isActive) {
                    delay(READ_INTERVAL_MS)
                    // Never connected, or the link went quiet: give up instead of holding a dead client.
                    if (SystemClock.elapsedRealtime() - lastReadingMs > SILENCE_LIMIT_MS) {
                        close()
                        break
                    }
                    try {
                        gatt.readRemoteRssi()
                    } catch (_: SecurityException) {
                        close()
                    }
                }
            }
        }
        awaitClose {
            try {
                gatt?.disconnect()
                gatt?.close()
            } catch (_: SecurityException) {
                // Permission was taken away meanwhile; nothing left to release.
            }
        }
    }

    private companion object {
        val AUDIO_PROFILES = listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
        const val READ_INTERVAL_MS = 1_000L
        const val SILENCE_LIMIT_MS = 12_000L
    }
}

/**
 * Keeps [onChange] told which of the [paired] devices are connected and, where the link's signal
 * can be read, feeds it to [repository] like scan results, so the list and find mode keep working
 * for a connected device (#119). Runs until cancelled; nothing is read while no screen needs it.
 */
suspend fun ConnectedDevices.watch(
    repository: DeviceRepository,
    paired: () -> List<PairedDevice>,
    onChange: (Set<String>) -> Unit,
): Unit = coroutineScope {
    val readers = HashMap<String, Job>()
    val lastTryMs = HashMap<String, Long>()
    while (isActive) {
        val own = paired().associateBy { it.address }
        val connected = addresses().filterTo(HashSet()) { it in own }
        onChange(connected)
        readers.keys.filter { it !in connected }.forEach { readers.remove(it)?.cancel() }
        val now = SystemClock.elapsedRealtime()
        for (address in connected) {
            if (readers[address]?.isActive == true) continue
            val triedAt = lastTryMs[address]
            if (triedAt != null && now - triedAt < READER_RETRY_MS) continue
            // Still advertising although connected: the scan already gives its signal.
            val heardAt = repository.devices.value[address]?.lastSeen
            if (heardAt != null && System.currentTimeMillis() - heardAt < ADVERTISING_FRESH_MS) continue
            lastTryMs[address] = now
            val device = own.getValue(address)
            readers[address] = launch { readings(address).collect { repository.onConnectionRssi(device, it) } }
        }
        delay(CONNECTED_POLL_MS)
    }
}

private const val CONNECTED_POLL_MS = 3_000L
private const val READER_RETRY_MS = 60_000L
private const val ADVERTISING_FRESH_MS = 10_000L
