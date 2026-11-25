package com.gamenuri.btscanner24

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gamenuri.btscanner24.core.ads.NearoAds
import com.gamenuri.btscanner24.core.ble.BleScanner
import com.gamenuri.btscanner24.core.data.DataDependenciesProvider
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.core.model.DistanceUnit
import com.gamenuri.btscanner24.core.ui.LocalDistanceUnit
import com.gamenuri.btscanner24.demo.DemoMode
import com.gamenuri.btscanner24.feature.finder.FINDER_EXIT_HOLD_MS
import com.gamenuri.btscanner24.feature.finder.FindEntryCover
import com.gamenuri.btscanner24.feature.finder.FinderScreen
import com.gamenuri.btscanner24.feature.finder.LocalArFindScene
import com.gamenuri.btscanner24.feature.finder.LocalFindScenePose
import com.gamenuri.btscanner24.feature.finder.rememberFindEntryWater
import com.gamenuri.btscanner24.feature.onboarding.OnboardingActions
import com.gamenuri.btscanner24.feature.onboarding.OnboardingContent
import com.gamenuri.btscanner24.feature.onboarding.OnboardingPage
import com.gamenuri.btscanner24.feature.onboarding.OnboardingScreen
import com.gamenuri.btscanner24.feature.protection.Protection
import com.gamenuri.btscanner24.feature.protection.ProtectionScreen
import com.gamenuri.btscanner24.feature.scan.LocalNearbyMap
import com.gamenuri.btscanner24.feature.scan.NearbyMapOptions
import com.gamenuri.btscanner24.feature.settings.AppInfo
import com.gamenuri.btscanner24.feature.settings.LicensesScreen
import com.gamenuri.btscanner24.feature.settings.PlaceRecording
import com.gamenuri.btscanner24.feature.settings.PrivacyOptions
import com.gamenuri.btscanner24.feature.settings.RewardedOffer
import com.gamenuri.btscanner24.feature.settings.SettingsScreen
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** What a tapped protection notification asks the app to show (#163). */
private data class ProtectionRequest(val open: String, val address: String?)

class MainActivity : ComponentActivity() {
    private val protectionRequest = MutableStateFlow<ProtectionRequest?>(null)

    private fun readProtectionRequest(intent: Intent?) {
        if (intent == null) return
        val open = intent.getStringExtra(Protection.EXTRA_OPEN) ?: return
        protectionRequest.value = ProtectionRequest(open, intent.getStringExtra(Protection.EXTRA_ADDRESS))
        // Read once: turning the phone must not open it again.
        intent.removeExtra(Protection.EXTRA_OPEN)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Debug demo launch (#47); always null in release builds.
        val demo = DemoMode.request(intent)
        val app = application as BtScannerApp
        if (demo != null) app.demo = demo
        if (demo == null) {
            // Protection the user left on, whose service Android stopped, runs again from here (#163).
            Protection.resumeIfOn(this)
            readProtectionRequest(intent)
            addOnNewIntentListener { readProtectionRequest(it) }
        }
        val showOnboarding = demo == null && needsOnboarding(app)
        setContent {
            // Locale outside the theme, so locale-dependent typography (#75) follows demo languages too.
            WithLocale(demo?.locale) {
                NearoTheme {
                    val findState = demo?.findState
                    val onboardingPage = demo?.onboardingPage
                    // Feet in the US, UK, Liberia and Myanmar until the user picks a unit in Settings (#83).
                    val pickedUnit by app.appState.distanceUnit.collectAsStateWithLifecycle()
                    val region = LocalConfiguration.current.locales[0].country
                    CompositionLocalProvider(
                        LocalDistanceUnit provides (pickedUnit ?: DistanceUnit.forRegion(region)),
                        LocalArFindScene provides (demo?.arScene != false),
                        LocalFindScenePose provides (demo?.findState == null || demo.followPose),
                        // `--es demo map` opens the Nearby tab on its 3D map, with the design's fixed view.
                        LocalNearbyMap provides NearbyMapOptions(
                            startOnMap = demo?.screen == "map",
                            followPose = demo == null || demo.followPose,
                        ),
                    ) {
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
                                onDistanceUnit = app.appState::setDistanceUnit,
                                protectionRequest = protectionRequest,
                            )
                        }
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
private fun AppNavHost(
    start: String,
    startOnMyDevices: Boolean,
    openPaywall: Boolean,
    onOnboardingDone: () -> Unit,
    onDistanceUnit: (DistanceUnit) -> Unit,
    protectionRequest: MutableStateFlow<ProtectionRequest?>,
) {
    val navController = rememberNavController()
    // Where the last touch came down, 0..1 across the screen: the find screen's water starts there (#107).
    val tapX = remember { FloatArray(1) { 0.5f } }
    val watchTaps = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (size.width > 0) tapX[0] = down.position.x / size.width
        }
    }
    // The find screen's water starts in the tap itself, over Home, before the navigation is done (#107).
    val findEntry = rememberFindEntryWater()
    // "View" on the find screen's saved bar goes back to Home on the My devices tab (#152).
    var myDevicesRequests by remember { mutableIntStateOf(0) }
    // A tapped protection notification (#163): the last-seen card on My devices, or the protection screen.
    val asked by protectionRequest.collectAsStateWithLifecycle()
    var lastSeenRequest by remember { mutableStateOf<String?>(null) }
    var turnOnProtection by remember { mutableStateOf(false) }
    LaunchedEffect(asked) {
        val request = asked ?: return@LaunchedEffect
        protectionRequest.value = null
        if (request.open == Protection.OPEN_LAST_SEEN) {
            lastSeenRequest = request.address
            myDevicesRequests++
            navController.popBackStack(Routes.HOME, inclusive = false)
        } else {
            turnOnProtection = request.open == Protection.OPEN_TURN_ON
            navController.navigate(Routes.PROTECTION) { launchSingleTop = true }
        }
    }
    val activity = LocalActivity.current
    val ads = remember(activity) { activity?.let { NearoAds.from(it) } }
    val rewardedReady by (ads?.rewardedReady ?: NoRewardedAd).collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().then(watchTaps)) {
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
                // Home stays put under the find screen while its water rises, and is back at once
                // under the draining water (#107).
                exitTransition = { if (targetState.isFinder()) hold(HOME_HOLD_MS) else null },
                popEnterTransition = { if (initialState.isFinder()) EnterTransition.None else null },
            ) {
                // Consent (UMP) and the ads SDK start with Home, never during onboarding (#31).
                LaunchedEffect(ads, activity) { if (ads != null && activity != null) ads.start(activity) }
                HomeScreen(
                    onFind = { address ->
                        findEntry.start(tapX[0])
                        navController.navigate(Routes.finder(address)) { launchSingleTop = true }
                    },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenProtection = {
                        turnOnProtection = false
                        navController.navigate(Routes.PROTECTION) { launchSingleTop = true }
                    },
                    lastSeenRequest = lastSeenRequest,
                    onLastSeenRequestHandled = { lastSeenRequest = null },
                    startOnMyDevices = startOnMyDevices,
                    myDevicesRequests = myDevicesRequests,
                    rewarded = ads?.let { nearoAds ->
                        RewardedAd(rewardedReady) { activity?.let { nearoAds.showRewarded(it) { } } }
                    },
                )
            }
            composable(Routes.SETTINGS) {
                val lastSeen = (LocalContext.current.applicationContext as DataDependenciesProvider).lastSeen
                val recordsPlace by lastSeen.recordsPlace.collectAsStateWithLifecycle(initialValue = false)
                val scope = rememberCoroutineScope()
                SettingsScreen(
                    appInfo = buildAppInfo,
                    onBack = { if (!navController.popBackStack()) navController.navigate(Routes.HOME) },
                    onLicenses = { navController.navigate(Routes.LICENSES) },
                    onDistanceUnit = onDistanceUnit,
                    openPaywall = openPaywall,
                    placeRecording = PlaceRecording(
                        isOn = recordsPlace,
                        onTurnOn = { scope.launch { lastSeen.setRecordsPlace(true) } },
                        onTurnOff = { deletePlaces ->
                            scope.launch {
                                lastSeen.setRecordsPlace(false)
                                if (deletePlaces) lastSeen.forgetPlaces()
                            }
                        },
                    ),
                    privacyOptions = ads?.let { nearoAds ->
                        PrivacyOptions(nearoAds.isPrivacyOptionsRequired) {
                            activity?.let(nearoAds::showPrivacyOptions)
                        }
                    },
                    rewarded = ads?.let { nearoAds ->
                        RewardedOffer(rewardedReady) { onDayPass ->
                            activity?.let { nearoAds.showRewarded(it, onDayPass) }
                        }
                    },
                )
            }
            composable(Routes.PROTECTION) {
                ProtectionScreen(onBack = { navController.popBackStack() }, turnOnNow = turnOnProtection)
            }
            composable(Routes.LICENSES) {
                LicensesScreen(librariesRes = R.raw.aboutlibraries, onBack = { navController.popBackStack() })
            }
            composable(
                Routes.FINDER,
                // The find screen draws itself as water (#107): it is there at once, showing only what
                // is under its waterline, and stays on top while that drains.
                enterTransition = { EnterTransition.None },
                popExitTransition = { hold(FINDER_EXIT_HOLD_MS) },
            ) { entry ->
                val entryTapX = remember { tapX[0] }
                val address = entry.arguments?.getString(Routes.ADDRESS_ARG).orEmpty()
                val save = rememberFinderSave(
                    address = address,
                    rewarded = ads?.let { nearoAds ->
                        RewardedAd(rewardedReady) { activity?.let { nearoAds.showRewarded(it) { } } }
                    },
                    onView = {
                        myDevicesRequests++
                        navController.popBackStack()
                    },
                )
                FinderScreen(
                    address = address,
                    save = save,
                    onClose = { navController.popBackStack() },
                    // Full-screen ad only here, after a finished find, and only when the rules allow (#81 ③).
                    onFoundDone = { if (ads != null && activity != null) ads.onFoundDone(activity) },
                    entryTapX = entryTapX,
                    entryWater = findEntry,
                )
            }
        }
        FindEntryCover(findEntry)
    }
}

/** Keeps the leaving screen composed and in place for [millis]; the other screen does the moving. */
private fun hold(millis: Int): ExitTransition = fadeOut(tween(millis), targetAlpha = HOLD_ALPHA)

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

private fun NavBackStackEntry.isFinder(): Boolean = destination.route == Routes.FINDER

/** Stands in for the rewarded-ad state when there are no ads (demo mode). */
private val NoRewardedAd = MutableStateFlow(false)

/** Longer than the find screen's first frame plus its water rise, on the slowest test phone (A50). */
private const val HOME_HOLD_MS = 2_500

/** Not exactly 1, so the hold is a real animation with a duration; invisible. */
private const val HOLD_ALPHA = 0.99f

/** Navigation routes. Each screen's back stack entry owns its ViewModel, so leaving find mode clears it. */
private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val LICENSES = "licenses"
    const val PROTECTION = "protection"
    const val ADDRESS_ARG = "address"
    const val FINDER = "finder/{$ADDRESS_ARG}"

    fun finder(address: String) = "finder/${Uri.encode(address)}"
}
