package com.lumix.estimator.domain.simulation

import com.lumix.estimator.domain.EquipmentSpecs
import com.lumix.estimator.domain.QuoteResult
import com.lumix.estimator.domain.SystemMode
import kotlinx.serialization.Serializable
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The simulator's view of a solar system — always derived from a real calculated
 * quote, never hand-entered. This is the only bridge between the estimator's output
 * and the simulation engine: every quantity the engine simulates against (PV, inverter,
 * battery capacity *and* its charge/discharge power limits) is read from here, not
 * re-derived or hardcoded separately inside [SimulationEngine].
 */
@Serializable
data class SimSystemConfig(
    val pvCapacityKw: Double,
    val panelCount: Int,
    val panelWatts: Int,
    val inverterKw: Double,
    val inverterName: String,
    val batteryCapacityKwh: Double,
    val batteryName: String?,
    val hasBattery: Boolean,
    val gridConnectable: Boolean,
    val avgDailyLoadKwh: Double,
    val peakLoadKw: Double,
    /** Rated maximum continuous charge power, before the SOC-dependent taper curve. */
    val batteryMaxChargeKw: Double,
    /** Rated maximum continuous discharge power, before the SOC-dependent taper curve. */
    val batteryMaxDischargeKw: Double,
    /** Round-trip charge efficiency (energy actually stored per kWh pushed into the battery). */
    val batteryChargeEfficiency: Double,
    /** Depth-of-discharge reserve floor as a 0..1 fraction of capacity (e.g. 0.20 = never below 20%). */
    val batteryDepthOfDischargeFraction: Double
) {
    companion object {
        fun from(result: QuoteResult): SimSystemConfig {
            val batteryCapacityKwh = result.totalBatteryKwh
            val inverterCeilingKw = result.inverterKw.coerceAtLeast(0.1)
            // Real per-model charge/discharge current, from EquipmentSpecs (2026 spec library) —
            // scaled by how many physical units this system actually has (inferred by comparing
            // the quoted total capacity against one real unit's own rated capacity, since the
            // catalog's own tier label, e.g. "15 kWh", doesn't always match the real unit's exact
            // rated figure, e.g. 16.07 kWh). Charge and discharge are no longer forced to match
            // each other — the real 10kWh unit alone is 150A charge / 200A discharge, genuinely
            // asymmetric. Either way, both are still capped by what the paired inverter can
            // actually push/pull.
            val matchedBattery = EquipmentSpecs.batterySpecFor(result.batteryName)
            val (batteryMaxChargeKw, batteryMaxDischargeKw) = if (matchedBattery != null) {
                val units = (batteryCapacityKwh / matchedBattery.ratedEnergyKwh).roundToInt().coerceAtLeast(1)
                val chargeKw = matchedBattery.maxChargeA * matchedBattery.voltageV / 1000.0 * units
                val dischargeKw = matchedBattery.maxDischargeA * matchedBattery.voltageV / 1000.0 * units
                chargeKw.coerceAtMost(inverterCeilingKw) to dischargeKw.coerceAtMost(inverterCeilingKw)
            } else {
                // No confirmed real spec for this battery tier — fall back to the same
                // conservative generic 0.5C estimate this app always used.
                val fallback = min(batteryCapacityKwh * 0.5, inverterCeilingKw)
                fallback to fallback
            }
            return SimSystemConfig(
                pvCapacityKw = result.pvKw,
                panelCount = result.panelCount,
                panelWatts = result.panelWatts,
                inverterKw = result.inverterKw,
                inverterName = result.inverterName,
                batteryCapacityKwh = batteryCapacityKwh,
                batteryName = result.batteryName,
                hasBattery = batteryCapacityKwh > 0,
                gridConnectable = result.effectiveSystemMode != SystemMode.OFFGRID,
                avgDailyLoadKwh = result.designDailyKwh,
                peakLoadKw = (result.peakWatts / 1000.0).coerceAtLeast(result.designDailyKwh / 10.0),
                batteryMaxChargeKw = batteryMaxChargeKw,
                batteryMaxDischargeKw = batteryMaxDischargeKw,
                batteryChargeEfficiency = 0.95,
                batteryDepthOfDischargeFraction = SimulationEngine.BATTERY_MIN_SOC_FRACTION
            )
        }
    }
}
