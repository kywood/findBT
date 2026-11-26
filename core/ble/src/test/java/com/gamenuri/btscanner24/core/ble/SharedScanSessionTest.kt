package com.gamenuri.btscanner24.core.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedScanSessionTest {
    private var starts = 0
    private var stops = 0
    private var failWith: ScanFailure? = null

    /** A scan that runs until it is cancelled, or reports [failWith] and ends like the real one. */
    private fun scan(): Flow<ScanEvent> = flow {
        starts++
        val reason = failWith
        if (reason != null) {
            emit(ScanEvent.Failed(reason))
            return@flow
        }
        try {
            awaitCancellation()
        } finally {
            stops++
        }
    }

    // Unconfined: the scan starts and stops inside the call that asked for it, so the test needs no waiting.
    private val session = SharedScanSession(
        scan = ::scan,
        onResult = {},
        permission = { true },
        scope = CoroutineScope(Dispatchers.Unconfined),
    )

    @Test
    fun `two holders share one scan, which stops with the last of them`() {
        val first = session.acquire()
        val second = session.acquire()
        assertEquals(1, starts)
        assertTrue(session.isScanning)
        first.close()
        assertEquals(0, stops)
        second.close()
        assertEquals(1, stops)
        assertFalse(session.isScanning)
    }

    @Test
    fun `closing a handle twice does not take the scan from another holder`() {
        val first = session.acquire()
        val second = session.acquire()
        first.close()
        first.close()
        assertTrue(session.isScanning)
        second.close()
    }

    @Test
    fun `a failure is kept until the scan is tried again`() {
        failWith = ScanFailure.BLUETOOTH_OFF
        val handle = session.acquire()
        assertEquals(ScanFailure.BLUETOOTH_OFF, session.failure.value)
        assertFalse(session.isScanning)
        failWith = null
        session.retry()
        assertNull(session.failure.value)
        assertTrue(session.isScanning)
        assertEquals(2, starts)
        handle.close()
    }

    @Test
    fun `retry does nothing while the scan runs or nobody holds it`() {
        session.retry()
        assertEquals(0, starts)
        val handle = session.acquire()
        session.retry()
        assertEquals(1, starts)
        handle.close()
    }

    @Test
    fun `the next holder starts a scan that had failed and the failure goes with the last one`() {
        failWith = ScanFailure.LOCATION_OFF
        val first = session.acquire()
        failWith = null
        val second = session.acquire()
        assertTrue(session.isScanning)
        assertNull(session.failure.value)
        first.close()
        second.close()
        assertNull(session.failure.value)
    }
}
