package com.gamenuri.btscanner24.core.model

/**
 * A device the user saved to My devices by hand (#108), with the name and type they gave it. The
 * same record the "what is this? → name it" flow of the device map writes (#86). Kept on the phone
 * only; never sent anywhere (#19).
 */
data class SavedDevice(
    val address: String,
    /** What the user called it; shown everywhere instead of the advertised name. */
    val name: String,
    /** The type the user picked; decides the icon. */
    val category: DeviceCategory,
    /** The name it advertised when saved: how it is recognised again after its address changes. */
    val advertisedName: String?,
    val savedAt: Long,
) {
    /** The same shape as a paired device, for the My devices list. */
    fun asPaired(): PairedDevice = PairedDevice(address, name, category)
}

/** Free users keep one saved device; Premium and a running day pass have no limit (#108). */
object SaveLimit {
    const val FREE_DEVICES = 1

    /** Whether one more device may be saved. Devices already saved always stay. */
    fun canSaveAnother(savedCount: Int, isPremium: Boolean): Boolean = isPremium || savedCount < FREE_DEVICES

    /**
     * Saving only helps when the device can be recognised later: by a name it advertises, since most
     * unnamed devices change their address every few minutes.
     */
    fun canBeSaved(device: BleDevice): Boolean = device.hasName
}

/** Saved devices as text for storage: one per line, tab-separated, newest format first field. */
object SavedDeviceCodec {
    private const val VERSION = "1"
    private const val FIELDS = 6

    fun encode(devices: List<SavedDevice>): String = devices.joinToString("\n") { device ->
        listOf(
            VERSION,
            device.address,
            device.category.name,
            device.savedAt.toString(),
            clean(device.advertisedName.orEmpty()),
            clean(device.name),
        ).joinToString("\t")
    }

    /** Lines that cannot be read (a future format, a damaged file) are skipped, never fatal. */
    fun decode(text: String): List<SavedDevice> = text.lineSequence().mapNotNull(::decodeLine).toList()

    private fun decodeLine(line: String): SavedDevice? {
        val parts = line.split('\t')
        if (parts.size != FIELDS || parts[0] != VERSION || parts[1].isBlank()) return null
        val category = DeviceCategory.entries.firstOrNull { it.name == parts[2] } ?: DeviceCategory.UNKNOWN
        val savedAt = parts[3].toLongOrNull() ?: return null
        return SavedDevice(
            address = parts[1],
            name = parts[5],
            category = category,
            advertisedName = parts[4].ifEmpty { null },
            savedAt = savedAt,
        )
    }

    /** Names are one line of plain text; tabs and line breaks would break the format. */
    private fun clean(text: String): String = text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()
}
