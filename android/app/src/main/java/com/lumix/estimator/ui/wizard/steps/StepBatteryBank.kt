package com.lumix.estimator.ui.wizard.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.SystemMode
import com.lumix.estimator.ui.components.IntField
import com.lumix.estimator.ui.components.NumberField
import com.lumix.estimator.ui.components.SectionCard

@Composable
fun StepBatteryBank(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (inputs.systemMode == SystemMode.HYBRID) {
            SectionCard(title = "Hybrid battery selection (LiFePO4)") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IntField(
                        label = "5 kWh count",
                        value = inputs.manualBatt5k,
                        onValueChange = { v -> onUpdate { it.copy(manualBatt5k = v) } },
                        modifier = Modifier.weight(1f)
                    )
                    IntField(
                        label = "10 kWh count",
                        value = inputs.manualBatt10k,
                        onValueChange = { v -> onUpdate { it.copy(manualBatt10k = v) } },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IntField(
                        label = "15 kWh count",
                        value = inputs.manualBatt15k,
                        onValueChange = { v -> onUpdate { it.copy(manualBatt15k = v) } },
                        modifier = Modifier.weight(1f)
                    )
                    IntField(
                        label = "16 kWh count",
                        value = inputs.manualBatt16k,
                        onValueChange = { v -> onUpdate { it.copy(manualBatt16k = v) } },
                        modifier = Modifier.weight(1f)
                    )
                }
                IntField(
                    label = "20 kWh count",
                    value = inputs.manualBatt20k,
                    onValueChange = { v -> onUpdate { it.copy(manualBatt20k = v) } }
                )
                Text(
                    "Custom capacity",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        label = "Capacity (kWh each)",
                        value = inputs.manualBattCustomKwh,
                        onValueChange = { v -> onUpdate { it.copy(manualBattCustomKwh = v.coerceAtLeast(0.0)) } },
                        suffix = "kWh",
                        modifier = Modifier.weight(1f)
                    )
                    IntField(
                        label = "Count",
                        value = inputs.manualBattCustomCount,
                        onValueChange = { v -> onUpdate { it.copy(manualBattCustomCount = v) } },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (inputs.systemMode == SystemMode.OFFGRID) {
            SectionCard(title = "Off-grid battery bank") {
                IntField(
                    label = "AGM 12V battery count",
                    value = inputs.manualAgmCount,
                    onValueChange = { v -> onUpdate { it.copy(manualAgmCount = v) } },
                    supportingText = "Each AGM module treated as ~2.4 kWh for sizing."
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Use automatic transfer switch", modifier = Modifier.weight(1f))
                    Switch(
                        checked = inputs.manualOffgridUseAutoTransfer,
                        onCheckedChange = { v -> onUpdate { it.copy(manualOffgridUseAutoTransfer = v) } }
                    )
                }
            }
        }

        if (inputs.systemMode == SystemMode.GRIDTIE) {
            SectionCard(title = "Battery bank") {
                Text(
                    "Grid-tie systems don't use a battery bank.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
