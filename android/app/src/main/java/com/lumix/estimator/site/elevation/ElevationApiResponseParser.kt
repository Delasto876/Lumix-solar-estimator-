package com.lumix.estimator.site.elevation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Pure JSON -> (status, reading) mapping for Google's Elevation API response, kept separate from
 * [GoogleElevationApiClient]'s own HTTP call so this logic is testable/diagnosable with a plain
 * JSON string, no network involved — same reason [com.lumix.estimator.site.solarapi
 * .SolarApiResponseParser] is split out from its own client.
 */
internal object ElevationApiResponseParser {

    @Serializable
    private data class WireLocation(val lat: Double = 0.0, val lng: Double = 0.0)

    @Serializable
    private data class WireResult(
        val elevation: Double = 0.0,
        val resolution: Double? = null,
        val location: WireLocation = WireLocation()
    )

    @Serializable
    private data class WireElevationResponse(
        val results: List<WireResult> = emptyList(),
        val status: String = ""
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @return `status` (Google's own response status string, e.g. "OK"/"ZERO_RESULTS") paired with
     * the first result's reading — Google's Elevation API returns at most one result for a single-
     * point request. A null reading is expected/correct whenever `status` isn't "OK", never
     * backfilled with a guess. Returns null only if [rawJson] doesn't parse as an Elevation API
     * response at all (malformed/unexpected shape) — the caller maps that to
     * [ElevationResult.Unavailable].
     */
    fun parse(rawJson: String): Pair<String, ElevationReading?>? {
        val wire = runCatching { json.decodeFromString<WireElevationResponse>(rawJson) }.getOrNull() ?: return null
        val reading = wire.results.firstOrNull()?.let { ElevationReading(it.elevation, it.resolution) }
        return wire.status to reading
    }
}
