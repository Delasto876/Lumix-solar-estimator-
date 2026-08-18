package com.lumix.estimator.map

import android.content.Context
import android.location.Geocoder
import com.lumix.estimator.domain.Catalog
import com.lumix.estimator.site.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * [GeocodingProvider.search] uses `android.location.Geocoder` — an Android framework service
 * (not the Google Maps Geocoding API), needing no API key or billing account, same as this
 * screen already relied on before this round. [GeocodingProvider.suggestKnownPlaces] is pure
 * local string matching against [Catalog.parishTowns] — the "Support Jamaica parish/town
 * searches" requirement, satisfied without needing this app to maintain its own coordinate
 * table (which would mean fabricating lat/lon data this app has no real source for).
 */
class AndroidGeocodingProvider(private val context: Context) : GeocodingProvider {

    override suspend fun search(query: String): List<GeocodeResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        // If the query text-matches a known Jamaica town, search with the parish appended for a
        // more precise geocoder hit (e.g. "Junction" alone is ambiguous; "Junction, St.
        // Elizabeth, Jamaica" is not) — still resolves the real coordinate over the network,
        // never a hardcoded one.
        val knownMatch = Catalog.parishTowns.entries.firstOrNull { (_, towns) ->
            towns.any { it.equals(trimmed, ignoreCase = true) }
        }
        val (matchedParish, matchedTown) = if (knownMatch != null) {
            knownMatch.key to knownMatch.value.first { it.equals(trimmed, ignoreCase = true) }
        } else null to null

        val searchText = when {
            matchedTown != null -> "$matchedTown, $matchedParish, Jamaica"
            trimmed.contains("jamaica", ignoreCase = true) -> trimmed
            else -> "$trimmed, Jamaica"
        }

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocationName(searchText, 5) ?: emptyList()
            addresses.map { address ->
                GeocodeResult(
                    label = address.getAddressLine(0) ?: searchText,
                    point = GeoPoint(address.latitude, address.longitude),
                    parish = matchedParish,
                    town = matchedTown
                )
            }
        } catch (e: Exception) {
            // No connection, no geocoder backend on this device, or a bad query — never throws
            // up to the caller; an empty result list is the "not found" signal.
            emptyList()
        }
    }

    override fun suggestKnownPlaces(query: String): List<KnownPlace> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        return Catalog.parishTowns.flatMap { (parish, towns) ->
            towns.filter { it.contains(trimmed, ignoreCase = true) }.map { KnownPlace(it, parish) }
        }.take(8)
    }
}
