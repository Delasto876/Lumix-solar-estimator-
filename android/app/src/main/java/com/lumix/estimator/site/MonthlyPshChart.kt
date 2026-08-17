package com.lumix.estimator.site

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumix.estimator.solar.ClearSkyPshEstimator
import com.lumix.estimator.ui.theme.LocalLumixPalette

private val monthLabels = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

/**
 * A 12-bar chart of estimated monthly Peak Sun Hours, built from [ClearSkyPshEstimator] — sun
 * geometry only, no real weather/aerosol data. The disclaimer beneath the chart is not optional
 * boilerplate: without it this reads exactly like a real irradiance dataset, which it isn't.
 */
@Composable
fun MonthlyPshChart(latitude: Double, longitude: Double, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    val monthly = remember(latitude, longitude) { ClearSkyPshEstimator.estimateMonthlyPsh(latitude, longitude) }
    val maxPsh = (monthly.maxOrNull() ?: 1.0).coerceAtLeast(1.0)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().height(90.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            monthly.forEachIndexed { index, value ->
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction = (value / maxPsh).toFloat().coerceIn(0.04f, 1f))
                            .background(
                                palette.solarYellow.copy(alpha = 0.78f),
                                RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                            )
                    )
                    Text(monthLabels[index], style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                }
            }
        }
        Text(
            "Estimated Peak Sun Hours by month — clear-sky geometry only (sun angle & day length), not measured weather data. Actual PSH will be lower due to clouds, humidity, and dust.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )
    }
}
