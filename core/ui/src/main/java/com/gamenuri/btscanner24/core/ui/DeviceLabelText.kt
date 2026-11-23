package com.gamenuri.btscanner24.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gamenuri.btscanner24.core.model.DeviceCategory
import com.gamenuri.btscanner24.core.model.DeviceLabel

/**
 * Title for a [DeviceLabel]. A named device shows its name, plus " · 3F2A" when [withSuffix] (several
 * devices share the name). A device without a name reads "Apple Tracker" or "Bluetooth device"; its
 * address suffix goes on the subtitle via [noNameLine]. Never "Unknown" (#46).
 */
@Composable
fun DeviceLabel.text(withSuffix: Boolean = false): String = when (this) {
    is DeviceLabel.Named -> if (withSuffix) stringResource(R.string.device_name_with_suffix, text, suffix) else text
    is DeviceLabel.Generic -> {
        val known = category != DeviceCategory.UNKNOWN
        // Local copy: no smart cast on a property from another module.
        val company = maker
        when {
            company != null && known -> stringResource(R.string.device_generic_maker_type, company, category.label())
            company != null -> stringResource(R.string.device_generic_maker, company)
            known -> category.label()
            else -> stringResource(R.string.device_generic)
        }
    }
}

/** Subtitle of a device without a name: "No name · …3F2A". */
@Composable
fun DeviceLabel.noNameLine(): String = stringResource(R.string.device_no_name_suffix, suffix)
