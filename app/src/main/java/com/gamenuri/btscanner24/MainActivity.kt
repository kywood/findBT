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
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.ExperimentalTransitionApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gamenuri.btscanner24.core.ble.BleScanner
import com.gamenuri.btscanner24.core.designsystem.component.WATER_TOTAL_MS
import com.gamenuri.btscanner24.core.designsystem.component.waterRise
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
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

@OptIn(ExperimentalAnimationApi::class, ExperimentalTransitionApi::class)
@Composable
private fun AppNavHost(start: String, startOnMyDevices: Boolean, openPaywall: Boolean, onOnboardingDone: () -> Unit) {
    val navController = rememberNavController()
    val water = rememberFindWater()
    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = start) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onDone = {
                        onOnboardingDone()
                        navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                    },
                )
            }
            composable(
                Routes.HOME,
                // The water cover has already hidden home when the find screen opens (#101).
                exitTransition = { if (targetState.isFinder()) ExitTransition.None else null },
                popEnterTransition = { if (initialState.isFinder()) holdIn(WATER_DRAIN_MS) else null },
            ) {
                HomeScreen(
                    onFind = { address -> water.rise { navController.navigate(Routes.finder(address)) } },
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
            composable(
                Routes.FINDER,
                enterTransition = { EnterTransition.None },
                popExitTransition = { holdOut(WATER_DRAIN_MS) },
            ) { entry ->
                // In: shown at once under the cover. Out: drains over [WATER_DRAIN_MS] (#90, #97).
                val drain by transition.animateFloat(
                    transitionSpec = {
                        if (targetState == EnterExitState.Visible) {
                            snap()
                        } else {
                            tween(WATER_DRAIN_MS, easing = LinearEasing)
                        }
                    },
                    label = "findDrain",
                ) { if (it == EnterExitState.Visible) 1f else 0f }
                // The cover lifts only once the find screen has drawn, so its first, slow frame stays hidden.
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    water.reveal()
                }
                FinderScreen(
                    address = entry.arguments?.getString(Routes.ADDRESS_ARG).orEmpty(),
                    onClose = { navController.popBackStack() },
                    entryProgress = { if (transition.targetState == EnterExitState.Visible) 1f else drain },
                )
            }
        }
        FindWaterCover(water)
    }
}

/**
 * Home to find (#90, #101). The water rises over the list first, as a plain fill above everything, and
 * only then is the find screen composed underneath. Its first frame is slow on older phones (about
 * 0.5 s on the A50), and a rise that runs during it is lost; this way the rise always plays in full.
 */
@Stable
private class FindWater(private val scope: CoroutineScope) {
    val level = Animatable(0f)
    val alpha = Animatable(1f)
    var covering by mutableStateOf(false)
        private set
    private var job: Job? = null

    fun rise(then: () -> Unit) {
        if (job?.isActive == true) return
        job = scope.launch {
            alpha.snapTo(1f)
            level.snapTo(0f)
            covering = true
            level.animateTo(1f, tween(WATER_TOTAL_MS, easing = LinearEasing))
            then()
            // Safety net: never leave the cover up if the find screen does not report back.
            delay(COVER_TIMEOUT_MS)
            lift()
        }
    }

    fun reveal() {
        if (!covering) return
        job?.cancel()
        job = scope.launch { lift() }
    }

    private suspend fun lift() {
        alpha.animateTo(0f, tween(COVER_FADE_MS))
        covering = false
    }
}

@Composable
private fun rememberFindWater(): FindWater {
    val scope = rememberCoroutineScope()
    return remember(scope) { FindWater(scope) }
}

@Composable
private fun FindWaterCover(water: FindWater) {
    if (!water.covering) return
    val colors = NearoTheme.colors.findSearching
    val brush = remember(colors) { Brush.verticalGradient(listOf(colors.background, colors.backgroundBottom)) }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = water.alpha.value }
            .waterRise(progress = { water.level.value })
            .background(brush),
    )
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
        // Bottom sheets and dialogs are separate windows that read the Activity's own resources, so
        // the language is set there too; otherwise the filter sheet stays in the phone's language (#89).
        @Suppress("DEPRECATION")
        base.resources.updateConfiguration(configuration, base.resources.displayMetrics)
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

/**
 * Keeps a screen on stage, unchanged, for [ms] so the other one can draw its own water transition
 * (#90). Nearly-1 alpha because a transition needs something to animate.
 */
private fun holdIn(ms: Int): EnterTransition = fadeIn(tween(ms), initialAlpha = HOLD_ALPHA)

private fun holdOut(ms: Int): ExitTransition = fadeOut(tween(ms), targetAlpha = HOLD_ALPHA)

private fun NavBackStackEntry.isFinder(): Boolean = destination.route == Routes.FINDER

/** Blue → dark drains faster than it rises (#97, color-modes-v1). */
private const val WATER_DRAIN_MS = 500

/** The cover fades off the find screen once it has drawn, which reads as its content fading in. */
private const val COVER_FADE_MS = 250
private const val COVER_TIMEOUT_MS = 2_000L
private const val HOLD_ALPHA = 0.999f

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
