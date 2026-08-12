package com.lumix.estimator.ui.simulation

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
import com.lumix.estimator.domain.simulation.SimFrame
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixColors
import kotlin.math.max

/** 24h Solar / Consumption / Battery-SOC curves, scrubbable and synced to the time dial. */
@Composable
fun EnergyGraph(
    timeline: List<SimFrame>,
    currentFrame: SimFrame,
    onScrub: (Double) -> Unit,
    modifier: Modifier = Modifier,
    graphHeight: Dp = 170.dp
) {
    if (timeline.isEmpty()) return
    val palette = LocalLumixPalette.current
    val maxPower = max(
        max(timeline.maxOf { it.pvKw }, timeline.maxOf { it.houseLoadKw }),
        timeline.maxOf { it.gridPowerKw }
    ).coerceAtLeast(0.1)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(graphHeight)
                .pointerInput(timeline) {
                    fun updateFromX(x: Float) {
                        val fraction = (x / size.width).coerceIn(0f, 1f)
                        onScrub(fraction * 24.0)
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { updateFromX(it.x) },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            updateFromX(change.position.x)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(graphHeight)) {
                val xStep = size.width / 24f
                val usableHeight = size.height * 0.88f
                val baseline = size.height

                fun yForPower(v: Double) = baseline - (v / maxPower * usableHeight).toFloat()
                fun yForPercent(p: Float) = baseline - (p / 100f * usableHeight)

                val solarPath = Path()
                val solarFill = Path()
                val loadPath = Path()
                val socPath = Path()
                val gridPath = Path()

                timeline.forEachIndexed { i, f ->
                    val x = (f.hour * xStep).toFloat()
                    val ySolar = yForPower(f.pvKw)
                    val yLoad = yForPower(f.houseLoadKw)
                    val ySoc = yForPercent(f.batterySocPercent)
                    // Grid draw is always >= 0 (import-only) — total of what's going to the
                    // house and/or charging the battery, matching the live GRID stat above.
                    val yGrid = yForPower(f.gridPowerKw)
                    if (i == 0) {
                        solarPath.moveTo(x, ySolar)
                        solarFill.moveTo(x, baseline)
                        solarFill.lineTo(x, ySolar)
                        loadPath.moveTo(x, yLoad)
                        socPath.moveTo(x, ySoc)
                        gridPath.moveTo(x, yGrid)
                    } else {
                        solarPath.lineTo(x, ySolar)
                        solarFill.lineTo(x, ySolar)
                        loadPath.lineTo(x, yLoad)
                        socPath.lineTo(x, ySoc)
                        gridPath.lineTo(x, yGrid)
                    }
                }
                solarFill.lineTo(size.width, baseline)
                solarFill.close()

                drawPath(solarFill, brush = Brush.verticalGradient(listOf(LumixColors.SolarYellow.copy(alpha = 0.22f), Color.Transparent)))
                drawPath(solarPath, color = LumixColors.SolarYellow, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                drawPath(loadPath, color = palette.textSecondary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                drawPath(
                    gridPath,
                    color = LumixColors.SolarAmber.copy(alpha = 0.85f),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 5f))
                    )
                )
                drawPath(
                    socPath,
                    color = LumixColors.TechnicalCyan.copy(alpha = 0.8f),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 6f))
                    )
                )

                val scrubX = (currentFrame.hour * xStep).toFloat()
                drawLine(
                    color = palette.solarYellow.copy(alpha = 0.4f),
                    start = Offset(scrubX, 0f),
                    end = Offset(scrubX, baseline),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            LegendItem(LumixColors.SolarYellow, "Solar")
            LegendItem(palette.textSecondary, "Consumption")
            LegendItem(LumixColors.SolarAmber, "Grid")
            LegendItem(LumixColors.TechnicalCyan, "Battery %")
        }

        Spacer(modifier = Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ReadoutStat("TIME", formatSimTime(currentFrame.hour))
            ReadoutStat("SOLAR", "%.2f kW".format(currentFrame.pvKw))
            ReadoutStat("LOAD", "%.2f kW".format(currentFrame.houseLoadKw))
            ReadoutStat("BATTERY", "${currentFrame.batterySocPercent.toInt()}%")
            ReadoutStat("GRID", "%.2f kW".format(currentFrame.gridPowerKw))
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    val palette = LocalLumixPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier.size(8.dp).clip(CircleShape).background(color)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
    }
}

@Composable
private fun ReadoutStat(label: String, value: String) {
    val palette = LocalLumixPalette.current
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
    }
}
