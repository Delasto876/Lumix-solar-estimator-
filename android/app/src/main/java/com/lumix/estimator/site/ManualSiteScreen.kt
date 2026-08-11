package com.lumix.estimator.site

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.site.geometry.RoofGeometryEngine
import com.lumix.estimator.ui.components.LabeledDropdown
import com.lumix.estimator.ui.components.LumixPrimaryButton
import com.lumix.estimator.ui.components.LumixSecondaryButton
import com.lumix.estimator.ui.components.NumberField
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixRadius

private val pitchOptions: List<Double?> = listOf(null, 0.0, 5.0, 10.0, 15.0, 20.0, 25.0, 30.0, 35.0, 40.0, 45.0)

/**
 * Location + roof entry entirely by typed numbers — no map, GPS, or network required. A peer
 * path to [com.lumix.estimator.site.map.SolarSiteMapScreen], not a fallback: some installers
 * already have exact coordinates and roof dimensions and would rather just type them in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSiteScreen(
    viewModel: SolarSiteViewModel,
    onSaved: (String) -> Unit,
    onBack: () -> Unit
) {
    val palette = LocalLumixPalette.current
    val state by viewModel.state.collectAsState()

    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf(state.draftLatitude ?: 0.0) }
    var longitude by remember { mutableStateOf(state.draftLongitude ?: 0.0) }

    var lengthM by remember { mutableStateOf(10.0) }
    var widthM by remember { mutableStateOf(8.0) }
    var azimuthDegrees by remember { mutableStateOf(180.0) }
    var pitchDegrees by remember { mutableStateOf<Double?>(20.0) }
    var panelWidthM by remember { mutableStateOf(2.278) }
    var panelHeightM by remember { mutableStateOf(1.134) }
    var panelWattage by remember { mutableStateOf(600.0) }
    var setbackM by remember { mutableStateOf(0.5) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enter Site Manually", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Text("×", style = MaterialTheme.typography.titleLarge) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionCard(title = "Location") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextEntryField(label = "Customer / property name (optional)", value = name, onValueChange = { name = it })
                        TextEntryField(label = "Address (optional)", value = address, onValueChange = { address = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            NumberField(
                                label = "Latitude",
                                value = latitude,
                                onValueChange = { latitude = it },
                                allowNegative = true,
                                modifier = Modifier.weight(1f)
                            )
                            NumberField(
                                label = "Longitude",
                                value = longitude,
                                onValueChange = { longitude = it },
                                allowNegative = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            "Roof measurements are approximate and should be verified on-site before installation.",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary
                        )
                        LumixSecondaryButton(
                            text = "Use This Location",
                            enabled = latitude != 0.0 || longitude != 0.0,
                            onClick = {
                                viewModel.setLocation(
                                    latitude = latitude,
                                    longitude = longitude,
                                    address = address.ifBlank { null },
                                    name = name.ifBlank { null }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (state.hasLocation) {
                item {
                    SectionCard(title = "Roof Dimensions") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                NumberField(label = "Length (m)", value = lengthM, onValueChange = { lengthM = it }, suffix = "m", modifier = Modifier.weight(1f))
                                NumberField(label = "Width (m)", value = widthM, onValueChange = { widthM = it }, suffix = "m", modifier = Modifier.weight(1f))
                            }
                            NumberField(
                                label = "Azimuth (facing direction)",
                                value = azimuthDegrees,
                                onValueChange = { azimuthDegrees = it.mod(360.0) },
                                suffix = "°",
                                supportingText = RoofGeometryEngine.compassLabel(azimuthDegrees)
                            )
                            LabeledDropdown(
                                label = "Roof pitch",
                                options = pitchOptions,
                                selected = pitchDegrees,
                                optionLabel = { it?.let { d -> "${d.toInt()}°" } ?: "Unknown — use horizontal estimate" },
                                onSelected = { pitchDegrees = it }
                            )
                            NumberField(label = "Setback from edges (m)", value = setbackM, onValueChange = { setbackM = it }, suffix = "m")
                        }
                    }
                }

                item {
                    SectionCard(title = "Panel Specification") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                NumberField(label = "Panel width (m)", value = panelWidthM, onValueChange = { panelWidthM = it }, suffix = "m", modifier = Modifier.weight(1f))
                                NumberField(label = "Panel height (m)", value = panelHeightM, onValueChange = { panelHeightM = it }, suffix = "m", modifier = Modifier.weight(1f))
                            }
                            NumberField(label = "Panel wattage", value = panelWattage, onValueChange = { panelWattage = it }, allowDecimal = false, suffix = "W")
                            LumixPrimaryButton(
                                text = "Analyze This Roof",
                                enabled = lengthM > 0 && widthM > 0 && panelWidthM > 0 && panelHeightM > 0 && panelWattage > 0,
                                onClick = {
                                    viewModel.addManualRoofPlane(
                                        lengthM = lengthM,
                                        widthM = widthM,
                                        azimuthDegrees = azimuthDegrees,
                                        pitchDegrees = pitchDegrees,
                                        panelWidthM = panelWidthM,
                                        panelHeightM = panelHeightM,
                                        panelWattage = panelWattage,
                                        setbackMeters = setbackM
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                items(state.roofPlanes) { plane ->
                    RoofPlaneCard(plane = plane, onRemove = { viewModel.removeRoofPlane(plane.id) })
                }

                if (state.roofPlanes.isNotEmpty()) {
                    item {
                        LumixPrimaryButton(
                            text = "Save Site",
                            onClick = {
                                viewModel.saveSite()?.let(onSaved)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextEntryField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(LumixRadius.sm),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = palette.solarYellow,
            unfocusedBorderColor = palette.outline,
            focusedLabelColor = palette.solarYellowText,
            unfocusedLabelColor = palette.textSecondary,
            cursorColor = palette.solarYellow,
            focusedTextColor = palette.textPrimary,
            unfocusedTextColor = palette.textPrimary
        ),
        modifier = modifier.fillMaxWidth()
    )
}
