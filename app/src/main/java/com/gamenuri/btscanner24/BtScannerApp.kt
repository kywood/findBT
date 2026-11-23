package com.gamenuri.btscanner24

import android.app.Application
import com.gamenuri.btscanner24.core.billing.PlayPremiumStore
import com.gamenuri.btscanner24.core.billing.PremiumStore
import com.gamenuri.btscanner24.core.ble.BleDependencies
import com.gamenuri.btscanner24.core.ble.BleScanner
import com.gamenuri.btscanner24.core.ble.BondedDevices
import com.gamenuri.btscanner24.core.ble.CompanyDirectory
import com.gamenuri.btscanner24.core.ble.DeviceRepository
import com.gamenuri.btscanner24.core.data.AppStateRepository
import com.gamenuri.btscanner24.core.data.DataDependenciesProvider
import com.gamenuri.btscanner24.core.data.HiddenDevicesRepository
import com.gamenuri.btscanner24.core.data.ListPreferencesRepository
import com.gamenuri.btscanner24.demo.DemoMode
import kotlin.concurrent.thread

class BtScannerApp :
    Application(),
    BleDependencies.Provider,
    DataDependenciesProvider,
    PremiumStore.Provider {

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
        )
    }

    override fun onCreate() {
        super.onCreate()
        // The Bluetooth SIG tables are ~4k lines; read them before the first scan result needs them.
        thread(name = "company-preload", isDaemon = true) { companies.preload() }
    }

    override val listPreferences: ListPreferencesRepository by lazy { ListPreferencesRepository(this) }

    override val hiddenDevices: HiddenDevicesRepository by lazy { HiddenDevicesRepository(this) }

    val appState: AppStateRepository by lazy { AppStateRepository(this) }

    // Connects to Google Play only when a screen first asks (Settings, paywall), not at start-up (#34).
    override val premium: PremiumStore by lazy { DemoMode.premium() ?: PlayPremiumStore(this) }
}
