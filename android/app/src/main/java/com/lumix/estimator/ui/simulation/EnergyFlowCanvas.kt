package com.lumix.estimator.ui.simulation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.lumix.estimator.R
import com.lumix.estimator.domain.simulation.EnergyFlow
import com.lumix.estimator.domain.simulation.FlowDirection
import com.lumix.estimator.ui.theme.LumixColors
import com.lumix.estimator.ui.theme.rememberReduceMotion

/** The reference photo's own pixel aspect ratio — panels/inverter/battery only line up at this ratio. */
internal const val IMAGE_ASPECT_RATIO = 1536f / 1024f

private val WarmWhite = Color(0xFFFFF3D6)

private fun colorFor(path: EnergyPath, flow: EnergyFlow): Color = when (path.id) {
    "solar_inverter" -> LumixColors.SolarYellow
    "inverter_house" -> WarmWhite
    "inverter_battery" -> if (flow.direction == FlowDirection.FORWARD) LumixColors.EnergyGreen else LumixColors.TechnicalCyan
    // Import-only: this path only ever carries JPS power inbound.
    "grid_inverter" -> LumixColors.SolarAmber
    else -> LumixColors.TechnicalCyan
}

/**
 * The full photoreal digital-twin visual: `bg_house_energy_routes.png` (a fixed, never-
 * redrawn background asset) with every dynamic layer composited above it — cloud coverage,
 * a sun position/intensity marker, an animated battery fill wash, and particles flowing
 * along the routes baked into the artwork. Every layer is driven by real simulation state
 * passed in by the caller; nothing here fabricates its own numbers.
 *
 * @param cloudCoverage 0f (clear) .. 1f (fully overcast), from the selected weather state.
 * @param sunProgress 0f at sunrise, 1f at sunset; null hides the sun marker (nighttime).
 * @param sunIntensity 0f..1f, scales the marker's glow — irradiance factor × cloud multiplier.
 * @param batterySocFraction 0f..1f state of charge; null hides the battery overlay (no battery in this system).
 */
@Composable
fun EnergyFlowCanvas(
    flows: List<EnergyFlow>,
    modifier: Modifier = Modifier,
    cloudCoverage: Float = 0f,
    sunProgress: Float? = null,
    sunIntensity: Float = 0f,
    batterySocFraction: Float? = null,
    batteryCharging: Boolean = false,
    debugShowPaths: Boolean = SolarSimulationPaths.DEBUG_SHOW_PATHS
) {
    Box(modifier = modifier.fillMaxWidth().aspectRatio(IMAGE_ASPECT_RATIO)) {
        Image(
            painter = painterResource(R.drawable.bg_house_energy_routes),
            contentDescription = "Solar home energy system",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxWidth().aspectRatio(IMAGE_ASPECT_RATIO)
        )

        CloudOverlay(coverageFraction = cloudCoverage)

        if (sunProgress != null) {
            SunIndicator(progressFraction = sunProgress, intensityFraction = sunIntensity)
        }

        if (batterySocFraction != null) {
            BatteryFillOverlay(fillFraction = batterySocFraction, charging = batteryCharging)
        }

        ParticleOverlay(flows = flows, debugShowPaths = debugShowPaths)
    }
}

/** The animated current-flow particles only — kept separate so it can be timed/tested on its own. */
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

            val color = colorFor(path, flow)
            val count = EnergyFlowPathManager.particleCountFor(flow.powerKw)
            if (count <= 0) return@forEach

            val speed = EnergyFlowPathManager.particleSpeedFor(flow.powerKw)
            val signedPhase = if (flow.direction == FlowDirection.REVERSE) -elapsedSeconds * speed else elapsedSeconds * speed
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
