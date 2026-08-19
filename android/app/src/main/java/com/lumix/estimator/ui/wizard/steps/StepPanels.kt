package com.lumix.estimator.ui.wizard.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.Catalog
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.ui.components.IntField
import com.lumix.estimator.ui.components.LabeledDropdown
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette
import kotlin.math.floor

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
    val palette = LocalLumixPalette.current

    // 2026-08-19 (map Part 5, "reject or warn if the chosen count/layout doesn't physically
    // fit"): mirrors the cap SystemCalculator.kt already silently applies against a traced roof's
    // RoofConstraint (floor(maxCapacityKw*1000 / selected panel watts), evened down) — but surfaced
    // HERE, at the moment the installer actually picks the count, instead of only after the fact
    // via ResultsScreen's post-hoc RoofConstraintBanner. Non-blocking: MANUAL mode's own
    // "never silently replace the installer's equipment" rule (see StepSystemReview's own doc)
    // means this warns, it doesn't stop them — SystemCalculator still applies the real cap either
    // way, so the quote itself is never wrong even if this warning is ignored.
    val roofCapExceededBy = remember(inputs.roofConstraint, inputs.manualPanelWatts, inputs.manualPanelCount) {
        val constraint = inputs.roofConstraint ?: return@remember null
        val maxForSelectedPanel = floor((constraint.maxCapacityKw * 1000.0) / inputs.manualPanelWatts).toInt()
            .let { if (it % 2 == 1) it - 1 else it }
            .coerceAtLeast(0)
        val chosen = inputs.manualPanelCount
        if (chosen > maxForSelectedPanel) chosen - maxForSelectedPanel to maxForSelectedPanel else null
    }

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
        if (roofCapExceededBy != null) {
            val (_, maxForSelectedPanel) = roofCapExceededBy
            SectionCard(title = "") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚠", color = palette.warningRedText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("MORE PANELS THAN THE TRACED ROOF FITS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                }
                Text(
                    "${inputs.roofConstraint?.roofLabel ?: "The traced roof"} fits at most $maxForSelectedPanel × ${inputs.manualPanelWatts}W panels at this size — the quote will be capped to that count unless a larger roof is traced or fewer panels are chosen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
