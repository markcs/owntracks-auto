package com.owntracks.auto.domain

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Reverse-geocodes coordinates to a human-readable address using Android's
 * built-in [Geocoder]. This uses the device's own geocoding backend rather
 * than Nominatim, so it adds no load to the owntrack-rs server or OSM, and
 * keeps the app self-contained.
 *
 * Results are cached in-memory per rounded coordinate so repeated lookups of
 * the same endpoint are free.
 */
class AddressResolver(context: Context) {

    private val appContext = context.applicationContext
    private val cache = HashMap<String, String?>()

    /** Whether the device has a geocoding backend available at all. */
    private val available: Boolean = Geocoder.isPresent()

    suspend fun resolve(lat: Double, lon: Double): String? {
        if (!available) return null
        val key = "%.4f,%.4f".format(lat, lon)
        cache[key]?.let { return it }
        if (cache.containsKey(key)) return null // cached negative result

        val result = runCatching { lookup(lat, lon) }.getOrNull()
        cache[key] = result
        return result
    }

    private suspend fun lookup(lat: Double, lon: Double): String? {
        val geocoder = Geocoder(appContext)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCoroutine { cont ->
                geocoder.getFromLocation(lat, lon, 1) { addresses ->
                    cont.resume(addresses.firstOrNull()?.let(::format))
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.let(::format)
            }
        }
    }

    /** Build a short, readable label from an [Address]. */
    private fun format(a: Address): String? {
        // Prefer a street-level line; fall back to locality / feature.
        val line = a.getAddressLine(0)
        if (!line.isNullOrBlank()) return line
        val parts = listOfNotNull(
            a.thoroughfare,
            a.locality ?: a.subAdminArea,
            a.adminArea,
        ).filter { it.isNotBlank() }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: a.featureName
    }
}
