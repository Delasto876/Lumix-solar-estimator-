package com.lumix.estimator.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumix.estimator.data.QuoteRepository
import com.lumix.estimator.data.SavedQuote
import com.lumix.estimator.domain.SavingsCalculator
import com.lumix.estimator.domain.formatCurrency
import com.lumix.estimator.ui.components.LumixPrimaryButton
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.components.SolarHeroVisual
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.numberDisplayStyle
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    quoteRepository: QuoteRepository,
    onStartQuote: () -> Unit,
    onOpenQuote: (Long) -> Unit
) {
    val palette = LocalLumixPalette.current
    val quotes by quoteRepository.observeAll().collectAsState(initial = null)
    var latest by remember { mutableStateOf<SavedQuote?>(null) }
    val latestEntity = quotes?.firstOrNull()

    LaunchedEffect(latestEntity?.id) {
        latest = latestEntity?.id?.let { quoteRepository.getSavedQuote(it) }
    }

    val projection = latest?.let { SavingsCalculator.project(it.inputs, it.result) }
    val greetingName = latest?.inputs?.customerName?.takeIf { it.isNotBlank() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Lumix") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    greetingText(greetingName),
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.textSecondary
                )
            }

            item {
                SectionCard(title = "") {
                    Text("☀️ Your Solar Potential", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    SolarHeroVisual(activity = (projection?.coveragePercent ?: 0f) / 100f)

                    if (projection != null) {
                        Text(
                            "${projection.coveragePercent.toInt()}%",
                            style = numberDisplayStyle(size = 40.sp),
                            color = palette.solarYellowText
                        )
                        Text(
                            "of your estimated energy needs could be powered by solar, based on your last quote.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                    } else {
                        Text(
                            "Run a quick estimate to see how much of your home's energy the sun could cover.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                    }

                    LumixPrimaryButton(
                        text = if (latest == null) "Start Solar Estimate" else "Start New Estimate",
                        onClick = onStartQuote,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (latest != null && projection != null) {
                val quote = latest!!
                item {
                    Text("Quick Energy Snapshot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                item {
                    SectionCard(
                        title = "",
                        modifier = Modifier.clickable { onOpenQuote(quote.id) }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            SnapshotStat(
                                label = "Monthly bill",
                                value = formatCurrency(projection.baselineMonthlyBill),
                                color = palette.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            SnapshotStat(
                                label = "Solar size",
                                value = "${"%.1f".format(quote.result.pvKw)} kW",
                                color = palette.technicalCyanText,
                                modifier = Modifier.weight(1f)
                            )
                            SnapshotStat(
                                label = "Potential savings",
                                value = "${formatCurrency(projection.monthlySavings)}/mo",
                                color = palette.energyGreenText,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapshotStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
    }
}

private fun greetingText(name: String?): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val timeOfDay = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    return if (name != null) "$timeOfDay, ${name.uppercase()}" else timeOfDay.uppercase()
}
