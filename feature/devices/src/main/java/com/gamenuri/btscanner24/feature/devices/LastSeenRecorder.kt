package com.gamenuri.btscanner24.feature.devices

import com.gamenuri.btscanner24.core.ble.BondedDevices
import com.gamenuri.btscanner24.core.ble.DeviceRepository
import com.gamenuri.btscanner24.core.data.LastSeenRepository
import com.gamenuri.btscanner24.core.data.SavedDevicesRepository
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.LastSeen
import com.gamenuri.btscanner24.core.model.LastSeenPolicy
import com.gamenuri.btscanner24.core.model.PairedDevice
import com.gamenuri.btscanner24.core.model.SavedDevice
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample

/**
 * Writes down when and where the user's own devices (paired or saved) were last heard (#162). It
 * only listens to what a scan already running finds, so it records while the app is on screen and
 * never starts a scan, keeps the phone awake or asks for anything itself. Other people's devices
 * are never recorded.
 */
@OptIn(FlowPreview::class)
class LastSeenRecorder(
    private val devices: DeviceRepository,
    private val bondedDevices: BondedDevices,
    private val savedDevices: SavedDevicesRepository,
    private val store: LastSeenRepository,
    /** The phone's position now, or null without permission or a fix. */
    private val locate: suspend () -> LastSeen.Place?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var paired: List<PairedDevice> = emptyList()
    private var pairedReadAt = Long.MIN_VALUE
    private var place: LastSeen.Place? = null
    private var placeReadAt = Long.MIN_VALUE

    /** Runs until cancelled. */
    suspend fun run() {
        val known = HashMap(store.lastSeen.first())
        combine(devices.devices.sample(CHECK_EVERY_MS), savedDevices.saved) { seen, saved -> seen to saved }
            .collect { (seen, saved) ->
                val now = clock()
                val due = due(pairedNow(now), saved, seen.values, known, now)
                if (due.isEmpty()) return@collect
                val here = placeNow(now)
                val records = due.map { (address, seenAt) ->
                    LastSeenPolicy.next(known[address], address, seenAt, here)
                }
                store.record(records)
                for (record in records) known[record.address] = record
            }
    }

    /** Paired devices change rarely and reading them is a call into the system: once in a while is enough. */
    private fun pairedNow(now: Long): List<PairedDevice> {
        if (now - pairedReadAt >= PAIRED_EVERY_MS || now < pairedReadAt) {
            pairedReadAt = now
            paired = if (bondedDevices.hasPermission()) bondedDevices.list() else emptyList()
        }
        return paired
    }

    /** One position serves every device heard within the same minute. */
    private suspend fun placeNow(now: Long): LastSeen.Place? {
        if (now - placeReadAt >= LastSeenPolicy.MIN_GAP_MS || now < placeReadAt) {
            placeReadAt = now
            place = locate()
        }
        return place
    }

    companion object {
        private const val CHECK_EVERY_MS = 5_000L
        private const val PAIRED_EVERY_MS = 60_000L

        /**
         * The user's devices that are being heard right now and whose record is old enough to write
         * again, as listed address to the time of the sighting.
         *
         * The My devices tab also lists a device heard under another address when only its name is the
         * same, which a neighbour's device of the same model would be too. That is not enough to write a
         * place down: a saved device heard under a new address counts only when the company ID, service
         * UUIDs and type kept at saving agree as well ([AdvertSignature]); with nothing kept, only its
         * own address counts. Paired devices are bonded to this phone and keep counting by name.
         */
        internal fun due(
            paired: List<PairedDevice>,
            saved: List<SavedDevice>,
            seen: Collection<BleDevice>,
            known: Map<String, LastSeen>,
            now: Long,
        ): List<Pair<String, Long>> {
            val state = MyDevices.build(paired, seen, hidden = emptySet(), now = now, saved = saved) { null }
            val signatures = saved.associate { it.address to it.signature }
            return (state.saved + state.nearby + state.notNearby + state.others).mapNotNull { item ->
                val sighting = item.sighting ?: return@mapNotNull null
                if (now - sighting.lastSeen > MyDevices.ACTIVE_WINDOW_MS) return@mapNotNull null
                val address = item.paired.address
                val sameAddress = sighting.address == address
                if (item.isSaved && !sameAddress && signatures[address]?.matches(sighting) != true) {
                    return@mapNotNull null
                }
                if (LastSeenPolicy.isDue(known[address], sighting.lastSeen)) address to sighting.lastSeen else null
            }
        }
    }
}
