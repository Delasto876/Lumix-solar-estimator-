package com.lumix.estimator.site

import kotlinx.serialization.Serializable

enum class PanelOrientation { PORTRAIT, LANDSCAPE }

/** Where one physical panel sits, in real map coordinates — same model the map, layout preview, and simulation all read from. */
@Serializable
data class PanelPlacement(
    val center: GeoPoint,
    val rotationDegrees: Double
)

@Serializable
data class PanelLayout(
    val panelWidthM: Double,
    val panelHeightM: Double,
    val panelWattage: Double,
    val panelCount: Int,
    val orientation: PanelOrientation,
    val placements: List<PanelPlacement> = emptyList()
) {
    val totalCapacityKw: Double get() = panelCount * panelWattage / 1000.0
}
