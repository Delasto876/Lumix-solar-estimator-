package com.lumix.estimator.ui.components

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
import androidx.compose.ui.unit.sp
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.numberDisplayStyle

/**
 * A slider fronted by its own big animated value readout, so dragging feels like
 * directly manipulating the number rather than a separate control next to it.
 */
@Composable
fun LumixValueSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    modifier: Modifier = Modifier,
    label: String? = null,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val palette = LocalLumixPalette.current

    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
        }
        Text(
            format(value),
            style = numberDisplayStyle(size = 34.sp),
            color = palette.textPrimary
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = palette.solarYellow,
                activeTrackColor = palette.solarYellow,
                inactiveTrackColor = palette.outline
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(format(valueRange.start), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            Text(format(valueRange.endInclusive), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        }
    }
}
