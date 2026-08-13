package com.lumix.estimator.ui.simulation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.align
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.lumix.estimator.R
import com.lumix.estimator.domain.simulation.EnergyFlow
import com.lumix.estimator.domain.simulation.FlowDirection
import com.lumix.estimator.ui.theme.LumixColors
import com.lumix.estimator.ui.theme.rememberReduceMotion

/** The reference photo's own pixel aspect ratio — panels/inverter/battery only line up at this ratio. */
internal const val IMAGE_ASPECT_RATIO = 1536f / 1024f

/**
 * The four real electrical routes, and the one color each is drawn in everywhere it appears
 * (line, particles, per-line chip accent, legend swatch) — a single mapping so those four
 * places can never drift apart from each other. Matched to the artwork's own printed glow-line
 * colors (A29): warm red for grid, gold for solar, green for battery, cyan-blue for house/load —
 * see [SolarSimulationPaths]'s class doc for why green/battery and blue/house pair the way they
 * do (the artwork's own printed endpoints, not the color names alone, decide the mapping).
 */
private fun colorFor(path: EnergyPath): Color = when (path.id) {
    "solar_inverter" -> LumixColors.SolarYellow
    "inverter_battery" -> LumixColors.EnergyGreen
    "inverter_house" -> LumixColors.TechnicalCyan
    // Import-only: this path only ever carries JPS power inbound.
    "grid_inverter" -> LumixColors.WarningRed
    else -> LumixColors.TechnicalCyan
}

/**
 * The full photoreal digital-twin visual: `bg_house_energy_routes.png` (a fixed, never-
 * redrawn background asset) with every dynamic layer composited above it — a full-scene
 * lighting/rain wash, cloud coverage, a sun position/intensity marker, an animated battery
 * fill wash, and glowing color-coded flow lines with particles riding them. Deliberately
 * minimal on-image text (A30: the earlier stat-card/legend/battery/system-card HUD and
 * per-line chips were pulled back out — too much floating data was competing with the house
 * and the animation itself for attention; the below-canvas `LivePowerRow` in
 * `SimulationScreen.kt` is where those numbers actually live). Every layer is driven by real
 * simulation state passed in by the caller; nothing here fabricates its own numbers.
 *
 * @param cloudCoverage 0f (clear) .. 1f (fully overcast), from the selected weather state.
 * @param sunProgress 0f at sunrise, 1f at sunset; null hides the sun marker (nighttime).
 * @param sunIntensity 0f..1f, scales the marker's glow — irradiance factor × cloud multiplier.
 * @param daylightFactor 0f outside daylight hours .. 1f at midday, undamped by weather — drives
 * how dark/cool the whole scene reads, so night always matches actual solar output hitting zero.
 * @param isStorm true for the heaviest weather state — adds a rain wash and falling streaks.
 * @param batterySocFraction 0f..1f state of charge; null hides the battery overlay (no battery in this system).
 * @param simTimeText The current simulated time (already formatted, e.g. "12:42 PM"), shown as a
 * subtle overlay in the scene's top-right corner — sky, not equipment, so nothing important is
 * ever covered. Null hides it entirely.
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
    batteryCharging: Boolean = false,
    simTimeText: String? = null,
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

        if (simTimeText != null) {
            SimClockOverlay(text = simTimeText, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

/** A small, subtle digital-clock chip in the scene's corner — the current simulated time, always legible over sky. */
@Composable
private fun SimClockOverlay(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(10.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.32f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontFeatureSettings = "tnum"
        )
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
