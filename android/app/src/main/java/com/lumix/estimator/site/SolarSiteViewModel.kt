package com.lumix.estimator.site

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lumix.estimator.site.geometry.PanelLayoutOptimizer
import com.lumix.estimator.site.geometry.RoofGeometryEngine
import com.lumix.estimator.site.solarapi.SolarApiClient
import com.lumix.estimator.site.solarapi.SolarApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class SiteUiState(
    val draftName: String? = null,
    val draftLatitude: Double? = null,
    val draftLongitude: Double? = null,
    val draftAddress: String? = null,
    /** Real Catalog.parishTowns values when the selected location resolved to a known Jamaica place — see SolarSite's own doc. */
    val draftParish: String? = null,
    val draftTown: String? = null,
    val roofPlanes: List<RoofPlane> = emptyList(),
    val savedSiteId: String? = null,
    /** Site Survey / Solar Mapping round: the current/last outcome of a Solar API auto-detect attempt for this site — see [SolarApiFetchState]'s own doc. */
    val solarApiFetchState: SolarApiFetchState = SolarApiFetchState.Idle
) {
    val hasLocation: Boolean get() = draftLatitude != null && draftLongitude != null
}

/**
 * Site Survey / Solar Mapping round: UI-facing status for "Auto-detect roof (Solar API)" — a
 * distinct state per [SolarApiResult] branch (plus [Loading]) so the map screen can show the right
 * message (a spinner, "no coverage here — trace manually," or a real error) rather than collapsing
 * everything into a single boolean.
 */
sealed class SolarApiFetchState {
    data object Idle : SolarApiFetchState()
    data object Loading : SolarApiFetchState()
    data class Succeeded(val segmentsAdded: Int) : SolarApiFetchState()
    data class NoCoverage(val message: String) : SolarApiFetchState()
    data class Failed(val reason: String) : SolarApiFetchState()
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

    /**
     * Synchronous local cache so [getSite] can stay a plain (non-suspend) function for its
     * existing call sites (e.g. `LumixNavHost`'s `onUseRoof`, `SiteDetailScreen`'s composable
     * body) while [repository] is Room/Flow-backed. Populated reactively from every DB change
     * below, and optimistically inside [saveSite] so a just-saved site is guaranteed present
     * immediately, without racing Room's Flow-invalidation timing.
     */
    private val cachedSites = mutableMapOf<String, SolarSite>()

    val savedSites: StateFlow<List<SolarSite>> =
        repository.sites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.sites.collect { list ->
                list.forEach { cachedSites[it.id] = it }
            }
        }
    }

    fun getSite(id: String): SolarSite? = cachedSites[id]

    fun startNewSite() {
        _state.value = SiteUiState()
    }

    fun setLocation(
        latitude: Double,
        longitude: Double,
        address: String?,
        name: String?,
        parish: String? = null,
        town: String? = null
    ) {
        _state.update {
            it.copy(
                draftLatitude = latitude, draftLongitude = longitude, draftAddress = address, draftName = name,
                draftParish = parish, draftTown = town
            )
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
        setbackMeters: Double = 0.5,
        excludedAreaM2: Double = 0.0,
        shadingFactor: Double = 1.0
    ) {
        appendRoofPlane(vertices, pitchDegrees, confirmedAzimuthDegrees, panelWidthM, panelHeightM, panelWattage, setbackMeters, excludedAreaM2, shadingFactor)
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
        setbackMeters: Double = 0.5,
        excludedAreaM2: Double = 0.0,
        shadingFactor: Double = 1.0
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
        appendRoofPlane(corners, pitchDegrees, azimuthDegrees, panelWidthM, panelHeightM, panelWattage, setbackMeters, excludedAreaM2, shadingFactor)
    }

    fun removeRoofPlane(id: String) {
        _state.update { it.copy(roofPlanes = it.roofPlanes.filterNot { plane -> plane.id == id }) }
    }

    /**
     * "Save location. Reload project. Confirm location and polygon remain" (2026-08-18): now
     * suspends on the real Room write (see [SiteRepository]'s own doc) rather than an in-memory
     * assignment, so a save genuinely completes — not just updates local state — before the
     * caller navigates away.
     */
    suspend fun saveSite(): String? {
        val s = _state.value
        val lat = s.draftLatitude ?: return null
        val lon = s.draftLongitude ?: return null
        val id = UUID.randomUUID().toString()
        val site = SolarSite(
            id = id,
            name = s.draftName,
            latitude = lat,
            longitude = lon,
            address = s.draftAddress,
            timestampMillis = System.currentTimeMillis(),
            roofPlanes = s.roofPlanes,
            parish = s.draftParish,
            town = s.draftTown
        )
        repository.save(site)
        cachedSites[id] = site
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
        setbackMeters: Double,
        excludedAreaM2: Double = 0.0,
        shadingFactor: Double = 1.0,
        solarApiAnnualSunshineHours: Double? = null
    ) {
        val label = "Roof ${('A' + _state.value.roofPlanes.size)}"
        val horizontalArea = RoofGeometryEngine.horizontalAreaM2(vertices)
        val roofArea = RoofGeometryEngine.roofAreaM2(horizontalArea, pitchDegrees)
        val usableArea = RoofGeometryEngine.usableAreaM2(vertices, horizontalArea, roofArea, setbackMeters, emptyList(), excludedAreaM2)
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
            shadingFactor = shadingFactor,
            panelLayout = panelLayout,
            solarApiAnnualSunshineHours = solarApiAnnualSunshineHours
        )
        _state.update { it.copy(roofPlanes = it.roofPlanes + roofPlane) }
    }

    /**
     * Site Survey / Solar Mapping round (spec "Automatically detect/use available building and
     * roof information when Google Solar API data is available... Allow the user to manually
     * override anything detected automatically"): calls [client] for the current draft location,
     * and on [SolarApiResult.Available] adds every real roof segment Google returned — each
     * segment's real bounding-box rectangle as this plane's initial vertices (editable afterward
     * with the exact same vertex tools a hand-traced roof already has), its real (non-ambiguous)
     * azimuth and pitch passed straight through as already-confirmed values (never re-guessed by
     * [RoofGeometryEngine.suggestAzimuthCandidates] the way a traced polygon's ambiguous azimuth
     * is), and its real median sunshine-hours figure carried onto the resulting [RoofPlane].
     * [SolarApiResult.NoCoverage]/[SolarApiResult.Unavailable] add nothing and update
     * [SiteUiState.solarApiFetchState] so the map screen can prompt manual tracing instead —
     * never a fabricated roof plane either way, per the spec's own "never fabricate roof geometry"
     * requirement.
     */
    suspend fun fetchAutoRoofFromSolarApi(
        client: SolarApiClient,
        panelWidthM: Double,
        panelHeightM: Double,
        panelWattage: Double,
        setbackMeters: Double = 0.5
    ) {
        val lat = _state.value.draftLatitude ?: return
        val lon = _state.value.draftLongitude ?: return
        _state.update { it.copy(solarApiFetchState = SolarApiFetchState.Loading) }
        when (val result = client.fetchBuildingInsights(lat, lon)) {
            is SolarApiResult.Available -> {
                result.insights.roofSegments.forEach { segment ->
                    appendRoofPlane(
                        vertices = segment.boundingBoxVertices,
                        pitchDegrees = segment.pitchDegrees,
                        confirmedAzimuthDegrees = segment.azimuthDegrees,
                        panelWidthM = panelWidthM,
                        panelHeightM = panelHeightM,
                        panelWattage = panelWattage,
                        setbackMeters = setbackMeters,
                        solarApiAnnualSunshineHours = segment.medianAnnualSunshineHours
                    )
                }
                _state.update { it.copy(solarApiFetchState = SolarApiFetchState.Succeeded(result.insights.roofSegments.size)) }
            }
            is SolarApiResult.NoCoverage -> _state.update { it.copy(solarApiFetchState = SolarApiFetchState.NoCoverage(result.message)) }
            is SolarApiResult.Unavailable -> _state.update { it.copy(solarApiFetchState = SolarApiFetchState.Failed(result.reason)) }
        }
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
