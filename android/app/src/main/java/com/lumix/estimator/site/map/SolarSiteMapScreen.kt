package com.lumix.estimator.site.map

import android.Manifest
import android.content.Context
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapType
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.lumix.estimator.location.DeviceLocationManager
import com.lumix.estimator.sensors.CompassManager
import com.lumix.estimator.site.GeoPoint
import com.lumix.estimator.site.ShadeAndExclusionSection
import com.lumix.estimator.site.SolarCompassBadge
import com.lumix.estimator.site.SolarSiteViewModel
import com.lumix.estimator.site.geometry.RoofGeometryEngine
import com.lumix.estimator.site.geometry.ShadeEstimator
import com.lumix.estimator.site.geometry.ShadeObstructionType
import com.lumix.estimator.ui.components.GlassSurface
import com.lumix.estimator.ui.components.LabeledDropdown
import com.lumix.estimator.ui.components.LumixIconButtonSurface
import com.lumix.estimator.ui.components.LumixPrimaryButton
import com.lumix.estimator.ui.components.LumixSecondaryButton
import com.lumix.estimator.ui.components.NumberField
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val pitchOptions: List<Double?> = listOf(null, 0.0, 5.0, 10.0, 15.0, 20.0, 25.0, 30.0, 35.0, 40.0, 45.0)
private val jamaicaDefault = LatLng(18.1096, -77.2975)

/**
 * Satellite map for selecting a customer's property and tracing roof planes directly on it.
 * A peer to [com.lumix.estimator.site.ManualSiteScreen], not the only way in — this screen
 * needs a configured Google Maps API key to actually render tiles; without one the map area
 * will show blank/error tiles from the SDK itself, which is why manual entry exists as a
 * fully independent alternative rather than a degraded fallback bolted onto this screen.
 */
@Composable
fun SolarSiteMapScreen(
    viewModel: SolarSiteViewModel,
    onSaved: (String) -> Unit,
    onBack: () -> Unit
) {
    val palette = LocalLumixPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()

    val mapController = remember { MapController() }
    val roofController = remember { RoofDrawingController() }
    val locationManager = remember { DeviceLocationManager(context) }
    val compassManager = remember { CompassManager(context) }
    val compassState by compassManager.state.collectAsState()

    val initialLatLng = remember {
        val lat = state.draftLatitude
        val lon = state.draftLongitude
        if (lat != null && lon != null) LatLng(lat, lon) else jamaicaDefault
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialLatLng, 17f)
    }

    var searchQuery by remember { mutableStateOf(state.draftAddress ?: "") }
    var searchError by remember { mutableStateOf(false) }
    var roofFormVertices by remember { mutableStateOf<List<GeoPoint>?>(null) }

    DisposableEffect(Unit) {
        compassManager.start()
        onDispose { compassManager.stop() }
    }

    // Declination depends on where on Earth you are; keep it current as the site location is set.
    LaunchedEffect(state.draftLatitude, state.draftLongitude) {
        val lat = state.draftLatitude
        val lon = state.draftLongitude
        if (lat != null && lon != null) compassManager.updateLocation(lat, lon)
    }

    fun moveToDeviceLocation() {
        scope.launch {
            locationManager.lastKnownLocation()?.let { location ->
                val latLng = LatLng(location.latitude, location.longitude)
                mapController.selectLocation(latLng)
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 18f))
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) moveToDeviceLocation()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = when (mapController.mapType) {
                    SiteMapType.NORMAL -> MapType.NORMAL
                    SiteMapType.SATELLITE -> MapType.SATELLITE
                    SiteMapType.HYBRID -> MapType.HYBRID
                }
            ),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false),
            onMapClick = { latLng ->
                if (roofController.isDrawing) {
                    roofController.addVertex(GeoPoint(latLng.latitude, latLng.longitude))
                } else {
                    mapController.selectLocation(latLng)
                }
            }
        ) {
            mapController.selectedLocation?.let { loc ->
                Marker(state = rememberMarkerState(position = loc), title = "Selected location")
            }

            // Already-saved roof planes for this draft site, in green.
            state.roofPlanes.forEach { plane ->
                if (plane.vertices.size >= 3) {
                    Polygon(
                        points = plane.vertices.map { LatLng(it.latitude, it.longitude) },
                        fillColor = Color(0x3363E6A5),
                        strokeColor = Color(0xFF63E6A5),
                        strokeWidth = 3f
                    )
                }
            }

            // The roof currently being traced, in solar yellow.
            if (roofController.vertices.size >= 2) {
                Polygon(
                    points = roofController.vertices.map { LatLng(it.latitude, it.longitude) },
                    fillColor = Color(0x4DFFD84D),
                    strokeColor = Color(0xFFFFD84D),
                    strokeWidth = 4f
                )
            }
            roofController.vertices.forEach { v ->
                Marker(state = rememberMarkerState(position = LatLng(v.latitude, v.longitude)))
            }
        }

        // Top bar: back button + search field + map-type switch.
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LumixIconButtonSurface(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                                    Text("Search customer location", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                                }
                                inner()
                            }
                        )
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = palette.textSecondary,
                            modifier = Modifier.padding(start = 8.dp).clickable {
                                scope.launch {
                                    val result = geocodeAddress(context, searchQuery)
                                    if (result != null) {
                                        mapController.selectLocation(result)
                                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(result, 17f))
                                        searchError = false
                                    } else {
                                        searchError = true
                                    }
                                }
                            }
                        )
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
        }

        // Right-side floating controls: zoom, layers, my location.
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LumixIconButtonSurface(onClick = {
                scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomIn()) }
            }) { Icon(Icons.Default.Add, contentDescription = "Zoom in") }
            LumixIconButtonSurface(onClick = {
                scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomOut()) }
            }) { Icon(Icons.Default.Remove, contentDescription = "Zoom out") }
            LumixIconButtonSurface(onClick = {
                mapController.setMapType(
                    when (mapController.mapType) {
                        SiteMapType.SATELLITE -> SiteMapType.HYBRID
                        SiteMapType.HYBRID -> SiteMapType.NORMAL
                        SiteMapType.NORMAL -> SiteMapType.SATELLITE
                    }
                )
            }) { Icon(Icons.Default.Layers, contentDescription = "Map type") }
            LumixIconButtonSurface(onClick = {
                if (locationManager.hasPermission()) {
                    moveToDeviceLocation()
                } else {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }) { Icon(Icons.Default.MyLocation, contentDescription = "My location") }
        }

        if (compassManager.isAvailable) {
            SolarCompassBadge(
                headingDegrees = compassState.trueHeadingDegrees,
                modifier = Modifier.align(Alignment.TopStart).padding(top = 84.dp, start = 16.dp)
            )
        }

        // Bottom panel.
        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
            if (roofController.isDrawing) {
                RoofDrawingControls(
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
            } else {
                SiteAnalysisPanel(
                    hasLocation = state.hasLocation,
                    hasPin = mapController.selectedLocation != null,
                    roofPlaneCount = state.roofPlanes.size,
                    totalCapacityKw = state.roofPlanes.sumOf { it.panelLayout?.totalCapacityKw ?: 0.0 },
                    onUseLocation = {
                        mapController.selectedLocation?.let { loc ->
                            viewModel.setLocation(loc.latitude, loc.longitude, searchQuery.ifBlank { null }, null)
                        }
                    },
                    onTraceRoof = { roofController.startDrawing() },
                    onSaveSite = { viewModel.saveSite()?.let(onSaved) }
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

@Composable
private fun SiteAnalysisPanel(
    hasLocation: Boolean,
    hasPin: Boolean,
    roofPlaneCount: Int,
    totalCapacityKw: Double,
    onUseLocation: () -> Unit,
    onTraceRoof: () -> Unit,
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

/** Blocking Geocoder call moved off the main thread; returns null (never throws) on any failure — no network, bad query, or no results. */
private suspend fun geocodeAddress(context: Context, query: String): LatLng? = withContext(Dispatchers.IO) {
    if (query.isBlank()) return@withContext null
    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        geocoder.getFromLocationName(query, 1)?.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
    } catch (e: Exception) {
        null
    }
}
