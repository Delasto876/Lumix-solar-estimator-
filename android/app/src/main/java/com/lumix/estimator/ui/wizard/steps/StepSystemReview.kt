package com.lumix.estimator.ui.wizard.steps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.data.SettingsRepository
import com.lumix.estimator.domain.BackupCoverage
import com.lumix.estimator.domain.EquipmentSelectionEngine
import com.lumix.estimator.domain.PriceList
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteMode
import com.lumix.estimator.domain.SystemCalculator
import com.lumix.estimator.domain.UsageMode
import com.lumix.estimator.domain.simulation.SimulationEngine
import com.lumix.estimator.ui.components.LumixPrimaryButton
import com.lumix.estimator.ui.simulation.formatSimTime
import com.lumix.estimator.ui.components.LumixSecondaryButton
import com.lumix.estimator.ui.components.NumberField
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette
import kotlin.math.min
import kotlinx.coroutines.launch

private data class EngineeringCheck(val label: String, val pass: Boolean, val detail: String?)

/**
 * A29/A43: redesigned from seven always-expanded cards stacked one after another (the "jumbled"
 * layout) into one clear hierarchy — a primary at-a-glance summary, a compact System Check
 * status line, and the full engineering detail tucked behind VIEW CALCULATIONS. Every number
 * shown is the exact same `preview`/`checks`/`confidenceChecks` this step already computed —
 * this is a layout change, not a recalculation.
 */
@Composable
fun StepSystemReview(
    inputs: QuoteInputs,
    onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit,
    settingsRepository: SettingsRepository,
    onJumpToStep: (Int) -> Unit = {}
) {
    val palette = LocalLumixPalette.current
    val scope = rememberCoroutineScope()
    val gridServiceAmps by settingsRepository.defaultGridServiceAmps.collectAsState(initial = SimulationEngine.DEFAULT_GRID_SERVICE_AMPS)
    val preview = remember(inputs) { SystemCalculator.calculate(inputs, PriceList.DEFAULT) }
    val requiredInverterKw = remember(preview) { preview.peakWatts * 1.25 / 1000.0 }
    val batteryMaxDischargeKw = remember(preview) {
        if (preview.totalBatteryKwh > 0) min(preview.totalBatteryKwh * 0.5, preview.inverterKw.coerceAtLeast(0.1)) else 0.0
    }
    val peakLoadKw = preview.peakWatts / 1000.0
    // A54: preview.estimatedBackupHours comes from an actual grid-disconnected simulation of this
    // exact system (BackupEstimator, run once inside SystemCalculator.calculate) — not a separate
    // ratio computed here, so this can never disagree with what the Simulation screen would show.

    // A52: real series-topology electrical validation for the EXACT selected panel/inverter pair —
    // the same rules EquipmentSelectionEngine's own search applies (voltage adds across a series
    // string, current does NOT multiply by panel count), checked against the inverter's real max
    // PV input power/voltage/MPPT range rather than a proxy like "AC rating x 1.3" (which had been
    // silently conflating the inverter's AC output rating with its actual PV input limit — a
    // different field entirely, and the exact class of bug flagged 2026-08-13).
    val pvCompat = remember(preview) {
        EquipmentSelectionEngine.checkPanelInverterCompatibility(
            preview.panelWatts, preview.panelCount, preview.inverterKw, preview.inverterName
        )
    }

    val checks = remember(preview, requiredInverterKw, batteryMaxDischargeKw, peakLoadKw, pvCompat) {
        listOf(
            // A49: this is distinct from the backup-coverage check below it — it fires whenever the
            // *selected* inverter can't cover ordinary peak household load, regardless of what
            // backup coverage was requested. GUIDED/LOAD equipment is chosen specifically to avoid
            // this (EquipmentSelectionEngine), so it should only ever realistically fire for MANUAL.
            EngineeringCheck(
                label = "Inverter capacity suitable for peak load",
                pass = preview.inverterKw >= requiredInverterKw - 0.05,
                detail = if (preview.inverterKw < requiredInverterKw - 0.05) {
                    "Selected inverter (%s, %.1f kW) is below the calculated peak-load requirement (%.2f kW)."
                        .format(preview.inverterName, preview.inverterKw, requiredInverterKw)
                } else null
            ),
            EngineeringCheck(
                label = "Inverter capacity suitable for backup coverage",
                pass = preview.backupCapacityWarningKw == null,
                detail = preview.backupCapacityWarningKw?.let {
                    "Requested backup coverage implies about %.1f kW — above what the %s (%.1f kW) can deliver."
                        .format(it, preview.inverterName, preview.inverterKw)
                }
            ),
            EngineeringCheck(
                label = "Battery capacity suitable for backup target",
                pass = preview.totalBatteryKwh >= preview.batteryRequiredKwh - 0.05,
                detail = if (preview.totalBatteryKwh < preview.batteryRequiredKwh - 0.05) {
                    "About %.1f kWh is needed for the requested backup; %.1f kWh is selected."
                        .format(preview.batteryRequiredKwh, preview.totalBatteryKwh)
                } else null
            ),
            // A66 (spec Phase 3A/8/25 — MANUAL mode must show a distinct PASS/FAIL for "backup
            // duration," not just nominal capacity): the check above compares nominal kWh against
            // a flat requirement; this one compares the REAL simulated outage duration
            // (`estimatedBackupHours`, an actual grid-disconnected day-simulation — A54) against
            // the hours the installer actually asked for. A64 already computes
            // `batteryBackupTargetMet` for exactly this comparison but it was never wired into this
            // review screen — a system whose nominal kWh check above passes can still fail this one
            // (e.g. its own DOD/discharge-power limits eat into the simulated runtime).
            EngineeringCheck(
                label = "Battery backup meets requested duration (simulated)",
                pass = preview.batteryBackupTargetMet != false,
                detail = if (preview.batteryBackupTargetMet == false) {
                    "Simulated backup: %.1f h — %.0f h requested. %s"
                        .format(preview.estimatedBackupHours, inputs.backupHours, preview.estimatedBackupReason)
                } else null
            ),
            EngineeringCheck(
                label = "Battery power suitable for peak load",
                pass = preview.totalBatteryKwh <= 0.0 || batteryMaxDischargeKw >= peakLoadKw - 0.05,
                detail = if (preview.totalBatteryKwh > 0.0 && batteryMaxDischargeKw < peakLoadKw - 0.05) {
                    "Peak load (%.1f kW) may exceed the battery's typical continuous discharge rate (%.1f kW)."
                        .format(peakLoadKw, batteryMaxDischargeKw)
                } else null
            ),
            EngineeringCheck(
                label = "PV array within inverter's max PV input power",
                pass = pvCompat.powerOk,
                detail = if (!pvCompat.powerOk) {
                    "%.2f kWp of panels exceeds the inverter's real maximum PV input, %.2f kW."
                        .format(pvCompat.arrayKw, pvCompat.requiredMaxPvKw)
                } else "%.2f kWp of %.2f kW max PV input.".format(pvCompat.arrayKw, pvCompat.requiredMaxPvKw)
            ),
            EngineeringCheck(
                label = "Series string Voc within inverter's max PV voltage",
                pass = pvCompat.vocOk,
                detail = if (!pvCompat.vocOk) {
                    "Cold-corrected series Voc %.0fV exceeds the inverter's maximum PV voltage %.0fV."
                        .format(pvCompat.stringVocV, pvCompat.mpptVoltageMaxV)
                } else null
            ),
            EngineeringCheck(
                label = "Series string Vmp within MPPT operating range",
                pass = pvCompat.vmpOk,
                detail = if (!pvCompat.vmpOk) {
                    "Series Vmp %.0fV is below the inverter's minimum MPPT operating voltage.".format(pvCompat.stringVmpV)
                } else null
            ),
            // A71 (spec Phase 6): the MPPT range's real per-model CEILING — a separate, lower
            // figure from the VOC check's own maxPvV ceiling above (see
            // EquipmentSelectionEngine.PanelCompatibilityResult.vmpUpperOk's own doc). Without
            // this, a design failing only this one would show every other check here passing while
            // it was actually electrically invalid.
            EngineeringCheck(
                label = "Series string Vmp within MPPT tracking-range ceiling",
                pass = pvCompat.vmpUpperOk,
                detail = if (!pvCompat.vmpUpperOk) {
                    "Series Vmp %.0fV exceeds the inverter's real MPPT tracking-range ceiling.".format(pvCompat.stringVmpV)
                } else null
            ),
            EngineeringCheck(
                label = "Series string short-circuit current within MPPT current limits",
                pass = pvCompat.iscOk,
                detail = if (!pvCompat.iscOk) {
                    "Series Isc %.1fA exceeds the inverter's implied max PV current — this is the panel's own current (voltage adds in series, current does not multiply by panel count)."
                        .format(pvCompat.stringIscA)
                } else null
            ),
            // A71: the string's real OPERATING current (Imp), against the inverter's real
            // continuous max input current per tracker — a different, typically lower datasheet
            // figure from the short-circuit check above (see PanelCompatibilityResult.impOk's own
            // doc).
            EngineeringCheck(
                label = "Series string operating current within continuous MPPT current limits",
                pass = pvCompat.impOk,
                detail = if (!pvCompat.impOk) {
                    "Series Imp %.1fA exceeds the inverter's real continuous max PV input current per tracker."
                        .format(pvCompat.stringImpA)
                } else null
            ),
            // A54 (spec §22–23): a real simulated day, starting from the battery's reserve floor,
            // checking whether the selected PV array can actually recharge it back to a usable SOC
            // by early afternoon — calculated, not assumed passing just because sizing "looks" big enough.
            EngineeringCheck(
                label = "Battery can recharge to a usable SOC by ~2 PM",
                pass = preview.batteryRechargeTargetMet != false,
                detail = if (preview.batteryRechargeTargetMet == false) {
                    val socText = "%.0f%%".format(preview.batteryRechargeSocAt2pmPercent ?: 0f)
                    val whenText = preview.batteryRechargeHour?.let { "reached later, around ${formatSimTime(it)}" }
                        ?: "never fully recharged within the simulated day"
                    "⚠ BATTERY RECHARGE TARGET NOT MET — only $socText SOC by 2 PM ($whenText). Consider more PV, a smaller battery, or reduced daytime load."
                } else null
            )
        )
    }

    val confidenceChecks = remember(inputs) {
        listOf(
            "Customer details" to (inputs.customerName.isNotBlank() && inputs.customerContact.isNotBlank()),
            "Property location" to inputs.parish.isNotBlank(),
            "Energy usage grounded in real data" to
                (inputs.quoteMode != QuoteMode.GUIDED || (inputs.usageMode == UsageMode.KWH && inputs.avgKwh > 0)),
            "Peak Sun Hours confirmed for this site" to (inputs.peakSunHours != 5.5),
            "Exact hardware model selected" to (inputs.quoteMode == QuoteMode.MANUAL)
        )
    }
    val confidencePercent = confidenceChecks.count { it.second } * 100 / confidenceChecks.size
    val issueCount = checks.count { !it.pass }

    // A49 — MANUAL mode never has its equipment silently replaced (spec §4/§29), so an undersized
    // choice instead blocks Calculate (see WizardScreen's SystemCalculator.hasUnacknowledgedManualWarnings)
    // until the installer explicitly reviews it here: change the equipment, or accept the warning.
    val manualWarnings = remember(preview) { listOfNotNull(preview.manualInverterWarning, preview.manualBatteryWarning) }
    val manualReviewBlocked = inputs.quoteMode == QuoteMode.MANUAL &&
        manualWarnings.any { it !in inputs.manualWarningsAcknowledged }

    var systemCheckOpen by remember { mutableStateOf(issueCount > 0) }
    var calculationsOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("SYSTEM DESIGN", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary, fontWeight = FontWeight.SemiBold)
        Text("Your recommended solar system", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary, modifier = Modifier.padding(bottom = 4.dp))

        if (manualReviewBlocked) {
            SectionCard(title = "") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚠", color = palette.warningRedText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("SYSTEM REVIEW REQUIRED", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                }
                Column(modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                    manualWarnings.forEach { w ->
                        Text(w, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary, modifier = Modifier.padding(vertical = 3.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LumixSecondaryButton(
                        text = if (preview.manualInverterWarning != null) "CHANGE INVERTER" else "CHANGE BATTERY",
                        onClick = { onJumpToStep(if (preview.manualInverterWarning != null) 10 else 11) },
                        modifier = Modifier.weight(1f)
                    )
                    LumixPrimaryButton(
                        text = "ACCEPT WITH WARNING",
                        onClick = { onUpdate { it.copy(manualWarningsAcknowledged = it.manualWarningsAcknowledged + manualWarnings) } },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (inputs.quoteMode != QuoteMode.MANUAL) {
            SectionCard(title = "") {
                Text("CALCULATED REQUIREMENTS → RECOMMENDED EQUIPMENT", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.solarYellowText, modifier = Modifier.padding(bottom = 10.dp))
                RequirementRow("☀ SOLAR", "≈ %.2f kW required".format(preview.requiredPvKw), "${preview.panelCount} × ${preview.panelWatts} W = %.2f kW".format(preview.pvKw), preview.panelSelectionReason)
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                RequirementRow("⚡ INVERTER", "≈ %.2f kW required".format(preview.requiredInverterKw), "${preview.inverterName}", preview.inverterSelectionReason)
                if (preview.totalBatteryKwh > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    RequirementRow("🔋 BATTERY", "≈ %.1f kWh usable required".format(preview.requiredBatteryUsableKwh), "%.1f kWh".format(preview.totalBatteryKwh), preview.batterySelectionReason)
                }
            }
        }

        SectionCard(title = "") {
            SummaryRow("SOLAR", "%.2f kW".format(preview.pvKw), "${preview.panelCount} × ${preview.panelWatts} W panels")
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            SummaryRow("INVERTER", "%.1f kW".format(preview.inverterKw), preview.inverterName)
            if (preview.totalBatteryKwh > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                SummaryRow(
                    "BATTERY", "%.2f kWh".format(preview.totalBatteryKwh),
                    (if (preview.estimatedBackupSufficient) "Estimated backup: %.0f+ h".format(preview.estimatedBackupHours) else "Estimated backup: %.1f h".format(preview.estimatedBackupHours))
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            SummaryRow("LOAD", "%.1f kWh/day".format(preview.designDailyKwh), "Peak %.2f kW".format(peakLoadKw))
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            SummaryRow("BACKUP COVERAGE", coverageLabel(inputs.backupCoverage), null)
            if (preview.totalBatteryKwh > 0 && preview.estimatedBackupReason.isNotBlank()) {
                Text(
                    preview.estimatedBackupReason,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        SectionCard(title = "") {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { systemCheckOpen = !systemCheckOpen },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (issueCount == 0) "✓" else "⚠",
                        color = if (issueCount == 0) palette.energyGreenText else palette.solarAmberText,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (issueCount == 0) "SYSTEM CHECK — all clear" else "SYSTEM CHECK — $issueCount need review",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                }
                Text(if (systemCheckOpen) "▾" else "▸", style = MaterialTheme.typography.titleMedium, color = palette.textSecondary)
            }
            AnimatedVisibility(visible = systemCheckOpen, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    checks.forEach { check -> CheckRow(label = check.label, pass = check.pass, detail = check.detail) }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    Text("DESIGN CONFIDENCE — $confidencePercent%", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary, fontWeight = FontWeight.SemiBold)
                    Text(
                        "How much of this design rests on real project data vs. this estimator's own defaults — not an engineering certification.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                    )
                    confidenceChecks.forEach { (label, ok) -> ConfidenceRow(label = label, met = ok) }
                }
            }
        }

        SectionCard(title = "") {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { calculationsOpen = !calculationsOpen },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("VIEW CALCULATIONS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Text(if (calculationsOpen) "▾" else "▸", style = MaterialTheme.typography.titleMedium, color = palette.textSecondary)
            }
            AnimatedVisibility(visible = calculationsOpen, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column {
                        Text("LOAD", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.solarYellowText, modifier = Modifier.padding(bottom = 6.dp))
                        ReviewRow("Peak load", "%.2f kW".format(peakLoadKw))
                        ReviewRow("Estimated daily energy", "%.1f kWh".format(preview.designDailyKwh))
                        ReviewRow("Estimated monthly energy", "%.0f kWh".format(preview.designDailyKwh * 30))
                    }
                    Column {
                        Text("SOLAR", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.solarYellowText, modifier = Modifier.padding(bottom = 6.dp))
                        ReviewRow("PV capacity", "%.2f kWp".format(preview.pvKw))
                        ReviewRow("Peak Sun Hours", "%.1f h".format(inputs.peakSunHours))
                        ReviewRow("Estimated daily solar", "%.1f kWh".format(preview.pvKw * inputs.peakSunHours))
                        ReviewRow("Estimated annual solar", "%.2f MWh".format(preview.pvKw * inputs.peakSunHours * 365 / 1000.0))
                    }
                    Column {
                        Text("INVERTER", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.solarYellowText, modifier = Modifier.padding(bottom = 6.dp))
                        ReviewRow("Peak load", "%.2f kW".format(peakLoadKw))
                        ReviewRow("Recommended (peak load + 25% headroom)", "%.2f kW".format(requiredInverterKw))
                        ReviewRow("Selected inverter", "${preview.inverterName} (%.1f kW)".format(preview.inverterKw))
                        ReviewRow("Inverter loading", "%.0f%%".format((peakLoadKw / preview.inverterKw * 100.0).coerceAtMost(999.0)))
                    }
                    if (preview.totalBatteryKwh > 0) {
                        Column {
                            Text("BATTERY", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.solarYellowText, modifier = Modifier.padding(bottom = 6.dp))
                            ReviewRow("Required backup energy", "%.1f kWh".format(preview.batteryRequiredKwh))
                            ReviewRow("Selected battery capacity", "%.1f kWh".format(preview.totalBatteryKwh))
                            ReviewRow("Usable capacity (80% DOD)", "%.1f kWh".format(preview.totalBatteryKwh * 0.8))
                            ReviewRow("Depth of discharge", "80% (20% reserve)")
                            ReviewRow("Maximum discharge power", "%.1f kW".format(batteryMaxDischargeKw))
                            ReviewRow(
                                "Estimated backup duration",
                                if (preview.estimatedBackupSufficient) "%.0f+ h".format(preview.estimatedBackupHours) else "%.1f h".format(preview.estimatedBackupHours)
                            )
                            Text(
                                "From an actual simulated outage (grid disconnected at dusk, battery starting full) — not a flat energy/average-load ratio.",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    Column {
                        Text("GRID", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.solarYellowText, modifier = Modifier.padding(bottom = 6.dp))
                        Text(
                            "Jamaica's standard residential utility service. Voltage and frequency are fixed by JPS; the current limit is your main-breaker rating and can be adjusted below.",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        ReviewRow("Grid voltage", "220V / 110V split-phase")
                        ReviewRow("System type", preview.effectiveSystemMode.name.lowercase().replaceFirstChar { it.uppercase() })
                        ReviewRow("Frequency", "50 Hz")
                        NumberField(
                            label = "Grid current limit",
                            value = gridServiceAmps,
                            onValueChange = { v -> scope.launch { settingsRepository.setDefaultGridServiceAmps(v.coerceIn(10.0, 200.0)) } },
                            allowDecimal = false,
                            suffix = "A",
                            supportingText = "Applies to new simulations for this system; also editable in Settings.",
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun coverageLabel(coverage: BackupCoverage): String = when (coverage) {
    BackupCoverage.ESSENTIALS -> "Essentials"
    BackupCoverage.CRITICAL_LOADS -> "Critical Loads"
    BackupCoverage.FULL -> "Full"
    BackupCoverage.MOST_LOAD -> "Most Load"
    BackupCoverage.CUSTOM -> "Custom"
}

@Composable
private fun SummaryRow(label: String, value: String, detail: String?) {
    val palette = LocalLumixPalette.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, fontWeight = FontWeight.SemiBold)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, modifier = Modifier.padding(top = 1.dp))
            }
        }
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
    }
}

/** A49 — LOAD-BASED/GUIDED "required vs recommended" row, with the plain-language selection reason underneath. */
@Composable
private fun RequirementRow(label: String, required: String, recommended: String, reason: String?) {
    val palette = LocalLumixPalette.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, fontWeight = FontWeight.SemiBold)
                Text(required, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(recommended, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Text("✓", color = palette.energyGreenText, fontWeight = FontWeight.Bold)
            }
        }
        if (reason != null) {
            Text(reason, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    val palette = LocalLumixPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
    }
}

/** A confidence signal — a soft "provided vs. default" cue, not a pass/fail judgment. */
@Composable
private fun ConfidenceRow(label: String, met: Boolean) {
    val palette = LocalLumixPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (met) palette.energyGreen else palette.outline)
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (met) palette.textPrimary else palette.textSecondary
        )
    }
}

@Composable
private fun CheckRow(label: String, pass: Boolean, detail: String?) {
    val palette = LocalLumixPalette.current
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (pass) "✓" else "⚠", color = if (pass) palette.energyGreenText else palette.solarAmberText, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
        }
        if (detail != null) {
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = palette.solarAmberText,
                modifier = Modifier.padding(start = 24.dp, top = 2.dp)
            )
        }
    }
}
