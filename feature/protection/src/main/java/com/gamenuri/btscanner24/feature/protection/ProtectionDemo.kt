package com.gamenuri.btscanner24.feature.protection

import android.content.Context
import com.gamenuri.btscanner24.core.data.DataDependenciesProvider

/**
 * For the debug demo mode only (#207): shows the "left behind" alert for a demo device on cue, so the
 * Play foreground-service video can be recorded on an emulator, which has no Bluetooth devices to lose.
 * Release builds have no demo mode and never call it.
 */
object ProtectionDemo {
    /** Whether protection mode is running now. */
    val isRunning: Boolean get() = ProtectionService.isRunning

    /** The same notification and in-app alert as a real one for [address], last heard at [lastHeardAt]. */
    suspend fun leftBehind(context: Context, address: String, name: String, lastHeardAt: Long) {
        ProtectionNotifications(context).leftBehind(address, name, lastHeardAt)
        (context.applicationContext as DataDependenciesProvider).protection.raiseAlert(address, lastHeardAt)
    }
}
