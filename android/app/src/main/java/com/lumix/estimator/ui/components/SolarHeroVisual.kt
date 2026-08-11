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

        val sunCenter = Offset(w * 0.5f, h * 0.22f)
        val sunRadius = 22.dp.toPx()

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

        // Roof
        val roofTop = h * 0.52f
        val roofBottom = h * 0.86f
        val roofLeft = w * 0.14f
        val roofRight = w * 0.86f
        val roofPeakY = h * 0.4f

        val roofPath = Path().apply {
            moveTo(w * 0.5f, roofPeakY)
            lineTo(roofRight, roofTop)
            lineTo(roofRight, roofBottom)
            lineTo(roofLeft, roofBottom)
            lineTo(roofLeft, roofTop)
            close()
        }
        drawPath(roofPath, color = palette.surfaceElevated)
        drawPath(roofPath, color = palette.outline, style = Stroke(width = 1.dp.toPx()))

        // Panels on the roof slope
        val panelCount = 6
        val panelW = (roofRight - roofLeft - 24.dp.toPx()) / panelCount
        val panelTop = roofTop + 10.dp.toPx()
        val panelBottom = roofBottom - 14.dp.toPx()
        repeat(panelCount) { i ->
            val litFraction = (clampedActivity * panelCount - i).coerceIn(0f, 1f)
            if (litFraction <= 0f) return@repeat
            val left = roofLeft + 12.dp.toPx() + i * panelW
            drawRoundRect(
                color = palette.technicalCyan.copy(alpha = 0.18f + 0.22f * litFraction),
                topLeft = Offset(left + 2.dp.toPx(), panelTop),
                size = Size(panelW - 4.dp.toPx(), panelBottom - panelTop),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
        }

        // Energy particles: sun -> roof ridge, for three staggered dots
        if (!reduceMotion && clampedActivity > 0.05f) {
            val target = Offset(w * 0.5f, roofPeakY)
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
