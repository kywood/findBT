package com.gamenuri.btscanner24.core.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import com.gamenuri.btscanner24.core.billing.PremiumStore
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
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
 * AdMob for Nearo (#31, placements #124 ads v3): a banner above the tabs, one full-screen ad after
 * "Found it" → Done, and the opt-in rewarded ad for a 24 h day pass. No ad sits inside a list. Nothing loads before the UMP consent step, and nothing shows to Premium users.
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

    /** The banner may show: consent done, SDK ready, not Premium (a day pass counts). */
    val showsAds: StateFlow<Boolean> = combine(ready, premium.state) { ready, state -> ready && !state.isPremium }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _rewardedReady = MutableStateFlow(false)

    /** A rewarded ad is loaded; the "Watch an ad" button hides until then (#81 ④⑤). */
    val rewardedReady: StateFlow<Boolean> = _rewardedReady.asStateFlow()

    private var interstitial: InterstitialAd? = null
    private var interstitialLoading = false
    private var rewarded: RewardedAd? = null
    private var rewardedLoading = false

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
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { initializeIfAllowed() } },
            { initializeIfAllowed() },
        )
        // Consent given in an earlier session: start right away.
        initializeIfAllowed()
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
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

    companion object {
        fun from(context: Context): NearoAds? = (context.applicationContext as? Provider)?.ads
    }
}
