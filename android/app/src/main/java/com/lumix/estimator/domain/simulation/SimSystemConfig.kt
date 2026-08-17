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
    val batteryDepthOfDischargeFraction: Double,
    /**
     * A69: the inverter's real PV DC input ceiling — what actually caps [SimFrame.pvKw]/
     * [SimFrame.potentialPvKw] each frame. Deliberately NOT [inverterKw] (the continuous AC
     * output rating): a real hybrid inverter's DC/MPPT input stage typically accepts meaningfully
     * more than its own AC rating, specifically to allow economical DC-side oversizing — capping
     * PV production at the AC rating instead understated legitimate solar-noon output for exactly
     * the deliberately-oversized designs where the extra DC headroom is the point. Defaults to
     * `inverterKw * 1.3` (the same fallback ratio [EquipmentSelectionEngine] itself already falls
     * back to when no confirmed spec match exists) so every existing caller that doesn't set this
     * explicitly keeps a sensible, inverter-relative ceiling rather than an unconstrained one.
     */
    val maxPvInputKw: Double = inverterKw * 1.3,
    /**
     * A70: the site-specific PSH (peak sun hours) this system was actually sized against —
     * [SimulationEngine.buildDayTimeline] scales its irradiance curve's amplitude to this value
     * (see [SimulationEngine.REFERENCE_CURVE_PSH_HOURS]'s own doc for why and how). Defaults to
     * that same reference value, so any config built without setting this explicitly (older saved
     * quotes' frozen `QuoteResult`, or a caller that hand-constructs a config) keeps the curve at
     * its native, unscaled amplitude rather than silently changing behavior underneath it.
     */
    val pshHours: Double = SimulationEngine.REFERENCE_CURVE_PSH_HOURS,
    /**
     * A80 (spec Phase 17): 1-12, or null for the fixed annual-average sunrise/sunset/weather
     * assumption every config built before this field existed already uses. See
     * [SimulationEngine.buildDayTimeline]'s own `installMonth` parameter doc, and
     * [com.lumix.estimator.domain.simulation.RechargeFeasibility]/[BackupEstimator], which read
     * this field directly to decide whether to generate a real month-specific weather curve.
     */
    val installMonth: Int? = null
) {
    companion object {
        fun from(result: QuoteResult): SimSystemConfig {
            val batteryCapacityKwh = result.totalBatteryKwh
            val inverterCeilingKw = result.inverterKw.coerceAtLeast(0.1)
            // A41/A42: real per-model charge/discharge current is resolved ONCE, at quote
            // calculation time (SystemCalculator, against EquipmentSpecs), and frozen into
            // QuoteResult.batteryMaxChargeKw/batteryMaxDischargeKw — never re-matched against
            // the *current* equipment catalog here. Re-matching live would mean a quote's
            // simulation could quietly change behavior months later if the spec library is
            // updated, which breaks reproducibility (a saved quote must always simulate the
            // same way it did when it was quoted). Only fall back to the generic 0.5C estimate
            // when no confirmed spec existed at calculation time — including for every quote
            // saved before this field existed, which is also the historically accurate reading.
            val batteryMaxChargeKw = result.batteryMaxChargeKw
                ?: min(batteryCapacityKwh * 0.5, inverterCeilingKw)
            val batteryMaxDischargeKw = result.batteryMaxDischargeKw
                ?: min(batteryCapacityKwh * 0.5, inverterCeilingKw)
            // A69: same reproducibility rationale as batteryMaxChargeKw above — see
            // QuoteResult.inverterMaxPvKw's own doc.
            val maxPvInputKw = result.inverterMaxPvKw ?: (inverterCeilingKw * 1.3)
            // A70: same reproducibility rationale again — the PSH actually used to size this
            // system, frozen at calculation time rather than re-read from whatever the parish
            // table says now. Falls back to the curve's own native reference value (an unscaled
            // curve) for any quote saved before this field existed.
            val pshHours = result.designPeakSunHours ?: SimulationEngine.REFERENCE_CURVE_PSH_HOURS
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
                batteryDepthOfDischargeFraction = SimulationEngine.BATTERY_MIN_SOC_FRACTION,
                maxPvInputKw = maxPvInputKw,
                pshHours = pshHours,
                installMonth = result.designInstallMonth
            )
        }
    }
}
