package com.gamenuri.btscanner24.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/**
 * One row of the device list (design home-v3; plain rows since home-header-v4): type icon, name with Mine/NEW
 * badges, "Maker · Type",
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
    /** The smaller row of the "Other devices" section (#82): 50 dp, grey 34 dp icon. */
    compact: Boolean = false,
    /** Adds a ⋮ menu with "Save to My devices" (#108); null where saving is not offered. */
    onSave: (() -> Unit)? = null,
) {
    val signal = stringResource(
        when (device.signalLevel) {
            SignalLevel.STRONG -> R.string.signal_strong
            SignalLevel.MEDIUM -> R.string.signal_medium
            SignalLevel.WEAK -> R.string.signal_weak
        },
    )
    // Plain M3 list row, no card (design home-header-v4): 64 dp, about 1.5 more rows per screen.
    val rowModifier = modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .alpha(if (isGone) 0.45f else 1f)
    Row(
        modifier = rowModifier
            .heightIn(min = if (compact) 50.dp else 64.dp)
            .padding(horizontal = 16.dp, vertical = if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (compact) DeviceIcon(device.category, size = 34.dp, muted = true) else DeviceIcon(device.category)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label.text(withSuffix = showSuffix),
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isPaired) PairedBadge(Modifier.padding(start = 6.dp))
                if (isNew) {
                    Badge(modifier = Modifier.padding(start = 6.dp)) { Text(stringResource(R.string.badge_new)) }
                }
            }
            Text(
                text = subtitle(device, label),
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
                    distanceMeters != null -> distanceText(distanceMeters)
                    else -> signal
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onSave != null) RowMenu(name = label.text(withSuffix = showSuffix), onSave = onSave)
    }
}

@Composable
private fun RowMenu(name: String, onSave: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.device_more_for, name))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.save_title)) },
                leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, contentDescription = null) },
                onClick = {
                    open = false
                    onSave()
                },
            )
        }
    }
}

/**
 * Second line. Named: "Samsung · Earbuds" (just the type when the maker is unknown, never "Unknown maker").
 * Unnamed: the title already says maker and type, so this line tells look-alikes apart: "No name · …3F2A".
 */
@Composable
private fun subtitle(device: BleDevice, label: DeviceLabel): String = when (label) {
    is DeviceLabel.Generic -> label.noNameLine()
    is DeviceLabel.Named -> device.manufacturer?.let {
        stringResource(R.string.device_maker_type, it, device.category.label())
    }
        ?: device.category.label()
}

/** "Mine" pill after the name (design note on #51): labelSmall, 6×2 dp padding, 4 dp corners, never wraps. */
@Composable
private fun PairedBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = stringResource(R.string.badge_paired),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
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
                category = DeviceCategory.EARBUDS,
            ),
            isNew = true,
            distanceMeters = 1.2,
        )
    }
}
