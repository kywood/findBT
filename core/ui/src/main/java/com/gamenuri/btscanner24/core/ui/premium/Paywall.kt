package com.gamenuri.btscanner24.core.ui.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.core.ui.R

/** Paywall events; the caller talks to the billing store. */
data class PaywallActions(val onBuy: () -> Unit = {}, val onRestore: () -> Unit = {}, val onClose: () -> Unit = {})

/**
 * "Nearo Premium" lifetime unlock (design screens-rest-v3 §3) in a bottom sheet. [price] is Google
 * Play's localized string; while it is null the buy button waits instead of showing a made-up price.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallSheet(price: String?, isPending: Boolean, actions: PaywallActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        PaywallContent(price, isPending, actions)
    }
}

@Composable
fun PaywallContent(price: String?, isPending: Boolean, actions: PaywallActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = actions.onRestore) { Text(stringResource(R.string.paywall_restore)) }
        }
        Text(
            text = stringResource(R.string.paywall_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(20.dp))
        Benefit(R.string.paywall_glyph_devices, R.string.paywall_benefit_devices)
        Benefit(R.string.paywall_glyph_ads, R.string.paywall_benefit_ads)
        Benefit(R.string.paywall_glyph_support, R.string.paywall_benefit_support)
        Spacer(Modifier.height(20.dp))
        Text(
            text = if (price != null) {
                stringResource(R.string.paywall_price, price)
            } else {
                stringResource(R.string.paywall_loading)
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.paywall_once),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        Spacer(Modifier.height(24.dp))
    }
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
    NearoTheme { PaywallContent(price = "$4.99", isPending = false, actions = PaywallActions()) }
}
