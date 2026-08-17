package com.lumix.estimator.ui.results

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import com.lumix.estimator.domain.DiagnosticCheck
import com.lumix.estimator.domain.QuoteResult
import com.lumix.estimator.domain.monthName
import com.lumix.estimator.domain.SavingsCalculator
import com.lumix.estimator.domain.SystemDiagnostics
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

            // A80 (spec Phase 17 §"SIZING REPORT" — "Solar resource assumption: Location, Month,
            // Typical PSH, Modeled weather, Estimated daily PV, Conservative estimate"): only
            // shown when an install month was actually picked (see
            // QuoteResult.estimatedTypicalDailyPvKwh's own doc) — nothing month-specific to report
            // otherwise, and this section exists specifically to disclose that assumption, not to
            // invent one.
            if (result.designInstallMonth != null) {
                item {
                    SectionCard(title = "Solar Resource Assumption") {
                        Text(
                            "Month: ${monthName(result.designInstallMonth) ?: ""} · Typical PSH: %.1f h".format(result.designPeakSunHours ?: inputs.peakSunHours),
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textPrimary
                        )
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            ResultStat(
                                "TYPICAL DAILY PV",
                                result.estimatedTypicalDailyPvKwh?.let { "%.1f kWh".format(it) } ?: "—",
                                Modifier.weight(1f)
                            )
                            ResultStat(
                                "CONSERVATIVE ESTIMATE",
                                result.estimatedConservativeDailyPvKwh?.let { "%.1f kWh".format(it) } ?: "—",
                                Modifier.weight(1f)
                            )
                        }
                        Text(
                            "Modeled from Jamaica seasonal climatological tendencies (not measured historical weather) — Typical vs. Cloudier-than-normal scenarios. Do not promise this exact production every day.",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(top = 10.dp)
                        )
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

            item {
                // A58 (spec §37–38): the diagnostics panel — every engineering check that went
                // into (or would have blocked) this exact system, plus the plain-language reasons
                // GUIDED/LOAD's EquipmentSelectionEngine picked each component, all in one place.
                DiagnosticsSection(result, inputs.backupHours)
            }
        }
    }
}

@Composable
private fun DiagnosticsSection(result: QuoteResult, targetBackupHours: Double) {
    val palette = LocalLumixPalette.current
    var open by remember(result) { mutableStateOf(false) }
    val checks = remember(result, targetBackupHours) { SystemDiagnostics.checksFor(result, targetBackupHours) }
    val failCount = checks.count { !it.pass }
    val reasons = listOfNotNull(
        result.panelSelectionReason?.let { "PV" to it },
        result.inverterSelectionReason?.let { "Inverter" to it },
        result.batterySelectionReason?.let { "Battery" to it }
    )

    SectionCard(title = "") {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { open = !open },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("WHY WAS THIS SYSTEM SELECTED?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Text(
                    if (failCount == 0) "All checks pass" else "$failCount check${if (failCount == 1) "" else "s"} need review",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (failCount == 0) palette.energyGreenText else palette.solarAmberText
                )
            }
            Text(if (open) "▾" else "▸", style = MaterialTheme.typography.titleMedium, color = palette.textSecondary)
        }
        AnimatedVisibility(visible = open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                if (reasons.isNotEmpty()) {
                    reasons.forEach { (label, reason) ->
                        Text(
                            "$label — $reason",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                checks.forEach { check -> DiagnosticRow(check) }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(check: DiagnosticCheck) {
    val palette = LocalLumixPalette.current
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (check.pass) "PASS" else "FAIL",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (check.pass) palette.energyGreenText else palette.warningRedText
            )
            Text(check.label, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
        }
        if (check.detail != null) {
            Text(
                check.detail,
                style = MaterialTheme.typography.labelSmall,
                color = palette.solarAmberText,
                modifier = Modifier.padding(start = 48.dp, top = 2.dp)
            )
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
