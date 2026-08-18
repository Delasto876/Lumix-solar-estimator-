package com.lumix.estimator.map

import com.lumix.estimator.site.GeoPoint

data class RouteEstimate(val distanceKm: Double, val durationMinutes: Double?)

/**
 * "The map system should be designed so that a routing provider can be added later. For now, do
 * not require a paid routing API. Keep routing behind an abstraction... so we can later connect:
 * OSRM, OpenRouteService, GraphHopper, or another provider." [NullRoutingProvider] is the current
 * default — always unavailable, so the map/site flow never depends on it.
 *
 * Deliberately NOT wired into [com.lumix.estimator.domain.pricing.DeliveryCalculator]'s pricing
 * output: the project owner's own explicit, later instruction was "let me manually enter delivery
 * price" (2026-08-18) — delivery stays a manual figure regardless of what this provider ever
 * returns. A real implementation here would surface a route-distance figure as informational
 * reference only (e.g. on the site detail screen), never as an automatic price override.
 */
fun interface RoutingProvider {
    suspend fun route(from: GeoPoint, to: GeoPoint): RouteEstimate?
}

object NullRoutingProvider : RoutingProvider {
    override suspend fun route(from: GeoPoint, to: GeoPoint): RouteEstimate? = null
}
