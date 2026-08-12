package com.lumix.estimator.ui.simulation

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.lumix.estimator.ui.theme.rememberReduceMotion
import kotlin.math.PI
import kotlin.math.sin

/**
 * A soft, semi-transparent cloud layer over the sky portion of the house image. Never
 * touches the underlying photo — purely an additive Canvas layer whose opacity tracks
 * [coverageFraction] (0f = clear sky, 1f = fully overcast), driven by the simulation's
 * weather multiplier.
 */
@Composable
fun CloudOverlay(coverageFraction: Float, modifier: Modifier = Modifier) {
    if (coverageFraction <= 0.01f) return
    val coverage = coverageFraction.coerceIn(0f, 1f)

    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(IMAGE_ASPECT_RATIO)) {
        val w = size.width
        val h = size.height
        val blobs = listOf(
            Triple(0.18f, 0.10f, 0.20f),
            Triple(0.45f, 0.06f, 0.24f),
            Triple(0.68f, 0.13f, 0.18f),
            Triple(0.85f, 0.08f, 0.15f)
        )
        blobs.forEach { (nx, ny, nRadius) ->
            val center = Offset(nx * w, ny * h)
            val radius = nRadius * w
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.55f * coverage), Color.White.copy(alpha = 0f)),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
    }
}

/**
 * A small glowing sun marker tracing a low arc across the sky, present only while
 * [intensityFraction] is above zero (i.e. during daylight hours). [progressFraction] is
 * 0f at sunrise and 1f at sunset — both come from the same irradiance model the engine
 * uses, so the marker's position and the actual solar output it represents never disagree.
 */
@Composable
fun SunIndicator(progressFraction: Float, intensityFraction: Float, modifier: Modifier = Modifier) {
    if (intensityFraction <= 0.01f) return
    val progress = progressFraction.coerceIn(0f, 1f)
    val intensity = intensityFraction.coerceIn(0f, 1f)

    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(IMAGE_ASPECT_RATIO)) {
        val w = size.width
        val h = size.height
        val nx = 0.08f + progress * 0.84f
        val arcY = 0.14f - 0.09f * sin(PI.toFloat() * progress)
        val center = Offset(nx * w, arcY * h)
        val glowRadius = (0.02f + intensity * 0.045f) * w

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFF3C4).copy(alpha = 0.85f * intensity),
                    Color(0xFFFFD84D).copy(alpha = 0.25f * intensity),
                    Color(0xFFFFD84D).copy(alpha = 0f)
                ),
                center = center,
                radius = glowRadius * 2.6f
            ),
            radius = glowRadius * 2.6f,
            center = center
        )
        drawCircle(color = Color(0xFFFFF7DE).copy(alpha = 0.95f * intensity), radius = glowRadius, center = center)
    }
}

/**
 * A translucent liquid-level wash rising within the battery unit's printed footprint
 * ([SolarSimulationPaths.batteryBounds]), representing live state of charge. The physical
 * battery artwork itself is untouched — this only adds a soft color wash and a bright
 * fill-line, so it reads as a live gauge rather than a redrawn object.
 */
@Composable
fun BatteryFillOverlay(fillFraction: Float, charging: Boolean, modifier: Modifier = Modifier) {
    val fill = fillFraction.coerceIn(0f, 1f)
    val color = if (charging) Color(0xFF63E6A5) else Color(0xFF58C7FF)

    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(IMAGE_ASPECT_RATIO)) {
        val w = size.width
        val h = size.height
        val bounds = SolarSimulationPaths.batteryBounds
        val left = bounds.left * w
        val right = bounds.right * w
        val top = bounds.top * h
        val bottom = bounds.bottom * h
        val fillTop = bottom - (bottom - top) * fill

        drawRect(
            color = color.copy(alpha = 0.22f),
            topLeft = Offset(left, fillTop),
            size = Size(right - left, bottom - fillTop)
        )
        drawLine(
            color = color.copy(alpha = 0.9f),
            start = Offset(left, fillTop),
            end = Offset(right, fillTop),
            strokeWidth = 2.5f
        )
    }
}

private const val RAIN_DROP_COUNT = 46
private const val RAIN_FALL_SPEED = 1.4f // phase units (full canvas heights) per second

/**
 * A full-scene lighting/precipitation wash over the *entire* photo — ground, walls and roof,
 * not just the sky — so the house actually reads as sunny, overcast, stormy or after-dark
 * rather than just showing a dim sun icon while the walls stay daylight-bright underneath it.
 *
 * [daylightFactor] is the engine's own undamped irradiance curve (0f outside daylight hours,
 * rising to ~1f at midday) so darkness always tracks real solar output, never disagrees with
 * it, and fades in smoothly around sunrise/sunset rather than snapping at the edges. Weather's
 * overcast wash ([cloudCoverage]) is applied independently so a cloudy midday and a clear
 * night read as visibly different, not just "the same darkness."
 */
@Composable
fun SceneAtmosphereOverlay(
    daylightFactor: Float,
    cloudCoverage: Float,
    isStorm: Boolean,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotion()
    var rainPhase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isStorm, reduceMotion) {
        if (!isStorm || reduceMotion) return@LaunchedEffect
        var lastNanos = -1L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos >= 0L) {
                    val deltaSeconds = (nanos - lastNanos) / 1_000_000_000f
                    rainPhase = EnergyFlowPathManager.wrapUnit(rainPhase + deltaSeconds * RAIN_FALL_SPEED)
                }
                lastNanos = nanos
            }
        }
    }

    val night = (1f - daylightFactor.coerceIn(0f, 1f))
    val overcast = cloudCoverage.coerceIn(0f, 1f)
    if (night <= 0.02f && overcast <= 0.02f && !isStorm) return

    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(IMAGE_ASPECT_RATIO)) {
        val w = size.width
        val h = size.height

        // Overcast/low-sunlight grey-down — independent of time of day, so a cloudy noon
        // dims the scene without pretending it's dark out.
        if (overcast > 0.02f) {
            drawRect(color = Color(0xFF3B434D).copy(alpha = overcast * 0.30f))
        }

        // Dusk-to-night color grade: cools and darkens as daylightFactor falls toward zero.
        if (night > 0.02f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF060B21).copy(alpha = night * 0.72f),
                        Color(0xFF0C1738).copy(alpha = night * 0.55f)
                    )
                )
            )
        }

        if (isStorm) {
            drawRect(color = Color(0xFF1B2530).copy(alpha = 0.22f))
            val dropLength = 0.028f * h
            val slant = 0.012f * w
            repeat(RAIN_DROP_COUNT) { i ->
                val seedX = (i * 0.0731f).mod(1f)
                val seedOffset = (i * 0.171f).mod(1f)
                val x = seedX * w
                val y = (seedOffset + rainPhase).mod(1f) * (h + dropLength) - dropLength
                drawLine(
                    color = Color(0xFFBFD4E8).copy(alpha = 0.35f),
                    start = Offset(x, y),
                    end = Offset(x - slant, y + dropLength),
                    strokeWidth = 1.6f
                )
            }
        }
    }
}
