package com.lumix.estimator.domain.simulation

import com.lumix.estimator.domain.QuoteResult
import com.lumix.estimator.domain.SystemMode
import kotlinx.serialization.Serializable
import kotlin.math.min

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
            // This catalog only carries one battery chemistry (LiFePO4), so there's no
            // per-model spec sheet to read a real charge/discharge rating from yet — 0.5C is
            // a typical continuous rate for that chemistry in residential packs, capped by
            // whatever the paired inverter can actually push/pull regardless of what the
            // battery itself could otherwise handle.
            val batteryRateKw = min(batteryCapacityKwh * 0.5, result.inverterKw.coerceAtLeast(0.1))
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
                batteryMaxChargeKw = batteryRateKw,
                batteryMaxDischargeKw = batteryRateKw,
                batteryChargeEfficiency = 0.95,
                batteryDepthOfDischargeFraction = SimulationEngine.BATTERY_MIN_SOC_FRACTION
            )
        }
    }
}
