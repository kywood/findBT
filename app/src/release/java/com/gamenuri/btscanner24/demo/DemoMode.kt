package com.gamenuri.btscanner24.demo

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.Composable
import com.gamenuri.btscanner24.DemoRequest
import com.gamenuri.btscanner24.core.billing.PremiumStore
import com.gamenuri.btscanner24.core.ble.BleDependencies

/** Release: no demo mode. Same API as the debug version so main code compiles in both. */
internal object DemoMode {
    @Suppress("UNUSED_PARAMETER")
    fun request(intent: Intent): DemoRequest? = null

    fun dependencies(app: Application): BleDependencies = error("Demo mode is debug-only")

    fun premium(): PremiumStore? = null

    @Composable
    fun FindScreen(state: String, onClose: () -> Unit): Unit = error("Demo mode is debug-only")
}
