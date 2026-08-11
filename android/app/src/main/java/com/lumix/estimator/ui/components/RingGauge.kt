package com.lumix.estimator.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixMotion
import com.lumix.estimator.ui.theme.numberDisplayStyle
import kotlin.math.roundToInt

/**
 * An animated ring showing an estimated coverage/progress percentage, with the
 * value counting up inside it — used for "solar coverage" and similar derived stats.
 */
@Composable
fun RingGauge(
    percent: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 148.dp,
    strokeWidth: Dp = 14.dp,
    trackColor: Color = LocalLumixPalette.current.outline,
    progressColor: Color = LocalLumixPalette.current.solarYellow,
    label: String? = null
) {
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(percent) {
        animatable.animateTo(percent.coerceIn(0f, 100f), animationSpec = LumixMotion.responsive())
    }

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2
            val arcSize = Size(size.width - strokeWidth.toPx(), size.height - strokeWidth.toPx())
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * (animatable.value / 100f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke
            )
        }
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${animatable.value.roundToInt()}%",
                    style = numberDisplayStyle(size = 28.sp),
                    color = LocalLumixPalette.current.textPrimary
                )
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalLumixPalette.current.textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
