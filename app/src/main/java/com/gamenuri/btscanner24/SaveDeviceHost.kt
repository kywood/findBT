package com.gamenuri.btscanner24

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamenuri.btscanner24.core.billing.PremiumState
import com.gamenuri.btscanner24.core.billing.PremiumStore
import com.gamenuri.btscanner24.core.ble.BleDependencies
import com.gamenuri.btscanner24.core.data.DataDependenciesProvider
import com.gamenuri.btscanner24.core.data.SavedDevicesRepository
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.DeviceCategory
import com.gamenuri.btscanner24.core.model.SaveLimit
import com.gamenuri.btscanner24.core.model.SavedDevice
import com.gamenuri.btscanner24.core.ui.premium.PaywallActions
import com.gamenuri.btscanner24.core.ui.premium.PaywallSheet
import com.gamenuri.btscanner24.core.ui.save.SaveDeviceSheet
import com.gamenuri.btscanner24.core.ui.save.SaveLockedActions
import com.gamenuri.btscanner24.core.ui.save.SaveLockedSheet
import com.gamenuri.btscanner24.feature.finder.FinderSave
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The device a save or rename sheet is open for (#108). */
data class SaveTarget(
    val address: String,
    val advertisedName: String?,
    val name: String,
    val category: DeviceCategory,
    /** Renaming a device that is saved already: never locked, keeps its place and date. */
    val savedAt: Long? = null,
) {
    companion object {
        fun of(device: BleDevice) = SaveTarget(
            address = device.address,
            advertisedName = device.name,
            name = device.modelName ?: device.name.orEmpty(),
            category = device.category,
        )

        fun of(saved: SavedDevice) =
            SaveTarget(saved.address, saved.advertisedName, saved.name, saved.category, saved.savedAt)
    }
}

/** The opt-in rewarded ad (#81): [show] plays it; a finished ad starts the 24 h pass by itself. */
class RewardedAd(val isReady: Boolean, val show: () -> Unit)

/**
 * The sheets of "Save to My devices" (#108) for [target]: the save sheet, or for a free user's
 * second device the locked sheet first. Getting Premium or finishing the rewarded ad carries
 * straight on to the save sheet. Names and the saved list stay on the phone (#19).
 */
@Composable
fun SaveDeviceHost(
    target: SaveTarget?,
    savedCount: Int,
    premium: PremiumState,
    store: PremiumStore,
    repository: SavedDevicesRepository,
    rewarded: RewardedAd?,
    onDismiss: () -> Unit,
    onSaved: (isNew: Boolean) -> Unit,
    /** Opened over the find screen: the save sheet is blue there (#152). */
    onFindScreen: Boolean = false,
) {
    var paywallOpen by remember { mutableStateOf(false) }
    // Bought, or the day pass started: the paywall has done its job.
    LaunchedEffect(premium.isPremium, target) { if (premium.isPremium || target == null) paywallOpen = false }
    if (target == null) return
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val isNew = target.savedAt == null
    val locked = isNew && !SaveLimit.canSaveAnother(savedCount, premium.isPremium)
    when {
        locked && paywallOpen -> PaywallSheet(
            price = premium.formattedPrice,
            isPending = premium.isPending,
            actions = PaywallActions(
                onBuy = { activity?.let(store::purchase) },
                onRestore = { scope.launch { store.restore() } },
                onClose = { paywallOpen = false },
                onWatchAd = rewarded?.takeIf { it.isReady && premium.dayPassAvailable }?.show,
            ),
        )
        locked -> SaveLockedSheet(
            // No pass left for today (#81: one a day); the button stays, switched off.
            adUsedToday = !premium.dayPassAvailable,
            actions = SaveLockedActions(
                onGetPremium = { paywallOpen = true },
                onWatchAd = rewarded?.takeIf { it.isReady }?.show,
                onDismiss = onDismiss,
            ),
        )
        else -> SaveDeviceSheet(
            initialName = target.name,
            initialCategory = target.category,
            onSave = { name, category ->
                scope.launch {
                    val savedAt = target.savedAt ?: System.currentTimeMillis()
                    repository.save(SavedDevice(target.address, name, category, target.advertisedName, savedAt))
                    onSaved(isNew)
                }
            },
            onDismiss = onDismiss,
            onFindScreen = onFindScreen,
        )
    }
}

/**
 * "Save to My devices" for the find screen (#152): hosts the same sheets as Home and hands the screen
 * its bookmark. Null while the device has no name yet and is not saved: a bare random address would
 * not be found again after it rotates.
 */
@Composable
fun rememberFinderSave(address: String, rewarded: RewardedAd?, onView: () -> Unit): FinderSave? {
    val app = LocalContext.current.applicationContext
    val store = (app as PremiumStore.Provider).premium
    val premium by store.state.collectAsStateWithLifecycle()
    val repository = (app as DataDependenciesProvider).savedDevices
    val saved by repository.saved.collectAsStateWithLifecycle(emptyList())
    val devices = (app as BleDependencies.Provider).bleDependencies.repository
    val hasName by remember(address) { devices.devices.map { it[address]?.hasName == true }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(false)
    var target by remember { mutableStateOf<SaveTarget?>(null) }
    var justSaved by remember { mutableStateOf(false) }
    val existing = saved.firstOrNull { it.address == address }
    SaveDeviceHost(
        target = target,
        savedCount = saved.size,
        premium = premium,
        store = store,
        repository = repository,
        rewarded = rewarded,
        onDismiss = { target = null },
        onSaved = { isNew ->
            target = null
            if (isNew) justSaved = true
        },
        onFindScreen = true,
    )
    if (existing == null && !hasName) return null
    return FinderSave(
        isSaved = existing != null,
        justSaved = justSaved,
        // Saved already: the same sheet renames it.
        onSave = { target = existing?.let(SaveTarget::of) ?: devices.devices.value[address]?.let(SaveTarget::of) },
        onView = onView,
    )
}
