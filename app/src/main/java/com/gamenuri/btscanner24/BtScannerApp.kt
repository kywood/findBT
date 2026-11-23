package com.gamenuri.btscanner24

import android.app.Application
import com.gamenuri.btscanner24.core.ble.BleDependencies
import com.gamenuri.btscanner24.core.ble.BleScanner
import com.gamenuri.btscanner24.core.ble.DeviceRepository

class BtScannerApp : Application(), BleDependencies.Provider {

    // Manual DI for now; to be replaced by Hilt (see docs/CONVENTION.md).
    override val bleDependencies: BleDependencies by lazy {
        BleDependencies(scanner = BleScanner(this), repository = DeviceRepository())
    }
}
