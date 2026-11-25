package com.gamenuri.btscanner24.core.ads

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * The anchored adaptive banner right above the bottom tabs (#81 ①). Takes no space until ads may
 * show (consent done, not Premium); a 1 dp line keeps it apart from the tabs against stray taps.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ads = remember(context) { NearoAds.from(context) } ?: return
    val shows by ads.showsAds.collectAsStateWithLifecycle()
    if (!shows) return
    val widthDp = LocalConfiguration.current.screenWidthDp
    val adView = remember(widthDp) {
        AdView(context).apply {
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
            adUnitId = ads.units.banner
            // A plain request: no keywords or extras, so nothing about scanned devices (#19).
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) { onDispose { adView.destroy() } }
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider()
        AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth())
    }
}
