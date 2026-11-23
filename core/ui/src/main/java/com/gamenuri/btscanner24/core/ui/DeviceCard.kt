package com.gamenuri.btscanner24.core.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.SignalLevel
import java.util.Date

@Composable
fun DeviceCard(
    device: BleDevice,
    modifier: Modifier = Modifier,
    isNew: Boolean = false,
    isGone: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    // Locale-aware time format (12h/24h follows the user's system setting).
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }
    val signal = stringResource(
        when (device.signalLevel) {
            SignalLevel.STRONG -> R.string.signal_strong
            SignalLevel.MEDIUM -> R.string.signal_medium
            SignalLevel.WEAK -> R.string.signal_weak
        },
    )

    // Devices not heard from recently stay in place but dimmed, so they don't look "nearby".
    val cardModifier = modifier.fillMaxWidth().alpha(if (isGone) 0.45f else 1f)
    val content: @Composable ColumnScope.() -> Unit = {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.name?.takeIf { device.hasName } ?: stringResource(R.string.device_unnamed),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isNew) {
                    Badge(modifier = Modifier.padding(start = 6.dp)) { Text(stringResource(R.string.badge_new)) }
                }
                if (isGone) {
                    Text(
                        text = stringResource(R.string.device_gone),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Text(device.address, style = MaterialTheme.typography.labelSmall)
            val maker = device.manufacturer ?: stringResource(R.string.manufacturer_unknown)
            Text(
                "${device.category.label()}  |  $maker",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                stringResource(
                    R.string.device_signal_line,
                    device.rssi,
                    signal,
                    timeFormat.format(Date(device.firstSeen)),
                ),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = cardModifier, content = content)
    } else {
        Card(modifier = cardModifier, content = content)
    }
}

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
            ),
        )
    }
}
