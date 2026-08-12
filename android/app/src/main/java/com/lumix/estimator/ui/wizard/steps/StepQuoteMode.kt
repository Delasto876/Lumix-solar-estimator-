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
import com.lumix.estimator.domain.QuoteMode
import com.lumix.estimator.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepQuoteMode(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
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
            Text(
                when (inputs.quoteMode) {
                    QuoteMode.GUIDED -> "We size the system from your JPS bill or usage."
                    QuoteMode.MANUAL -> "You choose the exact panels, inverter and battery."
                    QuoteMode.LOAD -> "We size the system from your appliance load."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
