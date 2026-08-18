package com.lumix.estimator.ui.wizard.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.Catalog
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.ui.components.LabeledDropdown
import com.lumix.estimator.ui.components.SectionCard

/**
 * A88 (spec Phase 26 §11 — MANUAL mode: "Then: INVERTER. Select inverter from the approved
 * equipment list."): split out of the old combined `StepInverterPanels.kt` so Battery -> Inverter
 * -> Panels are genuinely separate steps, matching the phase's own literal MANUAL flow order —
 * see [StepPanels] for the other half.
 */
@Composable
fun StepInverter(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
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
    }
}
