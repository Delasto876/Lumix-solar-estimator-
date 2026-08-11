package com.lumix.estimator.ui.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.QuoteMode
import com.lumix.estimator.ui.wizard.steps.Step1SiteInfo
import com.lumix.estimator.ui.wizard.steps.Step2Roof
import com.lumix.estimator.ui.wizard.steps.Step3Loads
import com.lumix.estimator.ui.wizard.steps.Step4Usage
import com.lumix.estimator.ui.wizard.steps.Step5Backup
import com.lumix.estimator.ui.wizard.steps.Step6Manual
import com.lumix.estimator.ui.wizard.steps.Step7Pricing

private val stepTitles = mapOf(
    1 to "Mode & Site Info",
    2 to "Roof & Mounting",
    3 to "Loads",
    4 to "JPS Bill / Usage",
    5 to "Backup Requirements",
    6 to "Manual System Builder",
    7 to "Pricing & Customer"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardScreen(
    viewModel: WizardViewModel,
    onBackToHome: () -> Unit,
    onResults: (Long) -> Unit
) {
    val inputs by viewModel.inputs.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val isCalculating by viewModel.isCalculating.collectAsState()
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
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LinearProgressIndicator(
                progress = { (stepIndex + 1f) / visibleSteps.size },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Step ${stepIndex + 1} of ${visibleSteps.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                when (currentStep) {
                    1 -> Step1SiteInfo(inputs, viewModel::update)
                    2 -> Step2Roof(inputs, viewModel::update)
                    3 -> Step3Loads(inputs, viewModel::update)
                    4 -> if (inputs.quoteMode == QuoteMode.GUIDED) Step4Usage(inputs, viewModel::update)
                    5 -> Step5Backup(inputs, viewModel::update)
                    6 -> if (inputs.quoteMode == QuoteMode.MANUAL) Step6Manual(inputs, viewModel::update)
                    7 -> Step7Pricing(inputs, viewModel::update)
                }
            }

            if (errors.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    errors.forEach {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = { viewModel.goBack() }, enabled = stepIndex > 0) {
                    Text("Back")
                }
                if (isLast) {
                    Button(
                        onClick = { viewModel.calculateAndSave { id -> onResults(id) } },
                        enabled = !isCalculating && viewModel.errorsForStep(7).isEmpty()
                    ) {
                        if (isCalculating) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(end = 8.dp)
                            )
                        }
                        Text("Calculate")
                    }
                } else {
                    Button(onClick = { viewModel.goNext() }) {
                        Text("Next")
                    }
                }
            }
        }
    }
}
