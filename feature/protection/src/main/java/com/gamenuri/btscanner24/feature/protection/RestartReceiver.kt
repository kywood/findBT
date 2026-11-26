package com.gamenuri.btscanner24.feature.protection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gamenuri.btscanner24.core.data.DataDependenciesProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * After the phone restarts, protection mode (#163) is not running: Android does not let an app start
 * this kind of service by itself without the background location permission, which Nearo does not
 * ask for. So the user is told, once, and turns it back on with a tap.
 */
class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext
        val protection = (app as? DataDependenciesProvider)?.protection ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (protection.settings.first().enabled) {
                    protection.setEnabled(false)
                    ProtectionNotifications(app).turnedOff(afterRestart = true)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
