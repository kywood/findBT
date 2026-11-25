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
    /** What else it advertised when saved; null for devices saved before this was kept. */
    val signature: AdvertSignature? = null,
) {
    /** The same shape as a paired device, for the My devices list. */
    fun asPaired(): PairedDevice = PairedDevice(address, name, category)
}

/**
 * The parts of an advertisement that stay the same when a device changes its random address: the
 * maker's company ID, its 16-bit service UUIDs and the kind of device. A name alone is shared by every
 * device of a model, so anything that is written down about a saved device heard under a new address
 * (its last-seen place, #162) needs these to agree as well.
 */
data class AdvertSignature(
    val companyId: Int?,
    val serviceUuids16: Set<Int>,
    val category: DeviceCategory,
) {
    /** Nothing to tell devices of the same name apart by. */
    val isBlank: Boolean
        get() = companyId == null && serviceUuids16.isEmpty() && category == DeviceCategory.UNKNOWN

    /** Every part that was kept is the same in [device]'s advertisement; never true for a blank signature. */
    fun matches(device: BleDevice): Boolean = !isBlank &&
        (companyId == null || companyId == device.companyId) &&
        (serviceUuids16.isEmpty() || serviceUuids16 == device.serviceUuids16.toSet()) &&
        (category == DeviceCategory.UNKNOWN || category == device.category)

    companion object {
        fun of(device: BleDevice) = AdvertSignature(device.companyId, device.serviceUuids16.toSet(), device.category)
    }
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
    /** Version 1 has no signature field; it is still read. */
    private const val VERSION_NAMES = "1"
    private const val VERSION = "2"
    private const val FIELDS = 6
    private const val SIGNATURE_PARTS = 3

    fun encode(devices: List<SavedDevice>): String = devices.joinToString("\n") { device ->
        listOf(
            VERSION,
            device.address,
            device.category.name,
            device.savedAt.toString(),
            clean(device.advertisedName.orEmpty()),
            clean(device.name),
            device.signature?.let(::encodeSignature).orEmpty(),
        ).joinToString("\t")
    }

    /** Company ID, service UUIDs and type, e.g. `117;6159,65146;EARBUDS`. */
    private fun encodeSignature(signature: AdvertSignature): String = listOf(
        signature.companyId?.toString().orEmpty(),
        signature.serviceUuids16.sorted().joinToString(","),
        signature.category.name,
    ).joinToString(";")

    private fun decodeSignature(text: String): AdvertSignature? {
        val parts = text.split(';')
        if (parts.size != SIGNATURE_PARTS) return null
        return AdvertSignature(
            companyId = parts[0].toIntOrNull(),
            serviceUuids16 = parts[1].split(',').mapNotNullTo(LinkedHashSet()) { it.toIntOrNull() },
            category = DeviceCategory.entries.firstOrNull { it.name == parts[2] } ?: DeviceCategory.UNKNOWN,
        )
    }

    /** Lines that cannot be read (a future format, a damaged file) are skipped, never fatal. */
    fun decode(text: String): List<SavedDevice> = text.lineSequence().mapNotNull(::decodeLine).toList()

    private fun decodeLine(line: String): SavedDevice? {
        val parts = line.split('\t')
        val fields = when (parts[0]) {
            VERSION_NAMES -> FIELDS
            VERSION -> FIELDS + 1
            else -> return null
        }
        if (parts.size != fields || parts[1].isBlank()) return null
        val category = DeviceCategory.entries.firstOrNull { it.name == parts[2] } ?: DeviceCategory.UNKNOWN
        val savedAt = parts[3].toLongOrNull() ?: return null
        return SavedDevice(
            address = parts[1],
            name = parts[5],
            category = category,
            advertisedName = parts[4].ifEmpty { null },
            savedAt = savedAt,
            signature = parts.getOrNull(FIELDS)?.let(::decodeSignature),
        )
    }

    /** Names are one line of plain text; tabs and line breaks would break the format. */
    private fun clean(text: String): String = text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()
}
