package com.gamenuri.btscanner24.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gamenuri.btscanner24.core.model.CategoryGroup
import com.gamenuri.btscanner24.core.model.DeviceCategory

@StringRes
fun DeviceCategory.labelRes(): Int = when (this) {
    DeviceCategory.EARBUDS -> R.string.category_earbuds
    DeviceCategory.HEADPHONES -> R.string.category_headphones
    DeviceCategory.HEARING_AID -> R.string.category_hearing_aid
    DeviceCategory.SPEAKER -> R.string.category_speaker
    DeviceCategory.WATCH -> R.string.category_watch
    DeviceCategory.FITNESS_BAND -> R.string.category_fitness_band
    DeviceCategory.TRACKER -> R.string.category_tracker
    DeviceCategory.PHONE -> R.string.category_phone
    DeviceCategory.TABLET -> R.string.category_tablet
    DeviceCategory.COMPUTER -> R.string.category_computer
    DeviceCategory.KEYBOARD -> R.string.category_keyboard
    DeviceCategory.MOUSE -> R.string.category_mouse
    DeviceCategory.GAMEPAD -> R.string.category_gamepad
    DeviceCategory.TV -> R.string.category_tv
    DeviceCategory.CAR -> R.string.category_car
    DeviceCategory.HEALTH -> R.string.category_health
    DeviceCategory.SENSOR -> R.string.category_sensor
    DeviceCategory.UNKNOWN -> R.string.category_unknown
}

@StringRes
fun CategoryGroup.labelRes(): Int = when (this) {
    CategoryGroup.EARBUDS -> R.string.category_group_earbuds
    CategoryGroup.WATCH -> R.string.category_group_watch
    CategoryGroup.TRACKER -> R.string.category_group_tracker
    CategoryGroup.OTHER -> R.string.category_group_other
}

@Composable
fun DeviceCategory.label(): String = stringResource(labelRes())

@Composable
fun CategoryGroup.label(): String = stringResource(labelRes())
