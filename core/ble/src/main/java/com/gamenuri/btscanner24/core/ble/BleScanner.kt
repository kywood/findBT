package com.gamenuri.btscanner24.core.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.ParcelUuid
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.gamenuri.btscanner24.core.model.TrackerSignal
import com.gamenuri.btscanner24.core.model.TrackerSignals
import java.util.Locale
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** One device a filtered scan listens for: by its address, by the name it advertises, or both. */
data class WatchTarget(val address: String?, val name: String?)

/**
 * Wraps the platform BLE scanner as a cold [Flow]. Collecting starts a scan,
 * cancelling the collection stops it, so the scan lifetime always follows the collector.
 */
// Open so the debug demo mode (#47) can replace real scanning with fixed devices.
open class BleScanner(context: Context) {

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter

    /**
     * Start timestamps (elapsedRealtime) used to stay under the platform's 5-starts-per-30s limit.
     * Scans start from more than one thread, so every access is synchronized on the list itself.
     */
    private val recentStarts = ArrayDeque<Long>()

    open val isSupported: Boolean
        get() = adapter != null &&
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    open val isEnabled: Boolean
        get() = adapter?.isEnabled == true

    /**
     * Android 11 and below deliver no scan results while the system Location setting is off, even
     * with every permission granted (Android 12+ uses BLUETOOTH_SCAN with neverForLocation instead).
     */
    open val isLocationOffBlockingScan: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            appContext.getSystemService(LocationManager::class.java)
                ?.let { !LocationManagerCompat.isLocationEnabled(it) } == true

    open fun hasScanPermission(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Milliseconds to wait before a new scan start is allowed, or 0 if it can start now. */
    open fun startCooldownMillis(now: Long = SystemClock.elapsedRealtime()): Long = synchronized(recentStarts) {
        while (recentStarts.isNotEmpty() && now - recentStarts.first() > START_WINDOW_MS) {
            recentStarts.removeFirst()
        }
        if (recentStarts.size < MAX_STARTS_PER_WINDOW) {
            0
        } else {
            START_WINDOW_MS - (now - recentStarts.first()) + 500
        }
    }

    open fun scan(): Flow<ScanEvent> = results(filters = null, mode = ScanSettings.SCAN_MODE_LOW_LATENCY)

    /**
     * A low-power scan that only reports [targets] (#163) and, with [trackers], the tracker formats
     * of [TrackerSignals] (#79). Unlike [scan] it keeps delivering with the screen off, which Android
     * allows for filtered scans only. Nothing to listen for, no scan: the flow just stays open.
     */
    open fun watch(targets: Collection<WatchTarget>, trackers: Boolean = false): Flow<ScanEvent> {
        val own = targets.flatMap { target ->
            listOfNotNull(
                target.address?.takeIf(BluetoothAdapter::checkBluetoothAddress)
                    ?.let { ScanFilter.Builder().setDeviceAddress(it).build() },
                target.name?.takeIf { it.isNotBlank() }?.let { ScanFilter.Builder().setDeviceName(it).build() },
            )
        }
        val filters = if (trackers) own + TrackerSignals.all.map(::filterFor) else own
        if (filters.isEmpty()) return callbackFlow { awaitClose { } }
        return results(filters, ScanSettings.SCAN_MODE_LOW_POWER)
    }

    private fun filterFor(signal: TrackerSignal): ScanFilter {
        val builder = ScanFilter.Builder()
        val companyId = signal.companyId
        val serviceData = signal.serviceData16
        val service = signal.serviceUuid16
        when {
            companyId != null -> builder.setManufacturerData(companyId, signal.prefix, signal.mask)
            serviceData != null -> builder.setServiceData(parcelUuid16(serviceData), signal.prefix, signal.mask)
            service != null -> builder.setServiceUuid(parcelUuid16(service))
        }
        return builder.build()
    }

    @SuppressLint("MissingPermission") // checked by hasScanPermission()
    private fun results(filters: List<ScanFilter>?, mode: Int): Flow<ScanEvent> = callbackFlow {
        val failure = when {
            !isSupported -> ScanFailure.NOT_SUPPORTED
            !hasScanPermission() -> ScanFailure.PERMISSION_MISSING
            !isEnabled -> ScanFailure.BLUETOOTH_OFF
            isLocationOffBlockingScan -> ScanFailure.LOCATION_OFF
            else -> null
        }
        val scanner = adapter?.bluetoothLeScanner
        if (failure != null || scanner == null) {
            trySend(ScanEvent.Failed(failure ?: ScanFailure.BLUETOOTH_OFF))
            close()
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(ScanEvent.Result(result))
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { trySend(ScanEvent.Result(it)) }
            }

            override fun onScanFailed(errorCode: Int) {
                val reason = when (errorCode) {
                    SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> ScanFailure.TOO_FREQUENT
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> ScanFailure.NOT_SUPPORTED
                    else -> ScanFailure.INTERNAL
                }
                trySend(ScanEvent.Failed(reason))
                close()
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(mode)
            .build()

        try {
            synchronized(recentStarts) { recentStarts.addLast(SystemClock.elapsedRealtime()) }
            scanner.startScan(filters, settings, callback)
        } catch (e: SecurityException) {
            trySend(ScanEvent.Failed(ScanFailure.PERMISSION_MISSING))
            close()
        } catch (e: IllegalStateException) {
            // Thrown when the adapter is turned off between the check and startScan.
            trySend(ScanEvent.Failed(ScanFailure.BLUETOOTH_OFF))
            close()
        }

        awaitClose {
            try {
                if (adapter?.isEnabled == true) scanner.stopScan(callback)
            } catch (_: SecurityException) {
            } catch (_: IllegalStateException) {
            }
        }
    }

    companion object {
        /** A 16-bit Bluetooth SIG UUID in its full form. */
        fun parcelUuid16(uuid16: Int): ParcelUuid =
            ParcelUuid.fromString(String.format(Locale.US, "0000%04x-0000-1000-8000-00805f9b34fb", uuid16))

        /** Whether [result] is one of the tracker formats unknown-tracker detection listens for (#79). */
        fun isTracker(result: ScanResult): Boolean {
            val record = result.scanRecord ?: return false
            return TrackerSignals.matches(
                makerData = { record.getManufacturerSpecificData(it) },
                serviceData = { record.getServiceData(parcelUuid16(it)) },
                serviceUuids16 = TrackerSignals.all.mapNotNull { it.serviceUuid16 }
                    .filter { record.serviceUuids?.contains(parcelUuid16(it)) == true },
            )
        }

        private const val START_WINDOW_MS = 30_000L
        private const val MAX_STARTS_PER_WINDOW = 5

        fun requiredPermissions(): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}
