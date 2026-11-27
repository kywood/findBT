package com.gamenuri.btscanner24.demo

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import com.gamenuri.btscanner24.DemoRequest
import com.gamenuri.btscanner24.core.billing.PremiumState
import com.gamenuri.btscanner24.core.billing.PremiumStore
import com.gamenuri.btscanner24.core.billing.RestoreResult
import com.gamenuri.btscanner24.core.ble.BleDependencies
import com.gamenuri.btscanner24.core.ble.BleScanner
import com.gamenuri.btscanner24.core.ble.BondedDevices
import com.gamenuri.btscanner24.core.ble.CompanyDirectory
import com.gamenuri.btscanner24.core.ble.ConnectedDevices
import com.gamenuri.btscanner24.core.ble.DeviceRepository
import com.gamenuri.btscanner24.core.ble.ScanEvent
import com.gamenuri.btscanner24.core.ble.WatchTarget
import com.gamenuri.btscanner24.core.data.LastSeenRepository
import com.gamenuri.btscanner24.core.data.SavedDevicesRepository
import com.gamenuri.btscanner24.core.model.DeviceCategory
import com.gamenuri.btscanner24.core.model.PairedDevice
import com.gamenuri.btscanner24.core.ui.save.SaveDeviceSheet
import com.gamenuri.btscanner24.feature.finder.FinderActions
import com.gamenuri.btscanner24.feature.finder.FinderContent
import com.gamenuri.btscanner24.feature.finder.FinderSave
import com.gamenuri.btscanner24.feature.finder.FoundContent
import com.gamenuri.btscanner24.feature.protection.ProtectionDemo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Debug-only demo mode (#47): fixed devices with generic names, so store and design screenshots
 * never show real people's devices or other companies' product names, and every find state can be
 * captured on cue. Driven by scripts/demo-screenshots.sh.
 */
internal object DemoMode {

    fun request(intent: Intent): DemoRequest? {
        val screen = intent.getStringExtra(EXTRA_SCREEN) ?: return null
        active = true
        val locale = intent.getStringExtra(EXTRA_LOCALE)
        // Korean names for the KO store set (#14); the scan list may already hold the English ones.
        val korean = locale?.startsWith("ko") == true
        if (korean != DemoData.korean) {
            DemoData.korean = korean
            demoRepository?.replaceAll(DemoData.devices(System.currentTimeMillis()))
            demoApp?.let(::seedSaved)
        }
        premiumBought = screen == "settings_premium"
        return DemoRequest(
            screen = screen,
            findState = intent.getStringExtra(EXTRA_FIND),
            locale = locale,
            onboardingPage = intent.getStringExtra(EXTRA_ONBOARDING),
            arScene = intent.getBooleanExtra(EXTRA_AR, true),
            showAds = intent.getBooleanExtra(EXTRA_ADS, false),
            followPose = intent.getBooleanExtra(EXTRA_POSE, false),
            leftBehindAfterSeconds = intent.getIntExtra(EXTRA_LEFT_BEHIND, -1).takeIf { it >= 0 },
        )
    }

    /**
     * With `--ei demo_left_behind <seconds>` (#207): once the user turns protection mode on, the demo's
     * away device ("Wallet Tag") raises the real "left behind" alert after that many seconds, heard last
     * four minutes before. Only the demo's own last-seen file is written.
     */
    fun onLaunch(app: Application, request: DemoRequest) {
        val seconds = request.leftBehindAfterSeconds ?: return
        leftBehindJob?.cancel()
        leftBehindJob = CoroutineScope(Dispatchers.Main).launch {
            while (!ProtectionDemo.isRunning) delay(POLL_MS)
            delay(seconds * 1_000L)
            val now = System.currentTimeMillis()
            val away = DemoData.savedAway(now)
            val heardAt = now - LEFT_BEHIND_HEARD_AGO_MS
            LastSeenRepository(app, demo = true).record(listOf(DemoData.savedAwaySeen(now).copy(seenAt = heardAt)))
            ProtectionDemo.leftBehind(app, away.address, away.name, heardAt)
        }
    }

    private var leftBehindJob: Job? = null

    fun dependencies(app: Application): BleDependencies {
        val repository = DeviceRepository(CompanyDirectory(app))
        repository.replaceAll(DemoData.devices(System.currentTimeMillis()))
        demoRepository = repository
        demoApp = app
        seedSaved(app)
        return BleDependencies(
            scanner = DemoScanner(app),
            repository = repository,
            bondedDevices = DemoBondedDevices(app),
            connectedDevices = DemoConnectedDevices(app),
        )
    }

    @Composable
    fun FindScreen(state: String, onClose: () -> Unit) {
        if (state == "found") {
            FoundContent(
                label = DemoData.findState("here").deviceLabel,
                elapsedSeconds = 102,
                onDone = onClose,
                category = DemoData.findState("here").category,
            )
            return
        }
        // "save", "saved" and "save_sheet" show the find screen's save path (#152) on the "close" state.
        val save = when (state) {
            "save", "save_sheet" -> FinderSave(isSaved = false, justSaved = false, onSave = {}, onView = {})
            "saved" -> FinderSave(isSaved = true, justSaved = true, onSave = {}, onView = {})
            else -> null
        }
        FinderContent(
            state = DemoData.findState(if (save != null) "close" else state),
            actions = FinderActions(onClose = onClose, onFound = onClose),
            save = save,
        )
        if (state == "save_sheet") {
            SaveDeviceSheet(
                initialName = DemoData.local("Keys Tag"),
                initialCategory = DeviceCategory.TRACKER,
                onSave = { _, _ -> },
                onDismiss = {},
                onFindScreen = true,
            )
        }
    }

    /** Fixed "$4.99" so paywall and settings shots never depend on Play (#32). */
    fun premium(): PremiumStore? = if (active) DemoPremiumStore(premiumBought) else null

    /** Set by MainActivity when a demo intent arrives. */
    var active: Boolean = false

    /** The demo's device list, refilled when the demo's language changes the names. */
    private var demoRepository: DeviceRepository? = null
    private var demoApp: Application? = null

    /**
     * Puts one saved device that is not around into the demo's own saved list and last-seen file
     * (never the user's), so My devices shows "last seen" in store shots (#14).
     */
    private fun seedSaved(app: Application) {
        val now = System.currentTimeMillis()
        CoroutineScope(Dispatchers.IO).launch {
            SavedDevicesRepository(app, demo = true).save(DemoData.savedAway(now))
            LastSeenRepository(app, demo = true).record(listOf(DemoData.savedAwaySeen(now)))
        }
    }

    private var premiumBought = false

    private const val EXTRA_SCREEN = "demo"
    private const val EXTRA_FIND = "demo_find"
    private const val EXTRA_LOCALE = "demo_locale"
    private const val EXTRA_ONBOARDING = "demo_onboarding"
    private const val EXTRA_AR = "demo_ar"
    private const val EXTRA_ADS = "demo_ads"
    private const val EXTRA_POSE = "demo_pose"
    private const val EXTRA_LEFT_BEHIND = "demo_left_behind"
    private const val POLL_MS = 500L
    private const val LEFT_BEHIND_HEARD_AGO_MS = 4 * 60_000L
}

/** Looks like a running scan but never reports a real device. */
private class DemoScanner(context: Context) : BleScanner(context) {
    override val isSupported: Boolean get() = true
    override val isEnabled: Boolean get() = true
    override val isLocationOffBlockingScan: Boolean get() = false

    override fun hasScanPermission(): Boolean = true

    override fun startCooldownMillis(now: Long): Long = 0

    override fun scan(): Flow<ScanEvent> = flow { awaitCancellation() }

    override fun watch(targets: Collection<WatchTarget>, trackers: Boolean): Flow<ScanEvent> =
        flow { awaitCancellation() }
}

private class DemoPremiumStore(isPremium: Boolean) : PremiumStore {
    override val state: StateFlow<PremiumState> =
        MutableStateFlow(PremiumState(isPremium = isPremium, formattedPrice = "$4.99"))

    override fun refresh() = Unit

    override fun purchase(activity: Activity): Boolean = false

    override suspend fun restore(): RestoreResult = RestoreResult.NOTHING_TO_RESTORE
}

private class DemoBondedDevices(context: Context) : BondedDevices(context) {
    override fun hasPermission(): Boolean = true

    override fun list(): List<PairedDevice> = DemoData.paired
}

/** The band is "connected" in the demo: it shows the connected state without a signal (#119). */
private class DemoConnectedDevices(context: Context) : ConnectedDevices(context) {
    override fun hasPermission(): Boolean = true

    override fun addresses(): Set<String> = setOf(DemoData.paired.last().address)

    override fun readings(address: String): Flow<Int> = emptyFlow()
}
