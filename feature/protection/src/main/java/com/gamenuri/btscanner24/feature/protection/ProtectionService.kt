package com.gamenuri.btscanner24.feature.protection

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.gamenuri.btscanner24.core.ble.BleDependencies
import com.gamenuri.btscanner24.core.ble.ScanEvent
import com.gamenuri.btscanner24.core.ble.ScanFailure
import com.gamenuri.btscanner24.core.ble.ScanWatch
import com.gamenuri.btscanner24.core.ble.scanContinuously
import com.gamenuri.btscanner24.core.data.DataDependenciesProvider
import com.gamenuri.btscanner24.core.data.LastSeenStore
import com.gamenuri.btscanner24.core.data.ProtectionRepository
import com.gamenuri.btscanner24.core.model.LastSeen
import com.gamenuri.btscanner24.core.model.LastSeenPolicy
import com.gamenuri.btscanner24.core.model.LeftBehindWatch
import com.gamenuri.btscanner24.core.model.ProtectionSettings
import com.gamenuri.btscanner24.core.model.SavedDevice
import com.gamenuri.btscanner24.core.sensors.MoveTrigger
import com.gamenuri.btscanner24.core.sensors.PhoneLocation
import com.gamenuri.btscanner24.core.ui.R as UiR
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Protection mode (#163): while the user has it on, listens for the devices they picked and tells
 * them when one may have been left behind (rules in [LeftBehindWatch]).
 *
 * - A foreground service with a notification that stays; started only from the open app.
 * - Listens with a filtered low-power scan, the only kind Android keeps delivering with the screen
 *   off, and checks once a minute with an alarm that wakes the phone, so it holds no wake lock.
 * - Devices connected to the phone count as present without being heard.
 * - Connects to nothing (#63) and sends nothing anywhere (#19).
 *
 * Everything here runs on the main thread.
 */
class ProtectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var ble: BleDependencies
    private lateinit var protection: ProtectionRepository
    private lateinit var lastSeen: LastSeenStore
    private lateinit var notifications: ProtectionNotifications
    private lateinit var moveTrigger: MoveTrigger
    private lateinit var location: PhoneLocation
    private lateinit var watch: LeftBehindWatch

    private var started = false
    private var settings = ProtectionSettings()
    private var devices: List<WatchedDevice> = emptyList()
    private var scanJob: Job? = null

    /** Why the scan cannot hear right now; null while it can. */
    private var blind: ScanFailure? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val address = intent.getStringExtra(Protection.EXTRA_ADDRESS)
            when (intent.action) {
                ProtectionNotifications.ACTION_TICK -> onTick()
                ProtectionNotifications.ACTION_STOP -> Protection.turnOff(this@ProtectionService)
                ProtectionNotifications.ACTION_MUTE_HOUR -> address?.let { mute(it, clock() + HOUR_MS) }
                ProtectionNotifications.ACTION_MUTE_TODAY -> address?.let { mute(it, endOfToday()) }
            }
        }
    }

    private val tick: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            this,
            0,
            Intent(ProtectionNotifications.ACTION_TICK).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ble = (application as BleDependencies.Provider).bleDependencies
        val data = application as DataDependenciesProvider
        protection = data.protection
        lastSeen = data.lastSeen
        notifications = ProtectionNotifications(this)
        moveTrigger = MoveTrigger(this)
        location = PhoneLocation(this)
        watch = LeftBehindWatch(motionKnown = moveTrigger.isAvailable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started && !begin(data = application as DataDependenciesProvider)) {
            // Android did not let it run (restarted in the background): say so instead of failing silently.
            CoroutineScope(Dispatchers.Default).launch { protection.setEnabled(false) }
            notifications.turnedOff(afterRestart = false)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun begin(data: DataDependenciesProvider): Boolean {
        val shown = foregroundTypes().any { type ->
            try {
                ServiceCompat.startForeground(this, ProtectionNotifications.STATUS_ID, status(), type)
                true
            } catch (_: RuntimeException) {
                // Not allowed from the background, or a permission the type needs is gone: try the next.
                false
            }
        }
        if (!shown) return false
        started = true
        isRunning = true
        notifications.cancelTurnedOff()
        val actions = IntentFilter().apply {
            addAction(ProtectionNotifications.ACTION_TICK)
            addAction(ProtectionNotifications.ACTION_STOP)
            addAction(ProtectionNotifications.ACTION_MUTE_HOUR)
            addAction(ProtectionNotifications.ACTION_MUTE_TODAY)
        }
        ContextCompat.registerReceiver(this, receiver, actions, ContextCompat.RECEIVER_NOT_EXPORTED)
        moveTrigger.start { watch.onMoved(clock()) }
        scope.launch { protection.setEnabled(true) }
        scope.launch {
            combine(protection.settings, data.savedDevices.saved) { next, saved -> next to saved }
                .collect { (next, saved) -> onSettings(next, saved) }
        }
        scheduleTick()
        return true
    }

    /**
     * Android 11 and older deliver scan results only with location access, which a service keeps in
     * the background only as a "location" one. From Android 12 the scan needs no location; "location"
     * is added there only when the user allowed it, for the last-seen place (#162).
     */
    private fun foregroundTypes(): List<Int> = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> listOf(0)
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> listOf(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        location.hasPermission() -> listOf(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        else -> listOf(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
    }

    private fun onSettings(next: ProtectionSettings, saved: List<SavedDevice>) {
        settings = next
        val bonded = ble.bondedDevices
        val paired = if (bonded.hasPermission()) bonded.list() else emptyList()
        val listed = WatchList.build(next.watched, saved, paired)
        val gone = devices.map { it.address } - listed.mapTo(HashSet()) { it.address }
        for (address in gone) {
            watch.forget(address)
            notifications.cancelAlert(address)
        }
        val changed = WatchList.targets(listed) != WatchList.targets(devices)
        devices = listed
        if (changed || scanJob?.isActive != true) restartScan()
        refreshStatus()
    }

    private fun restartScan() {
        scanJob?.cancel()
        scanJob = null
        val targets = WatchList.targets(devices)
        if (targets.isEmpty()) return
        val scanner = ble.scanner
        scanJob = scope.launch {
            // Started again every 20 minutes: Android quietly downgrades a scan that runs for 30.
            scanner.scanContinuously(watch = ScanWatch.FILTERED, source = { scanner.watch(targets) })
                .collect { event ->
                    when (event) {
                        is ScanEvent.Result -> onResult(event.result)
                        is ScanEvent.Failed -> {
                            blind = event.reason
                            watch.onBlind(clock())
                            refreshStatus()
                        }
                    }
                }
        }
    }

    private fun onResult(result: ScanResult) {
        // Into the list the screens read, so My devices shows it nearby and its last-seen record is kept
        // (#162). Not while a screen scans: the shared scan hears the same advertisement and feeding it
        // twice would skew the distance smoothing (#177).
        if (!ble.scanSession.isScanning) ble.repository.onScanResult(result)
        val device = WatchList.match(devices, result.device.address, result.scanRecord?.deviceName) ?: return
        if (blind != null) {
            blind = null
            refreshStatus()
        }
        heard(device.address, clock())
    }

    private fun heard(address: String, now: Long) {
        val wasAlerted = watch.onHeard(address, now)
        if (wasAlerted || address in settings.alerts) {
            notifications.cancelAlert(address)
            scope.launch { protection.clearAlert(address) }
        }
    }

    /** Once a minute, also with the screen off. */
    private fun onTick() {
        val now = clock()
        blind = cannotHear()?.also { watch.onBlind(now) }
        if (blind == null && scanJob?.isActive != true) restartScan()
        val links = ble.connectedDevices
        val connected = if (links.hasPermission()) links.addresses() else emptySet()
        val linked = devices.map { it.address }.filterTo(HashSet()) { it in connected }
        for (address in linked) heard(address, now)
        val watched = devices.mapTo(HashSet()) { it.address }
        for (alert in watch.due(watched, connected, settings.mutedUntil, now)) raise(alert)
        if (linked.isNotEmpty()) scope.launch { recordConnected(linked, now) }
        refreshStatus()
        scheduleTick()
    }

    private fun cannotHear(): ScanFailure? {
        val scanner = ble.scanner
        return when {
            !scanner.hasScanPermission() -> ScanFailure.PERMISSION_MISSING
            !scanner.isEnabled -> ScanFailure.BLUETOOTH_OFF
            scanner.isLocationOffBlockingScan -> ScanFailure.LOCATION_OFF
            else -> null
        }
    }

    private fun raise(alert: LeftBehindWatch.Alert) {
        val name = devices.firstOrNull { it.address == alert.address }?.name
            ?: getString(UiR.string.device_generic)
        notifications.leftBehind(alert.address, name, alert.lastHeardAt)
        scope.launch { protection.raiseAlert(alert.address, alert.lastHeardAt) }
    }

    private fun mute(address: String, until: Long) {
        notifications.cancelAlert(address)
        scope.launch { protection.mute(address, until) }
    }

    /**
     * A connected device rarely advertises, so the scan does not write its last-seen record: being
     * connected is as good as being heard, and the moment before it disconnects is the one to keep.
     */
    private suspend fun recordConnected(addresses: Set<String>, now: Long) {
        val known = lastSeen.lastSeen.first()
        val due = addresses.filter { LastSeenPolicy.isDue(known[it], now) }
        if (due.isEmpty()) return
        // The place only once the user turned place recording on, and never closer than about 100 m (#178).
        val fix = if (lastSeen.recordsPlace.first()) location.current() else null
        val place = fix?.let { LastSeen.Place.coarse(it.latitude, it.longitude, it.accuracyMeters) }
        lastSeen.record(due.map { LastSeenPolicy.next(known[it], it, now, place) })
    }

    private fun status() = notifications.status(devices.size, blind)

    private fun refreshStatus() {
        if (started) notifications.updateStatus(devices.size, blind)
    }

    private fun scheduleTick() {
        val alarms = getSystemService(AlarmManager::class.java) ?: return
        alarms.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + TICK_MS,
            tick,
        )
    }

    override fun onDestroy() {
        if (started) {
            getSystemService(AlarmManager::class.java)?.cancel(tick)
            unregisterReceiver(receiver)
            moveTrigger.stop()
            isRunning = false
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun clock(): Long = System.currentTimeMillis()

    private fun endOfToday(): Long =
        ZonedDateTime.now().toLocalDate().plusDays(1).atStartOfDay(ZonedDateTime.now().zone).toInstant().toEpochMilli()

    companion object {
        /** Whether the service is alive in this process; false again after the system stopped the app. */
        @Volatile
        var isRunning: Boolean = false
            private set

        private const val TICK_MS = 60_000L
        private const val HOUR_MS = 60 * 60_000L
    }
}
