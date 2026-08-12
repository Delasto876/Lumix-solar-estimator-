package com.lumix.estimator.ui.simulation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val tickHours = listOf(0, 3, 6, 9, 12, 15, 18, 21)
private const val SUNRISE = 5.5
private const val SUNSET = 18.5

fun formatSimTime(hour: Double): String {
    val totalMinutes = (hour * 60).roundToInt().mod(24 * 60)
    val h24 = totalMinutes / 60
    val m = totalMinutes % 60
    val amPm = if (h24 < 12) "AM" else "PM"
    val h12 = when {
        h24 == 0 -> 12
        h24 > 12 -> h24 - 12
        else -> h24
    }
    return "%d:%02d %s".format(h12, m, amPm)
}

// Noon at the top (12 o'clock), midnight at the bottom (6 o'clock) — matching the intuitive
// "sun is highest at the top" mental model, and consistent with the separate SunIndicator
// overlay's sunrise-on-the-left/sunset-on-the-right convention (verified: hourToAngleDeg(6)
// lands on the dial's left side, hourToAngleDeg(18) on the right). Previously mapped midnight
// to the top and noon to the bottom, which read as the sun dipping down at its actual peak.
private fun hourToAngleDeg(hour: Double): Double = ((hour - 12.0) / 24.0 * 360.0).mod(360.0)

private fun angleToOffset(angleDeg: Double, radius: Float, center: Offset): Offset {
    val rad = Math.toRadians(angleDeg - 90.0)
    return Offset(center.x + radius * cos(rad).toFloat(), center.y + radius * sin(rad).toFloat())
}

@Composable
fun TimeDial(
    hour: Double,
    onScrub: (Double) -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 240.dp,
    markerHour: Double? = null,
    markerColor: Color = LumixColors.EnergyGreen
) {
    val palette = LocalLumixPalette.current

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .size(diameter)
                .pointerInput(Unit) {
                    fun updateFromOffset(pos: Offset) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dx = pos.x - center.x
                        val dy = pos.y - center.y
                        val angleDeg = (Math.toDegrees(atan2(dy, dx).toDouble()) + 90.0).mod(360.0)
                        onScrub(angleDeg / 360.0 * 24.0)
                    }
                    detectDragGestures(
                        onDragStart = { updateFromOffset(it) },
                        onDrag = { change, _ ->
                            change.consume()
                            updateFromOffset(change.position)
                        }
                    )
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val ringRadius = size.minDimension / 2f - 20.dp.toPx()

            drawCircle(
                color = palette.outline,
                radius = ringRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // Daylight arc
            val sweepStart = hourToAngleDeg(SUNRISE).toFloat() - 90f
            val sweepAngle = ((SUNSET - SUNRISE) / 24.0 * 360.0).toFloat()
            drawArc(
                color = LumixColors.SolarYellow.copy(alpha = 0.35f),
                startAngle = sweepStart,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                size = Size(ringRadius * 2, ringRadius * 2),
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            )

            tickHours.forEach { h ->
                val pos = angleToOffset(hourToAngleDeg(h.toDouble()), ringRadius, center)
                drawCircle(palette.textSecondary.copy(alpha = 0.5f), radius = 2.dp.toPx(), center = pos)
            }

            if (markerHour != null) {
                val markerPos = angleToOffset(hourToAngleDeg(markerHour), ringRadius, center)
                drawCircle(markerColor.copy(alpha = 0.3f), radius = 9.dp.toPx(), center = markerPos)
                drawCircle(markerColor, radius = 4.5.dp.toPx(), center = markerPos)
            }

            // Handle line + dot
            val handlePos = angleToOffset(hourToAngleDeg(hour), ringRadius, center)
            drawLine(
                color = LumixColors.SolarYellow.copy(alpha = 0.4f),
                start = center,
                end = handlePos,
                strokeWidth = 1.5.dp.toPx()
            )
            drawCircle(LumixColors.SolarYellow.copy(alpha = 0.25f), radius = 12.dp.toPx(), center = handlePos)
            drawCircle(LumixColors.SolarYellow, radius = 7.dp.toPx(), center = handlePos)
            drawCircle(Color(0xFF221A00), radius = 3.dp.toPx(), center = handlePos)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatSimTime(hour),
                style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = palette.textPrimary
            )
            Text(
                "SIMULATED DAY",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary
            )
        }
    }
}
