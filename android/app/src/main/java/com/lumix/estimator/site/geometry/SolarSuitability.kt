package com.lumix.estimator.site.geometry

import com.lumix.estimator.site.RoofPlane
import kotlinx.serialization.Serializable

/**
 * Site Survey / Solar Mapping round (spec "Show roof areas using a clear suitability gradient
 * such as: Excellent / Good / Moderate / Poor / Unsuitable"): a 5-tier label for how good a roof
 * section's own sun exposure is — a narrower, exposure-only concept than [RoofScoreCalculator]'s
 * 100-point overall roof quality score (which also weighs area and usable-space, unrelated to
 * exposure). `@Serializable` (added alongside `SiteSurveySummary`) so a roof plane's tier can be
 * captured into a quote's persisted `QuoteInputs.siteSurveySummary` — purely additive, no
 * behavioral change to the calculator itself.
 */
@Serializable
enum class SolarSuitability(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    MODERATE("Moderate"),
    POOR("Poor"),
    UNSUITABLE("Unsuitable")
}

/**
 * @param score0to1 the underlying fraction the tier was bucketed from — kept alongside the tier
 *   for a continuous progress-bar-style rendering, not just the 5 discrete labels.
 * @param isImageryDerived true when [score0to1] came from real Google Solar API sunshine data
 *   ([SolarSuitabilityCalculator.fromSolarApi]); false when it came from the manual-checklist/
 *   geometry-only path ([SolarSuitabilityCalculator.fromRoofScore]) — drives which disclaimer
 *   [disclaimer] returns, per the spec's own "Do not claim exact hourly shade prediction from
 *   ordinary satellite imagery alone... label the result: Estimated shading — verify on site."
 */
data class SolarSuitabilityResult(
    val tier: SolarSuitability,
    val score0to1: Double,
    val isImageryDerived: Boolean
) {
    val disclaimer: String get() = if (isImageryDerived) {
        "From Google Solar API's own imagery analysis for this building."
    } else {
        "Estimated shading — verify on site."
    }
}

object SolarSuitabilityCalculator {
    private fun tierFor(fraction: Double): SolarSuitability = when {
        fraction >= 0.90 -> SolarSuitability.EXCELLENT
        fraction >= 0.75 -> SolarSuitability.GOOD
        fraction >= 0.55 -> SolarSuitability.MODERATE
        fraction >= 0.35 -> SolarSuitability.POOR
        else -> SolarSuitability.UNSUITABLE
    }

    /**
     * [segmentAnnualSunshineHours] relative to [buildingMaxSunshineHoursPerYear] — the best
     * segment Google itself found on the SAME building — rather than an absolute hours threshold.
     * This keeps the tier location-independent: a segment's fraction of what Google's own analysis
     * already confirmed is achievable on this exact roof is meaningful everywhere Solar API has
     * coverage, whereas a fixed absolute-hours cutoff would silently rate every roof in a cloudier
     * climate "worse" regardless of that roof's own real relative exposure.
     */
    fun fromSolarApi(segmentAnnualSunshineHours: Double, buildingMaxSunshineHoursPerYear: Double): SolarSuitabilityResult {
        val fraction = if (buildingMaxSunshineHoursPerYear > 0.0) {
            (segmentAnnualSunshineHours / buildingMaxSunshineHoursPerYear).coerceIn(0.0, 1.0)
        } else 0.0
        return SolarSuitabilityResult(tierFor(fraction), fraction, isImageryDerived = true)
    }

    /**
     * Manual/hand-traced path: reuses [RoofScoreCalculator]'s own orientation/pitch/shading
     * components (0-20 each, 60 max combined) — the exposure-relevant subset of its 100-point
     * score, not the whole score (area/usable-space aren't exposure factors). Always
     * [SolarSuitabilityResult.isImageryDerived] = false.
     */
    fun fromRoofScore(orientationScore: Int, pitchScore: Int, shadingScore: Int): SolarSuitabilityResult {
        val fraction = ((orientationScore + pitchScore + shadingScore) / 60.0).coerceIn(0.0, 1.0)
        return SolarSuitabilityResult(tierFor(fraction), fraction, isImageryDerived = false)
    }

    /**
     * One-stop evaluation for a real [RoofPlane]: uses the real Solar API path when this plane
     * carries both sunshine fields (see [RoofPlane.solarApiAnnualSunshineHours]/[RoofPlane
     * .solarApiBuildingMaxSunshineHoursPerYear]'s own docs for when that is), otherwise falls back
     * to the geometry/checklist path via [RoofScoreCalculator] using [latitude] for the
     * orientation/pitch ideal — the same computed-on-demand-with-latitude pattern
     * [com.lumix.estimator.site.SolarPotentialCard] already uses for its own Roof Score display,
     * rather than a second, persisted copy of a derived figure.
     */
    fun evaluate(plane: RoofPlane, latitude: Double): SolarSuitabilityResult {
        val apiHours = plane.solarApiAnnualSunshineHours
        val apiMaxHours = plane.solarApiBuildingMaxSunshineHoursPerYear
        if (apiHours != null && apiMaxHours != null) {
            return fromSolarApi(apiHours, apiMaxHours)
        }
        val score = RoofScoreCalculator.score(
            usableAreaM2 = plane.usableAreaM2,
            roofAreaM2 = plane.roofAreaM2,
            azimuthDegrees = plane.azimuthDegrees ?: plane.suggestedAzimuthDegrees,
            latitude = latitude,
            pitchDegrees = plane.pitchDegrees,
            shadingFactor = plane.shadingFactor
        )
        return fromRoofScore(score.orientationScore, score.pitchScore, score.shadingScore)
    }
}
