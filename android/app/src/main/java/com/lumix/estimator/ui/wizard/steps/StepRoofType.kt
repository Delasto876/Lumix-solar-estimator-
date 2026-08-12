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
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.RoofType
import com.lumix.estimator.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepRoofType(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "Roof type") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                RoofType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = inputs.roofType == type,
                        onClick = { onUpdate { it.copy(roofType = type) } },
                        shape = SegmentedButtonDefaults.itemShape(index, RoofType.entries.size)
                    ) {
                        Text(
                            when (type) {
                                RoofType.ZINC -> "Zinc / metal"
                                RoofType.SLAB -> "Concrete slab"
                                RoofType.SHINGLE -> "Shingle / Other"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        SectionCard(title = "Building height") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(false, true).forEachIndexed { index, twoPlus ->
                    SegmentedButton(
                        selected = inputs.twoOrMoreStoreys == twoPlus,
                        onClick = { onUpdate { it.copy(twoOrMoreStoreys = twoPlus) } },
                        shape = SegmentedButtonDefaults.itemShape(index, 2)
                    ) {
                        Text(if (twoPlus) "2+ storeys" else "1 storey")
                    }
                }
            }
        }
    }
}
