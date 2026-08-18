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

/**
 * A88 (spec Phase 26 §11 — MANUAL mode: "Then: PANELS. Panel model, Panel quantity."): the other
 * half of the old combined `StepInverterPanels.kt` — see [StepInverter]'s own doc for why they're
 * now separate steps. Panel model/quantity/PV kWp itself is NOT shown to the installer as a
 * dedicated "PV Configuration" screen elsewhere (§2 of this same phase removes that concept from
 * the workflow) — this IS the one place MANUAL mode's installer actually picks panels; GUIDED/LOAD
 * modes never see this step at all (the engineering engine picks panels for them).
 */
@Composable
fun StepPanels(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
