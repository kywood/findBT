package com.gamenuri.btscanner24.core.ui.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gamenuri.btscanner24.core.designsystem.icon.NearoIcons
import com.gamenuri.btscanner24.core.designsystem.theme.BrandIconGradientBottom
import com.gamenuri.btscanner24.core.designsystem.theme.BrandIconGradientTop
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.core.ui.R

/**
 * Paywall events; the caller talks to the billing store. [onWatchAd] is the opt-in rewarded ad (#81);
 * while it is null (no ad loaded, or ads not built yet) the "or watch an ad" part is not shown.
 */
data class PaywallActions(
    val onBuy: () -> Unit = {},
    val onRestore: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onWatchAd: (() -> Unit)? = null,
)

/**
 * "Nearo Premium" lifetime unlock (design screens-rest-v3 §3, review #92) in a bottom sheet. [price] is
 * Google Play's localized string; while it is null the buy button waits instead of showing a made-up price.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallSheet(price: String?, isPending: Boolean, actions: PaywallActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
    ) {
        PaywallContent(price, isPending, actions)
    }
}

@Composable
fun PaywallContent(price: String?, isPending: Boolean, actions: PaywallActions, modifier: Modifier = Modifier) {
    // Scrolls on short screens; the buy button stays in the flow right under the price card.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = actions.onClose, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.paywall_close))
            }
            TextButton(onClick = actions.onRestore) { Text(stringResource(R.string.paywall_restore)) }
        }
        AppIcon()
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.paywall_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))
        Benefit(R.string.paywall_glyph_devices, R.string.paywall_benefit_devices)
        Benefit(R.string.paywall_glyph_ads, R.string.paywall_benefit_ads)
        Benefit(R.string.paywall_glyph_support, R.string.paywall_benefit_support)
        Spacer(Modifier.height(16.dp))
        PriceCard(price)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = actions.onBuy,
            enabled = price != null && !isPending,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.paywall_buy), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(if (isPending) R.string.paywall_pending else R.string.paywall_price_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        actions.onWatchAd?.let { RewardedOffer(onClick = it) }
        Spacer(Modifier.height(24.dp))
    }
}

/** The launcher icon's look (brand gradient, white pin) at 48 dp; core modules can't reach the mipmap. */
@Composable
private fun AppIcon() {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(
                Brush.verticalGradient(listOf(BrandIconGradientTop, BrandIconGradientBottom)),
                RoundedCornerShape(12.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(NearoIcons.PinSignal, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun PriceCard(price: String?) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, MaterialTheme.colorScheme.primary, shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = PRICE_FILL_ALPHA), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = if (price != null) {
                stringResource(R.string.paywall_price, price)
            } else {
                stringResource(R.string.paywall_loading)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.paywall_once),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "or" divider, then the opt-in rewarded ad; the label always says it is an ad (policy, #81). */
@Composable
private fun RewardedOffer(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.paywall_or),
            modifier = Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.weight(1f))
    }
    FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.paywall_reward))
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.paywall_reward_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Benefit(glyph: Int, text: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(glyph),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true)
@Composable
private fun PaywallPreview() {
    NearoTheme { PaywallContent(price = "$4.99", isPending = false, actions = PaywallActions(onWatchAd = {})) }
}

private const val PRICE_FILL_ALPHA = 0.07f
