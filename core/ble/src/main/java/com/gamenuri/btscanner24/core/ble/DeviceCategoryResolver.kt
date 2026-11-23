package com.gamenuri.btscanner24.core.ble

import com.gamenuri.btscanner24.core.model.DeviceCategory

/**
 * Guesses a [DeviceCategory] from advertising data. Sources in order of trust:
 * GAP Appearance, vendor-specific manufacturer data, 16-bit service UUIDs, then the device name.
 */
object DeviceCategoryResolver {

    private const val APPLE = 0x004C

    /** Bluetooth SIG Appearance: category = value shr 6, sub-category = value and 0x3F. */
    fun fromAppearance(appearance: Int): DeviceCategory {
        val sub = appearance and 0x3F
        return when (appearance shr 6) {
            0x001 -> DeviceCategory.PHONE
            0x002 -> if (sub == 0x0F) DeviceCategory.TABLET else DeviceCategory.COMPUTER
            0x003 -> DeviceCategory.WATCH
            0x005 -> DeviceCategory.TV
            0x008, 0x009 -> DeviceCategory.TRACKER // Tag, Keyring
            0x00C, 0x00D, 0x00E, 0x010, 0x01B -> DeviceCategory.HEALTH // thermometer, HR, BP, glucose, ...
            0x00F -> when (sub) { // Human Interface Device
                0x01 -> DeviceCategory.KEYBOARD
                0x02 -> DeviceCategory.MOUSE
                0x03, 0x04 -> DeviceCategory.GAMEPAD
                else -> DeviceCategory.KEYBOARD
            }
            0x011, 0x012 -> DeviceCategory.FITNESS_BAND // running/walking, cycling sensors
            0x015 -> DeviceCategory.SENSOR
            0x021 -> DeviceCategory.SPEAKER // Audio Sink
            0x025 -> if (sub == 0x03) DeviceCategory.HEADPHONES else DeviceCategory.EARBUDS // Wearable Audio
            0x029 -> DeviceCategory.HEARING_AID
            else -> DeviceCategory.UNKNOWN
        }
    }

    /** Vendor formats inside manufacturer-specific data (AD type 0xFF), payload after the 2-byte company ID. */
    fun fromManufacturerData(companyId: Int, payload: ByteArray): DeviceCategory {
        if (companyId == APPLE && payload.isNotEmpty()) {
            return when (payload[0].toInt() and 0xFF) {
                0x07 -> DeviceCategory.EARBUDS // Proximity Pairing: AirPods / Beats
                0x12 -> DeviceCategory.TRACKER // Find My (AirTag and offline Find My accessories)
                else -> DeviceCategory.UNKNOWN
            }
        }
        return DeviceCategory.UNKNOWN
    }

    fun fromServiceUuids(uuids: Collection<Int>): DeviceCategory = when {
        uuids.any { it == 0x1802 || it == 0x1803 } -> DeviceCategory.TRACKER // Immediate Alert, Link Loss
        uuids.any { it in TRACKER_MEMBER_UUIDS } -> DeviceCategory.TRACKER
        uuids.any { it in LE_AUDIO_UUIDS || it in CLASSIC_AUDIO_UUIDS } -> DeviceCategory.EARBUDS
        uuids.any { it == 0x1812 } -> DeviceCategory.KEYBOARD // HID over GATT
        uuids.any { it == 0x180D || it == 0x1814 || it == 0x1816 } -> DeviceCategory.FITNESS_BAND
        uuids.any { it == 0x1809 || it == 0x1810 || it == 0x1808 || it == 0x1822 } -> DeviceCategory.HEALTH
        else -> DeviceCategory.UNKNOWN
    }

    fun fromName(name: String?): DeviceCategory {
        val n = name?.lowercase()?.trim().orEmpty()
        if (n.isEmpty()) return DeviceCategory.UNKNOWN
        return when {
            TRACKER_NAMES.any { it in n } -> DeviceCategory.TRACKER
            HEADPHONE_NAMES.any { it in n } -> DeviceCategory.HEADPHONES
            EARBUD_NAMES.any { it in n } -> DeviceCategory.EARBUDS
            WATCH_NAMES.any { it in n } -> DeviceCategory.WATCH
            BAND_NAMES.any { it in n } -> DeviceCategory.FITNESS_BAND
            SPEAKER_NAMES.any { it in n } -> DeviceCategory.SPEAKER
            "keyboard" in n || "keys" in n -> DeviceCategory.KEYBOARD
            "mouse" in n -> DeviceCategory.MOUSE
            "controller" in n || "gamepad" in n -> DeviceCategory.GAMEPAD
            TV_NAMES.any { it in n } || TV_TOKEN.containsMatchIn(n) -> DeviceCategory.TV
            HEALTH_NAMES.any { it in n } -> DeviceCategory.HEALTH
            CAR_NAMES.any { it in n } || CAR_TOKEN.containsMatchIn(n) -> DeviceCategory.CAR
            else -> DeviceCategory.UNKNOWN
        }
    }

    /** First known answer wins, in the order given. */
    fun firstKnown(vararg candidates: () -> DeviceCategory): DeviceCategory {
        for (candidate in candidates) {
            val category = candidate()
            if (category != DeviceCategory.UNKNOWN) return category
        }
        return DeviceCategory.UNKNOWN
    }

    private val LE_AUDIO_UUIDS = setOf(0x184E, 0x1850, 0x1844, 0x184F, 0x1853, 0x1855) // ASCS, PACS, VCS, ...
    private val CLASSIC_AUDIO_UUIDS = setOf(0x110B, 0x110A, 0x111E, 0x1108, 0x110D)
    private val TRACKER_MEMBER_UUIDS = setOf(0xFEED, 0xFEEC, 0xFD5A, 0xFE33) // Tile, Samsung SmartTag, Chipolo

    private val TRACKER_NAMES = listOf("tile", "smarttag", "smart tag", "airtag", "chipolo", "tracker", "itag", "nut")
    private val HEADPHONE_NAMES = listOf("wh-", "headphone", "qc45", "qc35", "airpods max", "momentum")
    private val EARBUD_NAMES = listOf(
        "airpods", "buds", "earbud", "wf-", "pods", "beats", "jabra", "soundcore", "liberty", "qcy", "tws",
        "jbl tune", "jbl live", "nothing ear", "pixel buds", "freebuds", "redmi buds", "headset",
    )
    private val WATCH_NAMES = listOf("watch", "garmin", "fenix", "forerunner", "amazfit gtr", "amazfit gts")
    private val BAND_NAMES = listOf("band", "fitbit", "galaxy fit", "charge 5", "charge 6", "inspire")
    private val SPEAKER_NAMES = listOf("speaker", "soundlink", "jbl flip", "jbl charge", "boom", "sonos")
    private val TV_NAMES = listOf("[tv]", " tv", "bravia", "tizen", "androidtv", "google tv", "chromecast", "fire tv")

    /** "MiKetv11", "LG_TV", "tv-box": "tv" at a word end or followed by digits. */
    private val TV_TOKEN = Regex("tv(\\d|$|[^a-z])")
    /** Head units and car kits; paired cars usually report the car-audio class anyway. */
    private val CAR_NAMES = listOf("carplay", "carkit", "car kit", "car multimedia", "uconnect", "mbux", "my bmw")

    /** "My Car", "CAR-1234": "car" as a whole word, not inside "card" or "scarf". */
    private val CAR_TOKEN = Regex("(^|[^a-z])car($|[^a-z])")
    private val HEALTH_NAMES = listOf(
        "scale", "mi_scale", "thermometer", "oximeter", "blood pressure", "glucose", "omron", "withings", "toothbrush",
    )
}
