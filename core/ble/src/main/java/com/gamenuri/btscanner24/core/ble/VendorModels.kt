package com.gamenuri.btscanner24.core.ble

/**
 * Decodes a product model from vendor-specific advertising data, so "AirPods Pro 2" shows instead
 * of a blank name (#46). Only formats that carry a model without a network lookup are decoded:
 * Google Fast Pair model IDs need Google's API (which would send scan data off the phone, #19), and
 * Samsung's 0x0075 payload has no public model field, so those fall back to the name and category.
 */
object VendorModels {

    private const val APPLE = 0x004C
    private const val APPLE_PROXIMITY_PAIRING = 0x07

    /** @param payload manufacturer data after the 2-byte company ID. */
    fun fromManufacturerData(companyId: Int, payload: ByteArray): String? {
        if (companyId != APPLE) return null
        // Proximity Pairing: [type 0x07][length][prefix][model hi][model lo]...
        if (payload.size < 5 || (payload[0].toInt() and 0xFF) != APPLE_PROXIMITY_PAIRING) return null
        val model = ((payload[3].toInt() and 0xFF) shl 8) or (payload[4].toInt() and 0xFF)
        return APPLE_AUDIO_MODELS[model]
    }

    /** Apple proximity-pairing model codes (public continuity-protocol research, e.g. OpenPods). */
    private val APPLE_AUDIO_MODELS = mapOf(
        0x0220 to "AirPods",
        0x0F20 to "AirPods (2nd gen)",
        0x1320 to "AirPods (3rd gen)",
        0x0E20 to "AirPods Pro",
        0x1420 to "AirPods Pro 2",
        0x2420 to "AirPods Pro 2",
        0x0A20 to "AirPods Max",
        0x0520 to "BeatsX",
        0x0620 to "Beats Solo3",
        0x0320 to "Powerbeats3",
        0x0920 to "Beats Studio3",
        0x0B20 to "Powerbeats Pro",
        0x0C20 to "Beats Solo Pro",
        0x1020 to "Beats Flex",
        0x1120 to "Beats Studio Buds",
        0x1220 to "Beats Fit Pro",
        0x1620 to "Beats Studio Buds+",
        0x1720 to "Beats Studio Pro",
    )
}
