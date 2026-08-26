package com.lumix.estimator.site.geometry

import com.lumix.estimator.site.GeoPoint
import kotlinx.serialization.Serializable

/**
 * Site Survey / Solar Mapping round (spec "Allow the user to mark exclusions manually: Chimneys,
 * Vents, Skylights, AC units, Water tanks, Roof edges/setbacks, Walkways, Fire access, Structural
 * obstructions, ... Other obstacles"): a real, typed, on-roof physical obstruction — distinct from
 * [ShadeObstructionType] (a nearby object that casts shade ONTO the roof from elsewhere, contributing
 * only a flat percentage estimate to [ShadeEstimator], never real drawn geometry). The spec's own
 * list also names "Trees" and "Nearby buildings" as exclusion types, but those are already correctly
 * modeled as [ShadeObstructionType] entries (a tree overhanging the roof is a shading concern with
 * its own real estimate path, not an area to subtract from a footprint the roof itself doesn't
 * physically lose) — deliberately not duplicated here to avoid two different mechanisms silently
 * double-counting the same real-world object.
 */
@Serializable
enum class RoofExclusionType(val label: String) {
    CHIMNEY("Chimney"),
    VENT("Vent"),
    SKYLIGHT("Skylight"),
    AC_UNIT("AC unit"),
    WATER_TANK("Water tank"),
    SETBACK_EDGE("Roof edge / setback"),
    WALKWAY("Walkway"),
    FIRE_ACCESS("Fire access"),
    STRUCTURAL("Structural obstruction"),
    OTHER("Other obstacle")
}

/**
 * A real, drawn polygon marking one physical obstruction on a roof — [RoofPlane.exclusionZones]
 * holds a list of these. [vertices] is a real map-coordinate polygon traced the same way the roof
 * itself is (see [com.lumix.estimator.site.map.RoofDrawingService]), not a typed number — its own
 * real area (via [RoofGeometryEngine.horizontalAreaM2]) is what [RoofGeometryEngine.usableAreaM2]
 * subtracts, and what [PanelLayoutOptimizer] avoids placing panels over, rather than a lump-sum
 * estimate applied on top of the whole roof with no actual location.
 */
@Serializable
data class RoofExclusionZone(
    val type: RoofExclusionType,
    val vertices: List<GeoPoint>,
    /** Optional free-text note (e.g. "old chimney, may be removed") — never required. */
    val note: String? = null
) {
    val areaM2: Double get() = RoofGeometryEngine.horizontalAreaM2(vertices)
}
