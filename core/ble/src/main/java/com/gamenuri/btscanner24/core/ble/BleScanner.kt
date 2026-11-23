package com.gamenuri.btscanner24.core.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Wraps the platform BLE scanner as a cold [Flow]. Collecting starts a scan,
 * cancelling the collection stops it, so the scan lifetime always follows the collector.
 */
class BleScanner(context: Context) {

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter

    /** Start timestamps (elapsedRealtime) used to stay under the platform's 5-starts-per-30s limit. */
    private val recentStarts = ArrayDeque<Long>()

    val isSupported: Boolean
        get() = adapter != null &&
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    val isEnabled: Boolean
        get() = adapter?.isEnabled == true

    fun hasScanPermission(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Milliseconds to wait before a new scan start is allowed, or 0 if it can start now. */
    fun startCooldownMillis(now: Long = SystemClock.elapsedRealtime()): Long {
        while (recentStarts.isNotEmpty() && now - recentStarts.first() > START_WINDOW_MS) {
            recentStarts.removeFirst()
        }
        if (recentStarts.size < MAX_STARTS_PER_WINDOW) return 0
        return START_WINDOW_MS - (now - recentStarts.first()) + 500
    }

    @SuppressLint("MissingPermission") // checked by hasScanPermission()
    fun scan(): Flow<ScanEvent> = callbackFlow {
        val failure = when {
            !isSupported -> ScanFailure.NOT_SUPPORTED
            !hasScanPermission() -> ScanFailure.PERMISSION_MISSING
            !isEnabled -> ScanFailure.BLUETOOTH_OFF
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
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            recentStarts.addLast(SystemClock.elapsedRealtime())
            scanner.startScan(null, settings, callback)
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
        private const val START_WINDOW_MS = 30_000L
        private const val MAX_STARTS_PER_WINDOW = 5

        fun requiredPermissions(): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}
