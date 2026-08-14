package com.lumix.estimator.domain.simulation

import com.lumix.estimator.domain.QuoteInputs

/**
 * A54 (spec §22–23 — "the PV system must be able to ... reach the desired SOC target during the
 * solar window. Evaluate whether the battery can reach its target SOC by approximately 2 PM ...
 * do NOT force the result, calculate it"): checks whether the selected PV array can realistically
 * recharge the battery from empty (its own reserve floor — the worst case, a battery drawn all the
 * way down overnight) back to a real "recharged" SOC by early afternoon, under the actual daily
 * load and irradiance curve — not assumed, not silently passed.
 *
 * Reuses [SimulationEngine.buildDayTimeline] (the same engine everything else in this app runs)
 * for a normal grid-connected day starting at the battery's reserve floor at midnight — SOL/SBU
 * inverter modes (the engine's own default) never let the grid charge the battery, so this
 * naturally isolates how much of the recharge is solar's own doing, with no separate no-grid
 * plumbing needed.
 */
object RechargeFeasibility {

    data class RechargeResult(
        val targetMet: Boolean,
        val socAtTargetHourPercent: Float,
        /** The hour SOC actually reached [TARGET_SOC_PERCENT], or null if it never did within the simulated day. */
        val hourReachedTarget: Double?
    )

    /** "Recharged" — matches the plain-language target a 2 PM check is judged against; not 100%, since a taper-limited top-up trickle can take much longer than the bulk of the recharge without meaningfully changing backup readiness. */
    private const val TARGET_SOC_PERCENT = 90.0
    const val TARGET_HOUR = 14.0

    fun evaluate(config: SimSystemConfig, inputs: QuoteInputs, dayType: DayType = DayType.WEEKDAY): RechargeResult? {
        if (!config.hasBattery || config.batteryCapacityKwh <= 0.0) return null

        val timeline = SimulationEngine.buildDayTimeline(
            config = config,
            gridConnected = config.gridConnectable,
            startSocFraction = config.batteryDepthOfDischargeFraction,
            applianceStates = defaultApplianceStates(inputs),
            dayType = dayType,
            resolutionMinutes = 5
        )

        val hourReached = timeline.firstOrNull { it.batterySocPercent >= TARGET_SOC_PERCENT }?.hour
        val socAtTarget = SimulationEngine.frameAt(timeline, TARGET_HOUR).batterySocPercent
        val targetMet = hourReached != null && hourReached <= TARGET_HOUR

        return RechargeResult(
            targetMet = targetMet,
            socAtTargetHourPercent = socAtTarget,
            hourReachedTarget = hourReached
        )
    }
}
