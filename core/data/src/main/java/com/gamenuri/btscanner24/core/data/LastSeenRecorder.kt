package com.gamenuri.btscanner24.core.data

import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.LastSeen
import com.gamenuri.btscanner24.core.model.LastSeenPolicy
import com.gamenuri.btscanner24.core.model.OwnDevices
import com.gamenuri.btscanner24.core.model.PairedDevice
import com.gamenuri.btscanner24.core.model.SavedDevice
import java.io.IOException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample

/**
 * Writes down when and where the user's own devices (paired or saved) were last heard (#162). It
 * only listens to what a scan already running finds, so it never starts a scan, keeps the phone
 * awake or asks for anything itself. Other people's devices are never recorded.
 *
 * @param seen every device the scan has heard, as it changes.
 * @param paired the devices paired with the phone right now; null while they cannot be read (no
 * permission, Bluetooth off), which is not the same as none.
 * @param locate the phone's position now, or null without permission or a fix. It can take seconds.
 */
@OptIn(FlowPreview::class)
class LastSeenRecorder(
    private val seen: Flow<Collection<BleDevice>>,
    private val paired: () -> List<PairedDevice>?,
    private val saved: Flow<List<SavedDevice>>,
    private val store: LastSeenStore,
    private val locate: suspend () -> LastSeen.Place?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val checkEveryMs: Long = CHECK_EVERY_MS,
) {
    private var pairedNow: List<PairedDevice> = emptyList()
    private var pairedReadAt: Long? = null
    private var pairedKnown = false
    private var place: LastSeen.Place? = null
    private var placeReadAt: Long? = null

    /** Runs until cancelled. */
    suspend fun run() {
        combine(seen.sample(checkEveryMs), saved) { heard, kept -> heard to kept }
            .collect { (heard, kept) ->
                try {
                    record(heard, kept)
                } catch (_: IOException) {
                    // The file could not be read or written this time; the next sighting tries again.
                }
            }
    }

    private suspend fun record(heard: Collection<BleDevice>, kept: List<SavedDevice>) {
        val now = clock()
        // Read each time, not remembered: a record the user removed in the meantime must not come back.
        val known = prune(store.lastSeen.first(), kept, now)
        val due = due(pairedAt(now), kept, heard, known, now)
        if (due.isEmpty()) return
        val here = placeNow(now)
        // Reading the position can take seconds. A device taken off My devices meanwhile stays off.
        val stillOwn = OwnDevices.list(pairedAt(now), saved.first()).mapTo(HashSet()) { it.device.address }
        store.record(
            due.filter { (address, _) -> address in stillOwn }
                .map { (address, seenAt) -> LastSeenPolicy.next(known[address], address, seenAt, here) },
        )
    }

    /**
     * Drops the records of devices that are no longer the user's (#178): unpaired in the phone's
     * settings, or removed from the saved list by a path that did not clean up. Only right after the
     * paired devices were really read; while they cannot be, nothing is taken for gone.
     */
    private suspend fun prune(
        known: Map<String, LastSeen>,
        kept: List<SavedDevice>,
        now: Long,
    ): Map<String, LastSeen> {
        val readBefore = pairedReadAt
        val pairedList = pairedAt(now)
        if (pairedReadAt == readBefore || !pairedKnown) return known
        val own = OwnDevices.list(pairedList, kept).mapTo(HashSet()) { it.device.address }
        val gone = known.keys - own
        if (gone.isEmpty()) return known
        store.remove(gone)
        return known - gone
    }

    /** Paired devices change rarely and reading them is a call into the system: once in a while is enough. */
    private fun pairedAt(now: Long): List<PairedDevice> {
        val readAt = pairedReadAt
        if (readAt == null || now - readAt >= PAIRED_EVERY_MS || now < readAt) {
            pairedReadAt = now
            val read = paired()
            pairedKnown = read != null
            // Unreadable for now: the last list read still says which devices are the user's.
            if (read != null) pairedNow = read
        }
        return pairedNow
    }

    /** One position serves every device heard within the same minute. */
    private suspend fun placeNow(now: Long): LastSeen.Place? {
        val readAt = placeReadAt
        if (readAt == null || now - readAt >= LastSeenPolicy.MIN_GAP_MS || now < readAt) {
            placeReadAt = now
            place = locate()
        }
        return place
    }

    companion object {
        private const val CHECK_EVERY_MS = 5_000L
        private const val PAIRED_EVERY_MS = 60_000L

        /**
         * The user's devices that are being heard right now, surely enough to write down
         * ([OwnDevices.isCertain]), and whose record is old enough to write again: listed address to
         * the time of the sighting.
         */
        fun due(
            paired: List<PairedDevice>,
            saved: List<SavedDevice>,
            seen: Collection<BleDevice>,
            known: Map<String, LastSeen>,
            now: Long,
        ): List<Pair<String, Long>> {
            val sightings = OwnDevices.Sightings(seen)
            return OwnDevices.list(paired, saved).mapNotNull { own ->
                val sighting = sightings.of(own) ?: return@mapNotNull null
                val address = own.device.address
                when {
                    now - sighting.lastSeen > OwnDevices.ACTIVE_WINDOW_MS -> null
                    !OwnDevices.isCertain(own, sighting) -> null
                    !LastSeenPolicy.isDue(known[address], sighting.lastSeen) -> null
                    else -> address to sighting.lastSeen
                }
            }
        }
    }
}
