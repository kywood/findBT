package com.gamenuri.btscanner24.core.data

import com.gamenuri.btscanner24.core.model.AdvertSignature
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.DeviceCategory
import com.gamenuri.btscanner24.core.model.LastSeen
import com.gamenuri.btscanner24.core.model.LastSeenPolicy
import com.gamenuri.btscanner24.core.model.OwnDevices
import com.gamenuri.btscanner24.core.model.PairedDevice
import com.gamenuri.btscanner24.core.model.SavedDevice
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LastSeenRecorderTest {

    private val now = 10_000_000L
    private val buds = PairedDevice("AA:00:00:00:00:01", "Galaxy Buds2 Pro", DeviceCategory.EARBUDS)

    private fun seen(address: String, name: String?, lastSeen: Long = now) =
        BleDevice(address = address, name = name, rssi = -60, firstSeen = 0, lastSeen = lastSeen)

    private fun due(
        seen: List<BleDevice>,
        known: Map<String, LastSeen> = emptyMap(),
        saved: List<SavedDevice> = emptyList(),
    ) = LastSeenRecorder.due(listOf(buds), saved, seen, known, now)

    private val tag = SavedDevice(
        address = "BB:00:00:00:00:01",
        name = "My keys",
        category = DeviceCategory.TRACKER,
        advertisedName = "Tagly 0006",
        savedAt = 5L,
        signature = AdvertSignature(companyId = 0x0157, serviceUuids16 = setOf(0xFE95), DeviceCategory.TRACKER),
    )

    private fun tagHeard(address: String) = seen(address, "Tagly 0006").copy(
        companyId = 0x0157,
        serviceUuids16 = listOf(0xFE95),
        category = DeviceCategory.TRACKER,
    )

    @Test
    fun `a saved device under a new address is written when its advertisement still agrees`() {
        assertEquals(listOf(tag.address to now), due(listOf(tagHeard("7F:00:00:00:00:10")), saved = listOf(tag)))
    }

    @Test
    fun `the same name alone never writes a saved device`() {
        val sameModel = tagHeard("7F:00:00:00:00:11").copy(companyId = 0x004C)
        val nameOnly = seen("7F:00:00:00:00:12", "Tagly 0006")
        assertTrue(due(listOf(sameModel), saved = listOf(tag)).isEmpty())
        assertTrue(due(listOf(nameOnly), saved = listOf(tag)).isEmpty())
    }

    @Test
    fun `a saved device with nothing kept counts under its own address only`() {
        val bare = tag.copy(signature = null)
        assertTrue(due(listOf(tagHeard("7F:00:00:00:00:13")), saved = listOf(bare)).isEmpty())
        assertEquals(listOf(tag.address to now), due(listOf(tagHeard(tag.address)), saved = listOf(bare)))
    }

    @Test
    fun `a paired device being heard is written under its paired address`() {
        val heard = seen("7F:11:22:33:44:55", "Galaxy Buds2 Pro", lastSeen = now - 2_000)
        assertEquals(listOf(buds.address to now - 2_000), due(listOf(heard)))
    }

    @Test
    fun `other people's devices are never written`() {
        assertTrue(due(listOf(seen("7F:11:22:33:44:77", "Someone's speaker"))).isEmpty())
    }

    @Test
    fun `a sighting that has gone quiet is not written again`() {
        val old = seen(buds.address, null, lastSeen = now - OwnDevices.ACTIVE_WINDOW_MS - 1)
        assertTrue(due(listOf(old)).isEmpty())
    }

    @Test
    fun `a fresh record waits for the minimum gap`() {
        val heard = listOf(seen(buds.address, null))
        val fresh = mapOf(buds.address to LastSeen(buds.address, now - LastSeenPolicy.MIN_GAP_MS + 1, null))
        val stale = mapOf(buds.address to LastSeen(buds.address, now - LastSeenPolicy.MIN_GAP_MS, null))
        assertTrue(due(heard, fresh).isEmpty())
        assertEquals(listOf(buds.address to now), due(heard, stale))
    }

    /** Records in memory; [failNext] makes the next write fail like a full or broken disk. */
    private class FakeStore : LastSeenStore {
        val records = MutableStateFlow<Map<String, LastSeen>>(emptyMap())
        var failNext = false
        override val lastSeen: Flow<Map<String, LastSeen>> = records

        override suspend fun record(records: Collection<LastSeen>) {
            if (failNext) {
                failNext = false
                throw IOException("disk")
            }
            this.records.value += records.associateBy { it.address }
        }

        override suspend fun remove(addresses: Collection<String>) {
            records.value -= addresses.toSet()
        }

        override val recordsPlace: Flow<Boolean> = MutableStateFlow(true)

        override suspend fun setRecordsPlace(on: Boolean) = Unit

        override suspend fun forgetPlaces() {
            records.value = records.value.mapValues { it.value.copy(place = null) }
        }
    }

    private val home = LastSeen.Place(37.5, 127.0, 300f)

    private fun recorder(
        store: FakeStore,
        seen: Flow<Collection<BleDevice>>,
        saved: MutableStateFlow<List<SavedDevice>>,
        locate: suspend () -> LastSeen.Place? = { home },
        paired: () -> List<PairedDevice>? = { listOf(buds) },
    ) = LastSeenRecorder(seen, paired, saved, store, locate, clock = { now }, checkEveryMs = 1_000)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `records of devices that are no longer the user's are dropped`() = runTest {
        val store = FakeStore()
        val old = "CC:00:00:00:00:09"
        store.records.value = mapOf(old to LastSeen(old, 5L, home), tag.address to LastSeen(tag.address, 6L, null))
        val seen = MutableStateFlow<Collection<BleDevice>>(emptyList())
        val job = launch { recorder(store, seen, MutableStateFlow(listOf(tag))).run() }
        advanceTimeBy(1_500)
        assertEquals(setOf(tag.address), store.records.value.keys)
        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `nothing is dropped while the paired devices cannot be read`() = runTest {
        val store = FakeStore()
        store.records.value = mapOf(buds.address to LastSeen(buds.address, 5L, home))
        val seen = MutableStateFlow<Collection<BleDevice>>(emptyList())
        val job = launch { recorder(store, seen, MutableStateFlow(emptyList()), paired = { null }).run() }
        advanceTimeBy(1_500)
        assertEquals(setOf(buds.address), store.records.value.keys)
        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `run writes the user's devices that are heard, with the place`() = runTest {
        val store = FakeStore()
        val seen = MutableStateFlow<Collection<BleDevice>>(listOf(seen(buds.address, null), tagHeard(tag.address)))
        val job = launch { recorder(store, seen, MutableStateFlow(listOf(tag))).run() }
        advanceTimeBy(1_500)
        assertEquals(
            mapOf(buds.address to LastSeen(buds.address, now, home), tag.address to LastSeen(tag.address, now, home)),
            store.records.value,
        )
        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a device taken off the list while the place is read is not written back`() = runTest {
        val store = FakeStore()
        val saved = MutableStateFlow(listOf(tag))
        val seen = MutableStateFlow<Collection<BleDevice>>(listOf(tagHeard(tag.address)))
        val removing: suspend () -> LastSeen.Place? = {
            saved.value = emptyList()
            home
        }
        val job = launch { recorder(store, seen, saved, removing).run() }
        advanceTimeBy(1_500)
        assertTrue(store.records.value.isEmpty())
        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a record removed by the user is not restored from an old copy`() = runTest {
        val store = FakeStore()
        val seen = MutableStateFlow<Collection<BleDevice>>(listOf(seen(buds.address, null)))
        val job = launch { recorder(store, seen, MutableStateFlow(emptyList())).run() }
        advanceTimeBy(1_500)
        store.remove(buds.address)
        // Heard again a moment later: written as new, not skipped as "written less than a minute ago".
        seen.value = listOf(seen(buds.address, null, lastSeen = now + 1))
        advanceTimeBy(1_500)
        assertEquals(LastSeen(buds.address, now + 1, home), store.records.value[buds.address])
        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a failed write does not end the recorder`() = runTest {
        val store = FakeStore().apply { failNext = true }
        val seen = MutableStateFlow<Collection<BleDevice>>(listOf(seen(buds.address, null)))
        val job = launch { recorder(store, seen, MutableStateFlow(emptyList())).run() }
        advanceTimeBy(1_500)
        assertTrue(store.records.value.isEmpty())
        seen.value = listOf(seen(buds.address, null, lastSeen = now + 1))
        advanceTimeBy(1_500)
        assertEquals(setOf(buds.address), store.records.value.keys)
        job.cancel()
    }
}
