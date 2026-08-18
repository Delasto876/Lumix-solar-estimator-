package com.lumix.estimator.ui.wizard.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.Catalog
import com.lumix.estimator.domain.MONTH_NAMES
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.SolarResource
import com.lumix.estimator.domain.SystemMode
import com.lumix.estimator.ui.components.LabeledDropdown
import com.lumix.estimator.ui.components.NumberField
import com.lumix.estimator.ui.components.SectionCard

/**
 * A88 (spec Phase 26 §7 — "Each mode should begin with: PARISH / TOWN... §18 MODE WORKFLOW: HOME
 * -> CHOOSE DESIGN MODE -> ... -> LOCATION / PSH"): the first step every design mode (GUIDED/
 * LOAD-BASED/MANUAL) actually shares, split out of the old combined "Property & System" step
 * (`StepPropertySystem.kt`, now `StepSiteDetails.kt` for what's left) so location genuinely comes
 * first, on its own, per the phase's own explicit workflow diagram. PSH itself is never
 * hand-typed-as-a-fake-number by this screen — [SolarResource.estimatedPshFor] (the existing
 * engineering/weather engine) supplies the auto-filled starting value; the installer can still
 * override it, exactly as before.
 *
 * "Solar system mode" (Hybrid/Off-grid/Grid-tie) stays on THIS early step rather than moving to
 * the deferred Site Details step ([StepSiteDetails]): unlike property type/system type/JPS rate
 * (which don't feed [com.lumix.estimator.domain.SystemCalculator] at all), system mode is a
 * genuine engineering input — it decides whether MANUAL mode's battery step even shows a battery
 * bank (grid-tie systems have none) and whether the grid is treated as connectable at all. Per
 * this phase's own §29 rule ("if a UI problem appears to require an engineering change, STOP and
 * report it instead"), moving an engineering-load-bearing field to a screen that visits AFTER
 * calculation would be exactly that kind of problem — so it stays here, pre-calculation, alongside
 * location. See the A88 README section for the full disclosed reasoning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepLocation(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "Location") {
            LabeledDropdown(
                label = "Parish",
                options = Catalog.parishes,
                selected = inputs.parish.ifBlank { "Select parish" },
                optionLabel = { it },
                onSelected = { v ->
                    onUpdate {
                        val updated = it.copy(parish = v, nearestTown = "")
                        // Auto-fill PSH from the new parish's rough regional estimate — unless the
                        // installer already typed their own figure below, which always wins.
                        if (updated.peakSunHoursManuallySet) updated
                        else updated.copy(peakSunHours = SolarResource.estimatedPshFor(v))
                    }
                }
            )
            val towns = Catalog.parishTowns[inputs.parish].orEmpty()
            if (inputs.parish.isNotBlank()) {
                LabeledDropdown(
                    label = "Nearest town",
                    options = towns.ifEmpty { listOf("Select a parish first") },
                    selected = inputs.nearestTown.ifBlank { towns.firstOrNull() ?: "Select a parish first" },
                    optionLabel = { it },
                    onSelected = { v -> onUpdate { it.copy(nearestTown = v) } }
                )
            }
            if (inputs.parish.isNotBlank()) {
                Text(
                    "${inputs.parish.uppercase()}${if (inputs.nearestTown.isNotBlank()) ", ${inputs.nearestTown}" else ""}\nJAMAICA",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            NumberField(
                label = "Estimated PSH (Peak Sun Hours)",
                value = inputs.peakSunHours,
                onValueChange = { v -> onUpdate { it.copy(peakSunHours = v, peakSunHoursManuallySet = true) } },
                suffix = "hrs/day",
                supportingText = if (inputs.parish.isBlank()) {
                    "Jamaica-wide default — not a measured, location-specific value. Select a parish above for a closer regional estimate, or edit directly."
                } else {
                    "Rough regional estimate for ${inputs.parish} based on general climate patterns, not measured satellite data. Edit directly if you know better for this exact site."
                },
                modifier = Modifier.padding(top = 4.dp)
            )
            // A80 (spec Phase 17 §"INSTALLATION MONTH"): optional — leaving it unset keeps the
            // fixed annual-average day-length/weather assumption. Does NOT affect equipment
            // sizing, only simulation/evaluation — the PSH field above is what actually sizes the array.
            LabeledDropdown(
                label = "Installation month (optional)",
                options = listOf(0) + (1..12).toList(),
                selected = inputs.installMonth ?: 0,
                optionLabel = { monthIndex -> if (monthIndex == 0) "Not specified" else MONTH_NAMES[monthIndex - 1] },
                onSelected = { v -> onUpdate { it.copy(installMonth = v.takeIf { m -> m != 0 }) } }
            )
        }

        SectionCard(title = "System mode") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SystemMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = inputs.systemMode == mode,
                        onClick = { onUpdate { it.copy(systemMode = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index, SystemMode.entries.size)
                    ) {
                        Text(
                            when (mode) {
                                SystemMode.HYBRID -> "Hybrid"
                                SystemMode.OFFGRID -> "Off-grid"
                                SystemMode.GRIDTIE -> "Grid-tie"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
