package com.gamenuri.btscanner24

import android.app.Application
import com.gamenuri.btscanner24.core.ble.BleDependencies
import com.gamenuri.btscanner24.core.ble.BleScanner
import com.gamenuri.btscanner24.core.ble.CompanyDirectory
import com.gamenuri.btscanner24.core.ble.DeviceRepository
import com.gamenuri.btscanner24.core.data.DataDependenciesProvider
import com.gamenuri.btscanner24.core.data.ListPreferencesRepository

class BtScannerApp :
    Application(),
    BleDependencies.Provider,
    DataDependenciesProvider {

    // Manual DI for now; to be replaced by Hilt (see docs/CONVENTION.md).
    override val bleDependencies: BleDependencies by lazy {
        BleDependencies(scanner = BleScanner(this), repository = DeviceRepository(CompanyDirectory(this)))
    }

    override val listPreferences: ListPreferencesRepository by lazy { ListPreferencesRepository(this) }
}
