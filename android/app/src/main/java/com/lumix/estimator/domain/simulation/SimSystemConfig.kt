package com.lumix.estimator.domain.simulation

import com.lumix.estimator.domain.QuoteResult
import com.lumix.estimator.domain.SystemMode
import kotlinx.serialization.Serializable

/**
 * The simulator's view of a solar system — always derived from a real calculated
 * quote, never hand-entered. This is the only bridge between the estimator's output
 * and the simulation engine.
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
    val peakLoadKw: Double
) {
    companion object {
        fun from(result: QuoteResult): SimSystemConfig {
            return SimSystemConfig(
                pvCapacityKw = result.pvKw,
                panelCount = result.panelCount,
                panelWatts = result.panelWatts,
                inverterKw = result.inverterKw,
                inverterName = result.inverterName,
                batteryCapacityKwh = result.totalBatteryKwh,
                batteryName = result.batteryName,
                hasBattery = result.totalBatteryKwh > 0,
                gridConnectable = result.effectiveSystemMode != SystemMode.OFFGRID,
                avgDailyLoadKwh = result.designDailyKwh,
                peakLoadKw = (result.peakWatts / 1000.0).coerceAtLeast(result.designDailyKwh / 10.0)
            )
        }
    }
}
