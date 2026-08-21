package com.lumix.estimator.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.commercial.LoadDefinition
import com.lumix.estimator.domain.commercial.LoadInstance
import com.lumix.estimator.ui.theme.LocalLumixPalette
import kotlin.math.roundToInt

/**
 * Phase 32 (shared by [com.lumix.estimator.ui.wizard.steps.StepCommercialIndustrialDesign]'s
 * "Loads" step and [com.lumix.estimator.ui.simulation.AppliancesSheetContent]'s Commercial/
 * Industrial branch — "for the appliance section... if commercial or industrial choose those in
 * appliances picker"): one catalog load type, always visible, quantity 0 = not included — see
 * [CatalogLoadRow]'s own doc. Extracted out of the wizard step (where it originated, Phase 31) so
 * the simulation's Appliances sheet can show the exact same picker instead of maintaining a
 * second, drifting copy.
 */

/** Matches SystemCalculator.acBtuPerWatt(AcInverterType.NON_INVERTER) — commercial/industrial AC loads have no inverter/non-inverter distinction of their own yet, so this uses the same non-inverter ratio residential AC defaults to. */
const val COMMERCIAL_AC_BTU_PER_WATT = 10.0

fun newInstanceFrom(def: LoadDefinition): LoadInstance = LoadInstance(
    definitionId = def.id,
    label = def.label,
    ratedWatts = def.defaultRatedWatts,
    voltage = def.defaultVoltage,
    phase = def.defaultPhase,
    frequencyHz = def.defaultFrequencyHz,
    powerFactor = def.defaultPowerFactor,
    operationType = def.defaultOperationType,
    priority = def.defaultPriority,
    startingSurgeWatts = def.defaultStartingSurgeMultiplier?.let { it * def.defaultRatedWatts },
    btu = def.defaultBtu
)

/** HH:MM time entry — the decimal-hour figures every Shift/BusinessHours/LoadInstance.typicalStartHour field is stored as underneath are hard to type directly, so this splits into two IntFields and converts. */
@Composable
fun TimeField(label: String, hour: Double, onChange: (Double) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    val h = hour.toInt().coerceIn(0, 23)
    val m = ((hour - h) * 60.0).roundToInt().coerceIn(0, 59)
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IntField(
                label = "HH", value = h,
                onValueChange = { newH -> onChange(newH.coerceIn(0, 23) + m / 60.0) },
                modifier = Modifier.weight(1f)
            )
            IntField(
                label = "MM", value = m,
                onValueChange = { newM -> onChange(h + newM.coerceIn(0, 59) / 60.0) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * One catalog load type, always visible — matching how the residential wizard's
 * `ApplianceType.entries` list already works (one row per type, quantity 0 = not included) instead
 * of an "Add a load" step. Raising the quantity above 0 reveals Watts (or BTU, for AC-type loads),
 * power factor, runtime, and "when it typically starts."
 */
@Composable
fun CatalogLoadRow(def: LoadDefinition, instance: LoadInstance?, onChange: (LoadInstance?) -> Unit) {
    val palette = LocalLumixPalette.current
    val quantity = instance?.quantity ?: 0

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(def.label, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
                Text(
                    if (def.isAcLoad) "${def.defaultBtu?.toInt() ?: 0} BTU typical" else "${def.defaultRatedWatts.toInt()}W typical",
                    style = MaterialTheme.typography.labelSmall, color = palette.textSecondary
                )
            }
            IntField(
                label = "Qty",
                value = quantity,
                onValueChange = { v ->
                    val q = v.coerceAtLeast(0)
                    when {
                        q <= 0 -> onChange(null)
                        instance != null -> onChange(instance.copy(quantity = q))
                        else -> onChange(newInstanceFrom(def).copy(quantity = q))
                    }
                },
                modifier = Modifier.weight(0.6f)
            )
        }
        if (instance != null && quantity > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                if (def.isAcLoad) {
                    NumberField(
                        label = "BTU", value = instance.btu ?: (instance.ratedWatts * COMMERCIAL_AC_BTU_PER_WATT), suffix = "BTU",
                        onValueChange = { v -> onChange(instance.copy(btu = v, ratedWatts = v / COMMERCIAL_AC_BTU_PER_WATT)) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    NumberField(
                        label = "Watts (each)", value = instance.ratedWatts, suffix = "W",
                        onValueChange = { v -> onChange(instance.copy(ratedWatts = v)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                NumberField(
                    label = "Power factor", value = instance.powerFactor,
                    onValueChange = { v -> onChange(instance.copy(powerFactor = v.coerceIn(0.01, 1.0))) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                NumberField(
                    label = "Runtime (hours/day)", value = instance.operatingHoursPerDay, suffix = "h",
                    onValueChange = { v -> onChange(instance.copy(operatingHoursPerDay = v.coerceIn(0.0, 24.0))) },
                    modifier = Modifier.weight(1f)
                )
                TimeField(
                    label = "Typically starts", hour = instance.typicalStartHour ?: 0.0,
                    onChange = { v -> onChange(instance.copy(typicalStartHour = v)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
