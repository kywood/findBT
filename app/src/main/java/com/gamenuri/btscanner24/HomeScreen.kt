package com.gamenuri.btscanner24

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamenuri.btscanner24.core.ads.AdBanner
import com.gamenuri.btscanner24.core.ads.NativeAdCard
import com.gamenuri.btscanner24.core.analytics.Analytics
import com.gamenuri.btscanner24.core.analytics.AnalyticsEvent
import com.gamenuri.btscanner24.core.analytics.FindSource
import com.gamenuri.btscanner24.core.billing.PremiumStore
import com.gamenuri.btscanner24.core.ui.R as UiR
import com.gamenuri.btscanner24.feature.devices.DevicesScreen
import com.gamenuri.btscanner24.feature.scan.ScanScreen

private enum class HomeTab(@param:StringRes val label: Int) {
    NEARBY(R.string.tab_nearby),
    MY_DEVICES(R.string.tab_my_devices),
}

/** Home (design home-v3): bottom bar with Nearby and My devices. A tap opens find mode; ⋮ opens Settings. */
@Composable
fun HomeScreen(onFind: (address: String) -> Unit, onOpenSettings: () -> Unit, startOnMyDevices: Boolean = false) {
    var tab by rememberSaveable { mutableStateOf(if (startOnMyDevices) HomeTab.MY_DEVICES else HomeTab.NEARBY) }
    val app = LocalContext.current.applicationContext
    val premium by (app as PremiumStore.Provider).premium.state.collectAsStateWithLifecycle()
    val analytics = (app as Analytics.Provider).analytics
    // "★ 24h" next to the title while a rewarded day pass runs (#81 ⑥).
    val dayPass = premium.dayPassUntil != null && !premium.isPurchased
    Scaffold(
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
                                    HomeTab.NEARBY -> Icon(painterResource(UiR.drawable.ic_device_beacon), null)
                                    HomeTab.MY_DEVICES -> Icon(Icons.Filled.Star, null)
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
                // The one native card, after the last row (#81 ②).
                listFooter = { NativeAdCard() },
            )
            // Saving unpaired trackers comes with device storage; until then "Add a tracker" opens Nearby.
            HomeTab.MY_DEVICES ->
                DevicesScreen(
                    onDeviceClick = {
                        analytics.log(AnalyticsEvent.FindStarted(FindSource.MY_DEVICES))
                        onFind(it)
                    },
                    modifier = content,
                    onAddTracker = { tab = HomeTab.NEARBY },
                    onOpenSettings = onOpenSettings,
                )
        }
    }
}

/** Not translated: a star and "24h" read the same in every app language. */
private const val DAY_PASS_BADGE = "  ★ 24h"
