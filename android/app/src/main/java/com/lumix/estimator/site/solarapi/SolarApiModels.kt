package com.lumix.estimator.site.solarapi

import com.lumix.estimator.site.GeoPoint

/**
 * Site Survey / Solar Mapping round (spec "When available, use Building Insights to obtain:
 * Building location, Roof segments, Roof segment area, Roof orientation/azimuth, Roof pitch,
 * Sunshine/sunniness information, Solar potential information"): one real roof segment as Google's
 * Solar API `buildingInsights.solarPotential.roofSegmentStats` actually reports it — a rectangle
 * (this API reports a bounding box per segment, not an arbitrary building-outline polygon), real
 * azimuth (not the geometry-only, 180°-ambiguous guess a hand-traced polygon alone can produce —
 * see [com.lumix.estimator.site.geometry.RoofGeometryEngine.suggestAzimuthCandidates]'s own doc),
 * and real pitch, all derived from Google's own imagery/DSM analysis for this specific building.
 */
data class SolarApiRoofSegment(
    val pitchDegrees: Double?,
    val azimuthDegrees: Double?,
    val areaMeters2: Double,
    /**
     * Annual sunshine hours across this segment, from worst-lit to best-lit tenth (Google's own
     * "sunshine quantiles" — typically 10 values). A real, imagery/shadow-model-derived figure,
     * not this app's own [com.lumix.estimator.domain.SolarResource] parish-level estimate — but
     * still an annual average, not an hour-by-hour shade prediction (see [medianAnnualSunshineHours]'s
     * own doc, and the spec's own "Do not claim exact hourly shade prediction from ordinary
     * satellite imagery alone").
     */
    val sunshineQuantiles: List<Double>,
    val boundingBoxSw: GeoPoint,
    val boundingBoxNe: GeoPoint,
    val center: GeoPoint
) {
    /**
     * The middle quantile as one representative annual-sunshine-hours figure for this segment —
     * a genuine measured/modeled number from Google's own imagery, suitable as an INPUT to a
     * suitability score, but still an estimate ("Estimated shading — verify on site" per the
     * spec's own wording), not a certified survey figure. Null when Google returned no quantile
     * data for this segment (a real, if unusual, partial-data case — never backfilled with a
     * guess).
     */
    val medianAnnualSunshineHours: Double? get() =
        sunshineQuantiles.takeIf { it.isNotEmpty() }?.let { it[it.size / 2] }

    /** The bounding box's own 4 corners as a real map polygon — an axis-aligned rectangle approximating this segment's real footprint, editable afterward with the same vertex tools a hand-traced roof already has (add/remove/drag) once added to a [com.lumix.estimator.site.SolarSite]. */
    val boundingBoxVertices: List<GeoPoint> get() = listOf(
        GeoPoint(boundingBoxSw.latitude, boundingBoxSw.longitude),
        GeoPoint(boundingBoxSw.latitude, boundingBoxNe.longitude),
        GeoPoint(boundingBoxNe.latitude, boundingBoxNe.longitude),
        GeoPoint(boundingBoxNe.latitude, boundingBoxSw.longitude)
    )
}

/** One building's real Solar API result — see [SolarApiRoofSegment]'s own doc for what each field actually represents. */
data class SolarApiBuildingInsights(
    val center: GeoPoint,
    /** e.g. "2023-06" from Google's own `imageryDate` — how current the underlying imagery is, surfaced so an installer can judge whether a site visit is warranted for anything that might have changed since (a new extension, a removed tree, a new neighboring structure). */
    val imageryDateLabel: String?,
    /** Google's own `imageryQuality` enum value (e.g. "HIGH"/"MEDIUM"/"LOW") — lower quality means every geometry/sunshine figure below is less reliable, surfaced rather than silently treated as equally trustworthy regardless. */
    val imageryQuality: String?,
    val maxArrayAreaMeters2: Double?,
    val maxSunshineHoursPerYear: Double?,
    val roofSegments: List<SolarApiRoofSegment>
)

/**
 * Every outcome [SolarApiClient.fetchBuildingInsights] can return — deliberately three-way, not a
 * plain nullable, because "no coverage at this location" (a real, common, expected outcome —
 * Google's own Solar API coverage is far from universal) and "couldn't reach the service/no key
 * configured" (an operational problem, not a coverage gap) call for different UI messaging. The
 * spec's own "Do NOT assume Solar API coverage exists everywhere... fall back gracefully to manual
 * roof polygon drawing... Never fabricate roof geometry, shading or solar data" applies to every
 * non-[Available] branch equally — the caller's only correct response to either is to fall back to
 * [com.lumix.estimator.site.map.RoofDrawingService]'s existing manual tracing, never to invent a
 * substitute result.
 */
sealed class SolarApiResult {
    data class Available(val insights: SolarApiBuildingInsights) : SolarApiResult()
    /** Google's own confirmed "no data for this location" response (HTTP 404 / NOT_FOUND) — a normal, expected outcome for real-world coverage gaps, not an error to alarm the installer over. */
    data class NoCoverage(val message: String) : SolarApiResult()
    /** No API key configured, a network failure, or an unexpected/malformed response — an operational problem the installer may be able to fix (check connectivity, check the key), unlike [NoCoverage]. */
    data class Unavailable(val reason: String) : SolarApiResult()
}
