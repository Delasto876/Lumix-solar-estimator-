package com.lumix.estimator.site

import kotlinx.serialization.Serializable

/**
 * One traced roof section. Areas and azimuth are estimates derived from a hand-traced
 * satellite polygon — see [RoofGeometryEngine][com.lumix.estimator.site.geometry.RoofGeometryEngine]
 * for how each field is computed, and the module README for the accuracy caveats that
 * apply to all of them (satellite tracing is a preliminary estimate, not a certified survey).
 *
 * @param suggestedAzimuthDegrees a geometry-derived hint (perpendicular to the polygon's
 *   longest edge) — inherently ambiguous by 180° from a flat traced shape alone, so it is
 *   never treated as authoritative.
 * @param azimuthDegrees the user-confirmed facing direction; null until confirmed.
 * @param shadingFactor 0f..1f fraction of exposure retained after shading (1.0 = no shading).
 */
@Serializable
data class RoofPlane(
    val id: String,
    val label: String,
    val vertices: List<GeoPoint>,
    val horizontalAreaM2: Double,
    val roofAreaM2: Double,
    val usableAreaM2: Double,
    val suggestedAzimuthDegrees: Double?,
    val azimuthDegrees: Double?,
    val pitchDegrees: Double?,
    val setbackMeters: Double = 0.5,
    val exclusionZones: List<List<GeoPoint>> = emptyList(),
    val shadingFactor: Double = 1.0,
    val panelLayout: PanelLayout? = null,
    /**
     * Site Survey / Solar Mapping round: a real annual sunshine-hours figure from Google's Solar
     * API for this segment (see [com.lumix.estimator.site.solarapi.SolarApiRoofSegment
     * .medianAnnualSunshineHours]'s own doc) — null for every hand-traced or manually-entered roof
     * plane (the vast majority of existing/future roof planes), since that data only exists when
     * this plane came from a real Solar API roof segment. Purely additive/informational so far;
     * not yet consumed by [shadingFactor] or [com.lumix.estimator.site.geometry.RoofScoreCalculator]
     * — a later phase turns this into the spec's own Excellent/Good/Moderate/Poor/Unsuitable
     * suitability gradient.
     */
    val solarApiAnnualSunshineHours: Double? = null
)
