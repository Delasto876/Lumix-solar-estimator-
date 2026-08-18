package com.lumix.estimator.ui.simulation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.simulation.WeatherScenario
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixMotion
import com.lumix.estimator.ui.theme.LumixRadius
import kotlin.math.roundToInt

/**
 * A80 (spec Phase 17 §"WEATHER SCENARIO SELECTION" — "Instead of: 70% SUN / 100% SUN, use:
 * WEATHER SCENARIO with options Typical/Clearer-than-normal/Cloudier-than-normal/Rainy/Custom"):
 * a climatological framing, not a flat sun-percentage button — each chip picks which
 * [WeatherScenario] [com.lumix.estimator.domain.simulation.WeatherEngine] generates the day's
 * curve from, not a direct multiplier on PV output.
 */
private val WeatherScenario.glyph: String
    get() = when (this) {
        WeatherScenario.TYPICAL -> "⛅"
        WeatherScenario.CLEARER -> "☀️"
        WeatherScenario.CLOUDIER -> "☁️"
        WeatherScenario.RAINY -> "🌧️"
        WeatherScenario.CUSTOM -> "🎛️"
    }

@Composable
fun WeatherSelector(
    selected: WeatherScenario,
    onSelect: (WeatherScenario) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLumixPalette.current

    // A84 (spec Phase 17, original §44 "MOBILE RESPONSIVENESS" — "do not allow: buttons outside
    // viewport"): each chip's Column previously sized to its own unconstrained content width —
    // fine for short single-word labels, but WeatherScenario's real labels ("Clearer than
    // normal", "Cloudier than normal") could push 5 of them past the screen edge on a narrow
    // (Samsung A15-class) display, the same bottom-nav-clipping shape A16 already fixed once
    // elsewhere. `weight(1f)` forces all 5 into the row's actual available width, wrapping each
    // label onto a second line (Text wraps by default) rather than overflowing the viewport.
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        WeatherScenario.entries.forEach { scenario ->
            val isSelected = scenario == selected
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.12f else 1f,
                animationSpec = LumixMotion.snappy(),
                label = "weatherChipScale"
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .scale(scale)
                    .clip(RoundedCornerShape(LumixRadius.md))
                    .background(if (isSelected) palette.solarYellow.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(scenario) }
                    .padding(vertical = 8.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(scenario.glyph, style = MaterialTheme.typography.titleMedium)
                Text(
                    scenario.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) palette.solarYellowText else palette.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * A80 (spec Phase 17 §"SOLAR VARIABILITY SLIDER" — "Add a slider called SOLAR RESOURCE or SOLAR
 * CONDITIONS. Do NOT label it simply SUN %... This does NOT multiply every timestep by one
 * constant number. Instead it adjusts the weather/irradiance model while preserving sunrise/
 * sunset/solar curve/cloud events/day length/PSH relationship"): [deviation] is a fractional
 * shift ([-0.2, 0.2]) fed straight into [com.lumix.estimator.domain.simulation.WeatherEngine
 * .generate]'s own `solarConditionsDeviation` — that function applies it as a single scalar over
 * the *whole* generated curve (so the cloud events/shape it already built stay intact), not a
 * second independent per-timestep multiplier layered on top.
 */
@Composable
fun SolarConditionsSlider(
    deviation: Float,
    onDeviationChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLumixPalette.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Solar Conditions",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
            val label = when {
                deviation > 0.02f -> "+${(deviation * 100).roundToInt()}%"
                deviation < -0.02f -> "${(deviation * 100).roundToInt()}%"
                else -> "Typical"
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
        }
        Slider(
            value = deviation,
            onValueChange = onDeviationChange,
            valueRange = -0.2f..0.2f,
            colors = SliderDefaults.colors(thumbColor = palette.solarYellow, activeTrackColor = palette.solarYellow)
        )
        Text(
            "Models a deviation from the selected scenario's own climatological baseline — not a direct sun percentage.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )
    }
}
