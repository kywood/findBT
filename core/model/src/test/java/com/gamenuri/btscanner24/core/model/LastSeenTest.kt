package com.gamenuri.btscanner24.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LastSeenTest {
    private val place = LastSeen.Place(37.5665, 126.978, 850f)

    @Test
    fun `records survive storage with and without a place`() {
        val records = listOf(
            LastSeen("AA:BB:CC:DD:EE:01", 1_000L, place),
            LastSeen("AA:BB:CC:DD:EE:02", 2_000L),
            LastSeen("AA:BB:CC:DD:EE:03", 3_000L, LastSeen.Place(-33.9, 18.4)),
        )
        assertEquals(records.associateBy { it.address }, LastSeenCodec.decode(LastSeenCodec.encode(records)))
    }

    @Test
    fun `damaged lines are skipped`() {
        val good = LastSeenCodec.encode(listOf(LastSeen("AA:01", 5L, place)))
        val decoded = LastSeenCodec.decode("garbage\n9\tAA:02\t7\t\t\t\n$good\n1\t\t5\t\t\t")
        assertEquals(setOf("AA:01"), decoded.keys)
        assertTrue(LastSeenCodec.decode("").isEmpty())
    }

    @Test
    fun `a device that stays nearby is written at most once a minute`() {
        val previous = LastSeen("AA:01", 100_000L)
        assertTrue(LastSeenPolicy.isDue(null, 0L))
        assertFalse(LastSeenPolicy.isDue(previous, 100_000L + LastSeenPolicy.MIN_GAP_MS - 1))
        assertTrue(LastSeenPolicy.isDue(previous, 100_000L + LastSeenPolicy.MIN_GAP_MS))
    }

    @Test
    fun `a sighting without a place keeps a fresh one and drops an old one`() {
        val previous = LastSeen("AA:01", 100_000L, place)
        val soon = LastSeenPolicy.next(previous, "AA:01", 100_000L + 90_000L, null)
        assertEquals(place, soon.place)
        assertEquals(190_000L, soon.seenAt)
        val late = LastSeenPolicy.next(previous, "AA:01", 100_000L + LastSeenPolicy.PLACE_KEEP_MS + 1, null)
        assertNull(late.place)
    }

    @Test
    fun `a new place replaces the old one`() {
        val moved = LastSeen.Place(35.1, 129.0, 30f)
        assertEquals(moved, LastSeenPolicy.next(LastSeen("AA:01", 1L, place), "AA:01", 2L, moved).place)
    }

    @Test
    fun `a place is kept to about a hundred metres however well the phone knew it`() {
        val place = LastSeen.Place.coarse(37.566535, 126.977969, accuracyMeters = 4f)
        assertEquals(37.567, place.latitude, 1e-9)
        assertEquals(126.978, place.longitude, 1e-9)
        assertEquals(100f, place.accuracyMeters)
        assertEquals(800f, LastSeen.Place.coarse(-33.8568, 151.2153, 800f).accuracyMeters)
        assertEquals(-33.857, LastSeen.Place.coarse(-33.8568, 151.2153, null).latitude, 1e-9)
    }
}
