package com.gamenuri.btscanner24.core.ads

import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import kotlin.math.roundToInt

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

/**
 * The one native card at the very end of the Nearby list (#81 ②). Built from plain views because
 * [NativeAdView] must own the clickable parts; it carries the required "Ad" badge and AdChoices,
 * and no signal bars or distance, so it never reads as a device row.
 */
@Composable
fun NativeAdCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ads = remember(context) { NearoAds.from(context) } ?: return
    val shows by ads.showsAds.collectAsStateWithLifecycle()
    val ad by ads.nativeAd.collectAsStateWithLifecycle()
    LaunchedEffect(shows) { if (shows) ads.refreshNativeIfStale() }
    val current = ad
    if (!shows || current == null) return

    val badge = stringResource(R.string.ad_badge)
    val colors = NativeCardColors(
        surface = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb(),
        title = MaterialTheme.colorScheme.onSurface.toArgb(),
        body = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
        action = MaterialTheme.colorScheme.primary.toArgb(),
        onAction = MaterialTheme.colorScheme.onPrimary.toArgb(),
    )
    val density = LocalDensity.current.density
    AndroidView(
        factory = { NativeCardView(it, density) },
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        update = { it.bind(current, badge, colors) },
    )
}

private class NativeCardColors(val surface: Int, val title: Int, val body: Int, val action: Int, val onAction: Int)

/** icon | "Ad" badge + headline over one line of body | call-to-action pill. */
private class NativeCardView(context: android.content.Context, private val density: Float) : FrameLayout(context) {
    private val adView = NativeAdView(context)
    private val icon = ImageView(context)
    private val badge = TextView(context)
    private val headline = TextView(context)
    private val body = TextView(context)
    private val action = Button(context)
    private val row = LinearLayout(context)

    init {
        val headlineRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            badge.textSize = BADGE_SP
            badge.setPadding(dp(5), dp(1), dp(5), dp(1))
            addView(badge, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(6) })
            headline.textSize = TITLE_SP
            headline.maxLines = 1
            headline.ellipsize = android.text.TextUtils.TruncateAt.END
            addView(headline, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(headlineRow, LinearLayout.LayoutParams(MATCH, WRAP))
            body.textSize = BODY_SP
            body.maxLines = 1
            body.ellipsize = android.text.TextUtils.TruncateAt.END
            addView(body, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        action.isAllCaps = false
        action.textSize = BODY_SP
        action.minHeight = 0
        action.minimumHeight = 0
        action.setPadding(dp(14), dp(6), dp(14), dp(6))
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        // Room at the top right for the AdChoices mark the SDK draws over the card.
        row.setPadding(dp(12), dp(12), dp(12), dp(12))
        row.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(12) })
        row.addView(action, LinearLayout.LayoutParams(WRAP, dp(36)))
        adView.addView(row, ViewGroup.LayoutParams(MATCH, WRAP))
        adView.iconView = icon
        adView.headlineView = headline
        adView.bodyView = body
        adView.callToActionView = action
        addView(adView, LayoutParams(MATCH, WRAP))
    }

    fun bind(ad: NativeAd, badgeText: String, colors: NativeCardColors) {
        row.background = rounded(colors.surface, CARD_RADIUS_DP)
        badge.text = badgeText
        badge.setTextColor(colors.body)
        badge.background = rounded(colors.body and 0x00FFFFFF or BADGE_FILL_ALPHA, BADGE_RADIUS_DP)
        headline.text = ad.headline
        headline.setTextColor(colors.title)
        body.text = ad.body
        body.setTextColor(colors.body)
        action.text = ad.callToAction
        action.visibility = if (ad.callToAction.isNullOrBlank()) GONE else VISIBLE
        action.setTextColor(colors.onAction)
        action.background = rounded(colors.action, PILL_RADIUS_DP)
        val drawable = ad.icon?.drawable
        icon.setImageDrawable(drawable)
        icon.visibility = if (drawable == null) GONE else VISIBLE
        adView.setNativeAd(ad)
    }

    private fun rounded(color: Int, radiusDp: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * density
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val TITLE_SP = 15f
        const val BODY_SP = 13f
        const val BADGE_SP = 11f
        const val CARD_RADIUS_DP = 16
        const val PILL_RADIUS_DP = 18
        const val BADGE_RADIUS_DP = 4
        const val BADGE_FILL_ALPHA = 0x29000000
    }
}
