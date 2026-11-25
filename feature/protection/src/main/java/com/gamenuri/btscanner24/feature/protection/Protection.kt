package com.gamenuri.btscanner24.feature.protection

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.gamenuri.btscanner24.core.data.DataDependenciesProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** How the rest of the app turns protection mode (#163) on and off and reads what its notifications ask for. */
object Protection {
    /** On the app's launch intent when a protection notification was tapped: one of the OPEN_ values. */
    const val EXTRA_OPEN = "nearo.protection.open"

    /** The protection screen. */
    const val OPEN_SCREEN = "screen"

    /** The protection screen, turning protection on again (after a restart). */
    const val OPEN_TURN_ON = "turn_on"

    /** My devices, with the last-seen card of [EXTRA_ADDRESS]. */
    const val OPEN_LAST_SEEN = "last_seen"
    const val EXTRA_ADDRESS = "nearo.protection.address"

    /** Starts the service; it marks protection as on once it runs. Call while the app is on screen. */
    fun turnOn(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, ProtectionService::class.java))
    }

    fun turnOff(context: Context) {
        val app = context.applicationContext
        app.stopService(Intent(app, ProtectionService::class.java))
        // Also when the service was not running any more (stopped by the system).
        CoroutineScope(Dispatchers.Default).launch { (app as DataDependenciesProvider).protection.setEnabled(false) }
    }

    /**
     * Protection the user left on but whose service is gone (the system stopped the app) starts again
     * when the app is opened. Call from the visible activity.
     */
    fun resumeIfOn(context: Context) {
        if (ProtectionService.isRunning) return
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Main).launch {
            val settings = (app as DataDependenciesProvider).protection.settings.first()
            if (settings.enabled && !ProtectionService.isRunning) {
                try {
                    turnOn(app)
                } catch (_: IllegalStateException) {
                    // The app left the screen in the meantime; the next launch tries again.
                }
            }
        }
    }
}
