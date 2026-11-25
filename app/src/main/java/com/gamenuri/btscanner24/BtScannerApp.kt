package com.gamenuri.btscanner24

import android.app.Application
import com.gamenuri.btscanner24.core.ads.AdUnits
import com.gamenuri.btscanner24.core.ads.InterstitialPolicy
import com.gamenuri.btscanner24.core.ads.NearoAds
import com.gamenuri.btscanner24.core.billing.DayPassPremiumStore
import com.gamenuri.btscanner24.core.billing.PlayPremiumStore
import com.gamenuri.btscanner24.core.billing.PremiumStore
import com.gamenuri.btscanner24.core.ble.BleDependencies
import com.gamenuri.btscanner24.core.ble.BleScanner
import com.gamenuri.btscanner24.core.ble.BondedDevices
import com.gamenuri.btscanner24.core.ble.CompanyDirectory
import com.gamenuri.btscanner24.core.ble.ConnectedDevices
import com.gamenuri.btscanner24.core.ble.DeviceRepository
import com.gamenuri.btscanner24.core.ble.MediaFindTone
import com.gamenuri.btscanner24.core.data.AppStateRepository
import com.gamenuri.btscanner24.core.data.DataDependenciesProvider
import com.gamenuri.btscanner24.core.data.HiddenDevicesRepository
import com.gamenuri.btscanner24.core.data.LastSeenRecorder
import com.gamenuri.btscanner24.core.data.LastSeenRepository
import com.gamenuri.btscanner24.core.data.ListPreferencesRepository
import com.gamenuri.btscanner24.core.data.SavedDevicesRepository
import com.gamenuri.btscanner24.core.model.LastSeen
import com.gamenuri.btscanner24.core.sensors.PhoneLocation
import com.gamenuri.btscanner24.demo.DemoMode
import com.gamenuri.btscanner24.demo.ScanCensus
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class BtScannerApp :
    Application(),
    BleDependencies.Provider,
    DataDependenciesProvider,
    PremiumStore.Provider,
    NearoAds.Provider {

    private val companies by lazy { CompanyDirectory(this) }

    /** Set by MainActivity before any screen reads [bleDependencies]; debug builds only (#47). */
    internal var demo: DemoRequest? = null

    // Manual DI for now; to be replaced by Hilt (see docs/CONVENTION.md).
    override val bleDependencies: BleDependencies by lazy {
        if (demo != null) return@lazy DemoMode.dependencies(this)
        BleDependencies(
            scanner = BleScanner(this),
            repository = DeviceRepository(companies),
            bondedDevices = BondedDevices(this),
            connectedDevices = ConnectedDevices(this),
            mediaFindTone = if (BuildConfig.EARBUD_TONE) MediaFindTone(this) else null,
        ).also { ScanCensus.start(it.repository) }
    }

    override fun onCreate() {
        super.onCreate()
        // The Bluetooth SIG tables are ~4k lines; read them before the first scan result needs them.
        thread(name = "company-preload", isDaemon = true) { companies.preload() }
    }

    override val listPreferences: ListPreferencesRepository by lazy { ListPreferencesRepository(this) }

    override val hiddenDevices: HiddenDevicesRepository by lazy { HiddenDevicesRepository(this) }
    private val userSavedDevices by lazy { SavedDevicesRepository(this) }
    private val demoSavedDevices by lazy { SavedDevicesRepository(this, demo = true) }

    // A demo launch (debug screenshots) keeps its saves apart from the user's own list.
    override val savedDevices: SavedDevicesRepository
        get() = if (demo != null) demoSavedDevices else userSavedDevices

    private val userLastSeen by lazy { LastSeenRepository(this) }
    private val demoLastSeen by lazy { LastSeenRepository(this, demo = true) }

    override val lastSeen: LastSeenRepository
        get() = if (demo != null) demoLastSeen else userLastSeen

    private var lastSeenRecorder: Job? = null

    /**
     * Starts writing down where the user's own devices were last heard (#162). Called once the
     * launch is known not to be a demo; it only listens to scans the screens run, so it does nothing
     * while the app is in the background.
     */
    internal fun startLastSeenRecorder() {
        if (lastSeenRecorder != null || demo != null) return
        val location = PhoneLocation(this)
        val bonded = bleDependencies.bondedDevices
        val recorder = LastSeenRecorder(
            seen = bleDependencies.repository.devices.map { it.values },
            paired = { if (bonded.hasPermission()) bonded.list() else emptyList() },
            saved = savedDevices.saved,
            store = lastSeen,
            locate = { location.current()?.let { LastSeen.Place(it.latitude, it.longitude, it.accuracyMeters) } },
        )
        lastSeenRecorder = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { recorder.run() }
    }

    val appState: AppStateRepository by lazy { AppStateRepository(this) }

    // Connects to Google Play only when a screen first asks (Settings, paywall), not at start-up (#34).
    // The rewarded ad's 24 h day pass (#81) sits on top of the purchase.
    override val premium: PremiumStore by lazy {
        val demoStore = DemoMode.premium()
        when {
            demoStore == null -> DayPassPremiumStore(this, PlayPremiumStore(this))
            // Demo with test ads: the rewarded ad's day pass works there too.
            demo?.showAds == true -> DayPassPremiumStore(this, demoStore)
            else -> demoStore
        }
    }

    /** No ads in the debug demo mode unless asked for: screenshots stay clean and repeatable. */
    override val ads: NearoAds? by lazy {
        if (demo?.showAds == false) return@lazy null
        val prefs = getSharedPreferences("ads", MODE_PRIVATE)
        val installedAt = packageManager.getPackageInfo(packageName, 0).firstInstallTime
        NearoAds(
            app = this,
            units = adUnits(),
            premium = premium,
            policy = InterstitialPolicy(InterstitialPolicy.SharedPreferencesStorage(prefs), installedAt),
        )
    }

    private fun adUnits(): AdUnits {
        val test = AdUnits.TEST
        return AdUnits(
            banner = BuildConfig.ADMOB_BANNER.ifBlank { test.banner },
            interstitial = BuildConfig.ADMOB_INTERSTITIAL.ifBlank { test.interstitial },
            rewarded = BuildConfig.ADMOB_REWARDED.ifBlank { test.rewarded },
        )
    }
}
