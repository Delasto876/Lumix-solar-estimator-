package com.lumix.estimator.ui.simulation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixColors
import kotlin.math.roundToInt

private val tickHours = listOf(0, 6, 9, 12, 15, 18, 21, 24)

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

/**
 * A horizontal scrub control for the simulated day, replacing the old circular time dial —
 * sits directly under the digital-twin scene rather than off to the side, so "drag to a time"
 * and "watch the scene react" are visually adjacent.
 */
@Composable
fun TimeSlider(
    hour: Double,
    onScrub: (Double) -> Unit,
    modifier: Modifier = Modifier,
    markerHour: Double? = null
) {
    val palette = LocalLumixPalette.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatSimTime(hour),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
            if (markerHour != null) {
                Text(
                    "🔋 Full at ${formatSimTime(markerHour)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.energyGreenText
                )
            }
        }

        Slider(
            value = hour.toFloat(),
            onValueChange = { onScrub(it.toDouble()) },
            valueRange = 0f..24f,
            colors = SliderDefaults.colors(
                thumbColor = LumixColors.SolarYellow,
                activeTrackColor = LumixColors.SolarYellow,
                inactiveTrackColor = palette.outline
            )
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            tickHours.forEach { h ->
                Text(
                    if (h == 24) "24:00" else "%02d:00".format(h),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary
                )
            }
        }
    }
}
