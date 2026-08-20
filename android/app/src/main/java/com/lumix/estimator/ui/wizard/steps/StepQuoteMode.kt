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
import com.lumix.estimator.domain.SystemType
import com.lumix.estimator.ui.components.SelectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette

private fun titleFor(category: SystemType) = when (category) {
    SystemType.RESIDENTIAL -> "🏠 Residential"
    SystemType.COMMERCIAL -> "🏢 Commercial"
    SystemType.INDUSTRIAL -> "🏭 Industrial"
}

private fun descriptionFor(category: SystemType) = when (category) {
    SystemType.RESIDENTIAL -> "A home. Uses the guided/load-based/manual design workflow below, with realistic household appliance schedules."
    SystemType.COMMERCIAL -> "A business — shop, office, restaurant, or similar. Uses a manual load list and your own business operating hours, with commercial-specific load types."
    SystemType.INDUSTRIAL -> "A factory, plant, or production facility. Manual design only — you enter your own shift schedule and equipment loads; nothing is assumed for you."
}

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
/**
 * Phase 28 §9 ("When the user selects RESIDENTIAL/COMMERCIAL/INDUSTRIAL, show [that type's]
 * appliances/loads and schedules... Do not create three separate calculators. Use the SAME
 * existing calculation engine with a Quote Type configuration"): the System Category picker lives
 * here, above the existing Design Mode picker, since both questions belong at the very start of the
 * flow and this step was already the wizard's first real decision point. Choosing COMMERCIAL/
 * INDUSTRIAL routes the rest of the design flow to [com.lumix.estimator.ui.wizard.steps
 * .StepCommercialIndustrialDesign] instead of the residential steps below (see
 * [com.lumix.estimator.ui.wizard.WizardViewModel.designSteps]) — the Design Mode cards are hidden
 * in that case since GUIDED/LOAD/MANUAL is a residential-only distinction
 * [com.lumix.estimator.domain.commercial.CommercialIndustrialCalculator] doesn't read.
 */
@Composable
fun StepQuoteMode(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    val palette = LocalLumixPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "What kind of system is this?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary
        )
        Text(
            "This decides which appliances/loads and default schedules you'll see next.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        SystemType.entries.forEach { category ->
            val selected = inputs.systemCategory == category
            SelectionCard(
                title = titleFor(category),
                description = descriptionFor(category) + if (selected) "\n\n✓ SELECTED" else "",
                selected = selected,
                onClick = { onUpdate { it.copy(systemCategory = category) } }
            )
        }

        if (inputs.systemCategory == SystemType.RESIDENTIAL) {
            Text(
                "How would you like to design this system?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
                modifier = Modifier.padding(top = 8.dp)
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
        } else {
            Text(
                "Commercial and industrial systems use a manual load list and their own schedule — continue to enter it.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
