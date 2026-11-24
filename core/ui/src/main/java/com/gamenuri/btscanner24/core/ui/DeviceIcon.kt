package com.gamenuri.btscanner24.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gamenuri.btscanner24.core.model.DeviceCategory

/** Nearo's own device icon set (design/v3/icons). */
@DrawableRes
fun DeviceCategory.iconRes(): Int = when (this) {
    DeviceCategory.EARBUDS, DeviceCategory.HEARING_AID -> R.drawable.ic_device_earbuds
    DeviceCategory.HEADPHONES -> R.drawable.ic_device_headphones
    DeviceCategory.SPEAKER -> R.drawable.ic_device_speaker
    DeviceCategory.WATCH -> R.drawable.ic_device_watch
    DeviceCategory.FITNESS_BAND -> R.drawable.ic_device_band
    DeviceCategory.TRACKER -> R.drawable.ic_device_tracker
    DeviceCategory.PHONE -> R.drawable.ic_device_phone
    DeviceCategory.TABLET -> R.drawable.ic_device_tablet
    DeviceCategory.COMPUTER -> R.drawable.ic_device_laptop
    DeviceCategory.KEYBOARD -> R.drawable.ic_device_keyboard
    DeviceCategory.MOUSE -> R.drawable.ic_device_mouse
    DeviceCategory.GAMEPAD -> R.drawable.ic_device_gamepad
    DeviceCategory.TV -> R.drawable.ic_device_tv
    DeviceCategory.CAR -> R.drawable.ic_device_car
    DeviceCategory.HEALTH -> R.drawable.ic_device_health
    DeviceCategory.SENSOR -> R.drawable.ic_device_beacon
    DeviceCategory.UNKNOWN -> R.drawable.ic_device_unknown
}

/** Icon on a rounded tile (token shape deviceIcon = 12 dp). Decorative: the row text names the type. */
@Composable
fun DeviceIcon(category: DeviceCategory, modifier: Modifier = Modifier, size: Dp = 40.dp, muted: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(if (muted) colors.surfaceVariant else colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(category.iconRes()),
            contentDescription = null,
            tint = if (muted) colors.onSurfaceVariant else colors.onPrimaryContainer,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}
