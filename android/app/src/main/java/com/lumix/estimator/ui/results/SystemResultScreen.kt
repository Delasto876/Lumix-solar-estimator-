package com.lumix.estimator.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.lumix.estimator.ui.components.LumixPrimaryButton
import com.lumix.estimator.ui.components.LumixSecondaryButton
import com.lumix.estimator.ui.components.RingGauge
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette

/**
 * A56 (spec §5–9, 35–36): the screen that lands right after DESIGN-flow "Calculate System" — the
 * engineering result of a system that's been sized but doesn't yet (and might never) have a
 * customer, site, or pricing attached to it. Deliberately shows only what the sizing engine
 * itself produced (PV/Inverter/Battery/Production/Backup/Coverage) — no customer fields, no
 * discount, no PDF export; those only exist once CREATE QUOTE is explicitly chosen, which is what
 * distinguishes this from [ResultsScreen] (the full quote/pricing screen QUOTE_DETAILS leads to).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemResultScreen(
    quoteId: Long,
    quoteRepository: QuoteRepository,
    onSimulate: (Long) -> Unit,
    onEditSystem: () -> Unit,
    onCreateQuote: () -> Unit,
    onBackToHome: () -> Unit
) {
    var saved by remember(quoteId) { mutableStateOf<SavedQuote?>(null) }
    val palette = LocalLumixPalette.current

    LaunchedEffect(quoteId) {
        saved = quoteRepository.getSavedQuote(quoteId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Result") },
                navigationIcon = {
                    IconButton(onClick = onBackToHome) {
                        Text("×", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LumixPrimaryButton(text = "⚡ Simulate", onClick = { onSimulate(quoteId) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LumixSecondaryButton(text = "Edit System", onClick = onEditSystem, modifier = Modifier.weight(1f))
                    LumixSecondaryButton(text = "Create Quote", onClick = onCreateQuote, modifier = Modifier.weight(1f))
                }
            }
        }
    ) { padding ->
        val current = saved
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val result = current.result
        val inputs = current.inputs
        val projection = remember(current) { SavingsCalculator.project(inputs, result) }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionCard(title = "") {
                    Text(
                        "☀️ SYSTEM CALCULATED",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.solarYellowText,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "No customer or pricing details yet — this is just the engineering result. Simulate it, keep editing, or create a quote when you're ready.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            item {
                SectionCard(title = "System") {
                    ResultStatRow("PV array", "%.2f kWp".format(result.pvKw), "${result.panelCount} × ${result.panelWatts}W panels")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ResultStatRow("Inverter", "%.1f kW".format(result.inverterKw), result.inverterName)
                    if (result.totalBatteryKwh > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        ResultStatRow("Battery", "%.1f kWh".format(result.totalBatteryKwh), result.batteryName ?: "")
                    }
                }
            }

            item {
                SectionCard(title = "Production") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ResultStat(
                            "DAILY PRODUCTION",
                            "%.1f kWh".format(result.pvKw * inputs.peakSunHours),
                            Modifier.weight(1f)
                        )
                        ResultStat("REQUIRED DAILY LOAD", "%.1f kWh".format(result.designDailyKwh), Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                        RingGauge(percent = projection.coveragePercent, diameter = 88.dp, strokeWidth = 8.dp, label = "coverage")
                    }
                }
            }

            if (result.totalBatteryKwh > 0) {
                item {
                    SectionCard(title = "Backup") {
                        ResultStat(
                            "ESTIMATED BACKUP",
                            if (result.estimatedBackupSufficient) "${result.estimatedBackupHours.toInt()}+ hrs" else "%.1f hrs".format(result.estimatedBackupHours)
                        )
                        if (result.estimatedBackupReason.isNotBlank()) {
                            Text(
                                result.estimatedBackupReason,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        if (result.batteryRechargeTargetMet == false) {
                            Text(
                                "⚠ Battery may not fully recharge by ~2 PM under typical conditions.",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.warningRedText,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultStatRow(label: String, value: String, detail: String) {
    val palette = LocalLumixPalette.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        }
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
    }
}

@Composable
private fun ResultStat(label: String, value: String, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = palette.textPrimary)
    }
}
