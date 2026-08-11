package com.lumix.estimator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumix.estimator.domain.SavingsYearPoint
import com.lumix.estimator.domain.formatCurrency
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.numberDisplayStyle
import kotlin.math.roundToInt

/**
 * Cost-without-solar vs cost-with-solar across the projection years, scrubbable
 * by horizontal drag. The filled line is what the system is estimated to save.
 */
@Composable
fun SavingsGraph(
    yearly: List<SavingsYearPoint>,
    modifier: Modifier = Modifier,
    graphHeight: Dp = 200.dp
) {
    if (yearly.isEmpty()) return
    val palette = LocalLumixPalette.current
    var selectedIndex by remember(yearly) { mutableIntStateOf(yearly.lastIndex) }
    val selected = yearly[selectedIndex.coerceIn(0, yearly.lastIndex)]

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    "Year ${selected.year}",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary
                )
                AnimatedCounterText(
                    targetValue = selected.cumulativeSavings,
                    format = { formatCurrency(it) },
                    style = numberDisplayStyle(size = 26.sp),
                    color = palette.textPrimary
                )
                Text(
                    "estimated cumulative savings",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val maxValue = (yearly.maxOfOrNull { it.costWithoutSolar } ?: 1.0).coerceAtLeast(1.0)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(graphHeight)
                .pointerInput(yearly) {
                    fun updateFromX(x: Float) {
                        val fraction = (x / size.width).coerceIn(0f, 1f)
                        selectedIndex = (fraction * (yearly.size - 1)).roundToInt()
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> updateFromX(offset.x) },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            updateFromX(change.position.x)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(graphHeight)) {
                val xStep = if (yearly.size > 1) size.width / (yearly.size - 1) else size.width
                val usableHeight = size.height * 0.85f
                val baseline = size.height

                fun yFor(value: Double): Float = baseline - (value / maxValue * usableHeight).toFloat()

                val withoutPath = Path()
                val withPath = Path()
                val fillPath = Path()

                yearly.forEachIndexed { i, point ->
                    val x = i * xStep
                    val yWithout = yFor(point.costWithoutSolar)
                    val yWith = yFor(point.costWithSolar)
                    if (i == 0) {
                        withoutPath.moveTo(x, yWithout)
                        withPath.moveTo(x, yWith)
                        fillPath.moveTo(x, baseline)
                        fillPath.lineTo(x, yWith)
                    } else {
                        withoutPath.lineTo(x, yWithout)
                        withPath.lineTo(x, yWith)
                        fillPath.lineTo(x, yWith)
                    }
                }
                fillPath.lineTo((yearly.size - 1) * xStep, baseline)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        listOf(palette.energyGreen.copy(alpha = 0.28f), Color.Transparent)
                    )
                )
                drawPath(
                    path = withoutPath,
                    color = palette.textSecondary.copy(alpha = 0.5f),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
                    )
                )
                drawPath(
                    path = withPath,
                    color = palette.energyGreen,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                val selX = selectedIndex * xStep
                drawLine(
                    color = palette.solarYellow.copy(alpha = 0.5f),
                    start = Offset(selX, 0f),
                    end = Offset(selX, baseline),
                    strokeWidth = 1.5.dp.toPx()
                )
                val selY = yFor(selected.costWithSolar)
                drawCircle(color = palette.solarYellow, radius = 5.dp.toPx(), center = Offset(selX, selY))
                drawCircle(
                    color = palette.background,
                    radius = 2.dp.toPx(),
                    center = Offset(selX, selY)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            LegendDot(color = palette.textSecondary.copy(alpha = 0.6f), label = "Without solar")
            LegendDot(color = palette.energyGreen, label = "With solar")
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalLumixPalette.current.textSecondary, fontWeight = FontWeight.Medium)
    }
}
