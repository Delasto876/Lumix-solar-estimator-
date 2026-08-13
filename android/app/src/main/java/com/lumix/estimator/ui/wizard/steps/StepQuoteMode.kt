package com.lumix.estimator.ui.wizard.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteMode
import com.lumix.estimator.ui.components.SelectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette

private fun titleFor(mode: QuoteMode) = when (mode) {
    QuoteMode.GUIDED -> "✨ Guided"
    QuoteMode.LOAD -> "📊 Load-Based"
    QuoteMode.MANUAL -> "🛠 Manual"
}

private fun descriptionFor(mode: QuoteMode) = when (mode) {
    QuoteMode.GUIDED -> "Let Lumix design the system. Answer a few simple questions and we'll recommend the solar, inverter and battery system — from your JPS bill or usage."
    QuoteMode.LOAD -> "Size from the home's actual loads. Build the system around the appliances and electrical demand of the property, not a bill estimate."
    QuoteMode.MANUAL -> "Full control. Select the exact panels, inverter, battery and system configuration yourself."
}

/**
 * A29/A44: three real design modes ([QuoteMode.GUIDED]/[QuoteMode.LOAD]/[QuoteMode.MANUAL]) all
 * feed the same one [com.lumix.estimator.domain.SystemCalculator] — this screen is purely how the
 * installer chooses to arrive at that one calculation, not a fork into separate engines.
 */
@Composable
fun StepQuoteMode(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    val palette = LocalLumixPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "How would you like to design this system?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary
        )
        Text(
            "All three end up at the same reviewable system — this just decides how you get there.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        QuoteMode.entries.forEach { mode ->
            val selected = inputs.quoteMode == mode
            SelectionCard(
                title = titleFor(mode),
                description = descriptionFor(mode) + if (selected) "\n\n✓ SELECTED" else "",
                selected = selected,
                onClick = { onUpdate { it.copy(quoteMode = mode) } }
            )
        }
    }
}
