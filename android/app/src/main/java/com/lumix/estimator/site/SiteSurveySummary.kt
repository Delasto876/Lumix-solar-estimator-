package com.lumix.estimator.site

import com.lumix.estimator.site.geometry.RoofExclusionType
import com.lumix.estimator.site.geometry.SolarSuitability
import com.lumix.estimator.site.geometry.SolarSuitabilityCalculator
import kotlinx.serialization.Serializable

/** One roof plane's real survey findings, captured at "Use This Roof" time — see [SiteSurveySummary]'s own doc. */
@Serializable
data class RoofPlaneSurveySummary(
    val label: String,
    val usableAreaM2: Double,
    val panelCount: Int,
    val capacityKw: Double,
    val suitability: SolarSuitability,
    /** True when [suitability] came from real Google Solar API sunshine data, false when estimated from geometry/manual shading — see [com.lumix.estimator.site.geometry.SolarSuitabilityResult]'s own doc. */
    val isImageryDerived: Boolean
)

/** One obstruction type's real drawn-zone count/area across a site's roof planes — see [SiteSurveySummary]'s own doc. */
@Serializable
data class ExclusionZoneSurveySummary(
    val type: RoofExclusionType,
    val count: Int,
    val totalAreaM2: Double
)

/** One saved [SiteMeasurement], flattened for the report — see [SiteSurveySummary]'s own doc. */
@Serializable
data class SiteMeasurementSurveySummary(
    val kind: SiteMeasurementKind,
    val label: String,
    val distanceMeters: Double
)

/**
 * Site Survey / Solar Mapping round (spec "produce a full site-survey summary for the final
 * quote/report"): a compact, real, `@Serializable` snapshot of everything the map-based survey
 * found — captured once, at the moment a roof is chosen for a quote (the same "Use This Roof" flow
 * that already builds [RoofConstraint]), and carried on [com.lumix.estimator.domain.QuoteInputs
 * .siteSurveySummary] from there. Capturing it here rather than re-deriving it from a live
 * [SolarSite] later means the final report always reflects the survey exactly as it stood when the
 * roof was chosen, even if the underlying saved site is later edited or deleted.
 */
@Serializable
data class SiteSurveySummary(
    val sourceSiteId: String,
    val latitude: Double,
    val longitude: Double,
    val parish: String?,
    val town: String?,
    val roofPlanes: List<RoofPlaneSurveySummary>,
    val exclusionZones: List<ExclusionZoneSurveySummary>,
    val measurements: List<SiteMeasurementSurveySummary>,
    /** Real ground elevation from Google's Elevation API, when it was fetched — see [com.lumix.estimator.site.elevation.ElevationReading]'s own doc. Null whenever the lookup wasn't run or found no data, never fabricated. */
    val groundElevationMeters: Double?
) {
    val totalUsableAreaM2: Double get() = roofPlanes.sumOf { it.usableAreaM2 }
    val totalPanelCount: Int get() = roofPlanes.sumOf { it.panelCount }
    val totalCapacityKw: Double get() = roofPlanes.sumOf { it.capacityKw }
    val totalExclusionAreaM2: Double get() = exclusionZones.sumOf { it.totalAreaM2 }

    companion object {
        /** Builds a real summary from a live [SolarSite] — every figure here traces back to something the survey actually measured/fetched, never invented. */
        fun from(site: SolarSite): SiteSurveySummary {
            val roofSummaries = site.roofPlanes.map { plane ->
                val suitability = SolarSuitabilityCalculator.evaluate(plane, site.latitude)
                RoofPlaneSurveySummary(
                    label = plane.label,
                    usableAreaM2 = plane.usableAreaM2,
                    panelCount = plane.panelLayout?.panelCount ?: 0,
                    capacityKw = plane.panelLayout?.totalCapacityKw ?: 0.0,
                    suitability = suitability.tier,
                    isImageryDerived = suitability.isImageryDerived
                )
            }
            val exclusionSummaries = site.roofPlanes
                .flatMap { it.exclusionZones }
                .groupBy { it.type }
                .map { (type, zones) -> ExclusionZoneSurveySummary(type, zones.size, zones.sumOf { zone -> zone.areaM2 }) }
            val measurementSummaries = site.siteMeasurements.map {
                SiteMeasurementSurveySummary(it.kind, it.label, it.distanceMeters)
            }
            return SiteSurveySummary(
                sourceSiteId = site.id,
                latitude = site.latitude,
                longitude = site.longitude,
                parish = site.parish,
                town = site.town,
                roofPlanes = roofSummaries,
                exclusionZones = exclusionSummaries,
                measurements = measurementSummaries,
                groundElevationMeters = site.groundElevationMeters
            )
        }
    }
}
