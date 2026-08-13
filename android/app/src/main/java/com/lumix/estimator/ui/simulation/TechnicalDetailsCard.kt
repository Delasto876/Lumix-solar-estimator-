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

        TechRow("PV Voltage", "%.0f V".format(readout.pvVoltage), "PV Current", "%.2f A".format(readout.pvCurrent))
        TechRow(
            "Ideal Output", "%.2f kW".format(readout.potentialPvKw),
            "Actual Output", "%.2f kW".format(readout.pvPowerKw)
        )
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
        TechRow("Energy Today", "%.1f kWh".format(readout.energyTodayKwh), "Energy This Month (est.)", "%.0f kWh".format(readout.energyMonthEstKwh))

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
