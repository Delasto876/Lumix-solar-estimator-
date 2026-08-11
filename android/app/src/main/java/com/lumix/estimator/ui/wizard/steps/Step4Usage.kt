package com.lumix.estimator.ui.wizard.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.UsageMode
import com.lumix.estimator.ui.components.LabeledDropdown
import com.lumix.estimator.ui.components.NumberField
import com.lumix.estimator.ui.components.SectionCard

@Composable
fun Step4Usage(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "JPS bill / usage") {
            LabeledDropdown(
                label = "How will you enter usage?",
                options = UsageMode.entries,
                selected = inputs.usageMode,
                optionLabel = {
                    when (it) {
                        UsageMode.BILL -> "Average JPS bill (J$)"
                        UsageMode.KWH -> "Average monthly kWh"
                        UsageMode.UNKNOWN -> "Not sure"
                    }
                },
                onSelected = { v -> onUpdate { it.copy(usageMode = v) } }
            )

            when (inputs.usageMode) {
                UsageMode.BILL -> NumberField(
                    label = "Average JPS bill (last 3 months, J$)",
                    value = inputs.avgBill,
                    onValueChange = { v -> onUpdate { it.copy(avgBill = v) } }
                )
                UsageMode.KWH -> NumberField(
                    label = "Average monthly usage (kWh)",
                    value = inputs.avgKwh,
                    onValueChange = { v -> onUpdate { it.copy(avgKwh = v) } }
                )
                UsageMode.UNKNOWN -> Text(
                    "The system will lean more on the load information & backup time.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
