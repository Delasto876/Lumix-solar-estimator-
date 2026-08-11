package com.lumix.estimator.site

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lumix.estimator.site.geometry.PanelLayoutOptimizer
import com.lumix.estimator.site.geometry.RoofGeometryEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class SiteUiState(
    val draftName: String? = null,
    val draftLatitude: Double? = null,
    val draftLongitude: Double? = null,
    val draftAddress: String? = null,
    val roofPlanes: List<RoofPlane> = emptyList(),
    val savedSiteId: String? = null
) {
    val hasLocation: Boolean get() = draftLatitude != null && draftLongitude != null
}

/**
 * Owns the site currently being built — whether its location and roof planes came from the
 * map (traced polygons) or manual entry (typed coordinates and dimensions), both paths run
 * through the exact same [RoofGeometryEngine]/[PanelLayoutOptimizer] pipeline below, so a
 * manually-entered roof is a peer input method, not a lesser approximation.
 */
class SolarSiteViewModel(private val repository: SiteRepository) : ViewModel() {
    private val _state = MutableStateFlow(SiteUiState())
    val state: StateFlow<SiteUiState> = _state

    val savedSites: StateFlow<List<SolarSite>> get() = repository.sites

    fun startNewSite() {
        _state.value = SiteUiState()
    }

    fun setLocation(latitude: Double, longitude: Double, address: String?, name: String?) {
        _state.update {
            it.copy(draftLatitude = latitude, draftLongitude = longitude, draftAddress = address, draftName = name)
        }
    }

    /** Turns a hand-traced polygon (real map vertices) into a fully analyzed [RoofPlane]. */
    fun addTracedRoofPlane(
        vertices: List<GeoPoint>,
        pitchDegrees: Double?,
        confirmedAzimuthDegrees: Double?,
        panelWidthM: Double,
        panelHeightM: Double,
        panelWattage: Double,
        setbackMeters: Double = 0.5
    ) {
        appendRoofPlane(vertices, pitchDegrees, confirmedAzimuthDegrees, panelWidthM, panelHeightM, panelWattage, setbackMeters)
    }

    /**
     * Builds a synthetic rectangular roof polygon from typed-in dimensions, centered on the
     * site's location, then runs it through the identical geometry + panel-packing pipeline a
     * traced roof uses — manual entry is a first-class alternative, not a rough estimate mode.
     */
    fun addManualRoofPlane(
        lengthM: Double,
        widthM: Double,
        azimuthDegrees: Double,
        pitchDegrees: Double?,
        panelWidthM: Double,
        panelHeightM: Double,
        panelWattage: Double,
        setbackMeters: Double = 0.5
    ) {
        val lat = _state.value.draftLatitude ?: return
        val lon = _state.value.draftLongitude ?: return
        val reference = GeoPoint(lat, lon)
        val halfLength = lengthM / 2.0
        val halfWidth = widthM / 2.0
        val corners = listOf(
            -halfWidth to -halfLength,
            halfWidth to -halfLength,
            halfWidth to halfLength,
            -halfWidth to halfLength
        ).map { (x, y) -> RoofGeometryEngine.toGeoPoint(x, y, reference) }
        appendRoofPlane(corners, pitchDegrees, azimuthDegrees, panelWidthM, panelHeightM, panelWattage, setbackMeters)
    }

    fun removeRoofPlane(id: String) {
        _state.update { it.copy(roofPlanes = it.roofPlanes.filterNot { plane -> plane.id == id }) }
    }

    fun saveSite(): String? {
        val s = _state.value
        val lat = s.draftLatitude ?: return null
        val lon = s.draftLongitude ?: return null
        val id = UUID.randomUUID().toString()
        repository.save(
            SolarSite(
                id = id,
                name = s.draftName,
                latitude = lat,
                longitude = lon,
                address = s.draftAddress,
                timestampMillis = System.currentTimeMillis(),
                roofPlanes = s.roofPlanes
            )
        )
        _state.update { it.copy(savedSiteId = id) }
        return id
    }

    private fun appendRoofPlane(
        vertices: List<GeoPoint>,
        pitchDegrees: Double?,
        confirmedAzimuthDegrees: Double?,
        panelWidthM: Double,
        panelHeightM: Double,
        panelWattage: Double,
        setbackMeters: Double
    ) {
        val label = "Roof ${('A' + _state.value.roofPlanes.size)}"
        val horizontalArea = RoofGeometryEngine.horizontalAreaM2(vertices)
        val roofArea = RoofGeometryEngine.roofAreaM2(horizontalArea, pitchDegrees)
        val usableArea = RoofGeometryEngine.usableAreaM2(vertices, horizontalArea, roofArea, setbackMeters, emptyList())
        val suggestedCandidates = RoofGeometryEngine.suggestAzimuthCandidates(vertices)
        val effectiveAzimuth = confirmedAzimuthDegrees ?: suggestedCandidates?.first ?: 0.0

        val panelLayout = PanelLayoutOptimizer.optimize(
            PanelLayoutOptimizer.Input(
                vertices = vertices,
                panelWidthM = panelWidthM,
                panelHeightM = panelHeightM,
                panelWattage = panelWattage,
                setbackM = setbackMeters,
                alignmentAzimuthDegrees = effectiveAzimuth
            )
        )

        val roofPlane = RoofPlane(
            id = UUID.randomUUID().toString(),
            label = label,
            vertices = vertices,
            horizontalAreaM2 = horizontalArea,
            roofAreaM2 = roofArea,
            usableAreaM2 = usableArea,
            suggestedAzimuthDegrees = suggestedCandidates?.first,
            azimuthDegrees = confirmedAzimuthDegrees,
            pitchDegrees = pitchDegrees,
            setbackMeters = setbackMeters,
            panelLayout = panelLayout
        )
        _state.update { it.copy(roofPlanes = it.roofPlanes + roofPlane) }
    }

    companion object {
        fun factory(repository: SiteRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SolarSiteViewModel(repository) as T
            }
        }
    }
}
