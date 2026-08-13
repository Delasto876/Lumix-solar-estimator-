package com.lumix.estimator.ui.simulation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.align
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumix.estimator.R
import com.lumix.estimator.domain.simulation.EnergyFlow
import com.lumix.estimator.domain.simulation.FlowDirection
import com.lumix.estimator.ui.theme.LumixColors
import com.lumix.estimator.ui.theme.rememberReduceMotion
import kotlin.math.roundToInt

/** The reference photo's own pixel aspect ratio — panels/inverter/battery only line up at this ratio. */
internal const val IMAGE_ASPECT_RATIO = 1173f / 1341f

/**
 * The four real electrical routes, and the one color each is drawn in everywhere it appears
 * (line, particles, per-line chip accent, legend swatch) — a single mapping so those four
 * places can never drift apart from each other.
 */
private fun colorFor(path: EnergyPath): Color = when (path.id) {
    "solar_inverter" -> LumixColors.SolarYellow
    "inverter_battery" -> LumixColors.TechnicalCyan
    "inverter_house" -> LumixColors.EnergyGreen
    // Import-only: this path only ever carries JPS power inbound.
    "grid_inverter" -> LumixColors.GridMagenta
    else -> LumixColors.TechnicalCyan
}

/**
 * The full photoreal digital-twin visual: `bg_house_energy_routes.png` (a fixed, never-
 * redrawn background asset) with every dynamic layer composited above it — a full-scene
 * lighting/rain wash, cloud coverage, a sun position/intensity marker, an animated battery
 * fill wash, glowing color-coded flow lines with particles riding them, floating per-line
 * value chips, and a HUD of stat/time/legend/battery/system cards. Every layer is driven by
 * real simulation state passed in by the caller; nothing here fabricates its own numbers.
 *
 * @param cloudCoverage 0f (clear) .. 1f (fully overcast), from the selected weather state.
 * @param sunProgress 0f at sunrise, 1f at sunset; null hides the sun marker (nighttime).
 * @param sunIntensity 0f..1f, scales the marker's glow — irradiance factor × cloud multiplier.
 * @param daylightFactor 0f outside daylight hours .. 1f at midday, undamped by weather — drives
 * how dark/cool the whole scene reads, so night always matches actual solar output hitting zero.
 * @param isStorm true for the heaviest weather state — adds a rain wash and falling streaks.
 * @param batterySocFraction 0f..1f state of charge; null hides all battery UI (no battery in this system).
 * @param batterySocKwh Current stored energy in kWh, shown alongside the percent on [BatteryCard].
 * @param simTimeText The current simulated time (already formatted, e.g. "12:42 PM"), shown in the
 * scene's top-right HUD card. Null hides it.
 * @param inverterModeLabel The active inverter mode's short code (e.g. "SBU"), shown next to the
 * time as "SBU Mode". Null hides it (e.g. off-grid systems with no mode concept).
 */
@Composable
fun EnergyFlowCanvas(
    flows: List<EnergyFlow>,
    modifier: Modifier = Modifier,
    cloudCoverage: Float = 0f,
    sunProgress: Float? = null,
    sunIntensity: Float = 0f,
    daylightFactor: Float = 1f,
    isStorm: Boolean = false,
    batterySocFraction: Float? = null,
    batterySocKwh: Double? = null,
    batteryCharging: Boolean = false,
    simTimeText: String? = null,
    inverterModeLabel: String? = null,
    debugShowPaths: Boolean = SolarSimulationPaths.DEBUG_SHOW_PATHS
) {
    Box(modifier = modifier.fillMaxWidth().aspectRatio(IMAGE_ASPECT_RATIO)) {
        Image(
            painter = painterResource(R.drawable.bg_house_energy_routes),
            contentDescription = "Solar home energy system",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxWidth().aspectRatio(IMAGE_ASPECT_RATIO)
        )

        SceneAtmosphereOverlay(daylightFactor = daylightFactor, cloudCoverage = cloudCoverage, isStorm = isStorm)

        CloudOverlay(coverageFraction = cloudCoverage)

        if (sunProgress != null) {
            SunIndicator(progressFraction = sunProgress, intensityFraction = sunIntensity)
        }

        if (batterySocFraction != null) {
            BatteryFillOverlay(fillFraction = batterySocFraction, charging = batteryCharging)
        }

        ParticleOverlay(flows = flows, debugShowPaths = debugShowPaths)

        FlowLabelChips(flows = flows, batterySocFraction = batterySocFraction)

        HudOverlay(
            flows = flows,
            batterySocFraction = batterySocFraction,
            batterySocKwh = batterySocKwh,
            batteryCharging = batteryCharging,
            simTimeText = simTimeText,
            inverterModeLabel = inverterModeLabel
        )
    }
}

/** "1,240 W", signed ("+1,240 W" / "-620 W") when [showSign] and the flow is genuinely nonzero. */
private fun formatWatts(kw: Double, showSign: Boolean = false): String {
    val watts = (kw * 1000.0).roundToInt()
    val sign = if (showSign && watts > 0) "+" else ""
    return "$sign${"%,d".format(watts)} W"
}

/**
 * Live icon + label + value chips drawn directly over the artwork's own baked "0 W" / "100%"
 * placeholder text at the Grid/Solar/Consumption/Battery labels — each value is the exact same
 * resolved power already driving the particles and line on that same path, so nothing here can
 * disagree with the animation. Positioned at [SolarSimulationPaths]'s pixel-measured label
 * bounds rather than a path midpoint, so the chip lands exactly where the artwork already
 * prints (and expects) a number.
 */
@Composable
private fun FlowLabelChips(flows: List<EnergyFlow>, batterySocFraction: Float?, modifier: Modifier = Modifier) {
    val flowById = remember(flows) { flows.associateBy { it.id } }
    val gridKw = flowById["grid_inverter"]?.powerKw ?: 0.0
    val solarKw = flowById["solar_inverter"]?.powerKw ?: 0.0
    val consumptionKw = flowById["inverter_house"]?.powerKw ?: 0.0
    val batteryFlow = flowById["inverter_battery"]
    val batteryKw = when {
        batteryFlow == null || !batteryFlow.active -> 0.0
        batteryFlow.direction == FlowDirection.FORWARD -> batteryFlow.powerKw
        else -> -batteryFlow.powerKw
    }
    val batteryPercentText = batterySocFraction?.let { " · ${(it * 100).roundToInt()}%" } ?: ""

    BoxWithConstraints(modifier = modifier.fillMaxWidth().aspectRatio(IMAGE_ASPECT_RATIO)) {
        FlowChip(
            icon = "🔌",
            label = "Grid → Inverter",
            value = formatWatts(gridKw),
            accent = LumixColors.GridMagenta,
            bounds = SolarSimulationPaths.gridLabelBounds,
            containerWidth = maxWidth,
            containerHeight = maxHeight
        )
        FlowChip(
            icon = "☀",
            label = "PV → Inverter",
            value = formatWatts(solarKw),
            accent = LumixColors.SolarYellow,
            bounds = SolarSimulationPaths.solarLabelBounds,
            containerWidth = maxWidth,
            containerHeight = maxHeight
        )
        FlowChip(
            icon = "🏠",
            label = "Inverter → Load",
            value = formatWatts(consumptionKw),
            accent = LumixColors.EnergyGreen,
            bounds = SolarSimulationPaths.consumptionLabelBounds,
            containerWidth = maxWidth,
            containerHeight = maxHeight
        )
        FlowChip(
            icon = "🔋",
            label = "Inverter → Battery",
            value = formatWatts(batteryKw, showSign = true) + batteryPercentText,
            accent = LumixColors.TechnicalCyan,
            bounds = SolarSimulationPaths.batteryLabelBounds,
            containerWidth = maxWidth,
            containerHeight = maxHeight
        )
    }
}

/**
 * Anchored to the top-left of the artwork's own baked icon+"0 W" text box. The backing is
 * nearly opaque so the baked digits are genuinely erased rather than dimmed-and-visible-
 * through, and a small color dot ties the chip back to the same hue as its line/legend entry.
 */
@Composable
private fun FlowChip(
    icon: String,
    label: String,
    value: String,
    accent: Color,
    bounds: NormalizedRect,
    containerWidth: Dp,
    containerHeight: Dp
) {
    val originX = containerWidth * bounds.left - 4.dp
    val originY = containerHeight * bounds.top - 3.dp
    Row(
        modifier = Modifier
            .offset(x = originX, y = originY)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.92f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(accent))
        Column {
            Text(
                "$icon $label",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1
            )
            Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFeatureSettings = "tnum",
                maxLines = 1
            )
        }
    }
}

/**
 * The scene's instrument cluster: stat cards top-left, time+mode and the flow legend top-right,
 * the battery gauge bottom-left, and a compact system summary bottom-right — all corner-anchored
 * so nothing ever sits over the house or equipment itself.
 */
@Composable
private fun HudOverlay(
    flows: List<EnergyFlow>,
    batterySocFraction: Float?,
    batterySocKwh: Double?,
    batteryCharging: Boolean,
    simTimeText: String?,
    inverterModeLabel: String?,
    modifier: Modifier = Modifier
) {
    val flowById = remember(flows) { flows.associateBy { it.id } }
    val batteryFlow = flowById["inverter_battery"]
    val batteryKw = when {
        batteryFlow == null || !batteryFlow.active -> 0.0
        batteryFlow.direction == FlowDirection.FORWARD -> batteryFlow.powerKw
        else -> -batteryFlow.powerKw
    }

    Box(modifier = modifier.fillMaxWidth().aspectRatio(IMAGE_ASPECT_RATIO)) {
        TopStatRow(flows = flows, modifier = Modifier.align(Alignment.TopStart).padding(10.dp))

        if (simTimeText != null || inverterModeLabel != null) {
            Column(
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TimeModeCard(timeText = simTimeText, modeLabel = inverterModeLabel)
                LegendCard()
            }
        } else {
            LegendCard(modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        }

        if (batterySocFraction != null) {
            BatteryCard(
                socFraction = batterySocFraction,
                socKwh = batterySocKwh,
                charging = batteryCharging,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
            )
        }

        SystemSummaryCard(
            flows = flows,
            batteryKw = if (batterySocFraction != null) batteryKw else null,
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
        )
    }
}

/** Compact translucent card — the shared look for every HUD element floating over the photo. */
@Composable
private fun HudCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}

@Composable
private fun TopStatRow(flows: List<EnergyFlow>, modifier: Modifier = Modifier) {
    val flowById = remember(flows) { flows.associateBy { it.id } }
    val solarKw = flowById["solar_inverter"]?.powerKw ?: 0.0
    val gridKw = flowById["grid_inverter"]?.powerKw ?: 0.0
    val homeKw = flowById["inverter_house"]?.powerKw ?: 0.0

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatCard(label = "SOLAR", valueKw = solarKw, accent = LumixColors.SolarYellow)
        StatCard(label = "GRID", valueKw = gridKw, accent = LumixColors.GridMagenta)
        StatCard(label = "HOME LOAD", valueKw = homeKw, accent = LumixColors.EnergyGreen)
    }
}

@Composable
private fun StatCard(label: String, valueKw: Double, accent: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accent))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), fontWeight = FontWeight.Bold)
            Text(
                "%.2f kW".format(valueKw),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum"
            )
        }
    }
}

/** Simulated clock + active inverter mode, e.g. "12:42 PM" / "● SBU Mode". */
@Composable
private fun TimeModeCard(timeText: String?, modeLabel: String?, modifier: Modifier = Modifier) {
    HudCard(modifier = modifier) {
        if (timeText != null) {
            Text(
                timeText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFeatureSettings = "tnum"
            )
        }
        if (modeLabel != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(LumixColors.EnergyGreen))
                Text(
                    "$modeLabel Mode",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private data class LegendEntry(val color: Color, val label: String)

/** The same four route→color pairings [colorFor] draws with — kept as the one legend source. */
private val ENERGY_LEGEND = listOf(
    LegendEntry(LumixColors.SolarYellow, "PV → Inverter"),
    LegendEntry(LumixColors.TechnicalCyan, "Inverter → Battery"),
    LegendEntry(LumixColors.EnergyGreen, "Inverter → Load"),
    LegendEntry(LumixColors.GridMagenta, "Grid → Inverter")
)

@Composable
private fun LegendCard(modifier: Modifier = Modifier) {
    HudCard(modifier = modifier) {
        ENERGY_LEGEND.forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(entry.color))
                Text(entry.label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun BatteryCard(socFraction: Float, socKwh: Double?, charging: Boolean, modifier: Modifier = Modifier) {
    val percent = (socFraction * 100).roundToInt()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("🔋", style = MaterialTheme.typography.titleMedium)
        Column {
            Text(
                "$percent%",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFeatureSettings = "tnum"
            )
            val detail = buildString {
                if (socKwh != null) append("%.1f kWh".format(socKwh))
                if (charging) append(if (isEmpty()) "Charging" else " · Charging")
            }
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun SystemSummaryCard(flows: List<EnergyFlow>, batteryKw: Double?, modifier: Modifier = Modifier) {
    val flowById = remember(flows) { flows.associateBy { it.id } }
    val rows = remember(flowById, batteryKw) {
        buildList {
            add("Solar" to (flowById["solar_inverter"]?.powerKw ?: 0.0))
            add("Home Load" to (flowById["inverter_house"]?.powerKw ?: 0.0))
            if (batteryKw != null) add("Battery" to batteryKw)
            add("Grid" to (flowById["grid_inverter"]?.powerKw ?: 0.0))
        }
    }

    HudCard(modifier = modifier) {
        Text("SYSTEM", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f), fontWeight = FontWeight.Bold)
        rows.forEach { (label, kw) ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    formatWatts(kw, showSign = label == "Battery"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFeatureSettings = "tnum"
                )
            }
        }
    }
}

/** The animated current-flow lines + particles only — kept separate so it can be timed/tested on its own. */
@Composable
private fun ParticleOverlay(flows: List<EnergyFlow>, debugShowPaths: Boolean) {
    val reduceMotion = rememberReduceMotion()
    var elapsedSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        var lastNanos = -1L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos >= 0L) {
                    elapsedSeconds += (nanos - lastNanos) / 1_000_000_000f
                }
                lastNanos = nanos
            }
        }
    }

    val flowById = remember(flows) { flows.associateBy { it.id } }

    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(IMAGE_ASPECT_RATIO)) {
        val w = size.width
        val h = size.height
        fun toOffset(p: NormalizedPoint) = Offset(p.x * w, p.y * h)

        SolarSimulationPaths.allPaths.forEach { path ->
            if (debugShowPaths) {
                for (i in 0 until path.points.size - 1) {
                    drawLine(
                        color = Color.Magenta,
                        start = toOffset(path.points[i]),
                        end = toOffset(path.points[i + 1]),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                    )
                }
            }

            val flow = flowById[path.id] ?: return@forEach
            if (!flow.active) return@forEach
            // A REVERSE flow on a path not marked bidirectional (e.g. grid_inverter, which is
            // strictly import-only) would be a resolver bug — fail safe by simply not drawing
            // it, rather than animating a physically-impossible direction on a fixed one-way route.
            if (flow.direction == FlowDirection.REVERSE && !path.bidirectional) return@forEach

            val color = colorFor(path)
            val speed = EnergyFlowPathManager.particleSpeedFor(flow.powerKw)
            val signedPhase = if (flow.direction == FlowDirection.REVERSE) -elapsedSeconds * speed else elapsedSeconds * speed

            // The conduit itself — a soft glow plus a dashed, animated line, in the route's own
            // legend color, drawn its full length (not just wherever a particle happens to be).
            val linePath = Path().apply {
                val start = toOffset(path.points.first())
                moveTo(start.x, start.y)
                path.points.drop(1).forEach { p ->
                    val o = toOffset(p)
                    lineTo(o.x, o.y)
                }
            }
            val dashPhase = -EnergyFlowPathManager.wrapUnit(signedPhase) * 48f
            drawPath(path = linePath, color = color.copy(alpha = 0.14f), style = Stroke(width = 9f))
            drawPath(
                path = linePath,
                color = color.copy(alpha = 0.85f),
                style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 11f), phase = dashPhase))
            )

            val count = EnergyFlowPathManager.particleCountFor(flow.powerKw)
            if (count <= 0) return@forEach
            val offsets = EnergyFlowPathManager.particleOffsets(count)

            offsets.forEach { base ->
                val t = EnergyFlowPathManager.wrapUnit(base + signedPhase)
                val center = toOffset(EnergyFlowPathManager.pointAt(path, t))
                // A soft glow behind a small solid core — power flow, not a video-game beam.
                drawCircle(color = color.copy(alpha = 0.22f), radius = 8f, center = center)
                drawCircle(color = color.copy(alpha = 0.9f), radius = 3.2f, center = center)
            }
        }
    }
}
