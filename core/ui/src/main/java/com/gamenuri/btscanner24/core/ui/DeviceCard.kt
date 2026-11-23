package com.gamenuri.btscanner24.core.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.SignalLevel
import java.util.Date

@Composable
fun DeviceCard(device: BleDevice, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Locale-aware time format (12h/24h follows the user's system setting).
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }
    val signal = stringResource(
        when (device.signalLevel) {
            SignalLevel.STRONG -> R.string.signal_strong
            SignalLevel.MEDIUM -> R.string.signal_medium
            SignalLevel.WEAK -> R.string.signal_weak
        }
    )

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = device.name?.takeIf { device.hasName } ?: stringResource(R.string.device_unnamed),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(device.address, style = MaterialTheme.typography.labelSmall)
            Text(
                "${device.deviceType}  |  ${device.manufacturer}",
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
}
