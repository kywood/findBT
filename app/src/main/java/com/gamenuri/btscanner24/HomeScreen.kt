package com.gamenuri.btscanner24

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.gamenuri.btscanner24.feature.devices.DevicesScreen
import com.gamenuri.btscanner24.feature.scan.ScanScreen
import com.gamenuri.btscanner24.core.ui.R as UiR

private enum class HomeTab(@StringRes val label: Int) {
    NEARBY(R.string.tab_nearby),
    MY_DEVICES(R.string.tab_my_devices),
}

/** Home (design home-v3): bottom bar with Nearby and My devices. Either tab opens find mode on tap. */
@Composable
fun HomeScreen(onFind: (address: String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.NEARBY) }
    Scaffold(
        bottomBar = {
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
        },
    ) { padding ->
        // Each tab draws its own top bar; only the bottom bar's space is passed down.
        val content = Modifier.padding(padding).consumeWindowInsets(padding)
        when (tab) {
            HomeTab.NEARBY -> ScanScreen(
                title = stringResource(R.string.app_name),
                onDeviceClick = { onFind(it.address) },
                modifier = content,
            )
            HomeTab.MY_DEVICES -> DevicesScreen(onDeviceClick = onFind, modifier = content)
        }
    }
}
