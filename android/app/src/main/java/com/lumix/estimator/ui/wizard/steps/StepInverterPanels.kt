package com.lumix.estimator.ui.wizard.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.Catalog
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.ui.components.IntField
import com.lumix.estimator.ui.components.LabeledDropdown
import com.lumix.estimator.ui.components.SectionCard

@Composable
fun StepInverterPanels(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
    }
}
