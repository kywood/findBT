package com.gamenuri.btscanner24

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gamenuri.btscanner24.core.ble.BleScanner
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.demo.DemoMode
import com.gamenuri.btscanner24.feature.finder.FinderScreen
import com.gamenuri.btscanner24.feature.onboarding.OnboardingActions
import com.gamenuri.btscanner24.feature.onboarding.OnboardingContent
import com.gamenuri.btscanner24.feature.onboarding.OnboardingPage
import com.gamenuri.btscanner24.feature.onboarding.OnboardingScreen
import com.gamenuri.btscanner24.feature.settings.AppInfo
import com.gamenuri.btscanner24.feature.settings.LicensesScreen
import com.gamenuri.btscanner24.feature.settings.SettingsScreen
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Debug demo launch (#47); always null in release builds.
        val demo = DemoMode.request(intent)
        val app = application as BtScannerApp
        if (demo != null) app.demo = demo
        val showOnboarding = demo == null && needsOnboarding(app)
        setContent {
            // Locale outside the theme, so locale-dependent typography (#75) follows demo languages too.
            WithLocale(demo?.locale) {
                NearoTheme {
                    val findState = demo?.findState
                    val onboardingPage = demo?.onboardingPage
                    when {
                        findState != null -> DemoMode.FindScreen(findState, onClose = ::finish)
                        onboardingPage != null -> DemoOnboarding(onboardingPage)
                        else -> AppNavHost(
                            start = when {
                                showOnboarding -> Routes.ONBOARDING
                                demo?.startOnSettings == true -> Routes.SETTINGS
                                else -> Routes.HOME
                            },
                            startOnMyDevices = demo?.startOnMyDevices == true,
                            openPaywall = demo?.openPaywall == true,
                            onOnboardingDone = app.appState::setOnboardingDone,
                        )
                    }
                }
            }
        }
    }

    /** First launch only; people updating from the old app who already granted the permission skip it (#30). */
    private fun needsOnboarding(app: BtScannerApp): Boolean {
        if (app.appState.onboardingDone) return false
        if (hasBlePermission(app)) {
            app.appState.setOnboardingDone()
            return false
        }
        return true
    }

    private fun hasBlePermission(context: Context): Boolean = BleScanner.requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

/** Debug demo shots of the onboarding pages; "_legacy" shows the Android 11 location wording. */
@Composable
private fun DemoOnboarding(name: String) {
    val page = OnboardingPage.entries.firstOrNull { name.removeSuffix("_legacy").equals(it.name, ignoreCase = true) }
        ?: OnboardingPage.INTRO
    OnboardingContent(page = page, actions = OnboardingActions(), legacyLocation = name.endsWith("_legacy"))
}

@Composable
private fun AppNavHost(start: String, startOnMyDevices: Boolean, openPaywall: Boolean, onOnboardingDone: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = start) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onDone = {
                    onOnboardingDone()
                    navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onFind = { address -> navController.navigate(Routes.finder(address)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                startOnMyDevices = startOnMyDevices,
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                appInfo = buildAppInfo,
                onBack = { if (!navController.popBackStack()) navController.navigate(Routes.HOME) },
                onLicenses = { navController.navigate(Routes.LICENSES) },
                openPaywall = openPaywall,
            )
        }
        composable(Routes.LICENSES) {
            LicensesScreen(librariesRes = R.raw.aboutlibraries, onBack = { navController.popBackStack() })
        }
        composable(Routes.FINDER) { entry ->
            FinderScreen(
                address = entry.arguments?.getString(Routes.ADDRESS_ARG).orEmpty(),
                onClose = { navController.popBackStack() },
            )
        }
    }
}

/**
 * Shows [content] in another language without touching the system setting, for demo screenshots in
 * all store languages (#47). Android 11 has no per-app language, so this wraps the resources instead.
 */
@Composable
private fun WithLocale(languageTag: String?, content: @Composable () -> Unit) {
    if (languageTag == null) {
        content()
        return
    }
    val base = LocalContext.current
    val configuration = remember(languageTag) {
        Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(languageTag)) }
    }
    // A wrapper (not createConfigurationContext alone) keeps the Activity reachable for permission launchers.
    val context = remember(languageTag) {
        val localized = base.createConfigurationContext(configuration)
        object : ContextWrapper(base) {
            override fun getResources(): Resources = localized.resources
        }
    }
    CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration) {
        content()
    }
}

/** What Settings shows about this build; the support address comes from local.properties (#56). */
private val buildAppInfo = AppInfo(
    versionName = BuildConfig.VERSION_NAME,
    versionCode = BuildConfig.VERSION_CODE.toLong(),
    supportEmail = BuildConfig.SUPPORT_EMAIL,
    privacyPolicyUrl = BuildConfig.PRIVACY_POLICY_URL,
)

/** Navigation routes. Each screen's back stack entry owns its ViewModel, so leaving find mode clears it. */
private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val LICENSES = "licenses"
    const val ADDRESS_ARG = "address"
    const val FINDER = "finder/{$ADDRESS_ARG}"

    fun finder(address: String) = "finder/${Uri.encode(address)}"
}
