package com.lumix.estimator.ui.wizard.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.ui.components.IntField
import com.lumix.estimator.ui.components.NumberField
import com.lumix.estimator.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepAirConditioning(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "Air conditioning") {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Do you have AC units?", modifier = Modifier.weight(1f))
                Switch(
                    checked = inputs.ac.hasAc,
                    onCheckedChange = { v -> onUpdate { it.copy(ac = it.ac.copy(hasAc = v)) } }
                )
            }

            if (inputs.ac.hasAc) {
                listOf(9000, 12000, 18000, 24000).chunked(2).forEach { pair ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { btu ->
                            IntField(
                                label = "$btu BTU",
                                value = inputs.ac.counts[btu] ?: 0,
                                onValueChange = { qty ->
                                    onUpdate {
                                        it.copy(ac = it.ac.copy(counts = it.ac.counts.toMutableMap().apply { this[btu] = qty }))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Text("Schedule", style = MaterialTheme.typography.bodyMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(true, false).forEachIndexed { index, standard ->
                        SegmentedButton(
                            selected = inputs.ac.useStandardHours == standard,
                            onClick = { onUpdate { it.copy(ac = it.ac.copy(useStandardHours = standard)) } },
                            shape = SegmentedButtonDefaults.itemShape(index, 2)
                        ) {
                            Text(if (standard) "Automatic" else "Custom hours/day")
                        }
                    }
                }
                Text(
                    if (inputs.ac.useStandardHours) {
                        "Estimates a realistic evening-window schedule with thermostat cycling — the same model the simulation itself uses, not a flat guess."
                    } else {
                        "Enter an explicit hours/day figure for all AC units combined instead."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!inputs.ac.useStandardHours) {
                    NumberField(
                        label = "Custom AC hours/day (all units)",
                        value = inputs.ac.customHours,
                        onValueChange = { v -> onUpdate { it.copy(ac = it.ac.copy(customHours = v)) } },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
