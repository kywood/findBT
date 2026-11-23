package com.gamenuri.btscanner24.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gamenuri.btscanner24.core.model.DeviceCategory
import com.gamenuri.btscanner24.core.model.DeviceLabel

/**
 * Display text for a [DeviceLabel]: the name, plus " · 3F2A" when [withSuffix] (several devices share
 * the name). A device without a name reads "Samsung Earbuds · 3F2A", never "Unknown" (#46).
 */
@Composable
fun DeviceLabel.text(withSuffix: Boolean = false): String = when (this) {
    is DeviceLabel.Named -> if (withSuffix) stringResource(R.string.device_name_with_suffix, text, suffix) else text
    is DeviceLabel.Generic -> {
        val known = category != DeviceCategory.UNKNOWN
        // Local copy: no smart cast on a property from another module.
        val company = maker
        when {
            company != null && known -> stringResource(
                R.string.device_generic_maker_type,
                company,
                category.label(),
                suffix,
            )
            company != null -> stringResource(R.string.device_generic_maker, company, suffix)
            known -> stringResource(R.string.device_generic_type, category.label(), suffix)
            else -> stringResource(R.string.device_generic, suffix)
        }
    }
}
