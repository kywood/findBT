package com.gamenuri.btscanner24.core.model

/** What kind of thing a device is. Display text lives in core:ui string resources, never here. */
enum class DeviceCategory(val filterGroup: CategoryGroup) {
    EARBUDS(CategoryGroup.EARBUDS),
    HEADPHONES(CategoryGroup.EARBUDS),
    HEARING_AID(CategoryGroup.EARBUDS),
    SPEAKER(CategoryGroup.OTHER),
    WATCH(CategoryGroup.WATCH),
    FITNESS_BAND(CategoryGroup.WATCH),
    TRACKER(CategoryGroup.TRACKER),
    PHONE(CategoryGroup.OTHER),
    TABLET(CategoryGroup.OTHER),
    COMPUTER(CategoryGroup.OTHER),
    KEYBOARD(CategoryGroup.OTHER),
    MOUSE(CategoryGroup.OTHER),
    GAMEPAD(CategoryGroup.OTHER),
    TV(CategoryGroup.OTHER),
    HEALTH(CategoryGroup.OTHER),
    SENSOR(CategoryGroup.OTHER),
    UNKNOWN(CategoryGroup.OTHER),
}

/** The chips on the device list filter: earbuds, watch, tracker, other. */
enum class CategoryGroup { EARBUDS, WATCH, TRACKER, OTHER }
