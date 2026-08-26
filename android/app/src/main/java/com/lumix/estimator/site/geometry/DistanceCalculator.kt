package com.lumix.estimator.site.geometry

import com.lumix.estimator.site.GeoPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Site Survey / Solar Mapping round: the one real great-circle distance rule for the whole module
 * — roof edge/dimension checks, equipment-to-equipment cable runs, electrical service distance all
 * go through this, rather than each measurement context keeping its own copy. Moved here from a
 * private helper inside `SolarSiteMapScreen.kt` (map Part 6's original A-to-B measure tool), per
 * the spec's own "no duplicated ... logic; separate concerns" rule — the UI layer now calls this
 * instead of computing distance itself.
 */
object DistanceCalculator {
    private const val EARTH_RADIUS_M = 6371000.0

    fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).let { it * it } + cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
