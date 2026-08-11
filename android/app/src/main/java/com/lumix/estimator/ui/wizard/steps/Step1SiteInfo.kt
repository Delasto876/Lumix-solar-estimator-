package com.lumix.estimator.ui.wizard.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.lumix.estimator.domain.PropertyType
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteMode
import com.lumix.estimator.domain.SystemMode
import com.lumix.estimator.domain.SystemTypeNew
import com.lumix.estimator.ui.components.LabeledDropdown
import com.lumix.estimator.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step1SiteInfo(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "Quote mode") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                QuoteMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = inputs.quoteMode == mode,
                        onClick = { onUpdate { it.copy(quoteMode = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index, QuoteMode.entries.size)
                    ) {
                        Text(
                            when (mode) {
                                QuoteMode.GUIDED -> "Guided"
                                QuoteMode.MANUAL -> "Manual"
                                QuoteMode.LOAD -> "Load-based"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        SectionCard(title = "Site info") {
            LabeledDropdown(
                label = "Property type",
                options = PropertyType.entries,
                selected = inputs.propertyType,
                optionLabel = { it.label },
                onSelected = { v -> onUpdate { it.copy(propertyType = v) } }
            )

            LabeledDropdown(
                label = "Parish",
                options = Catalog.parishes,
                selected = inputs.parish.ifBlank { "Select parish" },
                optionLabel = { it },
                onSelected = { v -> onUpdate { it.copy(parish = v, nearestTown = "") } }
            )

            val towns = Catalog.parishTowns[inputs.parish].orEmpty()
            LabeledDropdown(
                label = "Nearest town",
                options = towns.ifEmpty { listOf("Select a parish first") },
                selected = inputs.nearestTown.ifBlank { towns.firstOrNull() ?: "Select a parish first" },
                optionLabel = { it },
                onSelected = { v -> onUpdate { it.copy(nearestTown = v) } },
                supportingText = "Used for delivery & internal records."
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
