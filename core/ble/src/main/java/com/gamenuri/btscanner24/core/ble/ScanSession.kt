package com.gamenuri.btscanner24.core.ble

import android.bluetooth.le.ScanResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The one scan every screen shares (#177). Whoever needs scan results holds the session while it is
 * on screen; the platform scan runs while at least one holder is left and its results go into the
 * device list once, however many screens are listening. Before this each screen ran its own scan,
 * and two that overlapped fed every advertisement into the distance smoothing twice.
 */
interface ScanSession {
    /** Why the scan cannot run (permission, Bluetooth or Location off, no BLE); null while it runs or is not held. */
    val failure: StateFlow<ScanFailure?>

    /** Whether the app may scan at all right now; without it [acquire] only reports the failure. */
    val hasPermission: Boolean

    /** A scan is running right now. */
    val isScanning: Boolean

    /**
     * Keeps the scan running until the returned handle is closed; closing twice is harmless. If the
     * scan had stopped on a failure, it is started again.
     */
    fun acquire(): AutoCloseable

    /** Starts the scan again after a failure, if someone still holds it: the user's "Try again". */
    fun retry()
}

/**
 * @param scan the scan to collect while held: [BleScanner.scanContinuously], which already waits out
 * Android's start limit and restarts a scan that died quietly.
 * @param onResult where results go: [DeviceRepository.onScanResult]. Called on [scope]'s dispatcher,
 * off the main thread, because parsing is too heavy for it (#34).
 */
class SharedScanSession(
    private val scan: () -> Flow<ScanEvent>,
    private val onResult: (ScanResult) -> Unit,
    private val permission: () -> Boolean,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ScanSession {

    constructor(scanner: BleScanner, repository: DeviceRepository) : this(
        scan = { scanner.scanContinuously() },
        onResult = repository::onScanResult,
        permission = scanner::hasScanPermission,
    )

    private val lock = Any()
    private var holders = 0
    private var job: Job? = null
    private val _failure = MutableStateFlow<ScanFailure?>(null)

    override val failure: StateFlow<ScanFailure?> = _failure.asStateFlow()

    override val hasPermission: Boolean get() = permission()

    override val isScanning: Boolean get() = synchronized(lock) { job?.isActive == true }

    override fun acquire(): AutoCloseable {
        synchronized(lock) {
            holders++
            if (job?.isActive != true) startLocked()
        }
        val closed = AtomicBoolean(false)
        return AutoCloseable { if (closed.compareAndSet(false, true)) release() }
    }

    override fun retry() {
        synchronized(lock) { if (holders > 0 && job?.isActive != true) startLocked() }
    }

    private fun release() {
        synchronized(lock) {
            holders--
            if (holders > 0) return
            job?.cancel()
            job = null
            _failure.value = null
        }
    }

    private fun startLocked() {
        _failure.value = null
        job = scope.launch {
            scan().collect { event ->
                when (event) {
                    is ScanEvent.Result -> {
                        if (_failure.value != null) _failure.value = null
                        onResult(event.result)
                    }
                    // The flow ends after a failure that needs the user; [retry] or the next holder starts it again.
                    is ScanEvent.Failed -> _failure.value = event.reason
                }
            }
        }
    }
}
