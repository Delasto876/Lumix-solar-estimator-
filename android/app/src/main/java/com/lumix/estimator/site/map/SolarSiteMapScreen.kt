package com.lumix.estimator.site.map

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.ThreeDRotation
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapType
import com.google.maps.android.compose.DragState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.lumix.estimator.location.DeviceLocationManager
import com.lumix.estimator.map.AndroidGeocodingProvider
import com.lumix.estimator.map.GoogleMapsConfig
import com.lumix.estimator.map.KnownPlace
import com.lumix.estimator.network.NetworkConnectivityObserver
import com.lumix.estimator.sensors.CompassManager
import com.lumix.estimator.site.ElevationFetchState
import com.lumix.estimator.site.GeoPoint
import com.lumix.estimator.site.PanelOrientation
import com.lumix.estimator.site.RoofPlane
import com.lumix.estimator.site.ShadeAndExclusionSection
import com.lumix.estimator.site.SiteMeasurement
import com.lumix.estimator.site.SiteMeasurementKind
import com.lumix.estimator.site.SolarCompassBadge
import com.lumix.estimator.site.SolarApiFetchState
import com.lumix.estimator.site.SolarSiteViewModel
import com.lumix.estimator.site.StreetViewFetchState
import com.lumix.estimator.site.elevation.GoogleElevationApiClient
import com.lumix.estimator.site.geometry.DistanceCalculator
import com.lumix.estimator.site.geometry.SolarSuitability
import com.lumix.estimator.site.geometry.SolarSuitabilityCalculator
import com.lumix.estimator.site.solarapi.GoogleSolarApiClient
import com.lumix.estimator.site.streetview.GoogleStreetViewClient
import com.lumix.estimator.site.geometry.PanelLayoutOptimizer
import com.lumix.estimator.site.geometry.RoofExclusionType
import com.lumix.estimator.site.geometry.RoofExclusionZone
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

private val pitchOptions: List<Double?> = listOf(null, 0.0, 5.0, 10.0, 15.0, 20.0, 25.0, 30.0, 35.0, 40.0, 45.0)
private val jamaicaDefault = GeoPoint(18.1096, -77.2975)

private enum class RoofEditMode { NONE, MOVE, DELETE, ADD }

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)
private fun LatLng.toGeoPoint() = GeoPoint(latitude, longitude)


/**
 * 2026-08-19 ("change map to google map"): the base-map looks the installer can flip between —
 * now Google Maps' own native [MapType]s (real satellite/terrain/hybrid imagery Google itself
 * serves), not a third-party style URL (MapTiler/OpenFreeMap) the way the MapLibre round needed.
 * "Satellite" stays first/default, matching the earlier "let satellite view be the default view"
 * decision — only the underlying provider changed, not that choice.
 */
private data class MapTypeOption(val label: String, val mapType: MapType)

private val mapTypeOptions = listOf(
    MapTypeOption("Satellite", MapType.SATELLITE),
    MapTypeOption("Streets", MapType.NORMAL),
    MapTypeOption("Terrain", MapType.TERRAIN),
    MapTypeOption("Hybrid", MapType.HYBRID)
)

/** Every small colored "dot" marker icon this screen draws, generated once per [Density] rather than per-recomposition. */
private data class MapMarkerIcons(
    val vertex: BitmapDescriptor,
    val selectedVertex: BitmapDescriptor,
    val selectedLocation: BitmapDescriptor,
    val measurePoint: BitmapDescriptor
)

/**
 * 2026-08-19 ("change map to google map"): Google's default [Marker] icon is a teardrop pin,
 * right for a single "this is a place" marker but wrong for a roof-trace VERTEX (a point on a
 * line, not a location) — the old MapLibre version rendered vertices as small flat circles via a
 * `CircleLayer`, and this reproduces that same look with a procedurally-drawn dot bitmap instead,
 * since Maps Compose has no equivalent "circle marker" primitive of its own (its [com.google
 * .maps.android.compose.Circle] composable draws a geographic-radius circle sized in meters, not
 * a fixed-pixel screen marker, so it can't stand in for a tap target here).
 */
private fun buildMapMarkerIcons(density: Density): MapMarkerIcons = with(density) {
    MapMarkerIcons(
        vertex = dotBitmapDescriptor(14.dp.roundToPx(), Color(0xFFFFD84D).toArgb(), Color(0xFF1A1A1A).toArgb(), 2.dp.toPx()),
        selectedVertex = dotBitmapDescriptor(20.dp.roundToPx(), Color(0xFFFF5252).toArgb(), Color(0xFFFFFFFF).toArgb(), 2.dp.toPx()),
        selectedLocation = dotBitmapDescriptor(18.dp.roundToPx(), Color(0xFFFFD84D).toArgb(), Color(0xFF1A1A1A).toArgb(), 2.dp.toPx()),
        measurePoint = dotBitmapDescriptor(16.dp.roundToPx(), Color(0xFF4DA6FF).toArgb(), Color(0xFFFFFFFF).toArgb(), 2.dp.toPx())
    )
}

private fun dotBitmapDescriptor(diameterPx: Int, fillColor: Int, strokeColor: Int, strokeWidthPx: Float): BitmapDescriptor {
    val size = diameterPx.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor; style = Paint.Style.FILL }
    canvas.drawCircle(center, center, (center - strokeWidthPx).coerceAtLeast(0f), fillPaint)
    if (strokeWidthPx > 0f) {
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
        }
        canvas.drawCircle(center, center, (center - strokeWidthPx / 2f).coerceAtLeast(0f), strokePaint)
    }
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/**
 * Satellite-roof-tracing map. 2026-08-19 ("change map to google map, i am going to use google map
 * api in the app"): reverses the earlier "REPLACE THE CURRENT MAP IMPLEMENTATION" round, which had
 * moved off Google Maps specifically to avoid needing an API key/billing account — the user now
 * has (or is getting) a real Maps key, so this screen renders on the real Google Maps SDK for
 * Android via its official Jetpack Compose bindings (`com.google.maps.android:maps-compose`)
 * instead of MapLibre Native + third-party style/imagery providers. Needs `MAPS_API_KEY` in
 * `android/local.properties` to render any tiles at all — see `GoogleMapsConfig`'s own doc and
 * this file's `MapKeyMissingBanner`. [ManualSiteScreen] remains available as an alternative entry
 * point for an installer who prefers typed dimensions, not as a fallback for a broken map.
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
    val solarApiClient = remember { GoogleSolarApiClient() }
    val elevationClient = remember { GoogleElevationApiClient() }
    val streetViewClient = remember { GoogleStreetViewClient() }
    val geocodingProvider = remember { AndroidGeocodingProvider(context) }
    val locationManager = remember { DeviceLocationManager(context) }
    val compassManager = remember { CompassManager(context) }
    val compassState by compassManager.state.collectAsState()
    val connectivityObserver = remember { NetworkConnectivityObserver(context) }
    val isOnline by connectivityObserver.observe().collectAsState(initial = connectivityObserver.isOnline())

    val density = LocalDensity.current
    val markerIcons = remember(density) { buildMapMarkerIcons(density) }

    var editMode by remember { mutableStateOf(RoofEditMode.NONE) }
    var selectedMapType by remember { mutableStateOf(mapTypeOptions.first().mapType) }
    // 2026-08-19 ("change map to google map"): Traffic is now a REAL toggle, not just flagged as
    // out of scope the way map Part 2 had to leave it — Google Maps' SDK has genuine live traffic
    // data built in (MapProperties.isTrafficEnabled), unlike MapLibre/MapTiler, which had no
    // traffic data source at all. A boolean overlay on top of any base map type, not a 5th
    // MapTypeOption, since real traffic layers work that way in every mapping app.
    var trafficEnabled by remember { mutableStateOf(false) }

    // 2026-08-19 (map Part 6, "pin two points and measure the distance between them ... useful
    // for cable runs, roof-to-inverter placement distance, etc."): deliberately independent of
    // the roof-tracing state machine above — a separate on/off mode toggled from a floating
    // control, not something that needs its own RoofDrawingService-style undo history (there's
    // only ever at most 2 points).
    var measureModeActive by remember { mutableStateOf(false) }
    var measurePointA by remember { mutableStateOf<GeoPoint?>(null) }
    var measurePointB by remember { mutableStateOf<GeoPoint?>(null) }
    // Site Survey / Solar Mapping round ("measurement tool extensions... useful for cable runs,
    // roof-to-inverter placement distance, electrical service distance"): the two-point measure
    // above was always ephemeral (discarded on Done) — this lets the installer name and keep one
    // as a real SiteMeasurement instead, without changing the ephemeral default for a quick check.
    var measurementKind by remember { mutableStateOf(SiteMeasurementKind.ELECTRICAL_SERVICE) }
    var measurementLabel by remember { mutableStateOf("") }
    var showMeasurementsSheet by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf(state.draftAddress ?: "") }
    var searchError by remember { mutableStateOf(false) }
    var searchSuggestions by remember { mutableStateOf<List<KnownPlace>>(emptyList()) }
    var roofFormVertices by remember { mutableStateOf<List<GeoPoint>?>(null) }

    // Site Survey / Solar Mapping round (spec "Allow the user to mark exclusions manually:
    // Chimneys, Vents, Skylights, AC units, ... Structural obstructions, ... Other obstacles"):
    // a second, independent RoofDrawingService instance rather than reusing [roofController] —
    // an obstruction trace has its own separate undo/redo history and must never be confused with
    // an in-progress roof trace/edit (the two floating buttons are mutually gated below so only
    // one drawing mode can be active at a time).
    val obstructionController = remember { RoofDrawingService() }
    var obstructionFormVertices by remember { mutableStateOf<List<GeoPoint>?>(null) }
    var showObstructionsSheet by remember { mutableStateOf(false) }
    // Site Survey / Solar Mapping round ("Integrate Street View (where available) for site
    // verification"): a live verification aid, not sizing-relevant survey data — deliberately not
    // persisted onto SolarSite the way roof planes/exclusions/measurements are (see
    // StreetViewFetchState's own doc), so this only needs local UI state for whether the sheet is
    // showing.
    var showStreetViewSheet by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(jamaicaDefault.toLatLng(), 8f)
    }

    DisposableEffect(Unit) {
        compassManager.start()
        onDispose { compassManager.stop() }
    }

    LaunchedEffect(state.draftLatitude, state.draftLongitude) {
        val lat = state.draftLatitude
        val lon = state.draftLongitude
        if (lat != null && lon != null) {
            compassManager.updateLocation(lat, lon)
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(GeoPoint(lat, lon).toLatLng(), 17f))
        }
    }

    LaunchedEffect(searchQuery) {
        searchSuggestions = if (searchQuery.isBlank()) emptyList() else geocodingProvider.suggestKnownPlaces(searchQuery)
    }

    fun animateTo(point: GeoPoint, zoom: Float = 17f) {
        scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(point.toLatLng(), zoom)) }
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
                animateTo(point, 18f)
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) moveToDeviceLocation()
    }

    fun handleMapClick(point: GeoPoint) {
        when {
            obstructionController.isDrawing -> obstructionController.addVertex(point)
            roofController.isDrawing -> roofController.addVertex(point)
            roofController.isEditing && editMode == RoofEditMode.ADD -> {
                val afterIndex = roofController.selectedVertexIndex ?: (roofController.vertices.size - 1)
                roofController.insertVertexAfter(afterIndex, point)
            }
            // 2026-08-19 ("change map to google map"): MOVE (drag) and DELETE are now both handled
            // by each vertex's own Marker directly (draggable = true, and onClick when in DELETE
            // mode, in RoofVertexMarker below) — real per-marker hit-testing from the SDK itself,
            // not the nearest-tapped-point approximation the old MapLibre version needed since it
            // had no annotation-marker API to delegate to. A plain map tap in either of those two
            // modes intentionally does nothing.
            roofController.isEditing && (editMode == RoofEditMode.MOVE || editMode == RoofEditMode.DELETE) -> {}
            measureModeActive -> when {
                measurePointA == null -> measurePointA = point
                measurePointB == null -> measurePointB = point
                else -> { measurePointA = point; measurePointB = null }
            }
            else -> mapController.selectLocation(point)
        }
    }

    val mapProperties = remember(selectedMapType, trafficEnabled) {
        MapProperties(mapType = selectedMapType, isTrafficEnabled = trafficEnabled)
    }
    val mapUiSettings = remember {
        MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings,
            onMapClick = { latLng -> handleMapClick(latLng.toGeoPoint()) }
        ) {
            mapController.selectedLocation?.let { loc ->
                StaticGeoMarker(point = loc, icon = markerIcons.selectedLocation)
            }

            // Already-confirmed roof planes (from this session or an earlier one). Site Survey /
            // Solar Mapping round (spec "Show shading/sun exposure visually on the roof/map"):
            // fill/stroke color now tracks each plane's own real SolarSuitability tier instead of
            // one fixed green for every plane, regardless of how good that plane's own exposure is.
            state.roofPlanes.filter { it.vertices.size >= 3 }.forEach { plane ->
                val suitability = SolarSuitabilityCalculator.evaluate(plane, state.draftLatitude ?: 0.0).tier
                Polygon(
                    points = plane.vertices.map { it.toLatLng() },
                    fillColor = suitabilityFillColor(suitability),
                    strokeColor = suitabilityStrokeColor(suitability),
                    strokeWidth = 3f
                )
            }

            // The roof currently being traced or edited.
            val roofVertices = roofController.vertices
            if (roofVertices.size >= 3) {
                Polygon(
                    points = roofVertices.map { it.toLatLng() },
                    fillColor = Color(0x4DFFD84D),
                    strokeColor = Color(0xFFFFD84D),
                    strokeWidth = 4f
                )
            } else if (roofVertices.size == 2) {
                Polyline(points = roofVertices.map { it.toLatLng() }, color = Color(0xFFFFD84D), width = 4f)
            }
            roofVertices.forEachIndexed { index, point ->
                key(index) {
                    val selected = index == roofController.selectedVertexIndex
                    RoofVertexMarker(
                        point = point,
                        icon = if (selected) markerIcons.selectedVertex else markerIcons.vertex,
                        draggable = roofController.isEditing && editMode == RoofEditMode.MOVE,
                        onTap = {
                            if (roofController.isEditing && editMode == RoofEditMode.DELETE) {
                                roofController.deleteVertex(index)
                            }
                        },
                        onDragStart = { roofController.beginVertexDrag(index) },
                        onDragMove = { moved -> roofController.updateVertexDragPosition(index, moved) },
                        onDragEnd = { roofController.endVertexDrag() }
                    )
                }
            }

            // Site Survey / Solar Mapping round: already-added exclusion zones (chimneys, vents,
            // skylights, ...) for every roof plane, drawn as small red polygons distinct from the
            // suitability-colored roof planes themselves so they read as "avoid this area," not
            // "this section is unsuitable."
            state.roofPlanes.forEach { plane ->
                plane.exclusionZones.forEach { zone ->
                    if (zone.vertices.size >= 3) {
                        Polygon(
                            points = zone.vertices.map { it.toLatLng() },
                            fillColor = Color(0x66FF5252),
                            strokeColor = Color(0xFFFF5252),
                            strokeWidth = 2f
                        )
                    }
                }
            }

            // The obstruction polygon currently being traced.
            val obstructionVertices = obstructionController.vertices
            if (obstructionVertices.size >= 3) {
                Polygon(
                    points = obstructionVertices.map { it.toLatLng() },
                    fillColor = Color(0x66FF5252),
                    strokeColor = Color(0xFFFF5252),
                    strokeWidth = 4f
                )
            } else if (obstructionVertices.size == 2) {
                Polyline(points = obstructionVertices.map { it.toLatLng() }, color = Color(0xFFFF5252), width = 4f)
            }
            obstructionVertices.forEachIndexed { index, point ->
                key("obstruction_$index") {
                    StaticGeoMarker(point = point, icon = markerIcons.vertex)
                }
            }

            // Distance measurement (map Part 6).
            val measurePoints = listOfNotNull(measurePointA, measurePointB)
            if (measurePoints.size == 2) {
                Polyline(
                    points = measurePoints.map { it.toLatLng() },
                    color = Color(0xFF4DA6FF),
                    width = 6f,
                    pattern = listOf(Dash(30f), Gap(20f))
                )
            }
            // key(index), not key(point): the SAME two slots (first pin, second pin) should keep
            // stable marker identity across a measurement, not get torn down and recreated the
            // instant either point moves — see StaticGeoMarker's own doc for why identity churn is
            // the thing to avoid, not just "does the value look different."
            measurePoints.forEachIndexed { index, point ->
                key(index) {
                    StaticGeoMarker(point = point, icon = markerIcons.measurePoint)
                }
            }
        }

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
            if (!GoogleMapsConfig.isConfigured) {
                MapKeyMissingBanner()
            }
            SolarApiFetchBanner(state.solarApiFetchState)
            ElevationFetchBanner(state.elevationFetchState)
            if (state.roofPlanes.any { it.vertices.size >= 3 }) {
                SolarSuitabilityLegend()
            }
            MapTypeSwitcher(
                selectedMapType = selectedMapType,
                onSelect = { selectedMapType = it }
            )
        }

        // Right-side floating controls: zoom, my location, 3D, traffic, measure.
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MapControlButton(
                onClick = { scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomIn()) } },
                contentDescription = "Zoom in"
            ) { Icon(Icons.Default.Add, contentDescription = null) }
            MapControlButton(
                onClick = { scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomOut()) } },
                contentDescription = "Zoom out"
            ) { Icon(Icons.Default.Remove, contentDescription = null) }
            MapControlButton(
                onClick = {
                    mapController.toggle3D()
                    val target = mapController.selectedLocation?.toLatLng() ?: cameraPositionState.position.target
                    val newTilt = if (mapController.is3D) MapController.TILT_3D_DEGREES.toFloat() else MapController.TILT_FLAT_DEGREES.toFloat()
                    val newPosition = CameraPosition.Builder(cameraPositionState.position)
                        .target(target)
                        .tilt(newTilt)
                        .build()
                    scope.launch { cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(newPosition)) }
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
            MapControlButton(
                onClick = { trafficEnabled = !trafficEnabled },
                contentDescription = "Toggle traffic",
                active = trafficEnabled
            ) { Icon(Icons.Default.Traffic, contentDescription = null) }
            // 2026-08-19 (map Part 6): turning measure mode off also clears any in-progress pins —
            // leaving a stale point pinned after leaving the mode would be confusing, and there's
            // nothing to preserve (measurements aren't saved anywhere, unlike a traced roof).
            MapControlButton(
                onClick = {
                    measureModeActive = !measureModeActive
                    if (!measureModeActive) {
                        measurePointA = null
                        measurePointB = null
                        measurementLabel = ""
                    }
                },
                contentDescription = "Measure distance",
                active = measureModeActive
            ) { Icon(Icons.Default.Straighten, contentDescription = null) }
            if (state.siteMeasurements.isNotEmpty()) {
                MapControlButton(
                    onClick = { showMeasurementsSheet = true },
                    contentDescription = "View saved measurements"
                ) { Icon(Icons.Default.FormatListBulleted, contentDescription = null) }
            }
            if (state.hasLocation) {
                MapControlButton(
                    onClick = { scope.launch { viewModel.fetchElevation(elevationClient) } },
                    contentDescription = "Ground elevation",
                    active = state.elevationFetchState is ElevationFetchState.Loading
                ) { Icon(Icons.Default.Terrain, contentDescription = null) }
                MapControlButton(
                    onClick = {
                        showStreetViewSheet = true
                        scope.launch { viewModel.fetchStreetView(streetViewClient) }
                    },
                    contentDescription = "Street View site verification",
                    active = state.streetViewFetchState is StreetViewFetchState.Loading
                ) { Icon(Icons.Default.Visibility, contentDescription = null) }
            }
            // Site Survey / Solar Mapping round (spec "Automatically detect/use available building
            // and roof information when Google Solar API data is available"): reuses
            // RoofConfirmForm's own default panel assumption (2.278m x 1.134m, 600W) since Solar
            // API auto-detect has no per-roof form step of its own to collect a different one —
            // every added roof plane's panel layout stays fully re-editable afterward regardless.
            MapControlButton(
                onClick = {
                    scope.launch {
                        viewModel.fetchAutoRoofFromSolarApi(
                            client = solarApiClient,
                            panelWidthM = 2.278, panelHeightM = 1.134, panelWattage = 600.0
                        )
                    }
                },
                contentDescription = "Auto-detect roof (Solar API)",
                active = state.solarApiFetchState is SolarApiFetchState.Loading
            ) { Icon(Icons.Default.WbSunny, contentDescription = null) }
            // Site Survey / Solar Mapping round: obstruction tracing needs at least one confirmed
            // roof plane to attach to, and is gated off while a roof trace/edit is in progress so
            // the two polygon-drawing flows can never run at the same time.
            if (state.roofPlanes.isNotEmpty() && !roofController.isDrawing && !roofController.isEditing) {
                MapControlButton(
                    onClick = { obstructionController.startDrawing() },
                    contentDescription = "Add roof obstruction",
                    active = obstructionController.isDrawing
                ) { Icon(Icons.Default.Block, contentDescription = null) }
                MapControlButton(
                    onClick = { showObstructionsSheet = true },
                    contentDescription = "Manage obstructions"
                ) { Icon(Icons.Default.List, contentDescription = null) }
            }
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
                    },
                    onUndo = roofController::editUndo,
                    onRedo = roofController::editRedo,
                    canUndo = roofController.canUndoEdit,
                    canRedo = roofController.canRedoEdit
                )
                obstructionController.isDrawing -> RoofDrawingControls(
                    vertexCount = obstructionController.vertices.size,
                    onUndo = obstructionController::undo,
                    onClear = obstructionController::clear,
                    onCancel = obstructionController::cancelDrawing,
                    onDone = {
                        if (obstructionController.vertices.size >= 3) {
                            obstructionFormVertices = obstructionController.finishDrawing()
                        }
                    }
                )
                measureModeActive -> MeasureControls(
                    pointA = measurePointA,
                    pointB = measurePointB,
                    kind = measurementKind,
                    onKindChange = { measurementKind = it },
                    label = measurementLabel,
                    onLabelChange = { measurementLabel = it },
                    onClear = { measurePointA = null; measurePointB = null; measurementLabel = "" },
                    onDone = {
                        measureModeActive = false
                        measurePointA = null
                        measurePointB = null
                        measurementLabel = ""
                    },
                    onSave = {
                        val a = measurePointA
                        val b = measurePointB
                        if (a != null && b != null && measurementLabel.isNotBlank()) {
                            viewModel.addSiteMeasurement(measurementKind, measurementLabel, a, b)
                            measurePointA = null
                            measurePointB = null
                            measurementLabel = ""
                        }
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

    if (obstructionFormVertices != null) {
        ModalBottomSheet(
            onDismissRequest = { obstructionFormVertices = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            ObstructionConfirmForm(
                vertices = obstructionFormVertices!!,
                roofPlanes = state.roofPlanes,
                defaultPlaneId = state.roofPlanes.lastOrNull()?.id,
                onConfirm = { planeId, type ->
                    val plane = state.roofPlanes.firstOrNull { it.id == planeId }
                    val layout = plane?.panelLayout
                    viewModel.addExclusionZone(
                        planeId,
                        RoofExclusionZone(type, obstructionFormVertices!!),
                        layout?.panelWidthM ?: 2.278,
                        layout?.panelHeightM ?: 1.134,
                        layout?.panelWattage ?: 600.0
                    )
                    obstructionFormVertices = null
                },
                onCancel = { obstructionFormVertices = null }
            )
        }
    }

    if (showObstructionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showObstructionsSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            ObstructionsManageSheet(
                roofPlanes = state.roofPlanes,
                onRemove = { plane, index ->
                    val layout = plane.panelLayout
                    viewModel.removeExclusionZone(
                        plane.id, index,
                        layout?.panelWidthM ?: 2.278, layout?.panelHeightM ?: 1.134, layout?.panelWattage ?: 600.0
                    )
                },
                onClose = { showObstructionsSheet = false }
            )
        }
    }

    if (showMeasurementsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMeasurementsSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            SiteMeasurementsSheet(
                measurements = state.siteMeasurements,
                onRemove = { viewModel.removeSiteMeasurement(it) },
                onClose = { showMeasurementsSheet = false }
            )
        }
    }

    if (showStreetViewSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStreetViewSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            StreetViewSheet(
                fetchState = state.streetViewFetchState,
                onClose = { showStreetViewSheet = false }
            )
        }
    }
}

/**
 * 2026-08-19 ("when i set a point and zoom or move the map the point moves and does not stick to
 * the area on the map that was pinned"): a non-draggable dot marker for a fixed geographic point
 * (the selected-site pin, a measurement point) that stays correctly anchored through zoom/pan.
 *
 * The earlier version of this screen built each such marker's [MarkerState] via
 * `remember(point) { MarkerState(...) }` — keying `remember` on the point's own value recreates a
 * brand-new [MarkerState] (and tears down + re-adds the underlying native marker) every time that
 * value's identity changes, which is the wrong tool here: Compose can legitimately re-run this
 * composable's slot for reasons that have nothing to do with the point actually moving (recomposi-
 * tion triggered by sibling state elsewhere in the same `GoogleMap` content block), and a marker
 * that gets torn down and re-added mid-gesture is exactly what reads as "not sticking" during a
 * zoom/pan. [markerState] is now `remember`ed with STABLE identity (no key at all — this
 * composable's own call site is the only identity it needs) and its position is instead updated
 * IN PLACE via [SideEffect] whenever [point] differs from what's currently shown, matching
 * [RoofVertexMarker]'s own (already-correct, because it has to survive an active drag) pattern —
 * unifying both marker kinds onto the one approach that's actually safe against Compose recomposing
 * for unrelated reasons.
 */
@Composable
private fun StaticGeoMarker(point: GeoPoint, icon: BitmapDescriptor) {
    val markerState = remember { MarkerState(position = point.toLatLng()) }
    SideEffect {
        if (markerState.position != point.toLatLng()) {
            markerState.position = point.toLatLng()
        }
    }
    Marker(state = markerState, icon = icon, anchor = Offset(0.5f, 0.5f))
}

/**
 * 2026-08-19 ("change map to google map, ... drag any vertex to correct it"): a roof-trace vertex
 * rendered as a small dot [Marker] (see [buildMapMarkerIcons]'s own doc for why not the default
 * pin) that is draggable in-place via the Maps Compose SDK's own native marker-drag support —
 * replaces the hand-rolled `View.OnTouchListener` + `MapLibreMap.projection` hit-testing the
 * MapLibre version needed (it had no annotation-marker API to delegate to; Google Maps does).
 *
 * [MarkerState] is `remember`ed WITHOUT a key on [point] — deliberately, so its identity survives
 * across a whole drag gesture rather than being torn down and recreated on every position update
 * mid-drag (which a `remember(point)` key would cause, fighting the user's own finger). The
 * [SideEffect] below instead re-syncs the marker's displayed position from [point] only while
 * [MarkerState.dragState] is [DragState.END] — the idle/not-currently-dragging state, which
 * doubles as "no drag has ever started" AND "a drag just finished" — so external changes to this
 * vertex (Undo/Redo, or another Delete/Insert shifting this slot) still show up correctly without
 * ever overwriting a drag actually in progress.
 */
@Composable
private fun RoofVertexMarker(
    point: GeoPoint,
    icon: BitmapDescriptor,
    draggable: Boolean,
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onDragMove: (GeoPoint) -> Unit,
    onDragEnd: () -> Unit
) {
    val markerState = remember { MarkerState(position = point.toLatLng()) }
    SideEffect {
        if (markerState.dragState == DragState.END && markerState.position != point.toLatLng()) {
            markerState.position = point.toLatLng()
        }
    }
    LaunchedEffect(markerState) {
        snapshotFlow { markerState.dragState }.collect { dragState ->
            when (dragState) {
                DragState.START -> onDragStart()
                DragState.DRAG -> onDragMove(markerState.position.toGeoPoint())
                DragState.END -> onDragEnd()
            }
        }
    }
    Marker(
        state = markerState,
        icon = icon,
        draggable = draggable,
        anchor = Offset(0.5f, 0.5f),
        onClick = {
            onTap()
            true
        }
    )
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
 * 2026-08-19 ("change map to google map"): unlike MapLibre's `onStyleLoadFailed` (a real runtime
 * callback the previous version of this screen could react to), the Google Maps SDK has no public
 * Compose-level callback for "tile request failed/unauthorized" — an invalid/missing key instead
 * renders blank gray tiles and logs the real reason to Logcat internally, not to app code. This is
 * a pre-emptive check instead of a reactive one: if `MAPS_API_KEY` was never configured at build
 * time, say so up front rather than showing a silently-blank map with no explanation — "if tiles
 * fail to load, show a useful error, never a blank map with no explanation" still holds, just via
 * a different detection point than the MapLibre round used.
 */
@Composable
private fun MapKeyMissingBanner() {
    val palette = LocalLumixPalette.current
    GlassSurface(shape = RoundedCornerShape(LumixRadius.md)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("Google Maps isn't configured.", style = MaterialTheme.typography.labelSmall, color = palette.warningRedText)
            Text(
                "Add MAPS_API_KEY to android/local.properties (see the README) — the map will stay blank until then.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary
            )
        }
    }
}

/**
 * Site Survey / Solar Mapping round: a 5-step green→yellow→red heat-map matching this app's own
 * existing 3-color semantic vocabulary (energyGreen/solarYellow/warningRed — see `Color.kt`'s own
 * "restrained... a handful of quiet, deliberate hues" doc) rather than introducing new raw hex
 * values for this gradient. Map overlay colors in this screen are already raw hex (not palette-
 * routed, e.g. the roof-tracing/measurement colors above) since [Polygon]'s fill/stroke need a
 * concrete [Color] regardless of theme — this follows that same existing convention.
 */
private fun suitabilityFillColor(tier: SolarSuitability): Color = when (tier) {
    SolarSuitability.EXCELLENT -> Color(0x665FCFA0)
    SolarSuitability.GOOD -> Color(0x668FCF7A)
    SolarSuitability.MODERATE -> Color(0x66E8B04D)
    SolarSuitability.POOR -> Color(0x66E8935A)
    SolarSuitability.UNSUITABLE -> Color(0x66D9695F)
}

private fun suitabilityStrokeColor(tier: SolarSuitability): Color = when (tier) {
    SolarSuitability.EXCELLENT -> Color(0xFF5FCFA0)
    SolarSuitability.GOOD -> Color(0xFF8FCF7A)
    SolarSuitability.MODERATE -> Color(0xFFE8B04D)
    SolarSuitability.POOR -> Color(0xFFE8935A)
    SolarSuitability.UNSUITABLE -> Color(0xFFD9695F)
}

/** A compact key for the roof-plane suitability colors above, shown only once at least one roof has been added — otherwise there's nothing on the map yet for it to explain. */
@Composable
private fun SolarSuitabilityLegend() {
    val palette = LocalLumixPalette.current
    GlassSurface(shape = RoundedCornerShape(LumixRadius.md)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SolarSuitability.entries.forEach { tier ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(suitabilityStrokeColor(tier))
                    )
                    Text(tier.label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                }
            }
        }
    }
}

/**
 * Site Survey / Solar Mapping round (spec "If Solar API data is unavailable: fall back gracefully
 * to manual roof polygon drawing... Never fabricate roof geometry, shading or solar data"): one
 * banner per [SolarApiFetchState] branch — [SolarApiFetchState.NoCoverage] and [SolarApiFetchState
 * .Failed] both prompt manual tracing, but with different wording (a real coverage gap vs. an
 * operational problem the installer might be able to fix), never a silent failure or a fabricated
 * roof plane either way. [SolarApiFetchState.Idle] renders nothing.
 */
@Composable
private fun SolarApiFetchBanner(fetchState: SolarApiFetchState) {
    val palette = LocalLumixPalette.current
    when (fetchState) {
        is SolarApiFetchState.Idle -> Unit
        is SolarApiFetchState.Loading -> GlassSurface(shape = RoundedCornerShape(LumixRadius.md)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Checking Google Solar API for this roof…", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
        }
        is SolarApiFetchState.Succeeded -> GlassSurface(shape = RoundedCornerShape(LumixRadius.md)) {
            Text(
                "Auto-detected ${fetchState.segmentsAdded} roof section${if (fetchState.segmentsAdded == 1) "" else "s"} from Google Solar API — review and edit vertices/exclusions below before saving.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
        is SolarApiFetchState.NoCoverage -> GlassSurface(shape = RoundedCornerShape(LumixRadius.md)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("No Solar API data for this roof.", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Text(fetchState.message, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
        }
        is SolarApiFetchState.Failed -> GlassSurface(shape = RoundedCornerShape(LumixRadius.md)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("Solar API auto-detect failed.", style = MaterialTheme.typography.labelSmall, color = palette.warningRedText)
                Text(fetchState.reason, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
        }
    }
}

/**
 * Site Survey / Solar Mapping round (spec "Use elevation/topography data where useful for site
 * planning purposes... this must never be presented as a structural survey"): one banner per
 * [ElevationFetchState] branch, mirroring [SolarApiFetchBanner]'s own per-outcome shape — a real
 * reading, "no data here" (a real, if unusual, outcome for Google's Elevation API), or an
 * operational failure, never a silent no-op or a fabricated number either way.
 */
@Composable
private fun ElevationFetchBanner(fetchState: ElevationFetchState) {
    val palette = LocalLumixPalette.current
    when (fetchState) {
        is ElevationFetchState.Idle -> Unit
        is ElevationFetchState.Loading -> GlassSurface(shape = RoundedCornerShape(LumixRadius.md)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Checking ground elevation…", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
        }
        is ElevationFetchState.Succeeded -> GlassSurface(shape = RoundedCornerShape(LumixRadius.md)) {
            Text(
                "Ground elevation: %.0f m — context for site planning only, not a structural survey.".format(fetchState.elevationMeters),
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
        is ElevationFetchState.NoData -> GlassSurface(shape = RoundedCornerShape(LumixRadius.md)) {
            Text(fetchState.message, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp))
        }
        is ElevationFetchState.Failed -> GlassSurface(shape = RoundedCornerShape(LumixRadius.md)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("Elevation lookup failed.", style = MaterialTheme.typography.labelSmall, color = palette.warningRedText)
                Text(fetchState.reason, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
        }
    }
}

/**
 * 2026-08-19 ("change map to google map"): a compact segmented control to flip the base map
 * between [mapTypeOptions] — same look/behavior as the earlier MapLibre-era style switcher, now
 * driving Google Maps' own [MapType] instead of a style URL. Each cell sizes to its own natural
 * label width inside a horizontally-scrolling row (the "jumbled buttons" fix from map Part 2 —
 * `Text`'s default `maxLines = 1` clips rather than wraps once a fixed-width cell gets too narrow,
 * so cells are never compressed below their readable size regardless of how many options exist).
 */
@Composable
private fun MapTypeSwitcher(selectedMapType: MapType, onSelect: (MapType) -> Unit) {
    // 2026-08-18 contrast fix (carried over): a solid near-opaque dark backing, not just a
    // translucent glass tint, so every label stays readable over whatever's under the map.
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(LumixRadius.md))
            .background(Color(0xE6141414))
            .horizontalScroll(rememberScrollState())
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        mapTypeOptions.forEach { option ->
            val selected = option.mapType == selectedMapType
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(LumixRadius.sm))
                    .background(if (selected) Color(0xFFFFD84D) else Color.Transparent)
                    .clickable { onSelect(option.mapType) }
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
 * Every floating map control (back/zoom/3D/my-location/traffic/measure) gets this same solid,
 * near-opaque backing instead of a translucent glass tint, which reads close to invisible over
 * unpredictable live map imagery (ocean, forest, urban gray, bright satellite haze) — a bug found
 * and fixed once already in map Part 2, applied consistently to every control since.
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
                LumixSecondaryButton(text = "Undo", onClick = onUndo, enabled = vertexCount > 0, modifier = Modifier.weight(1f), compact = true)
                LumixSecondaryButton(text = "Clear", onClick = onClear, enabled = vertexCount > 0, modifier = Modifier.weight(1f), compact = true)
                LumixSecondaryButton(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f), compact = true)
                LumixPrimaryButton(text = "Done", onClick = onDone, enabled = vertexCount >= 3, modifier = Modifier.weight(1f), compact = true)
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
    onDone: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean
) {
    val palette = LocalLumixPalette.current
    GlassSurface(shape = RoundedCornerShape(LumixRadius.lg)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                when (mode) {
                    RoofEditMode.NONE -> "Editing roof — choose an action below"
                    RoofEditMode.MOVE -> "Press and drag a point to reposition it"
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
                LumixSecondaryButton(text = "Undo", onClick = onUndo, enabled = canUndo, modifier = Modifier.weight(1f), compact = true)
                LumixSecondaryButton(text = "Redo", onClick = onRedo, enabled = canRedo, modifier = Modifier.weight(1f), compact = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                LumixSecondaryButton(text = "Clear", onClick = onClear, modifier = Modifier.weight(1f), compact = true)
                LumixSecondaryButton(text = "Redraw", onClick = onRedraw, modifier = Modifier.weight(1f), compact = true)
                LumixPrimaryButton(text = "Done", onClick = onDone, modifier = Modifier.weight(1f), compact = true)
            }
        }
    }
}

/**
 * "Add a way to pin two points and measure the distance between them" (2026-08-19, map Part 6).
 *
 * Site Survey / Solar Mapping round ("measurement tool extensions... useful for cable runs,
 * roof-to-inverter placement distance, electrical service distance"): once both points are placed,
 * this now also offers naming the measurement and saving it as a real [SiteMeasurement] via
 * [onSave] — "Done" alone still just clears the pins the way it always did, for a quick check that
 * doesn't need to be kept.
 */
@Composable
private fun MeasureControls(
    pointA: GeoPoint?,
    pointB: GeoPoint?,
    kind: SiteMeasurementKind,
    onKindChange: (SiteMeasurementKind) -> Unit,
    label: String,
    onLabelChange: (String) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
    onSave: () -> Unit
) {
    val palette = LocalLumixPalette.current
    GlassSurface(shape = RoundedCornerShape(LumixRadius.lg)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                when {
                    pointA == null -> "Tap the map to place the first point"
                    pointB == null -> "Tap the map to place the second point"
                    else -> "Tap the map to start a new measurement"
                },
                style = MaterialTheme.typography.labelMedium,
                color = palette.textPrimary
            )
            if (pointA != null && pointB != null) {
                val meters = DistanceCalculator.haversineMeters(pointA, pointB)
                Text(
                    "%.1f m  (%.1f ft)".format(meters, meters * 3.28084),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.solarYellowText
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SiteMeasurementKind.entries.forEach { entry ->
                        val selected = entry == kind
                        Text(
                            entry.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) palette.solarYellowText else palette.textSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(LumixRadius.pill))
                                .background(if (selected) palette.solarYellow.copy(alpha = 0.16f) else palette.glass)
                                .clickable { onKindChange(entry) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                GlassSurface(shape = RoundedCornerShape(LumixRadius.md)) {
                    BasicTextField(
                        value = label,
                        onValueChange = onLabelChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = palette.textPrimary),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        decorationBox = { inner ->
                            if (label.isEmpty()) {
                                Text("Name this measurement (e.g. \"Meter to inverter\")", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                            }
                            inner()
                        }
                    )
                }
                LumixPrimaryButton(
                    text = "Save measurement",
                    onClick = onSave,
                    enabled = label.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    compact = true
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                LumixSecondaryButton(text = "Clear", onClick = onClear, modifier = Modifier.weight(1f), compact = true)
                LumixPrimaryButton(text = "Done", onClick = onDone, modifier = Modifier.weight(1f), compact = true)
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
                        LumixSecondaryButton(text = "Trace Roof", onClick = onTraceRoof, modifier = Modifier.weight(1f), compact = true)
                        if (canEditRoof) {
                            LumixSecondaryButton(text = "Edit Roof", onClick = onEditRoof, modifier = Modifier.weight(1f), compact = true)
                        }
                        if (roofPlaneCount > 0) {
                            LumixPrimaryButton(text = "Save Site", onClick = onSaveSite, modifier = Modifier.weight(1f), compact = true)
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

    // 2026-08-19 ("the result pop up is chopped off at the bottom and i cannot scroll to see all
    // data"): this form's content (8 fields + the panel-fit preview + the shade/exclusion section
    // + the Cancel/Add Roof Plane row) routinely exceeds a ModalBottomSheet's visible height,
    // especially with the keyboard up for a NumberField — but the Column had no scroll modifier at
    // all, so anything past the visible edge was simply unreachable. verticalScroll makes the
    // whole form scrollable; navigationBarsPadding + imePadding keep the last row (and the panel-
    // fit line above it) clear of the nav bar and the keyboard instead of hiding behind either.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .navigationBarsPadding()
            .imePadding(),
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

        // 2026-08-19 (map Part 5, "compute max panels that fit ... given the traced polygon's
        // area ... and the panel's physical dimensions"): a live rectangle-packing preview, not
        // just raw area math — reuses the same PanelLayoutOptimizer already wired into
        // SolarSiteViewModel.addTracedRoofPlane, recomputed here so the installer sees the real
        // fit *before* confirming, while width/height/setback/azimuth are still editable.
        val panelFitPreview = remember(vertices, panelWidthM, panelHeightM, setbackM, azimuth) {
            if (panelWidthM > 0.0 && panelHeightM > 0.0) {
                PanelLayoutOptimizer.optimize(
                    PanelLayoutOptimizer.Input(
                        vertices = vertices,
                        panelWidthM = panelWidthM,
                        panelHeightM = panelHeightM,
                        panelWattage = panelWattage,
                        setbackM = setbackM,
                        alignmentAzimuthDegrees = azimuth
                    )
                )
            } else {
                null
            }
        }
        // 2026-08-19 ("also use data from panel to use in sizing the panels when giving back data
        // as to how much panels can be used in a specific area that is mapped and mapping can be
        // irregularly shaped, measure how much panels can be fit in that area vertically"): this
        // was already computed above via PanelLayoutOptimizer.optimize, which packs real panel
        // rectangles against the traced polygon's EXACT outline (point-in-polygon + setback +
        // exclusion-zone checks, not a bounding-box estimate — so an irregular roof shape is
        // handled correctly) and tries BOTH orientations — vertical (portrait) rows and horizontal
        // (landscape) rows — keeping whichever seats more panels. Given a bigger, more prominent
        // callout here (was a single small line of `labelSmall` text, easy to miss scrolling past
        // it) since it's the actual answer to "how many panels fit," not a minor detail.
        if (panelFitPreview != null) {
            val fitKw = panelFitPreview.panelCount * panelWattage / 1000.0
            val orientationLabel = when (panelFitPreview.orientation) {
                PanelOrientation.PORTRAIT -> "vertical (portrait) rows"
                PanelOrientation.LANDSCAPE -> "horizontal (landscape) rows"
            }
            GlassSurface(shape = RoundedCornerShape(LumixRadius.md)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (panelFitPreview.panelCount > 0) {
                            "Fits %d panel(s)  ·  ~%.2f kWp".format(panelFitPreview.panelCount, fitKw)
                        } else {
                            "No panels fit this roof at this size/setback"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (panelFitPreview.panelCount > 0) palette.textPrimary else palette.warningRedText
                    )
                    Text(
                        if (panelFitPreview.panelCount > 0) {
                            "Best fit uses $orientationLabel — both vertical and horizontal layouts were checked against this roof's exact traced outline (not just a rectangle around it)."
                        } else {
                            "Neither vertical nor horizontal rows fit at this panel size and setback — try a smaller panel or a smaller setback."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                }
            }
        }

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

/**
 * Site Survey / Solar Mapping round (spec "Allow the user to mark exclusions manually: Chimneys,
 * Vents, Skylights, AC units, Water tanks, Roof edges/setbacks, Walkways, Fire access, Structural
 * obstructions, ... Other obstacles"): shown right after tracing an obstruction polygon on the
 * map — picks which roof section it belongs to (only asked when there's more than one) and its
 * [RoofExclusionType], then hands both back so the caller can build a real [RoofExclusionZone]
 * from the already-traced [vertices].
 */
@Composable
private fun ObstructionConfirmForm(
    vertices: List<GeoPoint>,
    roofPlanes: List<RoofPlane>,
    defaultPlaneId: String?,
    onConfirm: (planeId: String, type: RoofExclusionType) -> Unit,
    onCancel: () -> Unit
) {
    val palette = LocalLumixPalette.current
    val areaM2 = remember(vertices) { RoofGeometryEngine.horizontalAreaM2(vertices) }
    var selectedPlaneId by remember { mutableStateOf(defaultPlaneId ?: roofPlanes.lastOrNull()?.id) }
    var selectedType by remember { mutableStateOf(RoofExclusionType.CHIMNEY) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Mark Obstruction", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        Text(
            "Traced area: %.2f m² — panels will not be placed inside this shape.".format(areaM2),
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary
        )
        if (roofPlanes.size > 1) {
            LabeledDropdown(
                label = "Roof section",
                options = roofPlanes.map { it.id },
                selected = selectedPlaneId ?: roofPlanes.first().id,
                optionLabel = { id -> roofPlanes.firstOrNull { it.id == id }?.label ?: id },
                onSelected = { selectedPlaneId = it }
            )
        }
        LabeledDropdown(
            label = "Obstruction type",
            options = RoofExclusionType.entries,
            selected = selectedType,
            optionLabel = { it.label },
            onSelected = { selectedType = it }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            LumixSecondaryButton(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
            LumixPrimaryButton(
                text = "Add Obstruction",
                onClick = { selectedPlaneId?.let { onConfirm(it, selectedType) } },
                enabled = selectedPlaneId != null,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Site Survey / Solar Mapping round: lists every real, drawn exclusion zone across all roof
 * planes with a delete affordance — the read/manage half of the obstruction-marking flow, since
 * the map itself only ever shows the zones, never lets you remove one directly (there's no
 * per-vertex marker or tap target on a confirmed exclusion polygon, unlike an in-progress roof
 * edit — re-tracing a corrected shape is simpler than building a second drag/delete UI for what
 * is usually a small, one-off obstruction shape).
 */
@Composable
private fun ObstructionsManageSheet(
    roofPlanes: List<RoofPlane>,
    onRemove: (RoofPlane, Int) -> Unit,
    onClose: () -> Unit
) {
    val palette = LocalLumixPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Roof Obstructions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        if (roofPlanes.all { it.exclusionZones.isEmpty() }) {
            Text(
                "No obstructions marked yet. Use \"Add roof obstruction\" on the map to trace a chimney, vent, skylight, or other feature panels must avoid.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
        }
        roofPlanes.forEach { plane ->
            if (plane.exclusionZones.isNotEmpty()) {
                Text(plane.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                plane.exclusionZones.forEachIndexed { index, zone ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(zone.type.label, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
                            Text("~%.1f m²".format(zone.areaM2), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                        }
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove ${zone.type.label}",
                            tint = palette.warningRedText,
                            modifier = Modifier.clickable { onRemove(plane, index) }
                        )
                    }
                }
            }
        }
        LumixPrimaryButton(text = "Close", onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Site Survey / Solar Mapping round: lists every real, named, saved [SiteMeasurement] with its
 * distance and a delete action — the read/manage half of the measurement-saving flow (mirrors
 * [ObstructionsManageSheet]'s own shape for the equivalent obstruction list), so measurements taken
 * earlier in a survey (roof dimensions, equipment-to-equipment runs, electrical service distance)
 * stay visible and correctable without re-measuring on the map.
 */
@Composable
private fun SiteMeasurementsSheet(
    measurements: List<SiteMeasurement>,
    onRemove: (String) -> Unit,
    onClose: () -> Unit
) {
    val palette = LocalLumixPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Site Measurements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        if (measurements.isEmpty()) {
            Text(
                "No measurements saved yet. Use the ruler tool on the map, then \"Save measurement\" to keep one here.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
        }
        measurements.forEach { measurement ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(measurement.label, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
                    Text(
                        "${measurement.kind.label} — %.1f m (%.1f ft)".format(measurement.distanceMeters, measurement.distanceFeet),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                }
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove ${measurement.label}",
                    tint = palette.warningRedText,
                    modifier = Modifier.clickable { onRemove(measurement.id) }
                )
            }
        }
        LumixPrimaryButton(text = "Close", onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Site Survey / Solar Mapping round (spec "Integrate Street View (where available) for site
 * verification"): shows the real fetched ground-level photo (decoded from the raw bytes
 * [StreetViewFetchState.Succeeded] carries — the only place in this flow that touches
 * `android.graphics`, since [com.lumix.estimator.site.streetview.StreetViewClient] itself stays
 * pure Kotlin/JDK, same split [buildMapMarkerIcons] already established for marker bitmaps), or the
 * right message for every other outcome — a coverage gap and an operational failure are worded
 * differently, never collapsed into a single "couldn't load image" dead end.
 */
@Composable
private fun StreetViewSheet(fetchState: StreetViewFetchState, onClose: () -> Unit) {
    val palette = LocalLumixPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Street View", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        when (fetchState) {
            is StreetViewFetchState.Idle -> Text(
                "No photo fetched yet.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary
            )
            is StreetViewFetchState.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Fetching ground-level photo…", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            }
            is StreetViewFetchState.Succeeded -> {
                val bitmap = remember(fetchState.imageBytes) {
                    BitmapFactory.decodeByteArray(fetchState.imageBytes, 0, fetchState.imageBytes.size)
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Street View photo of the site",
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(LumixRadius.md))
                    )
                    Text(
                        "Real Google Street View imagery — use it to sanity-check access, obstructions, and pole/meter location, not as a substitute for a site visit.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                } else {
                    Text("Street View returned an image that couldn't be decoded.", style = MaterialTheme.typography.bodySmall, color = palette.warningRedText)
                }
            }
            is StreetViewFetchState.NoCoverage -> Text(fetchState.message, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            is StreetViewFetchState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Street View fetch failed.", style = MaterialTheme.typography.bodySmall, color = palette.warningRedText)
                Text(fetchState.reason, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
        }
        LumixPrimaryButton(text = "Close", onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
}
