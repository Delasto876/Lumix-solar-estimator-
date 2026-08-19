package com.lumix.estimator.site.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThreeDRotation
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.location.DeviceLocationManager
import com.lumix.estimator.map.AndroidGeocodingProvider
import com.lumix.estimator.map.GeocodeResult
import com.lumix.estimator.map.KnownPlace
import com.lumix.estimator.map.MapTilerSatelliteProvider
import com.lumix.estimator.map.OpenFreeMapProvider
import com.lumix.estimator.network.NetworkConnectivityObserver
import com.lumix.estimator.sensors.CompassManager
import com.lumix.estimator.site.GeoPoint
import com.lumix.estimator.site.RoofPlane
import com.lumix.estimator.site.ShadeAndExclusionSection
import com.lumix.estimator.site.SolarCompassBadge
import com.lumix.estimator.site.SolarSiteViewModel
import com.lumix.estimator.site.geometry.RoofGeometryEngine
import com.lumix.estimator.site.geometry.ShadeEstimator
import com.lumix.estimator.site.geometry.ShadeObstructionType
import com.lumix.estimator.ui.components.GlassSurface
import com.lumix.estimator.ui.components.LabeledDropdown
import com.lumix.estimator.ui.components.LumixPrimaryButton
import com.lumix.estimator.ui.components.LumixSecondaryButton
import com.lumix.estimator.ui.components.NumberField
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixRadius
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon as GeoJsonPolygon

private val pitchOptions: List<Double?> = listOf(null, 0.0, 5.0, 10.0, 15.0, 20.0, 25.0, 30.0, 35.0, 40.0, 45.0)
private val jamaicaDefault = GeoPoint(18.1096, -77.2975)

private enum class RoofEditMode { NONE, MOVE, DELETE, ADD }

// Source/layer IDs — created once in onMapReady, refreshed via SideEffect on every state change.
private const val SRC_SELECTED = "lumix-selected-location"
private const val LYR_SELECTED = "lumix-selected-location-layer"
private const val SRC_SAVED_FILL = "lumix-saved-planes-fill"
private const val LYR_SAVED_FILL = "lumix-saved-planes-fill-layer"
private const val SRC_SAVED_OUTLINE = "lumix-saved-planes-outline"
private const val LYR_SAVED_OUTLINE = "lumix-saved-planes-outline-layer"
private const val SRC_ROOF_FILL = "lumix-roof-fill"
private const val LYR_ROOF_FILL = "lumix-roof-fill-layer"
private const val SRC_ROOF_OUTLINE = "lumix-roof-outline"
private const val LYR_ROOF_OUTLINE = "lumix-roof-outline-layer"
private const val SRC_ROOF_VERTICES = "lumix-roof-vertices"
private const val LYR_ROOF_VERTICES = "lumix-roof-vertices-layer"
private const val SRC_ROOF_SELECTED_VERTEX = "lumix-roof-selected-vertex"
private const val LYR_ROOF_SELECTED_VERTEX = "lumix-roof-selected-vertex-layer"

/**
 * Every GeoJsonSource this screen manages, resolved once when the style finishes loading. Uses
 * `Style.getSource(id)` + an explicit cast rather than the generic `getSourceAs<T>` — safer
 * against SDK-version drift in this environment, where nothing here could be compiled/verified
 * (see [MapLibreMapView]'s own doc).
 */
private class MapLayerRefs(style: Style) {
    val selected: GeoJsonSource = style.getSource(SRC_SELECTED) as GeoJsonSource
    val savedFill: GeoJsonSource = style.getSource(SRC_SAVED_FILL) as GeoJsonSource
    val savedOutline: GeoJsonSource = style.getSource(SRC_SAVED_OUTLINE) as GeoJsonSource
    val roofFill: GeoJsonSource = style.getSource(SRC_ROOF_FILL) as GeoJsonSource
    val roofOutline: GeoJsonSource = style.getSource(SRC_ROOF_OUTLINE) as GeoJsonSource
    val roofVertices: GeoJsonSource = style.getSource(SRC_ROOF_VERTICES) as GeoJsonSource
    val roofSelectedVertex: GeoJsonSource = style.getSource(SRC_ROOF_SELECTED_VERTEX) as GeoJsonSource
}

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)
private fun LatLng.toGeoPoint() = GeoPoint(latitude, longitude)
private fun GeoPoint.toGeoJsonPoint(): Point = Point.fromLngLat(longitude, latitude)

private fun ringOf(vertices: List<GeoPoint>): List<Point> {
    val points = vertices.map { it.toGeoJsonPoint() }
    return if (points.isNotEmpty() && points.first() != points.last()) points + points.first() else points
}

private fun pointFeatures(vertices: List<GeoPoint>): FeatureCollection =
    FeatureCollection.fromFeatures(vertices.map { Feature.fromGeometry(it.toGeoJsonPoint()) })

private fun outlineFeatures(vertices: List<GeoPoint>): FeatureCollection {
    if (vertices.size < 2) return FeatureCollection.fromFeatures(emptyList())
    val line = org.maplibre.geojson.LineString.fromLngLats(ringOf(vertices))
    return FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(line)))
}

private fun fillFeatures(vertices: List<GeoPoint>): FeatureCollection {
    if (vertices.size < 3) return FeatureCollection.fromFeatures(emptyList())
    val polygon = GeoJsonPolygon.fromLngLats(listOf(ringOf(vertices)))
    return FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(polygon)))
}

private fun savedPlanesFillFeatures(planes: List<RoofPlane>): FeatureCollection =
    FeatureCollection.fromFeatures(
        planes.filter { it.vertices.size >= 3 }
            .map { Feature.fromGeometry(GeoJsonPolygon.fromLngLats(listOf(ringOf(it.vertices)))) }
    )

private fun savedPlanesOutlineFeatures(planes: List<RoofPlane>): FeatureCollection =
    FeatureCollection.fromFeatures(
        planes.filter { it.vertices.size >= 3 }
            .map { Feature.fromGeometry(org.maplibre.geojson.LineString.fromLngLats(ringOf(it.vertices))) }
    )

/**
 * 2026-08-18 map-view switcher: the base-map looks the installer can flip between with a labeled
 * button. [label] is kept to one clear word so the control never wraps/overlaps into unreadable text.
 */
private data class MapStyleOption(val label: String, val styleUrl: String)

/**
 * 2026-08-18 ("let satellite view be the default view"): Satellite (real aerial imagery via
 * [MapTilerSatelliteProvider]) is prepended — and therefore the default, since this list's
 * `.first()` seeds the initial selection below — only when a real MapTiler key has actually been
 * configured; see that provider's own doc for how to activate it. Without a key, this list starts
 * with "Streets" exactly as before, so a build with no key configured is completely unaffected.
 * "Bright"/"Light" are OpenFreeMap public vector styles (no API key, no billing).
 *
 * 2026-08-19 (map Part 2, "satellite, streets, terrain, and traffic layers"): "Terrain" (real
 * contour lines + hillshading, same MapTiler key/gate as Satellite) added alongside them.
 * "Traffic" is deliberately NOT added as a button here — there is no live-traffic data source
 * wired into this app (or into MapTiler's own style catalog; real-time traffic tiles are a
 * separate, differently-architected product most providers sell apart from static map styles), so
 * a "Traffic" button that did nothing would be worse than one that's simply not there yet. Flagged
 * to the user as a follow-up decision (which provider, if any) rather than faked here.
 */
private fun mapStyleOptions(): List<MapStyleOption> = buildList {
    MapTilerSatelliteProvider.styleUrlOrNull()?.let { add(MapStyleOption("Satellite", it)) }
    add(MapStyleOption("Streets", "https://tiles.openfreemap.org/styles/liberty"))
    MapTilerSatelliteProvider.terrainStyleUrlOrNull()?.let { add(MapStyleOption("Terrain", it)) }
    add(MapStyleOption("Bright", "https://tiles.openfreemap.org/styles/bright"))
    add(MapStyleOption("Light", "https://tiles.openfreemap.org/styles/positron"))
}

/**
 * Adds every roof-tracing [GeoJsonSource] + its style layer to [style]. Extracted so it can run both
 * on first load AND again after a runtime style swap (MapLibre's `setStyle` wipes all sources/layers,
 * so they must be re-added against the new style; the screen's own `SideEffect` then re-pushes the
 * current features into the freshly-rebuilt [MapLayerRefs]).
 */
private fun addRoofTracingLayers(style: Style) {
    style.addSource(GeoJsonSource(SRC_SELECTED, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        CircleLayer(LYR_SELECTED, SRC_SELECTED).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor(Color(0xFFFFD84D).toArgb()),
            PropertyFactory.circleStrokeColor(Color(0xFF1A1A1A).toArgb()),
            PropertyFactory.circleStrokeWidth(2f)
        )
    )
    style.addSource(GeoJsonSource(SRC_SAVED_FILL, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        FillLayer(LYR_SAVED_FILL, SRC_SAVED_FILL).withProperties(
            PropertyFactory.fillColor(Color(0x3363E6A5).toArgb())
        )
    )
    style.addSource(GeoJsonSource(SRC_SAVED_OUTLINE, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        LineLayer(LYR_SAVED_OUTLINE, SRC_SAVED_OUTLINE).withProperties(
            PropertyFactory.lineColor(Color(0xFF63E6A5).toArgb()),
            PropertyFactory.lineWidth(3f)
        )
    )
    style.addSource(GeoJsonSource(SRC_ROOF_FILL, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        FillLayer(LYR_ROOF_FILL, SRC_ROOF_FILL).withProperties(
            PropertyFactory.fillColor(Color(0x4DFFD84D).toArgb())
        )
    )
    style.addSource(GeoJsonSource(SRC_ROOF_OUTLINE, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        LineLayer(LYR_ROOF_OUTLINE, SRC_ROOF_OUTLINE).withProperties(
            PropertyFactory.lineColor(Color(0xFFFFD84D).toArgb()),
            PropertyFactory.lineWidth(4f)
        )
    )
    style.addSource(GeoJsonSource(SRC_ROOF_VERTICES, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        CircleLayer(LYR_ROOF_VERTICES, SRC_ROOF_VERTICES).withProperties(
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleColor(Color(0xFFFFD84D).toArgb()),
            PropertyFactory.circleStrokeColor(Color(0xFF1A1A1A).toArgb()),
            PropertyFactory.circleStrokeWidth(1.5f)
        )
    )
    style.addSource(GeoJsonSource(SRC_ROOF_SELECTED_VERTEX, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        CircleLayer(LYR_ROOF_SELECTED_VERTEX, SRC_ROOF_SELECTED_VERTEX).withProperties(
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleColor(Color(0xFFFF5252).toArgb()),
            PropertyFactory.circleStrokeColor(Color(0xFFFFFFFF).toArgb()),
            PropertyFactory.circleStrokeWidth(2f)
        )
    )
}

/**
 * Satellite-roof-tracing map, now on MapLibre Native + OpenFreeMap (2026-08-18 "REPLACE THE
 * CURRENT MAP IMPLEMENTATION" — see [com.lumix.estimator.map.OpenFreeMapProvider]'s own doc for
 * why "MapLibre GL JS" became MapLibre Native for this platform, and [MapLibreMapView]'s doc for
 * the one file in this change that could not be compiled/verified in this environment). No API
 * key or billing account is needed for this screen to render tiles at all — [ManualSiteScreen]
 * remains available as an alternative entry point for an installer who prefers typed dimensions,
 * not as a fallback for a broken map.
 */
@Composable
fun SolarSiteMapScreen(
    viewModel: SolarSiteViewModel,
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
    onSwitchToManual: (() -> Unit)? = null
) {
    val palette = LocalLumixPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()

    val mapController = remember { MapController() }
    val roofController = remember { RoofDrawingService() }
    val geocodingProvider = remember { AndroidGeocodingProvider(context) }
    val locationManager = remember { DeviceLocationManager(context) }
    val compassManager = remember { CompassManager(context) }
    val compassState by compassManager.state.collectAsState()
    val connectivityObserver = remember { NetworkConnectivityObserver(context) }
    val isOnline by connectivityObserver.observe().collectAsState(initial = connectivityObserver.isOnline())

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var layerRefs by remember { mutableStateOf<MapLayerRefs?>(null) }
    var tileLoadError by remember { mutableStateOf(false) }
    // 2026-08-19 map diagnostics (Part 1): MapLibre's own failure text (e.g. an HTTP status from a
    // rejected MapTiler request) — see MapLibreMapView's onStyleLoadFailed doc.
    var tileLoadErrorDetail by remember { mutableStateOf<String?>(null) }
    var editMode by remember { mutableStateOf(RoofEditMode.NONE) }
    var selectedStyleUrl by remember { mutableStateOf(mapStyleOptions().first().styleUrl) }

    var searchQuery by remember { mutableStateOf(state.draftAddress ?: "") }
    var searchError by remember { mutableStateOf(false) }
    var searchSuggestions by remember { mutableStateOf<List<KnownPlace>>(emptyList()) }
    var roofFormVertices by remember { mutableStateOf<List<GeoPoint>?>(null) }

    DisposableEffect(Unit) {
        compassManager.start()
        onDispose { compassManager.stop() }
    }

    LaunchedEffect(state.draftLatitude, state.draftLongitude) {
        val lat = state.draftLatitude
        val lon = state.draftLongitude
        if (lat != null && lon != null) compassManager.updateLocation(lat, lon)
    }

    LaunchedEffect(searchQuery) {
        searchSuggestions = if (searchQuery.isBlank()) emptyList() else geocodingProvider.suggestKnownPlaces(searchQuery)
    }

    fun animateTo(point: GeoPoint, zoom: Double = 17.0) {
        mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(point.toLatLng(), zoom))
    }

    fun runSearch(query: String) {
        scope.launch {
            val results = geocodingProvider.search(query)
            val best = results.firstOrNull()
            if (best != null) {
                mapController.selectLocation(best.point)
                animateTo(best.point)
                searchError = false
                searchSuggestions = emptyList()
            } else {
                searchError = true
            }
        }
    }

    fun moveToDeviceLocation() {
        scope.launch {
            locationManager.lastKnownLocation()?.let { location ->
                val point = GeoPoint(location.latitude, location.longitude)
                mapController.selectLocation(point)
                animateTo(point, 18.0)
            }
        }
    }

    // 2026-08-18 map-view switcher: swap the base-map style at runtime. setStyle wipes every
    // source/layer, so the roof-tracing layers are re-added against the new style and layerRefs is
    // rebuilt; the SideEffect above then re-pushes the current pin/roofs into them. The map's own
    // camera and click listener live on the map (not the style) and survive the swap untouched.
    fun switchMapStyle(url: String) {
        if (url == selectedStyleUrl) return
        val map = mapLibreMap ?: return
        selectedStyleUrl = url
        layerRefs = null
        // 2026-08-19 map diagnostics (Part 1): logged immediately before the request so a failure
        // reported by MapLibreMapView's own listener (registered once on the persistent MapView —
        // see its own doc) can be matched to exactly which style switch it was for.
        android.util.Log.d("LumixMapDiag", "requesting style switch: $url")
        tileLoadError = false
        tileLoadErrorDetail = null
        map.setStyle(Style.Builder().fromUri(url)) { style ->
            android.util.Log.d("LumixMapDiag", "style switch loaded OK: $url")
            addRoofTracingLayers(style)
            layerRefs = MapLayerRefs(style)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) moveToDeviceLocation()
    }

    // Push the latest Compose state into the already-created GeoJSON sources after every
    // recomposition this state affects — the standard pattern for keeping an imperative object
    // (the MapLibre style) in sync with Compose state, since MapLibre's classes are not
    // Compose-aware the way com.google.maps.android.compose's Marker/Polygon composables were.
    SideEffect {
        val refs = layerRefs ?: return@SideEffect
        mapController.selectedLocation?.let { loc ->
            refs.selected.setGeoJson(FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(loc.toGeoJsonPoint()))))
        } ?: refs.selected.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        refs.savedFill.setGeoJson(savedPlanesFillFeatures(state.roofPlanes))
        refs.savedOutline.setGeoJson(savedPlanesOutlineFeatures(state.roofPlanes))
        refs.roofFill.setGeoJson(fillFeatures(roofController.vertices))
        refs.roofOutline.setGeoJson(outlineFeatures(roofController.vertices))
        val selectedIndex = roofController.selectedVertexIndex
        val unselected = roofController.vertices.filterIndexed { i, _ -> i != selectedIndex }
        refs.roofVertices.setGeoJson(pointFeatures(unselected))
        refs.roofSelectedVertex.setGeoJson(
            if (selectedIndex != null && selectedIndex in roofController.vertices.indices) {
                pointFeatures(listOf(roofController.vertices[selectedIndex]))
            } else {
                FeatureCollection.fromFeatures(emptyList())
            }
        )
    }

    fun handleMapClick(point: GeoPoint) {
        when {
            roofController.isDrawing -> roofController.addVertex(point)
            roofController.isEditing && editMode == RoofEditMode.MOVE -> {
                if (roofController.selectedVertexIndex != null) {
                    roofController.moveSelectedVertexTo(point)
                    roofController.clearSelection()
                } else {
                    // Nearest existing vertex within a small tolerance selects it; otherwise no-op —
                    // MOVE POINT only relocates a vertex the installer actually tapped near.
                    val nearest = roofController.vertices.withIndex().minByOrNull { (_, v) ->
                        (v.latitude - point.latitude) * (v.latitude - point.latitude) +
                            (v.longitude - point.longitude) * (v.longitude - point.longitude)
                    }
                    val toleranceDegSq = 0.00005 * 0.00005 * 200 // generous finger-sized tap tolerance
                    if (nearest != null) {
                        val d = (nearest.value.latitude - point.latitude) * (nearest.value.latitude - point.latitude) +
                            (nearest.value.longitude - point.longitude) * (nearest.value.longitude - point.longitude)
                        if (d < toleranceDegSq) roofController.selectVertex(nearest.index)
                    }
                }
            }
            roofController.isEditing && editMode == RoofEditMode.DELETE -> {
                val nearest = roofController.vertices.withIndex().minByOrNull { (_, v) ->
                    (v.latitude - point.latitude) * (v.latitude - point.latitude) +
                        (v.longitude - point.longitude) * (v.longitude - point.longitude)
                }
                if (nearest != null) roofController.deleteVertex(nearest.index)
            }
            roofController.isEditing && editMode == RoofEditMode.ADD -> {
                val afterIndex = roofController.selectedVertexIndex ?: (roofController.vertices.size - 1)
                roofController.insertVertexAfter(afterIndex, point)
            }
            else -> mapController.selectLocation(point)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MapLibreMapView(
            styleUrl = selectedStyleUrl,
            modifier = Modifier.fillMaxSize(),
            onMapReady = { map, style ->
                mapLibreMap = map
                tileLoadError = false
                tileLoadErrorDetail = null

                addRoofTracingLayers(style)
                layerRefs = MapLayerRefs(style)

                val initial = state.draftLatitude?.let { lat -> state.draftLongitude?.let { lon -> GeoPoint(lat, lon) } } ?: jamaicaDefault
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(initial.toLatLng(), if (initial == jamaicaDefault) 8.0 else 17.0))

                map.addOnMapClickListener { latLng ->
                    handleMapClick(latLng.toGeoPoint())
                    true
                }
            },
            onStyleLoadFailed = { errorMessage ->
                tileLoadError = true
                tileLoadErrorDetail = errorMessage
            }
        )

        // Top bar: back button + search field + map-type switch. Same no-Scaffold situation as
        // the bottom panel below — statusBarsPadding() is what keeps this clear of the status
        // bar/display cutout on edge-to-edge devices.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MapControlButton(onClick = onBack, contentDescription = "Back") {
                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                }
                GlassSurface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(LumixRadius.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it; searchError = false },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = palette.textPrimary),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) {
                                    Text("Search parish, town, or address", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                                }
                                inner()
                            }
                        )
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = palette.textSecondary,
                            modifier = Modifier.padding(start = 8.dp).clickable { runSearch(searchQuery) }
                        )
                    }
                }
            }
            if (searchSuggestions.isNotEmpty()) {
                GlassSurface(shape = RoundedCornerShape(LumixRadius.md)) {
                    LazyColumn(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        items(searchSuggestions) { place ->
                            Text(
                                place.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchQuery = place.label
                                        runSearch(place.label)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
            if (searchError) {
                Text(
                    "Couldn't find that location — try a more specific address, or enter coordinates manually.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.warningRedText,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            if (!isOnline) {
                OfflineBanner(onSwitchToManual = onSwitchToManual)
            }
            if (tileLoadError) {
                TileErrorBanner(detail = tileLoadErrorDetail)
            }
            MapStyleSwitcher(
                selectedStyleUrl = selectedStyleUrl,
                onSelect = { switchMapStyle(it) }
            )
        }

        // Right-side floating controls: zoom, my location, 3D. (Base-map view switching moved to
        // the labeled MapStyleSwitcher under the search bar — a real OpenFreeMap style swap now,
        // not the old dead-end "satellite unavailable" toggle.)
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MapControlButton(
                onClick = { mapLibreMap?.let { map -> map.animateCamera(CameraUpdateFactory.zoomIn()) } },
                contentDescription = "Zoom in"
            ) { Icon(Icons.Default.Add, contentDescription = null) }
            MapControlButton(
                onClick = { mapLibreMap?.let { map -> map.animateCamera(CameraUpdateFactory.zoomOut()) } },
                contentDescription = "Zoom out"
            ) { Icon(Icons.Default.Remove, contentDescription = null) }
            MapControlButton(
                onClick = {
                    mapController.toggle3D()
                    val map = mapLibreMap
                    val target = mapController.selectedLocation?.toLatLng() ?: map?.cameraPosition?.target
                    if (map != null && target != null) {
                        val newTilt = if (mapController.is3D) MapController.TILT_3D_DEGREES else MapController.TILT_FLAT_DEGREES
                        map.animateCamera(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder(map.cameraPosition)
                                    .target(target)
                                    .tilt(newTilt)
                                    .build()
                            )
                        )
                    }
                },
                contentDescription = "Toggle 3D view",
                active = mapController.is3D
            ) { Icon(Icons.Default.ThreeDRotation, contentDescription = null) }
            MapControlButton(
                onClick = {
                    if (locationManager.hasPermission()) {
                        moveToDeviceLocation()
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                contentDescription = "My location"
            ) { Icon(Icons.Default.MyLocation, contentDescription = null) }
        }

        if (compassManager.isAvailable) {
            SolarCompassBadge(
                headingDegrees = compassState.trueHeadingDegrees,
                modifier = Modifier.align(Alignment.TopStart).padding(top = 84.dp, start = 16.dp)
            )
        }

        // Bottom panel. This screen has no Scaffold (it's a full-bleed map with overlaid
        // controls), so unlike Scaffold-based screens it gets zero automatic protection from
        // the system navigation bar — navigationBarsPadding() here is load-bearing, not defensive.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            when {
                roofController.isDrawing -> RoofDrawingControls(
                    vertexCount = roofController.vertices.size,
                    onUndo = roofController::undo,
                    onClear = roofController::clear,
                    onCancel = roofController::cancelDrawing,
                    onDone = {
                        if (roofController.vertices.size >= 3) {
                            roofFormVertices = roofController.finishDrawing()
                        }
                    }
                )
                roofController.isEditing -> RoofEditingControls(
                    mode = editMode,
                    onModeChange = { editMode = it; roofController.clearSelection() },
                    onClear = { roofController.clear(); editMode = RoofEditMode.NONE },
                    onRedraw = { roofController.startDrawing(); editMode = RoofEditMode.NONE },
                    onDone = {
                        val vertices = roofController.finishEditing()
                        if (vertices.size >= 3) roofFormVertices = vertices
                        editMode = RoofEditMode.NONE
                    }
                )
                else -> SiteAnalysisPanel(
                    hasLocation = state.hasLocation,
                    hasPin = mapController.selectedLocation != null,
                    roofPlaneCount = state.roofPlanes.size,
                    totalCapacityKw = state.roofPlanes.sumOf { it.panelLayout?.totalCapacityKw ?: 0.0 },
                    canEditRoof = state.roofPlanes.isNotEmpty(),
                    onUseLocation = {
                        mapController.selectedLocation?.let { loc ->
                            scope.launch {
                                // Reverse-search this exact point back through the geocoder to
                                // pick up a real parish/town when the installer dropped a pin
                                // rather than typing a search — feeds StepLocation's parish/PSH,
                                // never fabricated locally.
                                val results = geocodingProvider.search("${loc.latitude},${loc.longitude}")
                                val match = results.firstOrNull()
                                viewModel.setLocation(
                                    loc.latitude, loc.longitude,
                                    searchQuery.ifBlank { match?.label },
                                    null,
                                    match?.parish, match?.town
                                )
                            }
                        }
                    },
                    onTraceRoof = { roofController.startDrawing() },
                    onEditRoof = {
                        state.roofPlanes.lastOrNull()?.let { plane -> roofController.startEditing(plane.vertices) }
                    },
                    onSaveSite = { scope.launch { viewModel.saveSite()?.let(onSaved) } }
                )
            }
        }
    }

    if (roofFormVertices != null) {
        ModalBottomSheet(
            onDismissRequest = { roofFormVertices = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            RoofConfirmForm(
                vertices = roofFormVertices!!,
                onConfirm = { pitch, azimuth, panelWidthM, panelHeightM, panelWattage, setbackM, excludedAreaM2, shadingFactor ->
                    viewModel.addTracedRoofPlane(
                        roofFormVertices!!, pitch, azimuth, panelWidthM, panelHeightM, panelWattage, setbackM, excludedAreaM2, shadingFactor
                    )
                    roofFormVertices = null
                },
                onCancel = { roofFormVertices = null }
            )
        }
    }
}

/**
 * Shown whenever [NetworkConnectivityObserver] reports no internet — the one thing this screen
 * cannot function without (map tiles + address search both need a live connection), unlike the
 * rest of Solar Site, which is pure local math. Points straight at the always-available escape
 * hatch rather than leaving the user staring at blank map tiles with no explanation.
 */
@Composable
private fun OfflineBanner(onSwitchToManual: (() -> Unit)?) {
    val palette = LocalLumixPalette.current
    GlassSurface(shape = RoundedCornerShape(LumixRadius.md)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.WifiOff, contentDescription = null, tint = palette.warningRedText)
            Column(modifier = Modifier.weight(1f)) {
                Text("You're offline", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Text(
                    "Map tiles and address search need a connection. Roof tracing still works once tiles are loaded, or switch to typed dimensions.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary
                )
            }
            if (onSwitchToManual != null) {
                LumixSecondaryButton(text = "Manual Entry", onClick = onSwitchToManual)
            }
        }
    }
}

/**
 * "If tiles fail to load: show a useful error... Do NOT display a blank white/black map with no
 * explanation." 2026-08-19 map diagnostics (Part 1): [detail] surfaces MapLibre's own error text
 * (a 401/403 from a bad/mismatched MapTiler key shows up here, not just a generic "check your
 * connection" message that would hide exactly the failure this Part 1 diagnosis is looking for).
 */
@Composable
private fun TileErrorBanner(detail: String? = null) {
    val palette = LocalLumixPalette.current
    GlassSurface(shape = RoundedCornerShape(LumixRadius.md)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "Map tiles could not be loaded.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.warningRedText
            )
            if (!detail.isNullOrBlank()) {
                Text(detail, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            } else {
                Text("Check your internet connection.", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
        }
    }
}

/**
 * 2026-08-18 map-view switcher: a compact segmented control to flip the base map between
 * [mapStyleOptions].
 *
 * 2026-08-19 (map Part 2): originally one `weight(1f)` cell per option, sized to divide a fixed
 * total width evenly — correct for a small, fixed set of labels, but adding "Terrain" (a 5th
 * option, once a MapTiler key is configured) meant every cell got proportionally narrower, and
 * `Text`'s default `maxLines = 1` doesn't wrap OR shrink — it clips mid-word once a label no
 * longer fits, recreating the exact "jumbled buttons" bug already fixed once in this file. Each
 * cell now sizes to its own natural label width instead, and the whole row scrolls horizontally
 * (`horizontalScroll`) if there are ever more options than fit on screen at once — so a label is
 * never compressed below its readable size, regardless of how many styles exist now or later.
 */
@Composable
private fun MapStyleSwitcher(selectedStyleUrl: String, onSelect: (String) -> Unit) {
    // 2026-08-18 contrast fix: GlassSurface alone (a translucent tint over whatever map imagery is
    // underneath) left the unselected labels reading as low-contrast gray-on-gray against a busy
    // map — hard to read regardless of light/dark theme. This gives the whole switcher a solid
    // near-opaque dark backing (not just the glass tint), plus near-white unselected text and a
    // bold near-black selected label on solid yellow, so every label is readable at a glance no
    // matter what's under the map at that pan/zoom.
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(LumixRadius.md))
            .background(Color(0xE6141414))
            .horizontalScroll(rememberScrollState())
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        mapStyleOptions().forEach { option ->
            val selected = option.styleUrl == selectedStyleUrl
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(LumixRadius.sm))
                    .background(if (selected) Color(0xFFFFD84D) else Color.Transparent)
                    .clickable { onSelect(option.styleUrl) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    color = if (selected) Color(0xFF1A1A1A) else Color(0xFFF2F2F2)
                )
            }
        }
    }
}

/**
 * 2026-08-19 map Part 2 fix ("none of these are currently visible/working"): the back/zoom/3D/
 * my-location buttons were using [com.lumix.estimator.ui.components.LumixIconButtonSurface], whose
 * `palette.glass` background is a deliberately near-transparent tint (~8-10% opacity — see
 * `Color.kt`'s `GlassDark`/`GlassLight`) meant to sit over a known, solid app surface. Floating
 * directly over a live map with unpredictable, wildly varying colors underneath (ocean, forest,
 * urban gray, bright satellite haze), that same near-transparent tint plus a 1dp outline is often
 * genuinely close to invisible — the exact same root cause already found and fixed for the map-
 * style switcher a few rounds ago. This gives every floating map control that same solid,
 * near-opaque backing instead, so it reads at a glance regardless of what's under it.
 */
@Composable
private fun MapControlButton(onClick: () -> Unit, contentDescription: String, active: Boolean = false, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0xE6141414))
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides if (active) Color(0xFFFFD84D) else Color(0xFFF2F2F2)) {
            content()
        }
    }
}

@Composable
private fun RoofDrawingControls(
    vertexCount: Int,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    val palette = LocalLumixPalette.current
    GlassSurface(shape = RoundedCornerShape(LumixRadius.lg)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (vertexCount < 3) "Tap points around the roof edge ($vertexCount placed)" else "$vertexCount points — tap Done to close the polygon",
                style = MaterialTheme.typography.labelMedium,
                color = palette.textPrimary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                LumixSecondaryButton(text = "Undo", onClick = onUndo, enabled = vertexCount > 0, modifier = Modifier.weight(1f))
                LumixSecondaryButton(text = "Clear", onClick = onClear, enabled = vertexCount > 0, modifier = Modifier.weight(1f))
                LumixSecondaryButton(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
                LumixPrimaryButton(text = "Done", onClick = onDone, enabled = vertexCount >= 3, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** "The polygon must remain editable. Allow: MOVE POINT, DELETE POINT, ADD POINT, CLEAR ROOF, REDRAW." */
@Composable
private fun RoofEditingControls(
    mode: RoofEditMode,
    onModeChange: (RoofEditMode) -> Unit,
    onClear: () -> Unit,
    onRedraw: () -> Unit,
    onDone: () -> Unit
) {
    val palette = LocalLumixPalette.current
    GlassSurface(shape = RoundedCornerShape(LumixRadius.lg)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                when (mode) {
                    RoofEditMode.NONE -> "Editing roof — choose an action below"
                    RoofEditMode.MOVE -> "Tap a point, then tap where it should move to"
                    RoofEditMode.DELETE -> "Tap a point to delete it"
                    RoofEditMode.ADD -> "Tap the map to add a new point"
                },
                style = MaterialTheme.typography.labelMedium,
                color = palette.textPrimary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ModeChip("Move", mode == RoofEditMode.MOVE, Modifier.weight(1f)) { onModeChange(if (mode == RoofEditMode.MOVE) RoofEditMode.NONE else RoofEditMode.MOVE) }
                ModeChip("Delete", mode == RoofEditMode.DELETE, Modifier.weight(1f)) { onModeChange(if (mode == RoofEditMode.DELETE) RoofEditMode.NONE else RoofEditMode.DELETE) }
                ModeChip("Add", mode == RoofEditMode.ADD, Modifier.weight(1f)) { onModeChange(if (mode == RoofEditMode.ADD) RoofEditMode.NONE else RoofEditMode.ADD) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                LumixSecondaryButton(text = "Clear Roof", onClick = onClear, modifier = Modifier.weight(1f))
                LumixSecondaryButton(text = "Redraw", onClick = onRedraw, modifier = Modifier.weight(1f))
                LumixPrimaryButton(text = "Done", onClick = onDone, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalLumixPalette.current
    Box(
        modifier = modifier
            .background(
                if (selected) palette.solarYellow.copy(alpha = 0.25f) else Color.Transparent,
                RoundedCornerShape(LumixRadius.sm)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) palette.solarYellowText else palette.textPrimary)
    }
}

@Composable
private fun SiteAnalysisPanel(
    hasLocation: Boolean,
    hasPin: Boolean,
    roofPlaneCount: Int,
    totalCapacityKw: Double,
    canEditRoof: Boolean,
    onUseLocation: () -> Unit,
    onTraceRoof: () -> Unit,
    onEditRoof: () -> Unit,
    onSaveSite: () -> Unit
) {
    val palette = LocalLumixPalette.current
    GlassSurface(shape = RoundedCornerShape(LumixRadius.lg)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("SITE ANALYSIS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.textSecondary)
            when {
                !hasLocation && !hasPin -> Text(
                    "Search an address or tap the map to drop a pin on the property.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary
                )
                !hasLocation && hasPin -> LumixPrimaryButton(text = "Use This Location", onClick = onUseLocation, modifier = Modifier.fillMaxWidth())
                else -> {
                    Text(
                        if (roofPlaneCount == 0) "Location set — trace the roof to continue." else "$roofPlaneCount roof plane(s) · %.2f kW total".format(totalCapacityKw),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        LumixSecondaryButton(text = "Trace Roof", onClick = onTraceRoof, modifier = Modifier.weight(1f))
                        if (canEditRoof) {
                            LumixSecondaryButton(text = "Edit Roof", onClick = onEditRoof, modifier = Modifier.weight(1f))
                        }
                        if (roofPlaneCount > 0) {
                            LumixPrimaryButton(text = "Save Site", onClick = onSaveSite, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoofConfirmForm(
    vertices: List<GeoPoint>,
    onConfirm: (
        pitchDegrees: Double?,
        azimuthDegrees: Double,
        panelWidthM: Double,
        panelHeightM: Double,
        panelWattage: Double,
        setbackM: Double,
        excludedAreaM2: Double,
        shadingFactor: Double
    ) -> Unit,
    onCancel: () -> Unit
) {
    val palette = LocalLumixPalette.current
    val suggested = remember(vertices) { RoofGeometryEngine.suggestAzimuthCandidates(vertices) }
    val horizontalArea = remember(vertices) { RoofGeometryEngine.horizontalAreaM2(vertices) }

    var azimuth by remember { mutableStateOf(suggested?.first ?: 180.0) }
    var pitch by remember { mutableStateOf<Double?>(20.0) }
    var panelWidthM by remember { mutableStateOf(2.278) }
    var panelHeightM by remember { mutableStateOf(1.134) }
    var panelWattage by remember { mutableStateOf(600.0) }
    var setbackM by remember { mutableStateOf(0.5) }
    var selectedObstructions by remember { mutableStateOf<Set<ShadeObstructionType>>(emptySet()) }
    var exposurePercent by remember { mutableStateOf(100.0) }
    var excludedAreaM2 by remember { mutableStateOf(0.0) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Confirm Roof Plane", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        Text(
            "Traced area: %.1f m² (horizontal satellite projection — a preliminary estimate).".format(horizontalArea),
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary
        )
        if (suggested != null) {
            Text(
                "This roof edge suggests it faces ${RoofGeometryEngine.compassLabel(suggested.first)} (${suggested.first.toInt()}°) or ${RoofGeometryEngine.compassLabel(suggested.second)} (${suggested.second.toInt()}°) — a flat trace can't tell which side slopes down, so confirm the actual direction.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary
            )
        }
        NumberField(
            label = "Azimuth (facing direction)",
            value = azimuth,
            onValueChange = { azimuth = it.mod(360.0) },
            suffix = "°",
            supportingText = RoofGeometryEngine.compassLabel(azimuth)
        )
        LabeledDropdown(
            label = "Roof pitch",
            options = pitchOptions,
            selected = pitch,
            optionLabel = { it?.let { d -> "${d.toInt()}°" } ?: "Unknown — use horizontal estimate" },
            onSelected = { pitch = it }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            NumberField(label = "Panel width (m)", value = panelWidthM, onValueChange = { panelWidthM = it }, suffix = "m", modifier = Modifier.weight(1f))
            NumberField(label = "Panel height (m)", value = panelHeightM, onValueChange = { panelHeightM = it }, suffix = "m", modifier = Modifier.weight(1f))
        }
        NumberField(label = "Panel wattage", value = panelWattage, onValueChange = { panelWattage = it }, allowDecimal = false, suffix = "W")
        NumberField(label = "Setback from edges (m)", value = setbackM, onValueChange = { setbackM = it }, suffix = "m")

        ShadeAndExclusionSection(
            selectedObstructions = selectedObstructions,
            onToggleObstruction = { type ->
                selectedObstructions = if (type in selectedObstructions) selectedObstructions - type else selectedObstructions + type
                exposurePercent = ShadeEstimator.suggestExposureFraction(selectedObstructions) * 100.0
            },
            exposurePercent = exposurePercent,
            onExposurePercentChange = { exposurePercent = it },
            excludedAreaM2 = excludedAreaM2,
            onExcludedAreaChange = { excludedAreaM2 = it }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            LumixSecondaryButton(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
            LumixPrimaryButton(
                text = "Add Roof Plane",
                onClick = { onConfirm(pitch, azimuth, panelWidthM, panelHeightM, panelWattage, setbackM, excludedAreaM2, exposurePercent / 100.0) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
