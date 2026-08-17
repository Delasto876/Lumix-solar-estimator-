package com.lumix.estimator.site

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.lumix.estimator.solar.SolarPathSampler
import com.lumix.estimator.solar.SolarPosition
import com.lumix.estimator.ui.theme.LocalLumixPalette
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Today's sun path at this location — azimuth (x, compass direction) vs. elevation (y, height
 * above horizon) — with the roof's own facing direction overlaid so it's immediately visible
 * whether the sun's daily arc actually crosses the roof's orientation. Built on
 * [SolarPathSampler], the same Phase 2 sun-position math the digital twin's "Sun & Roof" card
 * uses, so this diagram and that live readout can never disagree.
 */
@Composable
fun SunPathDiagram(
    latitude: Double,
    longitude: Double,
    roofAzimuthDegrees: Double?,
    modifier: Modifier = Modifier
) {
    val palette = LocalLumixPalette.current
    val zone = remember { ZoneOffset.of("-05:00") }
    val today = remember { LocalDate.now() }
    val path = remember(latitude, longitude, today) {
        SolarPathSampler.sampleDaylightPath(latitude, longitude, today, zone)
    }

    Column(modifier = modifier) {
        if (path.size < 2) {
            Text(
                "Sun stays below the horizon all day at this location on this date.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary
            )
            return@Column
        }

        val minAz = path.minOf { it.azimuthDegrees }
        val maxAz = path.maxOf { it.azimuthDegrees }
        // Near the tropics in high-declination months the daylight path can straddle both ends
        // of the 0/360 compass boundary (sunrise just past north, sunset just before it); a huge
        // naive span means that's happening, so fall back to the full circle rather than a
        // stretched, mostly-empty window.
        val wraps = (maxAz - minAz) > 270.0
        val loAz = if (wraps) 0.0 else (minAz - 12.0).coerceAtLeast(0.0)
        val hiAz = if (wraps) 360.0 else (maxAz + 12.0).coerceAtMost(360.0)
        val azSpan = (hiAz - loAz).coerceAtLeast(1.0)

        val maxElevation = path.maxOf { it.elevationDegrees }
        val topElevation = (maxElevation + 8.0).coerceAtMost(90.0).coerceAtLeast(20.0)

        Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
            fun toOffset(position: SolarPosition): Offset = Offset(
                (((position.azimuthDegrees - loAz) / azSpan) * size.width).toFloat(),
                (size.height - (position.elevationDegrees / topElevation) * size.height).toFloat()
            )

            drawLine(
                color = palette.outline,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 2f
            )

            if (roofAzimuthDegrees != null && roofAzimuthDegrees in loAz..hiAz) {
                val x = (((roofAzimuthDegrees - loAz) / azSpan) * size.width).toFloat()
                drawLine(
                    color = palette.technicalCyan,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )
            }

            val arc = Path().apply {
                path.forEachIndexed { index, position ->
                    val p = toOffset(position)
                    if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
            }
            drawPath(arc, color = palette.solarYellow, style = Stroke(width = 4f))

            drawCircle(color = palette.solarYellow, radius = 5f, center = toOffset(path.first()))
            drawCircle(color = palette.solarYellow, radius = 5f, center = toOffset(path.last()))
            val peak = path.maxBy { it.elevationDegrees }
            drawCircle(color = palette.solarAmber, radius = 6f, center = toOffset(peak))
        }

        val timeFormat = remember { DateTimeFormatter.ofPattern("h:mm a") }
        val sunrise = path.first().sunrise?.format(timeFormat) ?: "—"
        val sunset = path.first().sunset?.format(timeFormat) ?: "—"
        Text(
            "Sunrise $sunrise · Peak ${maxElevation.toInt()}° elevation · Sunset $sunset" +
                if (roofAzimuthDegrees != null) " · roof facing marked in cyan" else "",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )
    }
}
