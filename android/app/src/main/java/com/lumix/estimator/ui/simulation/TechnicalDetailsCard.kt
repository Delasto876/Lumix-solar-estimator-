package com.lumix.estimator.ui.simulation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.simulation.SystemLosses
import com.lumix.estimator.domain.simulation.TechnicalReadout
import com.lumix.estimator.ui.theme.LocalLumixPalette

@Composable
fun TechnicalDetailsContent(readout: TechnicalReadout, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Typical values for a Jamaica-style split-phase 110V/220V, 50Hz grid and a 48V battery bus — modeled, not live telemetry. " +
                "Fixed system losses: inverter %.0f%%, DC wiring %.0f%%, soiling/availability %.0f%%.".format(
                    (1.0 - SystemLosses.INVERTER_EFFICIENCY) * 100.0,
                    (1.0 - SystemLosses.DC_WIRING_EFFICIENCY) * 100.0,
                    (1.0 - SystemLosses.SOILING_AVAILABILITY_EFFICIENCY) * 100.0
                ),
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )

        TechRow(
            if (readout.mpptStrings.size > 1) "PV Voltage (blended)" else "PV Voltage",
            "%.0f V".format(readout.pvVoltage), "PV Current", "%.2f A".format(readout.pvCurrent)
        )
        TechRow(
            "Ideal (no losses)", "%.2f kW".format(readout.potentialPvKw),
            "Available (after losses)", "%.2f kW".format(readout.harvestablePvKw)
        )
        TechRow(
            "Harvested (actual)", "%.2f kW".format(readout.pvPowerKw),
            "Throttled (battery full)", "%.2f kW".format(readout.pvCurtailedKw)
        )
        if (readout.pvCurtailedKw > 0.01) {
            Text(
                "The battery is full and there's nowhere for the surplus to go, so the inverter walks the array back off its maximum-power point — harvested output drops to just what the house and battery can take. The throttled figure is what the array could still make if there were somewhere to put it, not power being produced and dumped.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary
            )
        }
        if (readout.mpptStrings.size > 1) {
            Text(
                "${readout.mpptStrings.size} independent MPPT trackers — each carries its own string voltage, not one shared figure.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary
            )
            readout.mpptStrings.forEach { mppt ->
                TechRow(
                    "MPPT ${mppt.index} (${mppt.panelCount} panels)",
                    if (mppt.isActive) "%.0f V".format(mppt.operatingVoltageV) else "0 V",
                    "MPPT ${mppt.index} power",
                    "%.2f kW".format(mppt.powerKw)
                )
            }
        }
        TechRow(
            "Cell Temp", "%.0f°C".format(readout.cellTempC),
            "Temp Loss", "%.1f%%".format(readout.temperatureLossPercent)
        )
        TechRow("Battery Voltage", "%.1f V".format(readout.batteryVoltage), "Battery Current", "%.2f A".format(readout.batteryCurrent))
        TechRow("Inverter Output", "%.2f kW".format(readout.inverterOutputKw), "Frequency", if (readout.frequencyHz > 0) "%.2f Hz".format(readout.frequencyHz) else "—")
        TechRow("Grid 110V Current", "%.2f A".format(readout.gridLowCurrent), "Grid 220V Current", "%.2f A".format(readout.gridHighCurrent))
        TechRow(
            "Utility Service Limit", "${readout.gridServiceAmps.toInt()} A",
            "Service Used", if (readout.gridServiceUtilization > 0f) "%.0f%%".format(readout.gridServiceUtilization * 100f) else "—"
        )
        TechRow("Energy Today (harvested)", "%.1f kWh".format(readout.energyTodayKwh), "Available Today", "%.1f kWh".format(readout.energyTodayAvailableKwh))
        TechRow("Energy This Month (est.)", "%.0f kWh".format(readout.energyMonthEstKwh), "Throttled Today", "%.1f kWh".format((readout.energyTodayAvailableKwh - readout.energyTodayKwh).coerceAtLeast(0.0)))

        Text(
            "Neutral current is the imbalance between the two 110V legs — assumes lighting/outlet circuits are spread evenly across both, same as a real panel schedule.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )
        TechRow("Neutral Current", "%.2f A".format(readout.gridNeutralCurrent), "Worst-Case Startup Surge", "%.2f kW".format(readout.startupSurgeKw))
        Text(
            "Startup surge assumes every active motor (fridge, AC, pumps, washer/dryer) starts at the exact same instant — an edge case, not a sustained load. Most hybrid inverters tolerate roughly 2x their continuous rating for a few seconds.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )
        TechStat(
            "Energy Balance Check",
            if (readout.energyBalanceErrorKw < 0.005) "OK" else "%.3f kW off".format(readout.energyBalanceErrorKw)
        )
        Text(
            "Verifies every watt of solar/battery/grid is accounted for at this instant — should always read OK. A nonzero figure would mean the simulation invented or lost energy somewhere.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )
    }
}

@Composable
private fun TechRow(label1: String, value1: String, label2: String, value2: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TechStat(label1, value1, Modifier.weight(1f))
        TechStat(label2, value2, Modifier.weight(1f))
    }
}

@Composable
private fun TechStat(label: String, value: String, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = palette.textPrimary)
    }
}
