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
import com.lumix.estimator.domain.Catalog
import com.lumix.estimator.domain.ManualModeType
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.SystemMode
import com.lumix.estimator.ui.components.IntField
import com.lumix.estimator.ui.components.LabeledDropdown
import com.lumix.estimator.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step6Manual(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "Manual mode type") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ManualModeType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = inputs.manualModeType == type,
                        onClick = { onUpdate { it.copy(manualModeType = type) } },
                        shape = SegmentedButtonDefaults.itemShape(index, ManualModeType.entries.size)
                    ) {
                        Text(
                            when (type) {
                                ManualModeType.BATTERY_LED -> "Battery-led"
                                ManualModeType.PANEL_LED -> "Panel-led"
                                ManualModeType.FULL_MANUAL -> "Full manual"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Text(
                when (inputs.manualModeType) {
                    ManualModeType.BATTERY_LED -> "You set inverter & battery, the system sizes the panels."
                    ManualModeType.PANEL_LED -> "You set inverter & panels, the system sizes the battery."
                    ManualModeType.FULL_MANUAL -> "You set panels, inverter & battery."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard(title = "Inverter") {
            val noneLabel = "Let system choose inverter"
            LabeledDropdown(
                label = "Manual inverter (optional)",
                options = listOf("") + Catalog.manualInverters.map { it.id },
                selected = inputs.manualInverterId ?: "",
                optionLabel = { id -> if (id.isBlank()) noneLabel else Catalog.findManual(id)?.name ?: id },
                onSelected = { id ->
                    onUpdate {
                        if (id.isBlank()) {
                            it.copy(manualInverterId = null)
                        } else {
                            val invDef = Catalog.findManual(id)
                            it.copy(manualInverterId = id, systemMode = invDef?.mode ?: it.systemMode)
                        }
                    }
                }
            )
        }

        SectionCard(title = "Panels") {
            LabeledDropdown(
                label = "Panel wattage",
                options = Catalog.panelWattages,
                selected = inputs.manualPanelWatts,
                optionLabel = { "${it} W" },
                onSelected = { v -> onUpdate { it.copy(manualPanelWatts = v) } }
            )
            IntField(
                label = "Number of panels",
                value = inputs.manualPanelCount,
                onValueChange = { v -> onUpdate { it.copy(manualPanelCount = v) } },
                supportingText = "Rounded up to an even number automatically."
            )
        }

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
                IntField(
                    label = "15 kWh count",
                    value = inputs.manualBatt15k,
                    onValueChange = { v -> onUpdate { it.copy(manualBatt15k = v) } }
                )
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
    }
}
