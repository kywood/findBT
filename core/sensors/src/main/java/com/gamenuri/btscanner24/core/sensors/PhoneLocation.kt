package com.gamenuri.btscanner24.core.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Where the phone is, as far as it could tell. */
data class PlaceFix(val latitude: Double, val longitude: Double, val accuracyMeters: Float?)

/**
 * The phone's own position, read once when asked (#162). Uses the platform's [LocationManager] so no
 * Google Play services are needed, never asks for a permission itself and never keeps listening:
 * without a permission, a provider or a fix in time it simply has no answer.
 */
class PhoneLocation(context: Context) {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(LocationManager::class.java)

    /** Approximate location is enough; a precise one (the scan's permission on Android 11 and older) counts too. */
    fun hasPermission(): Boolean = PERMISSIONS.any {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    /** The position now, or a recent one the system still remembers; null when there is none to be had. */
    @SuppressLint("MissingPermission") // Checked by hasPermission() first; a revoke in between is caught.
    suspend fun current(): PlaceFix? {
        val locations = manager ?: return null
        if (!hasPermission()) return null
        return try {
            val providers = PROVIDERS.filter { it in locations.allProviders && locations.isProviderEnabled(it) }
            val fresh = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                providers.firstNotNullOfOrNull { request(locations, it) }
            } else {
                null
            }
            val location = fresh ?: providers.mapNotNull { locations.getLastKnownLocation(it) }
                .filter { ageMs(it) <= MAX_AGE_MS }
                .minByOrNull { ageMs(it) }
            location?.let { PlaceFix(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else null) }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            // A provider that went away between the two calls.
            null
        }
    }

    @SuppressLint("MissingPermission", "NewApi") // Called on Android 11 and newer only, after the permission check.
    private suspend fun request(locations: LocationManager, provider: String): Location? =
        withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                locations.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            }
        }

    private fun ageMs(location: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / NANOS_PER_MILLI

    private companion object {
        val PERMISSIONS = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

        // Cell and Wi-Fi only, never GPS (#178): quick, works indoors and no closer than the app needs.
        // "fused" is LocationManager.FUSED_PROVIDER (Android 12+), which honours an approximate permission.
        val PROVIDERS = listOf(LocationManager.NETWORK_PROVIDER, "fused")
        const val REQUEST_TIMEOUT_MS = 8_000L
        const val MAX_AGE_MS = 2 * 60_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
