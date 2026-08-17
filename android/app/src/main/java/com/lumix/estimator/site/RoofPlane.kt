package com.lumix.estimator.site

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
    val panelLayout: PanelLayout? = null
)
