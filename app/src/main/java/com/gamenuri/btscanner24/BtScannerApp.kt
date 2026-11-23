package com.gamenuri.btscanner24

import android.app.Application
import com.gamenuri.btscanner24.core.ble.BleDependencies
import com.gamenuri.btscanner24.core.ble.BleScanner
import com.gamenuri.btscanner24.core.ble.BondedDevices
import com.gamenuri.btscanner24.core.ble.CompanyDirectory
import com.gamenuri.btscanner24.core.ble.DeviceRepository
import com.gamenuri.btscanner24.core.data.DataDependenciesProvider
import com.gamenuri.btscanner24.core.data.HiddenDevicesRepository
import com.gamenuri.btscanner24.core.data.ListPreferencesRepository
import kotlin.concurrent.thread

class BtScannerApp :
    Application(),
    BleDependencies.Provider,
    DataDependenciesProvider {

    private val companies by lazy { CompanyDirectory(this) }

    // Manual DI for now; to be replaced by Hilt (see docs/CONVENTION.md).
    override val bleDependencies: BleDependencies by lazy {
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
}
