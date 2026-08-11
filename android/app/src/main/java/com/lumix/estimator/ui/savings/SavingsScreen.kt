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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.data.QuoteRepository
import com.lumix.estimator.data.SavedQuote
import com.lumix.estimator.domain.SavingsCalculator
import com.lumix.estimator.domain.formatCurrency
import com.lumix.estimator.ui.components.LumixPrimaryButton
import com.lumix.estimator.ui.components.RingGauge
import com.lumix.estimator.ui.components.SavingsGraph
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.numberDisplayStyle
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsScreen(
    quoteRepository: QuoteRepository,
    onStartQuote: () -> Unit
) {
    val palette = LocalLumixPalette.current
    val quotes by quoteRepository.observeAll().collectAsState(initial = null)
    var latest by remember { mutableStateOf<SavedQuote?>(null) }

    val latestId = quotes?.firstOrNull()?.id
    LaunchedEffect(latestId) {
        latest = latestId?.let { quoteRepository.getSavedQuote(it) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Savings") }) }
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

        val projection = remember(current) { SavingsCalculator.project(current.inputs, current.result) }

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
