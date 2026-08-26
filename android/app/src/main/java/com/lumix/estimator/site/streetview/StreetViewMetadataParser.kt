package com.lumix.estimator.site.streetview

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Pure JSON -> status mapping for Google's Street View Static API `metadata` endpoint, kept
 * separate from [GoogleStreetViewClient]'s own HTTP call so this logic is testable/diagnosable with
 * a plain JSON string, no network involved — same reason [com.lumix.estimator.site.solarapi
 * .SolarApiResponseParser]/[com.lumix.estimator.site.elevation.ElevationApiResponseParser] are each
 * split out from their own clients.
 */
internal object StreetViewMetadataParser {

    @Serializable
    private data class WireMetadataResponse(val status: String = "")

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @return the real `status` field (e.g. "OK" / "ZERO_RESULTS" / "REQUEST_DENIED" /
     * "OVER_QUERY_LIMIT"), or null only if [rawJson] doesn't parse as a metadata response at all
     * (malformed/unexpected shape) — the caller maps that to [StreetViewResult.Unavailable].
     */
    fun parseStatus(rawJson: String): String? =
        runCatching { json.decodeFromString<WireMetadataResponse>(rawJson) }.getOrNull()?.status
}
