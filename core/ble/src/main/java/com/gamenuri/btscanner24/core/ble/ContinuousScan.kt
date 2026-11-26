package com.gamenuri.btscanner24.core.ble

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * When a running platform scan should be thrown away and started again (#116, #117).
 *
 * Android gives no error in the cases that matter: a start beyond 5 in 30 s is accepted but never
 * delivers anything, some phones stop delivering after a while, and a scan that has run for 30
 * minutes is quietly turned into one that only hears what other apps scan for. So a scan that has
 * been silent for [silenceMs], or has run for [maxRunMs], is restarted.
 */
class ScanWatch(val silenceMs: Long = SILENCE_MS, val maxRunMs: Long = MAX_RUN_MS) {
    fun shouldRestart(now: Long, startedAt: Long, lastResultAt: Long): Boolean =
        now - maxOf(startedAt, lastResultAt) > silenceMs || now - startedAt > maxRunMs

    companion object {
        const val SILENCE_MS = 12_000L
        const val MAX_RUN_MS = 20 * 60_000L

        /** For a filtered scan silence is normal (the device is away); only the 30-minute rule applies. */
        val FILTERED = ScanWatch(silenceMs = Long.MAX_VALUE / 2)
    }
}

/**
 * Scans until the collector goes away. Unlike [BleScanner.scan] it waits out the platform's start
 * limit first, starts again when the scan dies quietly (see [ScanWatch]) and retries after
 * "scanning too frequently" or an internal error. Failures that need the user (permission,
 * Bluetooth or Location off, no BLE) are emitted once and end the flow.
 */
fun BleScanner.scanContinuously(
    watch: ScanWatch = ScanWatch(),
    elapsed: () -> Long = SystemClock::elapsedRealtime,
    /** The scan to keep alive; [BleScanner.watch] for the protection service's filtered one (#163). */
    source: () -> Flow<ScanEvent> = { scan() },
): Flow<ScanEvent> = channelFlow {
    while (isActive) {
        val cooldown = startCooldownMillis()
        if (cooldown > 0) delay(cooldown)
        val startedAt = elapsed()
        val lastResultAt = AtomicLong(startedAt)
        val failure = AtomicReference<ScanFailure?>(null)
        val run = launch {
            source().collect { event ->
                when (event) {
                    is ScanEvent.Result -> {
                        lastResultAt.set(elapsed())
                        send(event)
                    }
                    is ScanEvent.Failed -> failure.set(event.reason)
                }
            }
        }
        while (run.isActive && !watch.shouldRestart(elapsed(), startedAt, lastResultAt.get())) {
            delay(WATCH_INTERVAL_MS)
        }
        run.cancelAndJoin()
        when (val reason = failure.get()) {
            null -> Unit
            ScanFailure.TOO_FREQUENT, ScanFailure.INTERNAL -> delay(RETRY_DELAY_MS)
            else -> {
                send(ScanEvent.Failed(reason))
                return@channelFlow
            }
        }
    }
}

private const val WATCH_INTERVAL_MS = 1_000L
private const val RETRY_DELAY_MS = 6_000L
