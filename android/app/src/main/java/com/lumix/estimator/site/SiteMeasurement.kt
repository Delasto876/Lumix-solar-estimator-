package com.lumix.estimator.site

import com.lumix.estimator.site.geometry.DistanceCalculator
import kotlinx.serialization.Serializable

/**
 * Site Survey / Solar Mapping round (spec "measurement tool extensions... useful for cable runs,
 * roof-to-inverter placement distance, electrical service distance, etc."): the map's existing
 * measure tool (map Part 6) already computed a real distance between two tapped points, but
 * discarded it the moment the installer tapped Done — nothing was ever kept with the site. This is
 * what lets one of those measurements become part of the permanent site record instead: named at
 * capture time, kept on [SolarSite], and available to the final site-survey summary/report.
 */
@Serializable
enum class SiteMeasurementKind(val label: String) {
    ROOF_DIMENSION("Roof dimension"),
    EQUIPMENT_TO_EQUIPMENT("Equipment-to-equipment run"),
    ELECTRICAL_SERVICE("Electrical service distance"),
    OTHER("Other")
}

/** One saved, named, real point-to-point distance — see this file's own top-level doc. */
@Serializable
data class SiteMeasurement(
    val id: String,
    val kind: SiteMeasurementKind,
    val label: String,
    val pointA: GeoPoint,
    val pointB: GeoPoint
) {
    val distanceMeters: Double get() = DistanceCalculator.haversineMeters(pointA, pointB)
    val distanceFeet: Double get() = distanceMeters * 3.28084
}
