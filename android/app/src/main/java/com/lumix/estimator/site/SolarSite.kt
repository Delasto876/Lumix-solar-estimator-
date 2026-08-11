package com.lumix.estimator.site

/** A customer property: the real selected map location plus every roof plane traced on it. */
data class SolarSite(
    val id: String,
    val name: String?,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val timestampMillis: Long,
    val mapZoomLevel: Float? = null,
    val roofPlanes: List<RoofPlane> = emptyList()
) {
    val totalUsableAreaM2: Double get() = roofPlanes.sumOf { it.usableAreaM2 }
    val totalPanelCount: Int get() = roofPlanes.sumOf { it.panelLayout?.panelCount ?: 0 }
    val totalCapacityKw: Double get() = roofPlanes.sumOf { it.panelLayout?.totalCapacityKw ?: 0.0 }
}
