package com.gamenuri.btscanner24.core.ble

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * A loud find tone played as media through earbuds or a speaker that is connected to this phone
 * (#153). Android cannot choose the output for an app, so this only says whether the target is
 * where media goes right now, and plays there. Open for the debug demo mode.
 */
open class MediaFindTone(context: Context) {

    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)

    /**
     * Whether media played now comes out of [address]. True when a Bluetooth media output is present
     * and it is the target: by its address where Android gives one, otherwise when the target is the
     * only device connected for media.
     */
    open fun isOutput(address: String, connected: ConnectedDevices): Boolean {
        val audio = audio ?: return false
        val media = connected.mediaAddresses()
        if (address !in media) return false
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        if (outputs.isEmpty()) return false
        val byAddress = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            outputs.any { it.address.equals(address, ignoreCase = true) }
        return byAddress || media.size == 1
    }

    /**
     * Plays the tone for [durationMs] or until the caller is cancelled. Someone may be wearing the
     * earbuds, so the media volume stays at [START_VOLUME] for [QUIET_MS], then climbs slowly to
     * [TOP_VOLUME] of the maximum. [louderSteps] is how often the user asked for more; each step adds
     * [STEP_VOLUME]. The user's own volume is put back after.
     */
    open suspend fun play(durationMs: Long, louderSteps: () -> Int = { 0 }) = withContext(Dispatchers.Default) {
        val audio = audio ?: return@withContext
        val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val low = (max * START_VOLUME).roundToInt().coerceAtLeast(1)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        // Pauses music that is playing; it comes back when the tone ends.
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .build()
        audio.requestAudioFocus(focus)
        val track = buildTrack(attributes)
        try {
            var level = low
            audio.setMediaVolume(level)
            track.play()
            val startedAt = SystemClock.elapsedRealtime()
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                if (elapsed >= durationMs) break
                val next = volumeIndex(max, elapsed, louderSteps()).coerceAtLeast(low)
                if (next != level) {
                    level = next
                    audio.setMediaVolume(level)
                }
                delay(STEP_MS)
            }
        } finally {
            try {
                track.stop()
            } catch (_: IllegalStateException) {
                // Never started.
            }
            track.release()
            audio.setMediaVolume(before)
            audio.abandonAudioFocusRequest(focus)
        }
    }

    /** Quiet first, then a slow climb to the cap; only the user's "louder" taps go beyond it. */
    private fun volumeIndex(max: Int, elapsedMs: Long, louderSteps: Int): Int {
        val climb = ((elapsedMs - QUIET_MS) / RAMP_MS.toFloat()).coerceIn(0f, 1f)
        val fraction = START_VOLUME + (TOP_VOLUME - START_VOLUME) * climb + STEP_VOLUME * louderSteps
        return (max * min(1f, fraction)).roundToInt()
    }

    private fun AudioManager.setMediaVolume(index: Int) {
        try {
            setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
        } catch (_: SecurityException) {
            // Do Not Disturb can refuse volume changes; the tone still plays at the current volume.
        }
    }

    /** One second, looped: two short beeps near 3 kHz, where small earbud drivers are loudest. */
    private fun buildTrack(attributes: AudioAttributes): AudioTrack {
        val samples = ShortArray(SAMPLE_RATE)
        fillBeep(samples, startMs = 0, frequency = 2_800.0)
        fillBeep(samples, startMs = 250, frequency = 3_400.0)
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        track.write(samples, 0, samples.size)
        track.setLoopPoints(0, samples.size, -1)
        return track
    }

    private fun fillBeep(samples: ShortArray, startMs: Int, frequency: Double) {
        val first = SAMPLE_RATE * startMs / 1000
        val length = SAMPLE_RATE * BEEP_MS / 1000
        val fade = SAMPLE_RATE * FADE_MS / 1000
        for (i in 0 until length) {
            // Short fades at both ends, so the beep does not click.
            val envelope = min(1.0, min(i, length - i) / fade.toDouble())
            val value = sin(2 * PI * frequency * i / SAMPLE_RATE) * envelope
            samples[first + i] = (value * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val BEEP_MS = 180
        const val FADE_MS = 5
        const val START_VOLUME = 0.1f

        /** Where the tone stops climbing by itself; louder only when the user asks (#153). */
        const val TOP_VOLUME = 0.6f
        const val STEP_VOLUME = 0.1f
        const val QUIET_MS = 3_000L
        const val RAMP_MS = 10_000L
        const val STEP_MS = 250L
    }
}
