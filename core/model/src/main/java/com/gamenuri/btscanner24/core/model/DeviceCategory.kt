package com.gamenuri.btscanner24.core.model

/** What kind of thing a device is. Display text lives in core:ui string resources, never here. */
enum class DeviceCategory(val filterGroup: CategoryGroup) {
    EARBUDS(CategoryGroup.EARBUDS),
    HEADPHONES(CategoryGroup.HEADPHONES),
    HEARING_AID(CategoryGroup.EARBUDS),
    SPEAKER(CategoryGroup.SPEAKER),
    WATCH(CategoryGroup.WATCH),
    FITNESS_BAND(CategoryGroup.BAND),
    TRACKER(CategoryGroup.TRACKER),
    PHONE(CategoryGroup.PHONE),
    TABLET(CategoryGroup.TABLET),
    COMPUTER(CategoryGroup.LAPTOP),
    KEYBOARD(CategoryGroup.OTHER),
    MOUSE(CategoryGroup.OTHER),
    GAMEPAD(CategoryGroup.OTHER),
    TV(CategoryGroup.OTHER),
    CAR(CategoryGroup.OTHER),
    HEALTH(CategoryGroup.OTHER),
    SENSOR(CategoryGroup.OTHER),
    UNKNOWN(CategoryGroup.OTHER),
}

/** The "Device type" chips of the filter screen (#85, design nearby-filter-v1), in chip order. */
enum class CategoryGroup { EARBUDS, HEADPHONES, WATCH, BAND, TRACKER, PHONE, TABLET, SPEAKER, LAPTOP, OTHER }
