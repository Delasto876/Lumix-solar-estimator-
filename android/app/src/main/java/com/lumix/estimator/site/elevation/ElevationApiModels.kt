package com.lumix.estimator.site.elevation

/**
 * Site Survey / Solar Mapping round (spec "Use elevation/topography data where useful for site
 * planning purposes... this must never be presented as a structural survey"): a real ground
 * elevation reading at one map point, from Google's Elevation API — informational context for a
 * site visit (e.g. judging whether a long cable run also crosses a slope), never a substitute for
 * an actual structural or topographic survey.
 */
data class ElevationReading(
    val elevationMeters: Double,
    /** Google's own reported horizontal resolution for this data point, in meters — a bigger number means coarser underlying terrain data, surfaced rather than presenting every reading as equally precise. */
    val resolutionMeters: Double?
)

/**
 * Every outcome [ElevationApiClient.elevationAt] can return — mirrors [com.lumix.estimator.site
 * .solarapi.SolarApiResult]'s own three-way shape for the same reason: "no data for this location"
 * (Google's own `ZERO_RESULTS` status) is a real, expected outcome distinct from an operational
 * failure (no key configured, network error, malformed response) — neither is ever silently turned
 * into a fabricated number.
 */
sealed class ElevationResult {
    data class Available(val reading: ElevationReading) : ElevationResult()
    data class NoData(val message: String) : ElevationResult()
    data class Unavailable(val reason: String) : ElevationResult()
}
