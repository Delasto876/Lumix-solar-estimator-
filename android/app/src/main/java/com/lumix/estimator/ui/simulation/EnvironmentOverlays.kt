package com.lumix.estimator.ui.simulation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
