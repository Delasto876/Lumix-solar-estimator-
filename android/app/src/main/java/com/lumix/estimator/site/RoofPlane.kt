package com.lumix.estimator.site

import com.lumix.estimator.site.geometry.RoofExclusionZone
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
    /**
     * Site Survey / Solar Mapping round: real, individually-typed, drawn obstruction polygons
     * (chimney, vent, skylight, ...) — see [RoofExclusionZone]'s own doc. Was previously
     * `List<List<GeoPoint>>` (untyped raw polygons) but was never actually populated by any real
     * code path — [RoofGeometryEngine.usableAreaM2] and [PanelLayoutOptimizer.Input.exclusionZones]
     * both already had real subtraction/avoidance logic for this list, just never fed anything
     * (every call site passed `emptyList()`), so upgrading the type to something real callers can
     * now actually populate isn't a functional regression for any already-saved site.
     */
    val exclusionZones: List<RoofExclusionZone> = emptyList(),
    /**
     * Site Survey / Solar Mapping round: the manual lump-sum obstruction estimate from the initial
     * roof-creation form (see [RoofGeometryEngine.usableAreaM2]'s own `additionalExclusionAreaM2`
     * parameter) — persisted here (it used to be consumed once into [usableAreaM2] at creation time
     * and discarded) so [com.lumix.estimator.site.SolarSiteViewModel.addExclusionZone] can correctly
     * recompute [usableAreaM2] later without losing this original estimate.
     */
    val additionalExclusionAreaM2: Double = 0.0,
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
    val solarApiAnnualSunshineHours: Double? = null,
    /**
     * Site Survey / Solar Mapping round: the containing building's own best-segment annual
     * sunshine hours (Google Solar API's `solarPotential.maxSunshineHoursPerYear`), carried onto
     * each of that building's segments so [com.lumix.estimator.site.geometry
     * .SolarSuitabilityCalculator.evaluate] can compute a location-independent suitability
     * fraction (this segment's sunshine relative to the best Google found on the SAME roof)
     * without needing to re-thread building-level data at display time. Null for every manual/
     * hand-traced roof plane, same as [solarApiAnnualSunshineHours].
     */
    val solarApiBuildingMaxSunshineHoursPerYear: Double? = null
)
