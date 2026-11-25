package com.gamenuri.btscanner24.core.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.SystemClock
import com.gamenuri.btscanner24.core.billing.PremiumStore
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AdMob for Nearo (#31, placements #81 ads v2): a banner above the tabs, one native card at the end
 * of the Nearby list, a full-screen ad after "Found it" → Done, and the opt-in rewarded ad for a
 * 24 h day pass. Nothing loads before the UMP consent step, and nothing shows to Premium users.
 *
 * Requests are plain [AdRequest]s: no keywords, content URLs or extras, so no scan data (device
 * names, MAC, RSSI, makers, UUIDs, location) ever reaches AdMob (#19).
 */
class NearoAds(
    private val app: Application,
    val units: AdUnits,
    private val premium: PremiumStore,
    private val policy: InterstitialPolicy,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    interface Provider {
        /** Null in the debug demo mode, so screenshots never carry ads. */
        val ads: NearoAds?
    }

    private val scope = MainScope()
    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(app)
    private val ready = MutableStateFlow(false)
    private var started = false
    private var initializing = false

    /** Banner and native card may show: consent done, SDK ready, not Premium (a day pass counts). */
    val showsAds: StateFlow<Boolean> = combine(ready, premium.state) { ready, state -> ready && !state.isPremium }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _rewardedReady = MutableStateFlow(false)

    /** A rewarded ad is loaded; the "Watch an ad" button hides until then (#81 ④⑤). */
    val rewardedReady: StateFlow<Boolean> = _rewardedReady.asStateFlow()

    private val _nativeAd = MutableStateFlow<NativeAd?>(null)

    /** The end-of-list card's ad, kept here so scrolling the list does not load a new one each time. */
    val nativeAd: StateFlow<NativeAd?> = _nativeAd.asStateFlow()
    private var nativeLoadedAt = 0L
    private var nativeLoading = false

    private var interstitial: InterstitialAd? = null
    private var interstitialLoading = false
    private var rewarded: RewardedAd? = null
    private var rewardedLoading = false

    private val _statisticsAllowed = MutableStateFlow(false)

    /**
     * Usage statistics and crash reports may be collected (#33): where no consent is required, or
     * once the user agreed to storage on the device (TCF purpose 1) in the consent form. Stays
     * false until the consent step has run, and follows later changes in "Privacy options".
     */
    val statisticsAllowed: StateFlow<Boolean> = _statisticsAllowed.asStateFlow()

    private fun updateStatisticsConsent() {
        _statisticsAllowed.value = when (consent.consentStatus) {
            ConsentInformation.ConsentStatus.NOT_REQUIRED -> true
            ConsentInformation.ConsentStatus.OBTAINED -> {
                // Written by the consent form: one character per TCF purpose, "1" = agreed.
                val prefs = app.getSharedPreferences(app.packageName + "_preferences", Context.MODE_PRIVATE)
                val purposes = prefs.getString(TCF_PURPOSES, "")
                purposes.orEmpty().startsWith("1")
            }
            else -> false
        }
    }

    /** The EEA/UK "Privacy options" entry Settings must offer (#31). */
    val isPrivacyOptionsRequired: Boolean
        get() = consent.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    init {
        scope.launch { showsAds.collect { if (it) preload() } }
    }

    /** Once per process, from Home (never during onboarding): consent form if needed, then the SDK. */
    fun start(activity: Activity) {
        if (started) return
        started = true
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    updateStatisticsConsent()
                    initializeIfAllowed()
                }
            },
            { initializeIfAllowed() },
        )
        // Consent given in an earlier session: start right away.
        updateStatisticsConsent()
        initializeIfAllowed()
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { updateStatisticsConsent() }
    }

    private fun initializeIfAllowed() {
        if (initializing || !consent.canRequestAds()) return
        initializing = true
        scope.launch {
            withContext(Dispatchers.IO) { MobileAds.initialize(app) { } }
            ready.value = true
        }
    }

    private fun request(): AdRequest = AdRequest.Builder().build()

    private fun preload() {
        loadInterstitial()
        loadRewarded()
        loadNative()
    }

    /** "Found it" → Done (#81 ③). Shows a preloaded ad when the rules allow; never waits for one. */
    fun onFoundDone(activity: Activity) {
        val now = clock()
        val allowed = policy.onFindDone(now)
        val ad = interstitial
        if (!allowed || !showsAds.value || ad == null) return
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = loadInterstitial()
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) =
                loadInterstitial()
        }
        policy.onShown(now)
        ad.show(activity)
    }

    /**
     * Plays the opt-in rewarded ad (#81 ④⑤). [onDayPass] gets the day pass end once the ad was
     * watched to the end and the pass started.
     */
    fun showRewarded(activity: Activity, onDayPass: (until: Long) -> Unit) {
        val ad = rewarded ?: return
        rewarded = null
        _rewardedReady.value = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = loadRewarded()
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) = loadRewarded()
        }
        ad.show(activity) { premium.grantDayPass()?.let(onDayPass) }
    }

    /** Asks for a fresh card when the one shown is older than [NATIVE_MAX_AGE_MS]. */
    fun refreshNativeIfStale() {
        if (SystemClock.elapsedRealtime() - nativeLoadedAt > NATIVE_MAX_AGE_MS) loadNative()
    }

    private fun loadInterstitial() {
        if (!showsAds.value || interstitial != null || interstitialLoading) return
        interstitialLoading = true
        InterstitialAd.load(
            app,
            units.interstitial,
            request(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    interstitialLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialLoading = false
                }
            },
        )
    }

    private fun loadRewarded() {
        if (!ready.value || premium.state.value.isPremium || rewarded != null || rewardedLoading) return
        rewardedLoading = true
        RewardedAd.load(
            app,
            units.rewarded,
            request(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewarded = ad
                    rewardedLoading = false
                    _rewardedReady.value = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedLoading = false
                }
            },
        )
    }

    private fun loadNative() {
        if (!showsAds.value || nativeLoading) return
        nativeLoading = true
        AdLoader.Builder(app, units.native)
            .forNativeAd { ad ->
                _nativeAd.value?.destroy()
                _nativeAd.value = ad
                nativeLoadedAt = SystemClock.elapsedRealtime()
                nativeLoading = false
            }
            .withAdListener(
                object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        nativeLoading = false
                    }
                },
            )
            .withNativeAdOptions(
                NativeAdOptions.Builder().setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT).build(),
            )
            .build()
            .loadAd(request())
    }

    companion object {
        private const val NATIVE_MAX_AGE_MS = 60 * 60 * 1000L

        /** IAB TCF v2 key in the default shared preferences, written by the UMP consent form. */
        private const val TCF_PURPOSES = "IABTCF_PurposeConsents"

        fun from(context: Context): NearoAds? = (context.applicationContext as? Provider)?.ads
    }
}
