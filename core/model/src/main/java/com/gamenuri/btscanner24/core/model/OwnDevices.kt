package com.gamenuri.btscanner24.core.model

/** One of the user's own devices, paired with the phone or saved by hand, and how a scan recognises it. */
data class OwnDevice(
    /** As My devices lists it; a saved device takes the same shape. */
    val device: PairedDevice,
    /** Saved by hand (#108) and not paired. */
    val isSaved: Boolean,
    /** The names it may advertise under an address other than its own. */
    val matchNames: List<String>,
    /** What else a saved device advertised when it was saved; null for paired ones and old saves. */
    val signature: AdvertSignature? = null,
)

/**
 * Which scanned device is which of the user's own (#177): one place for the My devices tab, the
 * last-seen recorder (#162) and protection mode (#163).
 *
 * The listed address comes first. Most earbuds and watches advertise BLE from a random address that
 * differs from the paired (classic) one, so the fallback is the same device name; the newest
 * sighting wins. A device the user renamed on the phone still advertises its own name, so that one
 * is tried as well (#116). Saved devices (#108) match on the name they advertised when saved, not
 * on what the user called them.
 */
object OwnDevices {
    /** Heard within this time counts as "here now". */
    const val ACTIVE_WINDOW_MS = 30_000L

    /** Paired devices that are not hidden, then saved ones; a device that is both counts once, as paired. */
    fun list(paired: List<PairedDevice>, saved: List<SavedDevice>, hidden: Set<String> = emptySet()): List<OwnDevice> {
        val pairedAddresses = paired.mapTo(HashSet()) { it.address }
        return paired
            .filter { it.address !in hidden }
            .map { OwnDevice(it, isSaved = false, matchNames = listOfNotNull(it.name, it.advertisedName)) } +
            saved
                .filter { it.address !in pairedAddresses }
                .map { OwnDevice(it.asPaired(), isSaved = true, listOfNotNull(it.advertisedName), it.signature) }
    }

    /** What a scan heard, indexed once for many lookups. */
    class Sightings(seen: Collection<BleDevice>) {
        private val byAddress = seen.associateBy { it.address }
        private val byName = seen.filter { it.hasName }.groupBy { key(it.name!!) }

        /** The sighting that is [own], by the rules above; null when it was not heard. */
        fun of(own: OwnDevice): BleDevice? = byAddress[own.device.address]
            ?: own.matchNames.flatMap { byName[key(it)].orEmpty() }.maxByOrNull { it.lastSeen }
    }

    /**
     * Whether [sighting] is [own] surely enough to write something down about it (its last-seen
     * place, #162). A name alone is shared by every device of a model, a neighbour's included: a
     * saved device heard under another address counts only when the company ID, service UUIDs and
     * type kept at saving agree as well ([AdvertSignature]); with nothing kept, only its own address
     * counts. Paired devices are bonded to this phone and keep counting by name.
     */
    fun isCertain(own: OwnDevice, sighting: BleDevice): Boolean =
        sighting.address == own.device.address || !own.isSaved || own.signature?.matches(sighting) == true

    private fun key(name: String): String = name.trim().lowercase()
}
