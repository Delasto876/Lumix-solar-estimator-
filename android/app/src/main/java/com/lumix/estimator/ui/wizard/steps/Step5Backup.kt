package com.lumix.estimator.ui.wizard.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.BackupCoverage
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.ui.components.NumberField
import com.lumix.estimator.ui.components.SectionCard

private val presets = listOf(4, 8, 12, 24)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step5Backup(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "Backup duration in a power cut") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = presets + listOf(null)
                options.forEachIndexed { index, preset ->
                    SegmentedButton(
                        selected = inputs.backupHoursPreset == preset,
                        onClick = { onUpdate { it.copy(backupHoursPreset = preset) } },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size)
                    ) {
                        Text(preset?.let { "${it}h" } ?: "Custom")
                    }
                }
            }

            if (inputs.backupHoursPreset == null) {
                NumberField(
                    label = "Custom backup time (hours)",
                    value = inputs.backupHoursCustom,
                    onValueChange = { v -> onUpdate { it.copy(backupHoursCustom = v) } },
                    allowDecimal = false
                )
            }
        }

        SectionCard(title = "Backup coverage") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                BackupCoverage.entries.forEachIndexed { index, coverage ->
                    SegmentedButton(
                        selected = inputs.backupCoverage == coverage,
                        onClick = { onUpdate { it.copy(backupCoverage = coverage) } },
                        shape = SegmentedButtonDefaults.itemShape(index, BackupCoverage.entries.size)
                    ) {
                        Text(if (coverage == BackupCoverage.ESSENTIALS) "Essentials only" else "Most loads / full")
                    }
                }
            }
        }
    }
}
