package com.lumix.estimator.ui.simulation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.simulation.WeatherState
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixMotion
import com.lumix.estimator.ui.theme.LumixRadius

@Composable
fun WeatherSelector(
    selected: WeatherState,
    onSelect: (WeatherState) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLumixPalette.current

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        WeatherState.entries.forEach { weather ->
            val isSelected = weather == selected
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.12f else 1f,
                animationSpec = LumixMotion.snappy(),
                label = "weatherChipScale"
            )
            Column(
                modifier = Modifier
                    .scale(scale)
                    .clip(RoundedCornerShape(LumixRadius.md))
                    .background(if (isSelected) palette.solarYellow.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(weather) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(weather.glyph, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${(weather.multiplier * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) palette.solarYellowText else palette.textSecondary
                )
            }
        }
    }
}
