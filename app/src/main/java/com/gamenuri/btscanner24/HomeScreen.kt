package com.gamenuri.btscanner24

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamenuri.btscanner24.core.ads.AdBanner
import com.gamenuri.btscanner24.core.analytics.Analytics
import com.gamenuri.btscanner24.core.analytics.AnalyticsEvent
import com.gamenuri.btscanner24.core.analytics.FindSource
import com.gamenuri.btscanner24.core.billing.PremiumStore
import com.gamenuri.btscanner24.core.data.DataDependenciesProvider
import com.gamenuri.btscanner24.core.model.SaveLimit
import com.gamenuri.btscanner24.core.ui.R as UiR
import com.gamenuri.btscanner24.feature.devices.DevicesScreen
import com.gamenuri.btscanner24.feature.scan.ScanScreen
import kotlinx.coroutines.launch

private enum class HomeTab(@param:StringRes val label: Int) {
    NEARBY(R.string.tab_nearby),
    MY_DEVICES(R.string.tab_my_devices),
}

/** Home (design home-v3): bottom bar with Nearby and My devices. A tap opens find mode; ⋮ opens Settings. */
@Composable
fun HomeScreen(
    onFind: (address: String) -> Unit,
    onOpenSettings: () -> Unit,
    startOnMyDevices: Boolean = false,
    /** The rewarded ad for the locked save sheet (#108); null without ads. */
    rewarded: RewardedAd? = null,
) {
    var tab by rememberSaveable { mutableStateOf(if (startOnMyDevices) HomeTab.MY_DEVICES else HomeTab.NEARBY) }
    val app = LocalContext.current.applicationContext
    val store = (app as PremiumStore.Provider).premium
    val premium by store.state.collectAsStateWithLifecycle()
    // Save to My devices (#108): the saved list, the open sheet and its "saved" message.
    val savedRepository = (app as DataDependenciesProvider).savedDevices
    val saved by savedRepository.saved.collectAsStateWithLifecycle(emptyList())
    val savedAddresses = remember(saved) { saved.mapTo(HashSet()) { it.address } }
    var saveTarget by remember { mutableStateOf<SaveTarget?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(UiR.string.save_done)
    val viewLabel = stringResource(UiR.string.save_view)
    SaveDeviceHost(
        target = saveTarget,
        savedCount = saved.size,
        premium = premium,
        store = store,
        repository = savedRepository,
        rewarded = rewarded,
        onDismiss = { saveTarget = null },
        onSaved = { isNew ->
            saveTarget = null
            if (isNew) {
                scope.launch {
                    val result = snackbar.showSnackbar(savedMessage, viewLabel, duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) tab = HomeTab.MY_DEVICES
                }
            }
        },
    )
    val analytics = (app as Analytics.Provider).analytics
    // "★ 24h" next to the title while a rewarded day pass runs (#81 ⑥).
    val dayPass = premium.dayPassUntil != null && !premium.isPurchased
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                // Anchored banner right above the tabs; no height until an ad may show (#81 ①).
                AdBanner()
                NavigationBar {
                    HomeTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = {
                                when (item) {
                                    HomeTab.NEARBY -> Icon(painterResource(UiR.drawable.ic_tab_nearby), null)
                                    HomeTab.MY_DEVICES -> Icon(painterResource(UiR.drawable.ic_tab_mine), null)
                                }
                            },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Each tab draws its own top bar; only the bottom bar's space is passed down.
        val content = Modifier.padding(padding).consumeWindowInsets(padding)
        when (tab) {
            HomeTab.NEARBY -> ScanScreen(
                title = stringResource(R.string.app_name) + if (dayPass) DAY_PASS_BADGE else "",
                onDeviceClick = {
                    analytics.log(AnalyticsEvent.FindStarted(FindSource.NEARBY))
                    onFind(it.address)
                },
                modifier = content,
                onOpenSettings = onOpenSettings,
                onSaveDevice = { saveTarget = SaveTarget.of(it) },
                savedAddresses = savedAddresses,
            )
            // "Add a tracker" opens Nearby, where a row's ⋮ menu saves it (#108).
            HomeTab.MY_DEVICES ->
                DevicesScreen(
                    onDeviceClick = {
                        analytics.log(AnalyticsEvent.FindStarted(FindSource.MY_DEVICES))
                        onFind(it)
                    },
                    modifier = content,
                    onAddTracker = { tab = HomeTab.NEARBY },
                    onOpenSettings = onOpenSettings,
                    onEditSaved = { item ->
                        saved.firstOrNull { it.address == item.paired.address }?.let { saveTarget = SaveTarget.of(it) }
                    },
                    freeSaveLimit = SaveLimit.FREE_DEVICES.takeUnless { premium.isPremium },
                )
        }
    }
}

/** Not translated: a star and "24h" read the same in every app language. */
private const val DAY_PASS_BADGE = "  ★ 24h"
