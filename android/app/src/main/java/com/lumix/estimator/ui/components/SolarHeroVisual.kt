package com.lumix.estimator.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.rememberReduceMotion

/**
 * An ambient sun → panels → home illustration. [activity] (0..1) drives how "awake"
 * the scene looks — dim and mostly static with no quote yet, fully glowing with
 * flowing energy particles once a system is sized.
 *
 * A88 (spec Phase 26 §3/§4 — "sun positioned on the same side/direction as the solar panels...
 * modern residential house... realistic roof... Do not place the sun on the wrong side of the
 * house"): this is a Compose vector illustration, not a photorealistic photo — no image-generation
 * capability was available to produce the raster asset the phase actually describes (see the A88
 * README section for that disclosure). Within that constraint, this composable was reshaped from a
 * symmetric floating roof triangle into an actual house silhouette (roof + wall body + door) with
 * panels on only ONE roof slope and the sun positioned above that SAME slope — so "sun above/
 * behind the panel side" is now a real, checkable geometric relationship instead of an ambiguous
 * centered sun over a symmetric roof. The pulse/particle-flow animation timing itself is unchanged
 * from before this round (already a slow ~2.6s brighten/soften cycle, not a flash).
 */
@Composable
fun SolarHeroVisual(
    activity: Float,
    modifier: Modifier = Modifier
) {
    val palette = LocalLumixPalette.current
    val reduceMotion = rememberReduceMotion()
    val clampedActivity = activity.coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "solarHero")
    val pulse = if (reduceMotion) 0f else infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "sunPulse"
    ).value
    val flow = if (reduceMotion) 0f else infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "energyFlow"
    ).value

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        val w = size.width
        val h = size.height

        // A88: off-center, above the panel (right) slope of the roof — not centered over a
        // symmetric roof, so "sun on the panel side" is an actual geometric fact, not ambiguous.
        val sunCenter = Offset(w * 0.72f, h * 0.16f)
        val sunRadius = 20.dp.toPx()

        // Sun glow
        val glowScale = 1f + pulse * 0.12f
        drawCircle(
            color = palette.solarYellow.copy(alpha = 0.10f * (0.6f + 0.4f * clampedActivity)),
            radius = sunRadius * 3.2f * glowScale,
            center = sunCenter
        )
        drawCircle(
            color = palette.solarYellow.copy(alpha = 0.22f * (0.6f + 0.4f * clampedActivity)),
            radius = sunRadius * 1.9f * glowScale,
            center = sunCenter
        )
        drawCircle(
            color = palette.solarYellow.copy(alpha = 0.5f + 0.5f * clampedActivity),
            radius = sunRadius,
            center = sunCenter
        )

        // Roof (gable — two slopes) sitting on a simple house body, per A88's own doc above:
        // panels only go on the RIGHT slope (the same side the sun sits above).
        val roofTop = h * 0.48f
        val roofBottom = h * 0.72f
        val roofLeft = w * 0.14f
        val roofRight = w * 0.86f
        val roofPeakY = h * 0.34f
        val roofPeakX = w * 0.5f

        val roofPath = Path().apply {
            moveTo(roofPeakX, roofPeakY)
            lineTo(roofRight, roofTop)
            lineTo(roofRight, roofBottom)
            lineTo(roofLeft, roofBottom)
            lineTo(roofLeft, roofTop)
            close()
        }
        drawPath(roofPath, color = palette.surfaceElevated)
        drawPath(roofPath, color = palette.outline, style = Stroke(width = 1.dp.toPx()))

        // House wall body, below the roof eaves — what turns "a roof" into "a home."
        val wallTop = roofBottom - 2.dp.toPx()
        val wallBottom = h * 0.92f
        val wallLeft = w * 0.22f
        val wallRight = w * 0.78f
        drawRect(
            color = palette.surfaceElevated.copy(alpha = 0.7f),
            topLeft = Offset(wallLeft, wallTop),
            size = Size(wallRight - wallLeft, wallBottom - wallTop)
        )
        drawRect(
            color = palette.outline,
            topLeft = Offset(wallLeft, wallTop),
            size = Size(wallRight - wallLeft, wallBottom - wallTop),
            style = Stroke(width = 1.dp.toPx())
        )
        // Door, centered on the wall — a small, unambiguous "this is a house" cue.
        val doorW = (wallRight - wallLeft) * 0.16f
        val doorH = (wallBottom - wallTop) * 0.55f
        drawRoundRect(
            color = palette.outline.copy(alpha = 0.5f),
            topLeft = Offset(roofPeakX - doorW / 2f, wallBottom - doorH),
            size = Size(doorW, doorH),
            cornerRadius = CornerRadius(2.dp.toPx())
        )

        // Panels on the RIGHT roof slope only — the same slope the sun sits above.
        val panelCount = 4
        val panelSlopeLeft = roofPeakX + 6.dp.toPx()
        val panelSlopeRight = roofRight - 6.dp.toPx()
        val panelW = (panelSlopeRight - panelSlopeLeft) / panelCount
        val panelTop = roofTop + 8.dp.toPx()
        val panelBottom = roofBottom - 10.dp.toPx()
        repeat(panelCount) { i ->
            val litFraction = (clampedActivity * panelCount - i).coerceIn(0f, 1f)
            if (litFraction <= 0f) return@repeat
            val left = panelSlopeLeft + i * panelW
            drawRoundRect(
                color = palette.technicalCyan.copy(alpha = 0.18f + 0.22f * litFraction),
                topLeft = Offset(left + 2.dp.toPx(), panelTop),
                size = Size(panelW - 4.dp.toPx(), panelBottom - panelTop),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
        }

        // Energy particles: sun -> the panel slope (not the roof ridge), for three staggered
        // dots — reinforces SUN -> PANELS -> HOME along the same side the panels actually sit on.
        if (!reduceMotion && clampedActivity > 0.05f) {
            val target = Offset((panelSlopeLeft + panelSlopeRight) / 2f, (panelTop + panelBottom) / 2f)
            for (i in 0 until 3) {
                val phase = ((flow + i / 3f) % 1f)
                val pos = Offset(
                    x = sunCenter.x + (target.x - sunCenter.x) * phase,
                    y = sunCenter.y + (target.y - sunCenter.y) * phase
                )
                val alpha = (1f - phase) * 0.8f * clampedActivity
                drawCircle(color = palette.solarYellow.copy(alpha = alpha.coerceIn(0f, 1f)), radius = 3.dp.toPx(), center = pos)
            }
        }
    }
}
