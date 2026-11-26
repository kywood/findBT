package com.gamenuri.btscanner24.core.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * The one banner in the app, right above the bottom tabs of Nearby and My Devices (#81 ①, #204
 * option A): in its own dark frame with an "Ad" label, so it never reads as a row of the list.
 * Takes no space until ads may show (consent done, not Premium, no 24 h pass).
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ads = remember(context) { NearoAds.from(context) } ?: return
    val shows by ads.showsAds.collectAsStateWithLifecycle()
    if (!shows) return
    // The creative fills the frame's inside, so it is asked for that width.
    val widthDp = LocalConfiguration.current.screenWidthDp - 2 * FRAME_SIDE_DP
    val adView = remember(widthDp) {
        AdView(context).apply {
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
            adUnitId = ads.units.banner
            // A plain request: no keywords or extras, so nothing about scanned devices (#19).
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) { onDispose { adView.destroy() } }
    Column(
        modifier
            .fillMaxWidth()
            .background(AD_FRAME)
            .padding(horizontal = FRAME_SIDE_DP.dp, vertical = FRAME_TOP_DP.dp),
    ) {
        Text(
            stringResource(R.string.ad_label),
            style = MaterialTheme.typography.labelSmall,
            color = AD_LABEL,
            modifier = Modifier.padding(bottom = LABEL_GAP_DP.dp),
        )
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(CORNER_DP.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The frame of the ads sheet v4 (#204): the browse screens' dark surface, 12 dp at the sides, 8 dp above and below. */
private val AD_FRAME = Color(0xFF111318)
private val AD_LABEL = Color(0x99FFFFFF)
private const val FRAME_SIDE_DP = 12
private const val FRAME_TOP_DP = 8
private const val LABEL_GAP_DP = 4
private const val CORNER_DP = 8
