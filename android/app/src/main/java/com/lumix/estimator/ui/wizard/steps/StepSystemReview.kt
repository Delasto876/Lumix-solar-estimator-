package com.lumix.estimator.ui.wizard.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumix.estimator.data.SettingsRepository
import com.lumix.estimator.domain.PriceList
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteMode
import com.lumix.estimator.domain.SystemCalculator
import com.lumix.estimator.domain.UsageMode
import com.lumix.estimator.domain.simulation.SimulationEngine
import com.lumix.estimator.ui.components.NumberField
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.heroValueStyle
import kotlin.math.min
import kotlinx.coroutines.launch

private data class EngineeringCheck(val label: String, val pass: Boolean, val detail: String?)

/**
 * A real engineering/system-sizing review, not a summary — recomputes a live preview from the
 * current inputs (no pricing needed for this, so [PriceList.DEFAULT] stands in) and checks it
 * against the hardware actually selected, surfacing anything that doesn't add up before the
 * user reaches pricing. Also shows a data-completeness indicator: how much of this preview
 * rests on real project data vs. this estimator's own defaults — never presented as a
 * certification, just a transparency signal.
 */
@Composable
fun StepSystemReview(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit, settingsRepository: SettingsRepository) {
    val palette = LocalLumixPalette.current
    val scope = rememberCoroutineScope()
    val gridServiceAmps by settingsRepository.defaultGridServiceAmps.collectAsState(initial = SimulationEngine.DEFAULT_GRID_SERVICE_AMPS)
    val preview = remember(inputs) { SystemCalculator.calculate(inputs, PriceList.DEFAULT, PriceList.DEFAULT) }
    val requiredInverterKw = remember(preview) { preview.peakWatts * 1.25 / 1000.0 }
    val batteryMaxDischargeKw = remember(preview) {
        if (preview.totalBatteryKwh > 0) min(preview.totalBatteryKwh * 0.5, preview.inverterKw.coerceAtLeast(0.1)) else 0.0
    }
    val peakLoadKw = preview.peakWatts / 1000.0
    val estimatedBackupHours = remember(inputs, preview) {
        if (preview.batteryRequiredKwh > 0.01) inputs.backupHours * (preview.totalBatteryKwh / preview.batteryRequiredKwh) else 0.0
    }

    val checks = remember(preview, requiredInverterKw, batteryMaxDischargeKw, peakLoadKw) {
        listOf(
            EngineeringCheck(
                label = "Inverter capacity suitable",
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
            EngineeringCheck(
                label = "Battery power suitable for peak load",
                pass = preview.totalBatteryKwh <= 0.0 || batteryMaxDischargeKw >= peakLoadKw - 0.05,
                detail = if (preview.totalBatteryKwh > 0.0 && batteryMaxDischargeKw < peakLoadKw - 0.05) {
                    "Peak load (%.1f kW) may exceed the battery's typical continuous discharge rate (%.1f kW)."
                        .format(peakLoadKw, batteryMaxDischargeKw)
                } else null
            ),
            EngineeringCheck(
                label = "PV array within inverter input limit",
                pass = preview.pvKw <= preview.inverterKw * 1.3 + 0.05,
                detail = if (preview.pvKw > preview.inverterKw * 1.3 + 0.05) {
                    "%.1f kWp of panels is more than 130%% of the %.1f kW inverter's rated input."
                        .format(preview.pvKw, preview.inverterKw)
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

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "") {
            Text(
                "DESIGN CONFIDENCE",
                style = MaterialTheme.typography.labelMedium,
                color = palette.textSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "$confidencePercent%",
                style = heroValueStyle(size = 56.sp),
                color = palette.textPrimary,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                "How much of this design rests on real project data vs. this estimator's own defaults — not an engineering certification.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
            )
            confidenceChecks.forEach { (label, ok) -> ConfidenceRow(label = label, met = ok) }
        }

        SectionCard(title = "Engineering checks") {
            checks.forEach { check -> CheckRow(label = check.label, pass = check.pass, detail = check.detail) }
        }

        SectionCard(title = "Load") {
            ReviewRow("Peak load", "%.2f kW".format(peakLoadKw))
            ReviewRow("Estimated daily energy", "%.1f kWh".format(preview.designDailyKwh))
            ReviewRow("Estimated monthly energy", "%.0f kWh".format(preview.designDailyKwh * 30))
        }

        SectionCard(title = "Solar") {
            ReviewRow("PV capacity", "%.2f kWp".format(preview.pvKw))
            ReviewRow("Peak Sun Hours", "%.1f h".format(inputs.peakSunHours))
            ReviewRow("Estimated daily solar", "%.1f kWh".format(preview.pvKw * inputs.peakSunHours))
            ReviewRow("Estimated annual solar", "%.2f MWh".format(preview.pvKw * inputs.peakSunHours * 365 / 1000.0))
        }

        SectionCard(title = "Inverter") {
            ReviewRow("Peak load", "%.2f kW".format(peakLoadKw))
            ReviewRow("Recommended (peak load + 25% headroom)", "%.2f kW".format(requiredInverterKw))
            ReviewRow("Selected inverter", "${preview.inverterName} (%.1f kW)".format(preview.inverterKw))
            ReviewRow("Inverter loading", "%.0f%%".format((peakLoadKw / preview.inverterKw * 100.0).coerceAtMost(999.0)))
        }

        if (preview.totalBatteryKwh > 0) {
            SectionCard(title = "Battery") {
                ReviewRow("Required backup energy", "%.1f kWh".format(preview.batteryRequiredKwh))
                ReviewRow("Selected battery capacity", "%.1f kWh".format(preview.totalBatteryKwh))
                ReviewRow("Usable capacity (80% DOD)", "%.1f kWh".format(preview.totalBatteryKwh * 0.8))
                ReviewRow("Depth of discharge", "80% (20% reserve)")
                ReviewRow("Maximum discharge power", "%.1f kW".format(batteryMaxDischargeKw))
                ReviewRow("Estimated backup duration", "%.1f h".format(estimatedBackupHours))
            }
        }

        SectionCard(title = "Grid") {
            Text(
                "Jamaica's standard residential utility service. Voltage and frequency are fixed by JPS; the current limit is your main-breaker rating and can be adjusted below.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary
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

@Composable
private fun ReviewRow(label: String, value: String) {
    val palette = LocalLumixPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
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
    Column {
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
