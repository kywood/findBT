package com.gamenuri.btscanner24

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamenuri.btscanner24.core.ads.AdBanner
import com.gamenuri.btscanner24.core.billing.PremiumStore
import com.gamenuri.btscanner24.core.data.DataDependenciesProvider
import com.gamenuri.btscanner24.core.designsystem.component.WATER_RISE_MS
import com.gamenuri.btscanner24.core.designsystem.theme.FindBlueDeep
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
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
    /** Goes up each time another screen asks for the My devices tab (#152). */
    myDevicesRequests: Int = 0,
    /** The rewarded ad for the locked save sheet (#108); null without ads. */
    rewarded: RewardedAd? = null,
    /** Protection mode (#163): its screen, and the last-seen card a left-behind notification asks for. */
    onOpenProtection: () -> Unit = {},
    lastSeenRequest: String? = null,
    onLastSeenRequestHandled: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(if (startOnMyDevices) HomeTab.MY_DEVICES else HomeTab.NEARBY) }
    var handledRequests by rememberSaveable { mutableIntStateOf(myDevicesRequests) }
    LaunchedEffect(myDevicesRequests) {
        if (myDevicesRequests != handledRequests) {
            handledRequests = myDevicesRequests
            tab = HomeTab.MY_DEVICES
        }
    }
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
    // "★ 24h" next to the title while a rewarded day pass runs (#81 ⑥).
    val dayPass = premium.dayPassUntil != null && !premium.isPurchased
    // With the 3D map up, the bar under it takes the map's blue (design bt-map-3d v2.2, #173); it
    // changes over the time the water takes to rise, since the bar itself is not under the water.
    var mapShown by rememberSaveable { mutableStateOf(false) }
    val onBlue = tab == HomeTab.NEARBY && mapShown
    val find = NearoTheme.colors.findFar
    val barColor by animateColorAsState(
        targetValue = if (onBlue) {
            FindBlueDeep.copy(alpha = MAP_BAR_SHADE).compositeOver(find.background)
        } else {
            NavigationBarDefaults.containerColor
        },
        animationSpec = tween(WATER_RISE_MS),
        label = "barColor",
    )
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column(Modifier.background(barColor)) {
                // Anchored banner right above the tabs; no height until an ad may show (#81 ①).
                AdBanner()
                NavigationBar(containerColor = barColor) {
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
                            colors = if (onBlue) {
                                NavigationBarItemDefaults.colors(
                                    selectedIconColor = find.content,
                                    selectedTextColor = find.content,
                                    indicatorColor = find.content.copy(alpha = MAP_BAR_INDICATOR),
                                    unselectedIconColor = find.content.copy(alpha = MAP_BAR_UNSELECTED),
                                    unselectedTextColor = find.content.copy(alpha = MAP_BAR_UNSELECTED),
                                )
                            } else {
                                NavigationBarItemDefaults.colors()
                            },
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
                onDeviceClick = { onFind(it.address) },
                modifier = content,
                onOpenSettings = onOpenSettings,
                onSaveDevice = { saveTarget = SaveTarget.of(it) },
                savedAddresses = savedAddresses,
                onMapShown = { mapShown = it },
            )
            // "Add a tracker" opens Nearby, where a row's ⋮ menu saves it (#108).
            HomeTab.MY_DEVICES ->
                DevicesScreen(
                    onDeviceClick = onFind,
                    modifier = content,
                    onAddTracker = { tab = HomeTab.NEARBY },
                    onOpenSettings = onOpenSettings,
                    onEditSaved = { item ->
                        saved.firstOrNull { it.address == item.paired.address }?.let { saveTarget = SaveTarget.of(it) }
                    },
                    freeSaveLimit = SaveLimit.FREE_DEVICES.takeUnless { premium.isPremium },
                    onOpenProtection = onOpenProtection,
                    lastSeenRequest = lastSeenRequest,
                    onLastSeenRequestHandled = onLastSeenRequestHandled,
                )
        }
    }
}

/** How much of [FindBlueDeep] lies over the map's blue in the bar under it. */
private const val MAP_BAR_SHADE = 0.3f
private const val MAP_BAR_INDICATOR = 0.16f
private const val MAP_BAR_UNSELECTED = 0.7f

/** Not translated: a star and "24h" read the same in every app language. */
private const val DAY_PASS_BADGE = "  ★ 24h"
