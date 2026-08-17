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
import com.lumix.estimator.domain.JpsRate
import com.lumix.estimator.domain.MONTH_NAMES
import com.lumix.estimator.domain.PropertyType
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteMode
import com.lumix.estimator.domain.SolarResource
import com.lumix.estimator.domain.SystemMode
import com.lumix.estimator.domain.SystemTypeNew
import com.lumix.estimator.ui.components.LabeledDropdown
import com.lumix.estimator.ui.components.NumberField
import com.lumix.estimator.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepPropertySystem(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // A60 (spec §13–15): site location now lives here, in the design flow, because it drives
        // the Peak Sun Hours estimate PV sizing actually uses — not just a delivery/billing detail
        // (that's still asked again, pre-filled, in Create Quote's Customer step). Optional, same as
        // the rest of this step — never gates Calculate, per A56's own "no quote-adjacent field
        // blocks design" principle.
        SectionCard(title = "Site location") {
            LabeledDropdown(
                label = "Parish (optional)",
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
            NumberField(
                label = "Peak Sun Hours",
                value = inputs.peakSunHours,
                onValueChange = { v -> onUpdate { it.copy(peakSunHours = v, peakSunHoursManuallySet = true) } },
                suffix = "hrs",
                supportingText = if (inputs.parish.isBlank()) {
                    "Jamaica-wide default — not a measured, location-specific value. Select a parish above for a closer regional estimate, or edit directly."
                } else {
                    "Rough regional estimate for ${inputs.parish} based on general climate patterns, not measured satellite data. Edit directly if you know better for this exact site."
                },
                modifier = Modifier.padding(top = 4.dp)
            )
            // A80 (spec Phase 17 §"INSTALLATION MONTH" — "ask: which month is this system being
            // designed/installed for?"): optional, same as the rest of this step — leaving it
            // unset simply keeps the fixed annual-average day-length/weather assumption every
            // quote used before this field existed (see QuoteInputs.installMonth's own doc).
            // Deliberately does NOT affect equipment sizing, only simulation/evaluation — the
            // NumberField above is still what actually sizes the array.
            LabeledDropdown(
                label = "Installation month (optional)",
                options = listOf(0) + (1..12).toList(),
                selected = inputs.installMonth ?: 0,
                optionLabel = { monthIndex -> if (monthIndex == 0) "Not specified" else MONTH_NAMES[monthIndex - 1] },
                onSelected = { v -> onUpdate { it.copy(installMonth = v.takeIf { m -> m != 0 }) } }
            )
            if (inputs.installMonth != null) {
                Text(
                    "Simulation and recharge/backup checks will use ${MONTH_NAMES[inputs.installMonth!! - 1]}'s modeled Jamaica seasonal weather tendency instead of a flat annual average. This does not change panel/inverter/battery sizing.",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        SectionCard(title = "Property") {
            LabeledDropdown(
                label = "Property type",
                options = PropertyType.entries,
                selected = inputs.propertyType,
                optionLabel = { it.label },
                onSelected = { v -> onUpdate { it.copy(propertyType = v) } }
            )

            LabeledDropdown(
                label = "System type",
                options = SystemTypeNew.entries,
                selected = inputs.systemType,
                optionLabel = { if (it == SystemTypeNew.NEW) "New installation" else "Upgrade / Expansion" },
                onSelected = { v -> onUpdate { it.copy(systemType = v) } }
            )
        }

        SectionCard(title = "Solar system mode") {
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

            if (inputs.quoteMode == QuoteMode.GUIDED) {
                LabeledDropdown(
                    label = "JPS rate type",
                    options = JpsRate.entries,
                    selected = inputs.jpsRate,
                    optionLabel = {
                        when (it) {
                            JpsRate.RESIDENTIAL -> "Residential"
                            JpsRate.COMMERCIAL -> "Commercial / Business"
                            JpsRate.UNKNOWN -> "Not sure"
                        }
                    },
                    onSelected = { v -> onUpdate { it.copy(jpsRate = v) } }
                )
            }
        }
    }
}
