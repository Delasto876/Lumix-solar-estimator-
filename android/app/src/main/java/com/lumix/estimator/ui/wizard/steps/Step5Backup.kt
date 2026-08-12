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
import com.lumix.estimator.domain.BackupCoverage
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.ui.components.LumixValueSlider
import com.lumix.estimator.ui.components.NumberField
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette

private val presets = listOf(4, 8, 12, 16, 24)

/** New quotes only ever pick from these three — ESSENTIALS/FULL are legacy decode-compat values, never shown here. */
private val coverageOptions = listOf(BackupCoverage.MOST_LOAD, BackupCoverage.CRITICAL_LOADS, BackupCoverage.CUSTOM)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step5Backup(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    val palette = LocalLumixPalette.current
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
                coverageOptions.forEachIndexed { index, coverage ->
                    SegmentedButton(
                        selected = inputs.backupCoverage == coverage,
                        onClick = { onUpdate { it.copy(backupCoverage = coverage) } },
                        shape = SegmentedButtonDefaults.itemShape(index, coverageOptions.size)
                    ) {
                        Text(
                            when (coverage) {
                                BackupCoverage.MOST_LOAD -> "Most Load"
                                BackupCoverage.CRITICAL_LOADS -> "Critical Loads"
                                BackupCoverage.CUSTOM -> "Custom"
                                else -> coverage.name
                            }
                        )
                    }
                }
            }

            Text(
                when (inputs.backupCoverage) {
                    BackupCoverage.MOST_LOAD -> "Prioritize the majority of household loads during backup."
                    BackupCoverage.CRITICAL_LOADS -> "Back up only essentials — fridge, lights, a fan — to stretch runtime further on a smaller system."
                    BackupCoverage.CUSTOM -> "Choose your own share of the day's load to size backup for."
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (inputs.backupCoverage == BackupCoverage.CUSTOM) {
                LumixValueSlider(
                    value = (inputs.customBackupCoverageFraction * 100.0).toFloat(),
                    onValueChange = { v -> onUpdate { it.copy(customBackupCoverageFraction = (v / 100.0).toDouble().coerceIn(0.0, 1.0)) } },
                    valueRange = 10f..100f,
                    format = { "${it.toInt()}%" },
                    label = "Share of daily load to back up",
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}
