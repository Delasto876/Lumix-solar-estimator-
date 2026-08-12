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
import com.lumix.estimator.domain.ManualModeType
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepManualMode(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
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
    }
}
