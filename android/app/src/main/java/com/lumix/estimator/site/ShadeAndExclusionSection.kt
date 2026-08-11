package com.lumix.estimator.site

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.site.geometry.ShadeObstructionType
import com.lumix.estimator.ui.components.NumberField
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixRadius

/**
 * Nearby-obstruction checkboxes let the caller suggest a starting shading estimate (never
 * computed from imagery alone — no elevation data is available), plus a direct override and a
 * lump-sum excluded-area field for obstructions actually sitting on the roof (chimney, tank,
 * vents). This composable only reports the toggle; deriving a suggested exposure from the new
 * selection is the caller's job (via [com.lumix.estimator.site.geometry.ShadeEstimator]), so
 * this stays pure UI with a single source of truth for state.
 */
@Composable
fun ShadeAndExclusionSection(
    selectedObstructions: Set<ShadeObstructionType>,
    onToggleObstruction: (ShadeObstructionType) -> Unit,
    exposurePercent: Double,
    onExposurePercentChange: (Double) -> Unit,
    excludedAreaM2: Double,
    onExcludedAreaChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLumixPalette.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Nearby obstructions (optional)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ShadeObstructionType.entries.forEach { type ->
                val selected = type in selectedObstructions
                Text(
                    type.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) palette.solarYellowText else palette.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(LumixRadius.pill))
                        .background(if (selected) palette.solarYellow.copy(alpha = 0.16f) else palette.glass)
                        .clickable { onToggleObstruction(type) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        Text(
            "Estimated shading — satellite imagery alone can't measure real shade without elevation data. Adjust by hand if this looks off.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )
        NumberField(
            label = "Estimated solar exposure",
            value = exposurePercent,
            onValueChange = { onExposurePercentChange(it.coerceIn(0.0, 100.0)) },
            suffix = "%"
        )
        NumberField(
            label = "Excluded area on roof (chimney, tank, vents, ...)",
            value = excludedAreaM2,
            onValueChange = onExcludedAreaChange,
            suffix = "m²"
        )
    }
}
