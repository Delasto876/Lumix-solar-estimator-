package com.lumix.estimator.ui.simulation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumix.estimator.domain.simulation.SimApplianceType
import com.lumix.estimator.ui.components.AnimatedCounterText
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.numberDisplayStyle

@Composable
fun AppliancesSheetContent(
    appliances: Map<SimApplianceType, Boolean>,
    onToggle: (SimApplianceType) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLumixPalette.current
    val currentLoadKw = appliances.filterValues { it }.keys.sumOf { it.watts } / 1000.0

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            "APPLIANCES",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = palette.textSecondary
        )

        Text("CURRENT LOAD", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, modifier = Modifier.padding(top = 12.dp))
        AnimatedCounterText(
            targetValue = currentLoadKw,
            format = { "%.2f kW".format(it) },
            style = numberDisplayStyle(size = 34.sp),
            color = palette.solarYellowText
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        SimApplianceType.entries.forEach { type ->
            val isOn = appliances[type] ?: false
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(type) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(type.label, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
                    Text("${type.watts} W", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                }
                Checkbox(
                    checked = isOn,
                    onCheckedChange = { onToggle(type) },
                    colors = CheckboxDefaults.colors(checkedColor = palette.solarYellow, checkmarkColor = palette.background)
                )
            }
        }
    }
}
