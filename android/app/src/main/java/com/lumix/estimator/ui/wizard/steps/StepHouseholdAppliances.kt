package com.lumix.estimator.ui.wizard.steps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.ApplianceLoad
import com.lumix.estimator.domain.ApplianceType
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.ui.components.IntField
import com.lumix.estimator.ui.components.NumberField
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette

/**
 * A29/A43: no longer asks for hours/day. Every appliance the installer selects gets a realistic
 * default operating schedule automatically — the exact same schedule/duty-cycle model
 * [com.lumix.estimator.domain.simulation.SimAppliance] uses for the simulation itself (via
 * [com.lumix.estimator.domain.simulation.defaultDailyEnergyKwh]), so the estimator's sizing and
 * the simulation's behavior can never disagree for the same appliance selection. An installer who
 * genuinely wants an exact figure can still enter one, behind ADVANCED — never the default path.
 */
@Composable
fun StepHouseholdAppliances(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    val palette = LocalLumixPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Select how many of each you have. We'll automatically estimate when they typically run, based on real Jamaican household use — no need to work out hours per day.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )

        SectionCard(title = "Appliances") {
            ApplianceType.entries.forEachIndexed { index, type ->
                val load = inputs.appliances[type] ?: ApplianceLoad()
                ApplianceRow(
                    type = type,
                    load = load,
                    onChange = { updated ->
                        onUpdate { inp -> inp.copy(appliances = inp.appliances.toMutableMap().apply { this[type] = updated }) }
                    }
                )
                if (index < ApplianceType.entries.size - 1) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }

        SectionCard(title = "Other loads") {
            Text(
                "Anything not listed above — combined wattage and how long it typically runs each day.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    label = "Total watts",
                    value = inputs.otherWatts,
                    onValueChange = { v -> onUpdate { it.copy(otherWatts = v) } },
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    label = "Hours/day",
                    value = inputs.otherHours,
                    onValueChange = { v -> onUpdate { it.copy(otherHours = v) } },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ApplianceRow(type: ApplianceType, load: ApplianceLoad, onChange: (ApplianceLoad) -> Unit) {
    val palette = LocalLumixPalette.current
    var advancedOpen by remember(type) { mutableStateOf(!load.useAutoSchedule) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(type.label, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
                Text("${type.watts} W estimated", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
            IntField(
                label = "Qty",
                value = load.qty,
                onValueChange = { qty -> onChange(load.copy(qty = qty)) },
                modifier = Modifier.weight(0.6f)
            )
        }

        if (load.qty > 0) {
            Text(
                if (advancedOpen) "ADVANCED ▾" else "ADVANCED ▸",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = palette.solarYellowText,
                modifier = Modifier.padding(top = 6.dp).clickable { advancedOpen = !advancedOpen }
            )
            if (advancedOpen) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    Text(
                        if (load.useAutoSchedule) "Using the automatic schedule below as a starting duration — enter your own to override it."
                        else "Using a manual hours/day figure instead of the automatic schedule.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(
                            label = "Hours/day (override)",
                            value = load.hours,
                            onValueChange = { h -> onChange(load.copy(hours = h, useAutoSchedule = false)) },
                            modifier = Modifier.weight(1f)
                        )
                        if (!load.useAutoSchedule) {
                            Text(
                                "Use automatic",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.solarYellowText,
                                modifier = Modifier.clickable { onChange(load.copy(useAutoSchedule = true)) }
                            )
                        }
                    }
                }
            }
        }
    }
}
