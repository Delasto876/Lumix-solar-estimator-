package com.lumix.estimator.ui.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.data.SettingsRepository
import com.lumix.estimator.domain.QuoteMode
import com.lumix.estimator.domain.SystemCalculator
import com.lumix.estimator.ui.components.LumixPrimaryButton
import com.lumix.estimator.ui.components.LumixSecondaryButton
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.wizard.steps.StepBatteryBank
import com.lumix.estimator.ui.wizard.steps.StepCommercialIndustrialDesign
import com.lumix.estimator.ui.wizard.steps.StepCustomer
import com.lumix.estimator.ui.wizard.steps.StepHouseholdAppliances
import com.lumix.estimator.ui.wizard.steps.StepInverter
import com.lumix.estimator.ui.wizard.steps.StepLocation
import com.lumix.estimator.ui.wizard.steps.StepManualMode
import com.lumix.estimator.ui.wizard.steps.StepPanels
import com.lumix.estimator.ui.wizard.steps.StepQuoteMode
import com.lumix.estimator.ui.wizard.steps.StepRoofType
import com.lumix.estimator.ui.wizard.steps.StepSiteDetails
import com.lumix.estimator.ui.wizard.steps.StepSystemReview
import com.lumix.estimator.ui.wizard.steps.Step4Usage
import com.lumix.estimator.ui.wizard.steps.Step5Backup
import com.lumix.estimator.ui.wizard.steps.Step7Pricing

// A88 (spec Phase 26): renumbered per WizardViewModel's own step-scheme doc comment — see that
// file for which steps are visible, and in what order, per QuoteMode.
private val stepTitles = mapOf(
    1 to "Customer",
    2 to "Design Mode",
    3 to "Location",
    4 to "Roof Type",
    5 to "JPS Bill / Usage",
    6 to "Manual Mode",
    7 to "Battery",
    8 to "Inverter",
    9 to "Panels",
    10 to "Appliances",
    11 to "Backup",
    12 to "System Review",
    13 to "Site Details",
    14 to "Pricing & Discount",
    15 to "Commercial/Industrial Design"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardScreen(
    viewModel: WizardViewModel,
    settingsRepository: SettingsRepository,
    onBackToHome: () -> Unit,
    /** A56: DESIGN flow's last step ("Calculate System") finished — a preliminary row now exists; go to `SystemResultScreen`. */
    onSystemCalculated: (Long) -> Unit,
    /** A56: QUOTE_DETAILS flow's last step ("Save Quote") finished — the same row is now complete; go to the full `ResultsScreen`. */
    onQuoteSaved: (Long) -> Unit,
    /** A88: SITE_DETAILS flow's one step ("Done") finished — return to `SystemResultScreen` for the same quote. */
    onSiteDetailsDone: (Long) -> Unit
) {
    val palette = LocalLumixPalette.current
    var showCalculationSequence by remember { mutableStateOf(false) }

    if (showCalculationSequence) {
        CalculationSequenceOverlay(
            onComplete = {
                viewModel.calculateAndSave { id ->
                    showCalculationSequence = false
                    if (viewModel.flowMode.value == WizardFlowMode.DESIGN) onSystemCalculated(id) else onQuoteSaved(id)
                }
            }
        )
        return
    }

    val inputs by viewModel.inputs.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val flowMode by viewModel.flowMode.collectAsState()
    val visibleSteps = viewModel.visibleSteps()
    val stepIndex = visibleSteps.indexOf(currentStep).coerceAtLeast(0)
    val errors = viewModel.errorsForStep(currentStep)
    val isLast = viewModel.isLastStep()
    // A88 (spec Phase 26 §17): System Review (12) is the DESIGN flow's real "Calculate System"
    // trigger point, kept decoupled from "is this literally the last array element" so appending
    // Site Details (13) after it as a SITE_DETAILS-only excursion can't retroactively move where
    // Calculate System fires — see WizardViewModel.openSiteDetails's own doc for why this shape
    // was chosen over folding Site Details into designSteps() directly.
    // Phase 28 §9: COMMERCIAL/INDUSTRIAL's design flow ends at the new step 15 (Commercial/
    // Industrial Design) instead of System Review (12) — same "Calculate System" trigger point,
    // just a different final step for that flow (see WizardViewModel.designSteps' own doc).
    val isSystemReviewStep = (currentStep == 12 || currentStep == 15) && flowMode == WizardFlowMode.DESIGN

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stepTitles[currentStep] ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBackToHome) {
                        Text("×", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LumixSecondaryButton(text = "Back", onClick = { viewModel.goBack() }, enabled = stepIndex > 0)
                when {
                    isSystemReviewStep -> {
                        // A56: DESIGN's System Review only needs to know the system itself is
                        // valid — no quote/pricing fields exist yet at this point in the flow.
                        val calcEnabled = !SystemCalculator.hasUnacknowledgedManualWarnings(inputs)
                        LumixPrimaryButton(
                            text = "Calculate System",
                            onClick = { showCalculationSequence = true },
                            enabled = calcEnabled
                        )
                    }
                    isLast && flowMode == WizardFlowMode.QUOTE_DETAILS -> {
                        // "Save Quote," not "Create Quote" — SystemResultScreen's own button
                        // already says "Create Quote" to START this flow; this finishes it.
                        LumixPrimaryButton(
                            text = "Save Quote",
                            onClick = { showCalculationSequence = true },
                            enabled = errors.isEmpty()
                        )
                    }
                    isLast && flowMode == WizardFlowMode.SITE_DETAILS -> {
                        LumixPrimaryButton(
                            text = "Done",
                            onClick = { viewModel.saveSiteDetails(onSiteDetailsDone) }
                        )
                    }
                    isLast -> {
                        // Reachable only if a mode's designSteps() ever ends on something other
                        // than System Review (12) — not the case today, kept as a safe fallback.
                        LumixPrimaryButton(text = "Continue", onClick = { showCalculationSequence = true })
                    }
                    else -> {
                        LumixPrimaryButton(text = "Continue", onClick = { viewModel.goNext() })
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LinearProgressIndicator(
                progress = { (stepIndex + 1f) / visibleSteps.size },
                color = palette.solarYellow,
                trackColor = palette.outline,
                modifier = Modifier.fillMaxWidth().height(2.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    "%02d".format(stepIndex + 1),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
                Text(
                    " / %02d".format(visibleSteps.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                when (currentStep) {
                    1 -> StepCustomer(inputs, viewModel::update)
                    2 -> StepQuoteMode(inputs, viewModel::update, settingsRepository)
                    3 -> StepLocation(inputs, viewModel::update)
                    4 -> StepRoofType(inputs, viewModel::update)
                    5 -> if (inputs.quoteMode == QuoteMode.GUIDED) Step4Usage(inputs, viewModel::update)
                    6 -> if (inputs.quoteMode == QuoteMode.MANUAL) StepManualMode(inputs, viewModel::update)
                    7 -> if (inputs.quoteMode == QuoteMode.MANUAL) StepBatteryBank(inputs, viewModel::update)
                    8 -> if (inputs.quoteMode == QuoteMode.MANUAL) StepInverter(inputs, viewModel::update)
                    9 -> if (inputs.quoteMode == QuoteMode.MANUAL) StepPanels(inputs, viewModel::update)
                    10 -> StepHouseholdAppliances(inputs, viewModel::update)
                    11 -> Step5Backup(inputs, viewModel::update)
                    12 -> StepSystemReview(inputs, viewModel::update, settingsRepository, viewModel::goToStep)
                    13 -> StepSiteDetails(inputs, viewModel::update)
                    14 -> Step7Pricing(inputs, viewModel::update)
                    15 -> StepCommercialIndustrialDesign(inputs, viewModel::update)
                }
            }

            if (errors.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    errors.forEach {
                        Text(it, color = palette.warningRedText, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
