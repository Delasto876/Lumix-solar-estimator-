package com.lumix.estimator.ui.simulation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.EquipmentSpecs
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.simulation.SimFrame
import com.lumix.estimator.domain.simulation.SimSystemConfig
import com.lumix.estimator.domain.simulation.SimulationEngine
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixRadius

enum class InspectTarget(val glyph: String, val title: String, val shortLabel: String) {
    PANELS("🟦", "Solar Array", "Panels"),
    INVERTER("⚡", "Inverter", "Inverter"),
    BATTERY("🔋", "Battery", "Battery"),
    GRID("🔌", "JPS Grid", "Grid"),
    HOME("🏠", "Your Home", "Home")
}

@Composable
fun InspectChipRow(
    hasBattery: Boolean,
    onSelect: (InspectTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLumixPalette.current
    val targets = InspectTarget.entries.filter { it != InspectTarget.BATTERY || hasBattery }

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        targets.forEach { target ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(LumixRadius.md))
                    .clickable { onSelect(target) }
                    .padding(vertical = 8.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(target.glyph, style = MaterialTheme.typography.titleLarge)
                Text(target.shortLabel, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
        }
    }
}

@Composable
fun ComponentDetailContent(
    target: InspectTarget,
    frame: SimFrame,
    config: SimSystemConfig,
    inputs: QuoteInputs?,
    gridConnected: Boolean,
    gridServiceAmps: Double = SimulationEngine.DEFAULT_GRID_SERVICE_AMPS,
    modifier: Modifier = Modifier
) {
    val palette = LocalLumixPalette.current
    val lines = detailLines(target, frame, config, inputs, gridConnected, gridServiceAmps)

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row {
            Text(target.glyph, style = MaterialTheme.typography.headlineSmall)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(lines.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Text(lines.subtitle, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        lines.rows.forEach { (label, value) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            }
        }
        if (lines.note != null) {
            Text(
                lines.note,
                style = MaterialTheme.typography.labelSmall,
                color = palette.warningRedText,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

private data class DetailLines(
    val title: String,
    val subtitle: String,
    val rows: List<Pair<String, String>>,
    val note: String? = null
)

private fun fmtKw(v: Double) = "%.2f kW".format(v)

private fun detailLines(
    target: InspectTarget,
    f: SimFrame,
    config: SimSystemConfig,
    inputs: QuoteInputs?,
    gridConnected: Boolean,
    gridServiceAmps: Double
): DetailLines = when (target) {
    InspectTarget.PANELS -> {
        val spec = EquipmentSpecs.panelSpecFor(config.panelWatts)
        DetailLines(
            title = "Solar Array",
            subtitle = if (spec != null) "${config.panelCount} × ${spec.brand} ${spec.model}" else "${config.panelCount} × ${config.panelWatts} W",
            rows = listOf(
                "Total capacity" to fmtKw(config.pvCapacityKw),
                "Ideal output (no losses)" to fmtKw(f.potentialPvKw),
                "Actual output" to fmtKw(f.pvKw),
                "Cell temp" to "%.0f°C".format(f.cellTempC),
                "To home" to fmtKw(f.solarToHouseKw),
                "To battery" to fmtKw(f.solarToBatteryKw),
                "Curtailed (unused)" to fmtKw(f.curtailedSolarKw)
            ) + listOfNotNull(
                spec?.vmpV?.let { "Vmp (per panel)" to "%.1f V".format(it) },
                spec?.vocV?.let { "Voc (per panel)" to "%.1f V".format(it) },
                spec?.efficiencyPercent?.let { "Module efficiency" to "%.1f%%".format(it) }
            ),
            note = spec?.let { "Reference spec: ${it.model} — ${it.status.lowercase()}." }
        )
    }
    InspectTarget.INVERTER -> {
        val spec = EquipmentSpecs.inverterSpecFor(config.inverterKw)
        val overLimitNote = if (f.pvKw >= config.inverterKw - 0.01) "Producing at the inverter's rated limit." else null
        val frequencyNote = when {
            spec == null -> null
            spec.jamaicaFrequencyConfirmed -> null
            spec.frequencyUnverified -> "Reference unit's 50 Hz support is unverified — confirm against the real datasheet before installing in Jamaica."
            else -> "Reference unit's datasheet lists ${spec.frequencyHzRaw} Hz only — verify 50 Hz (Jamaica) compatibility before installing."
        }
        DetailLines(
            title = config.inverterName,
            subtitle = if (spec != null) "${spec.brand} ${spec.model}" else "${"%.1f".format(config.inverterKw)} kW hybrid inverter",
            rows = listOf(
                "Current output" to fmtKw(f.pvKw),
                "Home load" to fmtKw(f.houseLoadKw),
                "Battery" to fmtKw(f.batteryPowerKw),
                "Grid" to fmtKw(f.gridPowerKw)
            ) + listOfNotNull(
                spec?.mpptCount?.let { "MPPT inputs" to it.toString() },
                spec?.maxPvW?.let { "Max PV input" to "$it W" },
                spec?.batteryVoltageRange?.let { "Battery voltage range" to it },
                spec?.efficiencyPercent?.let { "Rated efficiency" to "%.1f%%".format(it) }
            ),
            note = listOfNotNull(overLimitNote, frequencyNote).takeIf { it.isNotEmpty() }?.joinToString(" ")
        )
    }
    InspectTarget.BATTERY -> {
        val chargingRate = f.batteryPowerKw
        val cutoffFraction = SimulationEngine.BATTERY_MIN_SOC_FRACTION
        val runtimeHours = if (chargingRate < -0.01) {
            val availableKwh = (f.batterySocKwh - config.batteryCapacityKwh * cutoffFraction).coerceAtLeast(0.0)
            availableKwh / (-chargingRate)
        } else null
        val spec = EquipmentSpecs.batterySpecFor(config.batteryName)
        DetailLines(
            title = if (spec != null) "${spec.brand} ${spec.model}" else config.batteryName ?: "Battery bank",
            subtitle = "${"%.1f".format(config.batteryCapacityKwh)} kWh capacity",
            rows = listOfNotNull(
                "Charge" to "${f.batterySocPercent.toInt()}%",
                "Stored energy" to "${"%.1f".format(f.batterySocKwh)} kWh",
                when {
                    chargingRate > 0.01 -> "Charging" to "+${fmtKw(chargingRate)}"
                    chargingRate < -0.01 -> "Discharging" to fmtKw(chargingRate)
                    else -> "Status" to "Idle"
                },
                runtimeHours?.let { "Estimated runtime" to formatHours(it) },
                "Reserve cutoff" to "${(cutoffFraction * 100).toInt()}%",
                spec?.let { "Chemistry" to it.chemistry },
                spec?.let { "Max charge / discharge" to "${it.maxChargeA}A / ${it.maxDischargeA}A per unit" },
                spec?.let { "Rated cycle life" to "${it.cycleLife} cycles" }
            )
        )
    }
    InspectTarget.GRID -> {
        val serviceKw = gridServiceAmps * SimulationEngine.GRID_SERVICE_VOLTAGE / 1000.0
        DetailLines(
            title = "JPS Grid",
            subtitle = if (!config.gridConnectable) "This system has no grid connection" else if (gridConnected) "Connected" else "Disconnected — running on backup",
            rows = listOf(
                "To home" to fmtKw(f.gridToHouseKw),
                "Charging battery" to fmtKw(f.gridToBatteryKw),
                "Service limit" to "${gridServiceAmps.toInt()}A (${fmtKw(serviceKw)})"
            ),
            note = when {
                f.unmetLoadKw <= 0.01 -> null
                !config.gridConnectable || !gridConnected -> "Demand exceeds solar + battery, and the grid is unavailable — some load can't be met."
                else -> "Demand exceeds solar + battery, and the ${gridServiceAmps.toInt()}A utility service limit — some load can't be met."
            }
        )
    }
    InspectTarget.HOME -> DetailLines(
        title = "Your Home",
        subtitle = inputs?.propertyType?.label ?: "Current consumption",
        rows = listOf(
            "Current load" to fmtKw(f.houseLoadKw),
            "From solar" to fmtKw(f.solarToHouseKw),
            "From battery" to fmtKw(f.batteryToHouseKw),
            "From grid" to fmtKw(f.gridToHouseKw)
        ),
        note = if (f.unmetLoadKw > 0.01) "${fmtKw(f.unmetLoadKw)} of demand can't be supplied right now." else null
    )
}

private fun formatHours(hours: Double): String {
    if (hours >= 99) return "99+ h"
    val h = hours.toInt()
    val m = ((hours - h) * 60).toInt()
    return "${h}h ${m}m"
}
