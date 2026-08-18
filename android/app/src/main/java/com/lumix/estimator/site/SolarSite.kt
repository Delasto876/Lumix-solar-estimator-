package com.lumix.estimator.site

import kotlinx.serialization.Serializable

/**
 * A customer property: the real selected map location plus every roof plane traced on it.
 *
 * [parish]/[town] (2026-08-18 "MAP/SOLAR DESIGN CONNECTION" — "The selected property location
 * must feed the existing solar design system. Use the location for: parish, town, PSH guidance...
 * customer site, project location. Do NOT make the map a disconnected visual feature"): real
 * values from [com.lumix.estimator.domain.Catalog.parishTowns] when the map's
 * [com.lumix.estimator.map.GeocodingProvider] resolved the selected point to a known Jamaica
 * place — null when it didn't (e.g. a pin dropped somewhere the geocoder couldn't identify), never
 * a guess. See [com.lumix.estimator.ui.nav.LumixNavHost]'s "Use This Roof" wiring for where these
 * flow into [com.lumix.estimator.domain.QuoteInputs.parish]/[com.lumix.estimator.domain.QuoteInputs.nearestTown]/peakSunHours.
 */
@Serializable
data class SolarSite(
    val id: String,
    val name: String?,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val timestampMillis: Long,
    val mapZoomLevel: Float? = null,
    val roofPlanes: List<RoofPlane> = emptyList(),
    val parish: String? = null,
    val town: String? = null
) {
    val totalUsableAreaM2: Double get() = roofPlanes.sumOf { it.usableAreaM2 }
    val totalPanelCount: Int get() = roofPlanes.sumOf { it.panelLayout?.panelCount ?: 0 }
    val totalCapacityKw: Double get() = roofPlanes.sumOf { it.panelLayout?.totalCapacityKw ?: 0.0 }
}
