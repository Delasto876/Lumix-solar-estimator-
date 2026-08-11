package com.lumix.estimator.site

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumix.estimator.sensors.CompassMath
import com.lumix.estimator.ui.theme.LocalLumixPalette
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A compact "fixed dial, rotating needle" compass — N/E/S/W stay upright at their positions,
 * and the needle rotates to point toward [headingDegrees] (0 = north), the same convention
 * used by a real handheld compass. [headingDegrees] should already be true-north-corrected
 * (see [CompassManager]) when used for solar orientation.
 */
@Composable
fun SolarCompassBadge(
    headingDegrees: Double,
    modifier: Modifier = Modifier,
    diameter: Dp = 84.dp,
    label: String = "TRUE NORTH"
) {
    val palette = LocalLumixPalette.current

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(modifier = Modifier.size(diameter), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(diameter)) {
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                drawCircle(color = palette.surfaceElevated, radius = radius)
                drawCircle(color = palette.outline, radius = radius, style = Stroke(width = 2f))

                // Needle points toward true north relative to the current heading: if the
                // device faces east (heading=90), north is to the device's left, so the
                // needle rotates by -heading from "up".
                val needleAngleRad = Math.toRadians((-headingDegrees) - 90.0)
                val needleLength = radius * 0.72f
                val tip = Offset(
                    center.x + (needleLength * cos(needleAngleRad)).toFloat(),
                    center.y + (needleLength * sin(needleAngleRad)).toFloat()
                )
                val tail = Offset(
                    center.x - (needleLength * 0.5f * cos(needleAngleRad)).toFloat(),
                    center.y - (needleLength * 0.5f * sin(needleAngleRad)).toFloat()
                )
                drawLine(color = palette.warningRed, start = center, end = tip, strokeWidth = 4f)
                drawLine(color = palette.textSecondary, start = center, end = tail, strokeWidth = 3f)
                drawCircle(color = palette.solarYellow, radius = 4f, center = center)
            }

            CompassTick("N", Modifier.align(Alignment.TopCenter))
            CompassTick("S", Modifier.align(Alignment.BottomCenter))
            CompassTick("E", Modifier.align(Alignment.CenterEnd))
            CompassTick("W", Modifier.align(Alignment.CenterStart))
        }
        Text(
            "${normalizedHeading(headingDegrees).roundToInt()}° ${CompassMath.compassLabel(headingDegrees)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
    }
}

@Composable
private fun CompassTick(text: String, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = palette.textSecondary,
        modifier = modifier.padding(4.dp)
    )
}

private fun normalizedHeading(degrees: Double): Double {
    val d = degrees % 360.0
    return if (d < 0) d + 360.0 else d
}
