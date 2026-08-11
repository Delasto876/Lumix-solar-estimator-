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
import com.lumix.estimator.domain.simulation.TechnicalReadout
import com.lumix.estimator.ui.theme.LocalLumixPalette

@Composable
fun TechnicalDetailsContent(readout: TechnicalReadout, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Typical values for a 110V/60Hz grid and a 48V battery bus — modeled, not live telemetry.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )

        TechRow("PV Voltage", "%.0f V".format(readout.pvVoltage), "PV Current", "%.2f A".format(readout.pvCurrent))
        TechRow("Battery Voltage", "%.1f V".format(readout.batteryVoltage), "Battery Current", "%.2f A".format(readout.batteryCurrent))
        TechRow("Inverter Output", "%.2f kW".format(readout.inverterOutputKw), "Grid Voltage", "%.0f V".format(readout.gridVoltage))
        TechRow("Grid Current", "%.2f A".format(readout.gridCurrent), "Frequency", if (readout.frequencyHz > 0) "%.2f Hz".format(readout.frequencyHz) else "—")
        TechRow("Energy Today", "%.1f kWh".format(readout.energyTodayKwh), "Energy This Month (est.)", "%.0f kWh".format(readout.energyMonthEstKwh))
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
