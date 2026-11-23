package com.gamenuri.btscanner24.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.DeviceCategory
import com.gamenuri.btscanner24.core.model.DeviceLabel
import com.gamenuri.btscanner24.core.model.DeviceNaming
import com.gamenuri.btscanner24.core.model.SignalLevel
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One row of the device list (design home-v3): type icon, name with Mine/NEW badges, "Maker · Type",
 * and on the right signal bars with the approximate distance. Gone devices stay in place, dimmed.
 * The MAC address and raw RSSI live on the detail and Advanced screens, not here.
 */
@Composable
fun DeviceCard(
    device: BleDevice,
    modifier: Modifier = Modifier,
    label: DeviceLabel = DeviceNaming.label(device),
    showSuffix: Boolean = false,
    isPaired: Boolean = false,
    isNew: Boolean = false,
    isGone: Boolean = false,
    distanceMeters: Double? = null,
    onClick: (() -> Unit)? = null,
) {
    val signal = stringResource(
        when (device.signalLevel) {
            SignalLevel.STRONG -> R.string.signal_strong
            SignalLevel.MEDIUM -> R.string.signal_medium
            SignalLevel.WEAK -> R.string.signal_weak
        },
    )
    val cardModifier = modifier.fillMaxWidth().alpha(if (isGone) 0.45f else 1f)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val content: @Composable ColumnScope.() -> Unit = {
        Row(
            modifier = Modifier.heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeviceIcon(device.category)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label.text(withSuffix = showSuffix),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isPaired) {
                        Badge(
                            modifier = Modifier.padding(start = 6.dp),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) { Text(stringResource(R.string.badge_paired)) }
                    }
                    if (isNew) {
                        Badge(modifier = Modifier.padding(start = 6.dp)) { Text(stringResource(R.string.badge_new)) }
                    }
                }
                Text(
                    // No "Unknown maker": without a company the line is just the type.
                    text = device.manufacturer?.let { stringResource(R.string.device_maker_type, it, device.category.label()) }
                        ?: device.category.label(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                SignalBars(level = device.signalLevel, label = signal, inactive = isGone)
                Text(
                    text = when {
                        isGone -> stringResource(R.string.device_gone)
                        distanceMeters != null -> stringResource(R.string.device_distance, formatMeters(distanceMeters))
                        else -> signal
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = cardModifier, colors = colors, content = content)
    } else {
        Card(modifier = cardModifier, colors = colors, content = content)
    }
}

/** "~0.8" under 2 m, whole metres above: anything more precise would be false precision. */
fun formatMeters(meters: Double): String =
    if (meters < 2.0) String.format(Locale.getDefault(), "%.1f", meters) else meters.roundToInt().toString()

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DeviceCardPreview() {
    NearoTheme {
        DeviceCard(
            device = BleDevice(
                address = "AA:BB:CC:DD:EE:FF",
                name = "Galaxy Buds2 Pro",
                rssi = -58,
                firstSeen = 0,
                lastSeen = 0,
                manufacturer = "Samsung",
                category = DeviceCategory.EARBUDS,
            ),
            isNew = true,
            distanceMeters = 1.2,
        )
    }
}
