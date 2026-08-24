package com.lumix.estimator.ui.wizard.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.data.SettingsRepository
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteMode
import com.lumix.estimator.domain.SystemType
import com.lumix.estimator.domain.commercial.CommercialFacilityType
import com.lumix.estimator.domain.commercial.CommercialIndustrialDesign
import com.lumix.estimator.domain.commercial.FacilitySelection
import com.lumix.estimator.domain.commercial.IndustrialFacilityType
import com.lumix.estimator.ui.components.LabeledDropdown
import com.lumix.estimator.ui.components.SelectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixPalette
import kotlinx.coroutines.launch

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
fun StepQuoteMode(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit, settingsRepository: SettingsRepository) {
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
            FacilityTypeSection(inputs, onUpdate, settingsRepository, palette)
        }
    }
}

/**
 * Phase 42 (spec §1 — "immediately ask: What type of facility is this?"): a plain dropdown over
 * [CommercialFacilityType]/[IndustrialFacilityType]'s own labels plus any installer-added custom
 * names ([SettingsRepository.customCommercialFacilityNames]/[SettingsRepository
 * .customIndustrialFacilityNames]) — §24 "Do not redesign the application in this phase. Add the
 * minimum UI required." Choosing a facility here only sets [CommercialIndustrialDesign.facility];
 * it does not touch [CommercialIndustrialDesign.loads] in this phase — the facility-specific
 * default load libraries are a later phase of this same update, and per §1's own "Do NOT force
 * facility assumptions on the user," every load added to a design stays fully editable regardless.
 */
@Composable
private fun FacilityTypeSection(
    inputs: QuoteInputs,
    onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit,
    settingsRepository: SettingsRepository,
    palette: LumixPalette
) {
    val scope = rememberCoroutineScope()
    val facility = (inputs.commercialIndustrialDesign ?: CommercialIndustrialDesign()).facility

    fun updateFacility(transform: (FacilitySelection) -> FacilitySelection) {
        onUpdate {
            val liveDesign = it.commercialIndustrialDesign ?: CommercialIndustrialDesign()
            it.copy(commercialIndustrialDesign = liveDesign.copy(facility = transform(liveDesign.facility)))
        }
    }

    Text(
        "What type of facility is this?",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = palette.textPrimary,
        modifier = Modifier.padding(top = 12.dp)
    )
    Text(
        "This only labels the design for now — every load stays yours to add, edit, or remove.",
        style = MaterialTheme.typography.labelSmall,
        color = palette.textSecondary,
        modifier = Modifier.padding(bottom = 4.dp)
    )

    if (inputs.systemCategory == SystemType.COMMERCIAL) {
        val customNames by settingsRepository.customCommercialFacilityNames.collectAsState(initial = emptyList())
        val options = customNames + CommercialFacilityType.entries.filter { !it.isCustom }.map { it.label } + CommercialFacilityType.CUSTOM.label
        val currentLabel = when {
            facility.commercialType == CommercialFacilityType.CUSTOM -> facility.customFacilityName.ifBlank { CommercialFacilityType.CUSTOM.label }
            facility.commercialType != null -> facility.commercialType.label
            else -> ""
        }
        LabeledDropdown(
            label = "Facility type",
            options = options,
            selected = currentLabel,
            optionLabel = { it.ifBlank { "Select facility type…" } },
            onSelected = { label ->
                updateFacility {
                    when {
                        label == CommercialFacilityType.CUSTOM.label -> FacilitySelection(commercialType = CommercialFacilityType.CUSTOM, customFacilityName = "")
                        label in customNames -> FacilitySelection(commercialType = CommercialFacilityType.CUSTOM, customFacilityName = label)
                        else -> FacilitySelection(commercialType = CommercialFacilityType.entries.firstOrNull { it.label == label && !it.isCustom })
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (facility.commercialType == CommercialFacilityType.CUSTOM) {
            CustomFacilityNameField(
                value = facility.customFacilityName,
                onValueChange = { name -> updateFacility { it.copy(customFacilityName = name) } },
                onSave = { name -> scope.launch { settingsRepository.addCustomCommercialFacilityName(name) } },
                alreadySaved = facility.customFacilityName in customNames
            )
        }
    } else if (inputs.systemCategory == SystemType.INDUSTRIAL) {
        val customNames by settingsRepository.customIndustrialFacilityNames.collectAsState(initial = emptyList())
        val options = customNames + IndustrialFacilityType.entries.filter { !it.isCustom }.map { it.label } + IndustrialFacilityType.CUSTOM.label
        val currentLabel = when {
            facility.industrialType == IndustrialFacilityType.CUSTOM -> facility.customFacilityName.ifBlank { IndustrialFacilityType.CUSTOM.label }
            facility.industrialType != null -> facility.industrialType.label
            else -> ""
        }
        LabeledDropdown(
            label = "Facility type",
            options = options,
            selected = currentLabel,
            optionLabel = { it.ifBlank { "Select facility type…" } },
            onSelected = { label ->
                updateFacility {
                    when {
                        label == IndustrialFacilityType.CUSTOM.label -> FacilitySelection(industrialType = IndustrialFacilityType.CUSTOM, customFacilityName = "")
                        label in customNames -> FacilitySelection(industrialType = IndustrialFacilityType.CUSTOM, customFacilityName = label)
                        else -> FacilitySelection(industrialType = IndustrialFacilityType.entries.firstOrNull { it.label == label && !it.isCustom })
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (facility.industrialType == IndustrialFacilityType.CUSTOM) {
            CustomFacilityNameField(
                value = facility.customFacilityName,
                onValueChange = { name -> updateFacility { it.copy(customFacilityName = name) } },
                onSave = { name -> scope.launch { settingsRepository.addCustomIndustrialFacilityName(name) } },
                alreadySaved = facility.customFacilityName in customNames
            )
        }
    }
}

@Composable
private fun CustomFacilityNameField(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: (String) -> Unit,
    alreadySaved: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Custom facility name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (value.isNotBlank() && !alreadySaved) {
            TextButton(onClick = { onSave(value) }) {
                Text("Save \"$value\" to my facility list")
            }
        }
    }
}
