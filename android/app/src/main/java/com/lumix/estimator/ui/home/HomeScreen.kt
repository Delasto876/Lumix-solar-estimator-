package com.lumix.estimator.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumix.estimator.data.QuoteRepository
import com.lumix.estimator.data.SavedQuote
import com.lumix.estimator.domain.SavingsCalculator
import com.lumix.estimator.domain.formatCurrency
import com.lumix.estimator.ui.components.LumixPrimaryButton
import com.lumix.estimator.ui.components.SolarHeroVisual
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.heroValueStyle
import com.lumix.estimator.ui.theme.numberDisplayStyle
import java.util.Calendar

/**
 * "Open the app → immediately understand the energy system": one hero (system size, live-feeling
 * visual, status), a compact stat row beneath it, one call to action. No dashboard grid of small
 * cards — everything that isn't the hero is a plain label/value pair sitting on the background.
 */
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

    val quote = latest
    val projection = quote?.let { SavingsCalculator.project(it.inputs, it.result) }
    val greetingName = quote?.inputs?.customerName?.takeIf { it.isNotBlank() }

    Box(modifier = Modifier.fillMaxSize().background(palette.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item {
                Column(modifier = Modifier.statusBarsPadding().padding(top = 12.dp)) {
                    Text(
                        greetingLabel(),
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.textSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        greetingName ?: "Lumix",
                        style = MaterialTheme.typography.displaySmall,
                        color = palette.textPrimary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            if (quote != null && projection != null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenQuote(quote.id) }
                    ) {
                        Text(
                            "YOUR HOME",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                            Text(
                                "%.1f".format(quote.result.pvKw),
                                style = heroValueStyle(),
                                color = palette.textPrimary
                            )
                            Text(
                                "kWp",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textSecondary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
                            )
                        }
                        StatusRow(label = "System sized", color = palette.energyGreen)

                        SolarHeroVisual(
                            activity = (projection.coveragePercent / 100f).coerceIn(0f, 1f),
                            modifier = Modifier.padding(top = 16.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            HomeStat(label = "COVERAGE", value = "${projection.coveragePercent.toInt()}%", modifier = Modifier.weight(1f))
                            HomeStat(
                                label = "BATTERY",
                                value = if (quote.result.totalBatteryKwh > 0) "%.1f kWh".format(quote.result.totalBatteryKwh) else "—",
                                modifier = Modifier.weight(1f)
                            )
                            HomeStat(label = "MONTHLY SAVINGS", value = formatCurrency(projection.monthlySavings), modifier = Modifier.weight(1f))
                        }
                    }
                }

                item {
                    LumixPrimaryButton(
                        text = "Start New Estimate",
                        onClick = onStartQuote,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                item {
                    HomeEmptyState(onStartQuote = onStartQuote)
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = LocalLumixPalette.current.textSecondary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HomeStat(label: String, value: String, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    Column(modifier = modifier) {
        Text(
            value,
            style = numberDisplayStyle(size = 20.sp),
            color = palette.textPrimary
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun HomeEmptyState(onStartQuote: () -> Unit) {
    val palette = LocalLumixPalette.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = 40.dp)) {
        Text(
            "NO SYSTEM YET",
            style = MaterialTheme.typography.labelLarge,
            color = palette.textSecondary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Design your first solar system.",
            style = MaterialTheme.typography.headlineMedium,
            color = palette.textPrimary,
            modifier = Modifier.padding(top = 6.dp, bottom = 28.dp)
        )
        LumixPrimaryButton(
            text = "Start Estimate",
            onClick = onStartQuote,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun greetingLabel(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "GOOD MORNING"
        hour < 17 -> "GOOD AFTERNOON"
        else -> "GOOD EVENING"
    }
}
