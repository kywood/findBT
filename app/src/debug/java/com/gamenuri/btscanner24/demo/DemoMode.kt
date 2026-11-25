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
import com.gamenuri.btscanner24.core.model.PairedDevice
import com.gamenuri.btscanner24.feature.finder.FinderActions
import com.gamenuri.btscanner24.feature.finder.FinderContent
import com.gamenuri.btscanner24.feature.finder.FoundContent
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

/**
 * Debug-only demo mode (#47): fixed devices with generic names, so store and design screenshots
 * never show real people's devices or other companies' product names, and every find state can be
 * captured on cue. Driven by scripts/demo-screenshots.sh.
 */
internal object DemoMode {

    fun request(intent: Intent): DemoRequest? {
        val screen = intent.getStringExtra(EXTRA_SCREEN) ?: return null
        active = true
        premiumBought = screen == "settings_premium"
        return DemoRequest(
            screen = screen,
            findState = intent.getStringExtra(EXTRA_FIND),
            locale = intent.getStringExtra(EXTRA_LOCALE),
            onboardingPage = intent.getStringExtra(EXTRA_ONBOARDING),
            arScene = intent.getBooleanExtra(EXTRA_AR, true),
            showAds = intent.getBooleanExtra(EXTRA_ADS, false),
            followPose = intent.getBooleanExtra(EXTRA_POSE, false),
        )
    }

    fun dependencies(app: Application): BleDependencies {
        val repository = DeviceRepository(CompanyDirectory(app))
        repository.replaceAll(DemoData.devices(System.currentTimeMillis()))
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
        FinderContent(state = DemoData.findState(state), actions = FinderActions(onClose = onClose, onFound = onClose))
    }

    /** Fixed "$4.99" so paywall and settings shots never depend on Play (#32). */
    fun premium(): PremiumStore? = if (active) DemoPremiumStore(premiumBought) else null

    /** Set by MainActivity when a demo intent arrives. */
    var active: Boolean = false

    private var premiumBought = false

    private const val EXTRA_SCREEN = "demo"
    private const val EXTRA_FIND = "demo_find"
    private const val EXTRA_LOCALE = "demo_locale"
    private const val EXTRA_ONBOARDING = "demo_onboarding"
    private const val EXTRA_AR = "demo_ar"
    private const val EXTRA_ADS = "demo_ads"
    private const val EXTRA_POSE = "demo_pose"
}

/** Looks like a running scan but never reports a real device. */
private class DemoScanner(context: Context) : BleScanner(context) {
    override val isSupported: Boolean get() = true
    override val isEnabled: Boolean get() = true
    override val isLocationOffBlockingScan: Boolean get() = false

    override fun hasScanPermission(): Boolean = true

    override fun startCooldownMillis(now: Long): Long = 0

    override fun scan(): Flow<ScanEvent> = flow { awaitCancellation() }
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
