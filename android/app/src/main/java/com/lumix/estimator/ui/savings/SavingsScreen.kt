package com.lumix.estimator.ui.savings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.data.QuoteRepository
import com.lumix.estimator.data.SavedQuote
import com.lumix.estimator.data.SettingsRepository
import com.lumix.estimator.domain.SavingsCalculator
import com.lumix.estimator.domain.formatCurrency
import com.lumix.estimator.ui.components.LargeTitleTopBar
import com.lumix.estimator.ui.components.LumixPrimaryButton
import com.lumix.estimator.ui.components.RingGauge
import com.lumix.estimator.ui.components.SavingsGraph
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixColors
import com.lumix.estimator.ui.theme.numberDisplayStyle
import androidx.compose.ui.unit.sp

private const val DEFAULT_PROJECTION_YEAR = 15

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsScreen(
    quoteRepository: QuoteRepository,
    settingsRepository: SettingsRepository,
    onStartQuote: () -> Unit
) {
    val palette = LocalLumixPalette.current
    val quotes by quoteRepository.observeAll().collectAsState(initial = null)
    var latest by remember { mutableStateOf<SavedQuote?>(null) }
    val billEscalationRate by settingsRepository.billEscalationRate.collectAsState(initial = SavingsCalculator.BILL_ESCALATION_RATE)
    val panelDegradationRate by settingsRepository.panelDegradationRate.collectAsState(initial = SavingsCalculator.PANEL_DEGRADATION_RATE)
    var selectedYear by remember { mutableIntStateOf(DEFAULT_PROJECTION_YEAR) }

    val latestId = quotes?.firstOrNull()?.id
    LaunchedEffect(latestId) {
        latest = latestId?.let { quoteRepository.getSavedQuote(it) }
    }

    Scaffold(
        topBar = { LargeTitleTopBar(title = "Savings") }
    ) { padding ->
        val current = latest
        if (quotes == null) {
            return@Scaffold
        }
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Your savings story starts here.",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary
                    )
                    Text(
                        "Run an estimate to see how much a solar system could save you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
                    )
                    LumixPrimaryButton(text = "Start your estimate", onClick = onStartQuote)
                }
            }
            return@Scaffold
        }

        val projection = remember(current, billEscalationRate, panelDegradationRate) {
            SavingsCalculator.project(current.inputs, current.result, billEscalationRate, panelDegradationRate)
        }
        val selectedPoint = projection.yearly.getOrElse(selectedYear - 1) { projection.yearly.last() }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionCard(title = "") {
                    Text(
                        "Estimated monthly savings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                    Text(
                        formatCurrency(projection.monthlySavings),
                        style = numberDisplayStyle(size = 40.sp),
                        color = palette.energyGreenText
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        StatColumn(
                            label = "Payback",
                            value = projection.paybackYears?.let { "%.1f yrs".format(it) } ?: "—"
                        )
                        StatColumn(
                            label = "20-yr savings",
                            value = formatCurrency(projection.yearly.last().cumulativeSavings)
                        )
                    }
                }
            }

            item {
                SectionCard(title = "Year-by-year projection") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Year $selectedYear", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                        Text(
                            "Cumulative: ${formatCurrency(selectedPoint.cumulativeSavings)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.energyGreenText
                        )
                    }
                    Slider(
                        value = selectedYear.toFloat(),
                        onValueChange = { selectedYear = it.toInt().coerceIn(1, projection.yearly.size) },
                        valueRange = 1f..projection.yearly.size.toFloat(),
                        steps = projection.yearly.size - 2,
                        colors = SliderDefaults.colors(thumbColor = LumixColors.SolarYellow, activeTrackColor = LumixColors.SolarYellow)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatColumn(label = "This year's savings", value = formatCurrency(selectedPoint.yearlySavings))
                        StatColumn(label = "Cost without solar", value = formatCurrency(selectedPoint.costWithoutSolar))
                        StatColumn(label = "Cost with solar", value = formatCurrency(selectedPoint.costWithSolar))
                    }
                    Text(
                        "ESTIMATE — assumes %.1f%%/yr bill escalation and %.1f%%/yr panel degradation, editable in Settings."
                            .format(billEscalationRate * 100.0, panelDegradationRate * 100.0),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            item {
                SectionCard(title = "Solar coverage") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        RingGauge(percent = projection.coveragePercent, diameter = 108.dp, strokeWidth = 10.dp)
                        Column {
                            Text(
                                "of your estimated usage could be met by this system.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textSecondary
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "20-year projection") {
                    SavingsGraph(yearly = projection.yearly)
                }
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    val palette = LocalLumixPalette.current
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
    }
}
