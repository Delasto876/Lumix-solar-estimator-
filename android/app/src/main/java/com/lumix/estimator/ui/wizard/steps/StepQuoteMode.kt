package com.lumix.estimator.ui.wizard.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteMode
import com.lumix.estimator.ui.components.SelectionCard

private fun titleFor(mode: QuoteMode) = when (mode) {
    QuoteMode.GUIDED -> "Guided"
    QuoteMode.MANUAL -> "Manual"
    QuoteMode.LOAD -> "Load-based"
}

private fun descriptionFor(mode: QuoteMode) = when (mode) {
    QuoteMode.GUIDED -> "We'll size the system from your JPS bill or usage."
    QuoteMode.MANUAL -> "Choose the exact panels, inverter and battery yourself."
    QuoteMode.LOAD -> "We'll size the system from your appliance load."
}

@Composable
fun StepQuoteMode(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QuoteMode.entries.forEach { mode ->
            SelectionCard(
                title = titleFor(mode),
                description = descriptionFor(mode),
                selected = inputs.quoteMode == mode,
                onClick = { onUpdate { it.copy(quoteMode = mode) } }
            )
        }
    }
}
