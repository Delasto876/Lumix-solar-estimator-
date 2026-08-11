package com.lumix.estimator.domain.simulation

import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteResult
import com.lumix.estimator.domain.SystemMode
import kotlinx.serialization.Serializable

/**
 * The simulator's view of a solar system — always derived from a real calculated
 * quote, never hand-entered. This is the only bridge between the estimator's output
 * and the simulation engine.
 *
 * [siteLatitude]/[siteLongitude]/[roofAzimuthDegrees]/[roofPitchDegrees] are only present when
 * the quote came from Solar Site's "Use This Roof" (i.e. [QuoteInputs.roofConstraint] was set).
 * All four are null otherwise, and the simulation falls back to its original generic,
 * location-agnostic solar model — Solar Site data is an enhancement, never a requirement.
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
    val siteLatitude: Double? = null,
    val siteLongitude: Double? = null,
    val roofAzimuthDegrees: Double? = null,
    val roofPitchDegrees: Double? = null
) {
    /** True only when every field needed for a site-aware solar position model is present. */
    val isSiteAware: Boolean
        get() = siteLatitude != null && siteLongitude != null && roofAzimuthDegrees != null && roofPitchDegrees != null

    companion object {
        fun from(result: QuoteResult, inputs: QuoteInputs): SimSystemConfig {
            val constraint = inputs.roofConstraint
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
                peakLoadKw = (result.peakWatts / 1000.0).coerceAtLeast(result.designDailyKwh / 10.0),
                siteLatitude = constraint?.latitude,
                siteLongitude = constraint?.longitude,
                roofAzimuthDegrees = constraint?.azimuthDegrees,
                roofPitchDegrees = constraint?.pitchDegrees
            )
        }
    }
}
