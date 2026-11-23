package com.gamenuri.btscanner24

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.feature.finder.FinderScreen
import com.gamenuri.btscanner24.feature.scan.ScanScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NearoTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = Routes.SCAN) {
                    composable(Routes.SCAN) {
                        ScanScreen(
                            title = stringResource(R.string.app_name),
                            onDeviceClick = { device -> navController.navigate(Routes.finder(device.address)) },
                        )
                    }
                    composable(Routes.FINDER) { entry ->
                        FinderScreen(
                            address = entry.arguments?.getString(Routes.ADDRESS_ARG).orEmpty(),
                            onClose = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

/** Navigation routes. Each screen's back stack entry owns its ViewModel, so leaving find mode clears it. */
private object Routes {
    const val SCAN = "scan"
    const val ADDRESS_ARG = "address"
    const val FINDER = "finder/{$ADDRESS_ARG}"

    fun finder(address: String) = "finder/${Uri.encode(address)}"
}
