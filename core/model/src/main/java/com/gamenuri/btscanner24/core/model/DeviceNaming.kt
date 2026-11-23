package com.gamenuri.btscanner24.core.model

/** What the list and find screen call a device. Text for [Generic] is built in core:ui from string resources. */
sealed interface DeviceLabel {
    /** Last four hex digits of the address, e.g. "3F2A", to tell look-alike devices apart. */
    val suffix: String

    data class Named(val text: String, val source: NameSource, override val suffix: String) : DeviceLabel

    /** No usable name: shown as "Samsung Earbuds · 3F2A", never as "Unknown". */
    data class Generic(val maker: String?, val category: DeviceCategory, override val suffix: String) : DeviceLabel
}

/** Where a name came from, best first. */
enum class NameSource { PAIRED_ALIAS, USER, MODEL, ADVERTISED }

/**
 * Picks the name people recognise (#46): the phone's name for a paired device, then a name the user
 * gave it, then the model decoded from the advertisement, then the advertised name, then a generic label.
 */
object DeviceNaming {

    fun label(device: BleDevice, pairedAlias: String? = null, userName: String? = null): DeviceLabel {
        val suffix = addressSuffix(device.address)
        val advertised = device.name?.takeIf { device.hasName }
        val candidates = listOf(
            pairedAlias to NameSource.PAIRED_ALIAS,
            userName to NameSource.USER,
            device.modelName to NameSource.MODEL,
            advertised to NameSource.ADVERTISED,
        )
        for ((text, source) in candidates) {
            val clean = text?.trim()
            if (!clean.isNullOrEmpty() && !clean.equals(Constants.UNKNOWN, ignoreCase = true)) {
                return DeviceLabel.Named(clean, source, suffix)
            }
        }
        return DeviceLabel.Generic(device.manufacturer, device.category, suffix)
    }

    /** "AA:BB:CC:DD:3F:2A" -> "3F2A". */
    fun addressSuffix(address: String): String = address.filter { it.isLetterOrDigit() }.takeLast(4).uppercase()

    /**
     * Keys of labels that appear more than once on screen, so the UI adds the address suffix to them.
     * Generic labels always show their suffix, so only names are counted.
     */
    fun duplicateNames(labels: Collection<DeviceLabel>): Set<String> = labels
        .filterIsInstance<DeviceLabel.Named>()
        .groupingBy { it.text.lowercase() }
        .eachCount()
        .filterValues { it > 1 }
        .keys

    fun isDuplicate(label: DeviceLabel, duplicates: Set<String>): Boolean =
        label is DeviceLabel.Named && label.text.lowercase() in duplicates
}
