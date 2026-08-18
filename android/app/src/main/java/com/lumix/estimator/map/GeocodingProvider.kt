package com.lumix.estimator.map

import com.lumix.estimator.site.GeoPoint

data class GeocodeResult(
    val label: String,
    val point: GeoPoint,
    /** Real values from [com.lumix.estimator.domain.Catalog.parishTowns] when this result matched a known Jamaica parish/town, so the caller never has to re-derive them from free text. */
    val parish: String? = null,
    val town: String? = null
)

/**
 * Address/place search — a separate abstraction so the map screen never calls
 * `android.location.Geocoder` (or any future geocoding backend) directly.
 * [AndroidGeocodingProvider] is the current implementation: Android's own built-in geocoder,
 * which needs no separate API key or billing account (it's a framework service, distinct from
 * the Google Maps Geocoding API), plus a local, offline, always-available Jamaica parish/town
 * name suggester built from this app's own already-real [com.lumix.estimator.domain.Catalog.parishTowns]
 * data (never fabricated coordinates — town/parish names only; resolving an actual coordinate
 * for a selected suggestion still goes through the real network geocoder).
 */
interface GeocodingProvider {
    /** Network geocode — resolves free text (an address, or a Jamaica town/parish name) to real coordinates. Empty list on no match or no connection; never throws. */
    suspend fun search(query: String): List<GeocodeResult>

    /** Offline, synchronous: known Jamaica town/parish names whose label contains [query] (case-insensitive) — for instant autocomplete while typing, before any network call. */
    fun suggestKnownPlaces(query: String): List<KnownPlace>
}

/** One real (town, parish) pair from [com.lumix.estimator.domain.Catalog.parishTowns] — a name suggestion only, not a coordinate. */
data class KnownPlace(val town: String, val parish: String) {
    val label: String get() = "$town, $parish"
}
