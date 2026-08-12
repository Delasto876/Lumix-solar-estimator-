package com.lumix.estimator.ui.simulation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumix.estimator.domain.simulation.EnergyFlowResolver
import com.lumix.estimator.domain.simulation.InverterMode
import com.lumix.estimator.domain.simulation.SimFrame
import com.lumix.estimator.domain.simulation.SimulationEngine
import com.lumix.estimator.domain.simulation.TechnicalModel
import com.lumix.estimator.sensors.CompassMath
import com.lumix.estimator.ui.components.AnimatedCounterText
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixColors
import com.lumix.estimator.ui.theme.LumixRadius
import com.lumix.estimator.ui.theme.numberDisplayStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulationScreen(
    quoteId: Long,
    viewModel: SimulationViewModel,
    onBack: () -> Unit
) {
    val palette = LocalLumixPalette.current
    val state by viewModel.state.collectAsState()
    var showAppliances by remember { mutableStateOf(false) }
    var inspectTarget by remember { mutableStateOf<InspectTarget?>(null) }
    var showGraph by remember { mutableStateOf(false) }

    LaunchedEffect(quoteId) {
        viewModel.load(quoteId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.systemLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        state.currentFrame?.let {
                            Text(it.status.label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("×", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        },
        bottomBar = {
            if (!state.loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    TransportBar(
                        isPlaying = state.isPlaying,
                        speed = state.speed,
                        onTogglePlay = viewModel::togglePlay,
                        onSpeedChange = viewModel::setSpeed,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { padding ->
        if (state.loading || state.config == null || state.currentFrame == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val config = state.config!!
        val frame = state.currentFrame!!
        val flows = remember(frame) { EnergyFlowResolver.resolve(frame) }
        val sunProgress = remember(frame.hour) { SimulationEngine.daylightProgress(frame.hour) }
        val sunIntensity = remember(frame.hour, state.weather) {
            (SimulationEngine.irradianceFactor(frame.hour) * state.weather.multiplier).toFloat()
        }
        val cloudCoverage = remember(state.weather) { (1.0 - state.weather.multiplier).toFloat() }
        val batterySocFraction = if (config.hasBattery) frame.batterySocPercent / 100f else null

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(frame)
                    ModeToggle(technicalMode = state.technicalMode, onToggle = viewModel::setTechnicalMode)
                }
            }

            if (config.gridConnectable && !state.gridConnected) {
                item { OutageBanner() }
            }

            item {
                SectionCard(title = "", accentColor = statusColor(frame.status)) {
                    EnergyFlowCanvas(
                        flows = flows,
                        cloudCoverage = cloudCoverage,
                        sunProgress = sunProgress,
                        sunIntensity = sunIntensity,
                        batterySocFraction = batterySocFraction,
                        batteryCharging = frame.batteryPowerKw > 0.0,
                        modifier = Modifier.clip(RoundedCornerShape(LumixRadius.md))
                    )
                    InspectChipRow(
                        hasBattery = config.hasBattery,
                        onSelect = { inspectTarget = it },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            item {
                LivePowerRow(frame)
            }

            if (state.technicalMode) {
                item {
                    SectionCard(title = "Technical") {
                        TechnicalDetailsContent(
                            readout = remember(frame, config, state.timeline, state.appliances, state.gridServiceAmps) {
                                TechnicalModel.compute(frame, config, state.timeline, state.appliances, state.gridServiceAmps)
                            }
                        )
                    }
                }
            }

            if (config.isSiteAware) {
                item {
                    val sunPosition = remember(frame.hour, config) {
                        SimulationEngine.sitePosition(frame.hour, config.siteLatitude!!, config.siteLongitude!!)
                    }
                    SunAndRoofCard(
                        sunAzimuthDegrees = sunPosition.azimuthDegrees,
                        sunElevationDegrees = sunPosition.elevationDegrees,
                        roofAzimuthDegrees = config.roofAzimuthDegrees!!,
                        roofPitchDegrees = config.roofPitchDegrees!!
                    )
                }
            }

            item {
                SectionCard(title = "") {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showAppliances = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Appliances", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                            Text("Tap to turn things on or off", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                        }
                        Text("%.2f kW".format(state.applianceLoadKw), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.solarYellowText)
                    }

                    if (config.gridConnectable) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("JPS Grid", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                                Text(
                                    if (state.gridConnected) "Connected" else "Disconnected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.gridConnected) palette.textSecondary else palette.warningRedText
                                )
                            }
                            Switch(
                                checked = state.gridConnected,
                                onCheckedChange = { viewModel.setGridConnected(it) },
                                colors = SwitchDefaults.colors(checkedTrackColor = palette.solarYellow)
                            )
                        }
                    }
                }
            }

            if (config.gridConnectable) {
                item {
                    SectionCard(title = "Inverter Mode") {
                        InverterModeSelector(selected = state.inverterMode, onSelect = viewModel::setInverterMode)
                        Text(
                            state.inverterMode.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        Text(
                            "Utility service limit",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                        GridServiceAmpsSelector(selected = state.gridServiceAmps, onSelect = viewModel::setGridServiceAmps)
                        if (state.inverterMode == InverterMode.UTI && config.hasBattery) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Grid charges battery", style = MaterialTheme.typography.labelMedium, color = palette.textPrimary)
                                Switch(
                                    checked = state.gridChargeEnabled,
                                    onCheckedChange = { viewModel.setGridChargeEnabled(it) },
                                    colors = SwitchDefaults.colors(checkedTrackColor = palette.solarYellow)
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Weather") {
                    WeatherSelector(selected = state.weather, onSelect = viewModel::setWeather)
                    WhatIfRow(
                        actions = buildList {
                            add(
                                WhatIfAction(
                                    glyph = "☁️",
                                    label = if (state.cloudEventActive) "Clouds rolling in…" else "Cloud Event",
                                    enabled = !state.cloudEventActive,
                                    onClick = viewModel::triggerCloudEvent
                                )
                            )
                            if (config.gridConnectable) {
                                add(
                                    WhatIfAction(
                                        glyph = "⚡",
                                        label = "Simulate Outage",
                                        enabled = state.gridConnected,
                                        onClick = { viewModel.setGridConnected(false) }
                                    )
                                )
                            }
                            if (config.hasBattery) {
                                add(
                                    WhatIfAction(
                                        glyph = "🪫",
                                        label = "Low Battery (20%)",
                                        onClick = { viewModel.setStartSocFraction(0.2) }
                                    )
                                )
                            }
                        },
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            item {
                SectionCard(title = "") {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        TimeDial(
                            hour = state.currentHour,
                            onScrub = viewModel::scrubTo,
                            markerHour = state.batteryFullHour
                        )
                    }
                    if (state.batteryFullHour != null) {
                        Text(
                            "🔋 Battery full at ${formatSimTime(state.batteryFullHour!!)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.energyGreenText,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            item {
                SectionCard(title = "") {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showGraph = !showGraph },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Energy Graph", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                        Text(if (showGraph) "▾" else "▸", style = MaterialTheme.typography.titleMedium, color = palette.textSecondary)
                    }
                    AnimatedVisibility(
                        visible = showGraph,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        EnergyGraph(
                            timeline = state.timeline,
                            currentFrame = frame,
                            onScrub = viewModel::scrubTo,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }
        }

        if (showAppliances) {
            ModalBottomSheet(
                onDismissRequest = { showAppliances = false },
                sheetState = rememberModalBottomSheetState()
            ) {
                AppliancesSheetContent(
                    appliances = state.appliances,
                    currentHour = state.currentHour,
                    onToggle = { viewModel.toggleAppliance(it) },
                    onSetRuns = { type, runs -> viewModel.setApplianceRuns(type, runs) }
                )
            }
        }

        val selected = inspectTarget
        if (selected != null) {
            ModalBottomSheet(
                onDismissRequest = { inspectTarget = null },
                sheetState = rememberModalBottomSheetState()
            ) {
                ComponentDetailContent(
                    target = selected,
                    frame = frame,
                    config = config,
                    inputs = state.inputs,
                    gridConnected = state.gridConnected,
                    gridServiceAmps = state.gridServiceAmps
                )
            }
        }
    }
}

@Composable
private fun ModeToggle(technicalMode: Boolean, onToggle: (Boolean) -> Unit) {
    val palette = LocalLumixPalette.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(LumixRadius.pill))
            .background(palette.glass)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(false, true).forEach { isTechnical ->
            val selected = isTechnical == technicalMode
            Text(
                if (isTechnical) "Technical" else "Basic",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) palette.solarYellowText else palette.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(LumixRadius.pill))
                    .background(if (selected) palette.solarYellow.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onToggle(isTechnical) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun InverterModeSelector(selected: InverterMode, onSelect: (InverterMode) -> Unit) {
    val palette = LocalLumixPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LumixRadius.pill))
            .background(palette.glass)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InverterMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Text(
                mode.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) palette.solarYellowText else palette.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(LumixRadius.pill))
                    .background(if (isSelected) palette.solarYellow.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}

private val GRID_SERVICE_AMP_PRESETS = listOf(15.0, 30.0, 60.0, 100.0)

@Composable
private fun GridServiceAmpsSelector(selected: Double, onSelect: (Double) -> Unit) {
    val palette = LocalLumixPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(LumixRadius.pill))
            .background(palette.glass)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GRID_SERVICE_AMP_PRESETS.forEach { amps ->
            val isSelected = amps == selected
            Text(
                "${amps.toInt()}A",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) palette.solarYellowText else palette.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(LumixRadius.pill))
                    .background(if (isSelected) palette.solarYellow.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(amps) }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun OutageBanner() {
    val palette = LocalLumixPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LumixRadius.md))
            .background(LumixColors.WarningRed.copy(alpha = 0.14f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("⚡", style = MaterialTheme.typography.titleMedium)
        Column {
            Text("JPS OUTAGE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = palette.warningRedText)
            Text("Home running on backup", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        }
    }
}

@Composable
private fun StatusPill(frame: SimFrame) {
    val palette = LocalLumixPalette.current
    val color = statusColor(frame.status)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(LumixRadius.pill))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            frame.status.label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary
        )
    }
}

@Composable
private fun LivePowerRow(frame: SimFrame) {
    val palette = LocalLumixPalette.current
    SectionCard(title = "") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LiveStat(label = "SOLAR", valueKw = frame.pvKw, color = palette.solarYellowText, modifier = Modifier.weight(1f))
            LiveStat(label = "GRID", valueKw = frame.gridPowerKw, color = palette.solarAmberText, modifier = Modifier.weight(1f))
            LiveStat(label = "HOME", valueKw = frame.houseLoadKw, color = palette.textPrimary, modifier = Modifier.weight(1f))
            if (frame.batterySocPercent > 0f) {
                Column(modifier = Modifier.weight(1f)) {
                    LiveStat(label = "BATTERY", valueKw = frame.batteryPowerKw, color = if (frame.batteryPowerKw >= 0) palette.energyGreenText else palette.technicalCyanText, showSign = true)
                    Text(
                        "${frame.batterySocPercent.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                }
            }
        }
    }
}

/**
 * Shown only for quotes built from a Solar Site "Use This Roof" flow ([SimSystemConfig.isSiteAware]).
 * Sun position updates live as the time dial scrubs, using today's real date — this is the
 * live-compass idea from Solar Site, brought into the digital twin.
 */
@Composable
private fun SunAndRoofCard(
    sunAzimuthDegrees: Double,
    sunElevationDegrees: Double,
    roofAzimuthDegrees: Double,
    roofPitchDegrees: Double
) {
    val palette = LocalLumixPalette.current
    SectionCard(title = "Sun & Roof") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SunRoofStat(
                label = "SUN",
                value = if (sunElevationDegrees > 0) {
                    "${sunAzimuthDegrees.toInt()}° ${CompassMath.compassLabel(sunAzimuthDegrees)}"
                } else "Below horizon",
                sub = if (sunElevationDegrees > 0) "${sunElevationDegrees.toInt()}° elevation" else null,
                color = palette.solarYellowText,
                modifier = Modifier.weight(1f)
            )
            SunRoofStat(
                label = "ROOF FACES",
                value = "${roofAzimuthDegrees.toInt()}° ${CompassMath.compassLabel(roofAzimuthDegrees)}",
                sub = "${roofPitchDegrees.toInt()}° pitch",
                color = palette.textPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SunRoofStat(label: String, value: String, sub: String?, color: Color, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        if (sub != null) {
            Text(sub, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        }
    }
}

@Composable
private fun LiveStat(label: String, valueKw: Double, color: Color, modifier: Modifier = Modifier, showSign: Boolean = false) {
    val palette = LocalLumixPalette.current
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        AnimatedCounterText(
            targetValue = valueKw,
            format = { v -> (if (showSign && v > 0.001) "+" else "") + "%.2f kW".format(v) },
            style = numberDisplayStyle(size = 16.sp, weight = FontWeight.Bold),
            color = color
        )
    }
}
