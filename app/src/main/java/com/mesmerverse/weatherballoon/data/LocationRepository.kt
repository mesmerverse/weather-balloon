package com.mesmerverse.weatherballoon.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * One-shot device position via the platform LocationManager — no Play
 * Services, works with GrapheneOS's own location stack. Coarse accuracy is
 * requested and is plenty: forecasts are grid-based anyway.
 */
class LocationRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Returns (lat, lon) or null if unavailable within the timeout. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Pair<Double, Double>? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        ).firstOrNull { it in manager.allProviders && manager.isProviderEnabled(it) }
            ?: return null

        val fresh = withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                    cont.resume(location)
                }
            }
        }
        // A cached fix is fine for a forecast, but not an arbitrarily old one:
        // it could silently be from wherever the phone was days ago.
        val location = fresh ?: manager.getLastKnownLocation(provider)
            ?.takeIf { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos < MAX_FIX_AGE_NANOS }
            ?: return null
        return location.latitude to location.longitude
    }

    /**
     * Best-effort town name for the header. Where a geocoder backend exists
     * (stock Android: Play Services), this is a network lookup — callers must
     * pass coordinates already rounded to ~1 km, never a raw fix. GrapheneOS
     * ships no backend, so this returns null there and the UI says
     * "My location".
     */
    suspend fun placeName(lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
            withTimeoutOrNull(5_000) {
                suspendCancellableCoroutine { cont ->
                    Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1) { found ->
                        val address = found.firstOrNull()
                        cont.resume(address?.locality ?: address?.subAdminArea)
                    }
                }
            }
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()
    }

    private companion object {
        const val MAX_FIX_AGE_NANOS = 60L * 60 * 1_000_000_000 // 1 hour
    }
}
