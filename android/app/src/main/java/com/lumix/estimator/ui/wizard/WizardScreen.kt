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
import com.lumix.estimator.ui.components.LumixPrimaryButton
import com.lumix.estimator.ui.components.LumixSecondaryButton
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.wizard.steps.StepAirConditioning
import com.lumix.estimator.ui.wizard.steps.StepBatteryBank
import com.lumix.estimator.ui.wizard.steps.StepCustomer
import com.lumix.estimator.ui.wizard.steps.StepHouseholdAppliances
import com.lumix.estimator.ui.wizard.steps.StepInverterPanels
import com.lumix.estimator.ui.wizard.steps.StepManualMode
import com.lumix.estimator.ui.wizard.steps.StepPropertySystem
import com.lumix.estimator.ui.wizard.steps.StepQuoteMode
import com.lumix.estimator.ui.wizard.steps.StepRoofType
import com.lumix.estimator.ui.wizard.steps.StepSystemReview
import com.lumix.estimator.ui.wizard.steps.Step4Usage
import com.lumix.estimator.ui.wizard.steps.Step5Backup
import com.lumix.estimator.ui.wizard.steps.Step7Pricing

private val stepTitles = mapOf(
    1 to "Customer",
    2 to "Quote Mode",
    3 to "Property & System",
    4 to "Roof Type",
    5 to "Air Conditioning",
    6 to "Appliances",
    7 to "JPS Bill / Usage",
    8 to "Backup Requirements",
    9 to "Manual Mode",
    10 to "Inverter & Panels",
    11 to "Battery Bank",
    12 to "System Review",
    13 to "Pricing & Discount"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardScreen(
    viewModel: WizardViewModel,
    settingsRepository: SettingsRepository,
    onBackToHome: () -> Unit,
    onResults: (Long) -> Unit
) {
    val palette = LocalLumixPalette.current
    var showCalculationSequence by remember { mutableStateOf(false) }

    if (showCalculationSequence) {
        CalculationSequenceOverlay(
            onComplete = {
                viewModel.calculateAndSave { id ->
                    showCalculationSequence = false
                    onResults(id)
                }
            }
        )
        return
    }

    val inputs by viewModel.inputs.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val visibleSteps = viewModel.visibleSteps()
    val stepIndex = visibleSteps.indexOf(currentStep).coerceAtLeast(0)
    val errors = viewModel.errorsForStep(currentStep)
    val isLast = viewModel.isLastStep()

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
                if (isLast) {
                    LumixPrimaryButton(
                        text = "Calculate",
                        onClick = { showCalculationSequence = true },
                        enabled = viewModel.errorsForStep(13).isEmpty()
                    )
                } else {
                    LumixPrimaryButton(text = "Next", onClick = { viewModel.goNext() })
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
                    2 -> StepQuoteMode(inputs, viewModel::update)
                    3 -> StepPropertySystem(inputs, viewModel::update)
                    4 -> StepRoofType(inputs, viewModel::update)
                    5 -> StepAirConditioning(inputs, viewModel::update)
                    6 -> StepHouseholdAppliances(inputs, viewModel::update)
                    7 -> if (inputs.quoteMode == QuoteMode.GUIDED) Step4Usage(inputs, viewModel::update)
                    8 -> Step5Backup(inputs, viewModel::update)
                    9 -> if (inputs.quoteMode == QuoteMode.MANUAL) StepManualMode(inputs, viewModel::update)
                    10 -> if (inputs.quoteMode == QuoteMode.MANUAL) StepInverterPanels(inputs, viewModel::update)
                    11 -> if (inputs.quoteMode == QuoteMode.MANUAL) StepBatteryBank(inputs, viewModel::update)
                    12 -> StepSystemReview(inputs, viewModel::update, settingsRepository)
                    13 -> Step7Pricing(inputs, viewModel::update)
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
