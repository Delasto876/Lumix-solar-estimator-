package com.lumix.estimator.ui.wizard.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.ApplianceLoad
import com.lumix.estimator.domain.ApplianceType
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.ui.components.IntField
import com.lumix.estimator.ui.components.NumberField
import com.lumix.estimator.ui.components.SectionCard

@Composable
fun StepHouseholdAppliances(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "Appliances") {
            ApplianceType.entries.forEach { type ->
                val load = inputs.appliances[type] ?: ApplianceLoad()
                Text(type.label, style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IntField(
                        label = "Quantity",
                        value = load.qty,
                        onValueChange = { qty ->
                            onUpdate { inp ->
                                inp.copy(appliances = inp.appliances.toMutableMap().apply {
                                    this[type] = (this[type] ?: ApplianceLoad()).copy(qty = qty)
                                })
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        label = "Hours/day",
                        value = load.hours,
                        onValueChange = { hours ->
                            onUpdate { inp ->
                                inp.copy(appliances = inp.appliances.toMutableMap().apply {
                                    this[type] = (this[type] ?: ApplianceLoad()).copy(hours = hours)
                                })
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Text("Other loads", style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
