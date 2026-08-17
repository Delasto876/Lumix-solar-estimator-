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
        val hourReachedTarget: Double?,
        /**
         * A80 (spec Phase 17 — "BATTERY RECHARGE TEST": "Track SOC at sunrise, 10 AM, noon, 2 PM,
         * 4 PM, sunset, midnight, 6 AM"): the same simulated day's SOC at exactly those checkpoints,
         * labeled for direct display — not a second simulation, just more samples read from the one
         * timeline this function already builds. Sunrise/sunset use the same month-aware (or
         * annual-average) hours the timeline itself was built against, so a checkpoint literally
         * labeled "Sunrise" always matches where the timeline's own irradiance actually starts.
         */
        val checkpoints: List<RechargeCheckpoint> = emptyList()
    )

    data class RechargeCheckpoint(val label: String, val hour: Double, val socPercent: Float)

    /** "Recharged" — matches the plain-language target a 2 PM check is judged against; not 100%, since a taper-limited top-up trickle can take much longer than the bulk of the recharge without meaningfully changing backup readiness. */
    private const val TARGET_SOC_PERCENT = 90.0
    const val TARGET_HOUR = 14.0

    fun evaluate(
        config: SimSystemConfig,
        inputs: QuoteInputs,
        dayType: DayType = DayType.WEEKDAY,
        /**
         * A80 (spec Phase 17 §"REAL-WORLD SIMULATION MODES"): TYPICAL by default — a real,
         * reproducible weather curve for [config]'s own [SimSystemConfig.installMonth] instead of
         * the flat clear-sky assumption every caller used before this parameter existed. Passing
         * [WeatherScenario.CLOUDIER] lets a caller ask "does this system still recharge under a
         * cloudier-than-normal version of this month?" — the spec's own "evaluate against at
         * minimum Typical Case and Conservative Case" — without a second code path.
         */
        scenario: WeatherScenario = WeatherScenario.TYPICAL
    ): RechargeResult? {
        if (!config.hasBattery || config.batteryCapacityKwh <= 0.0) return null

        // A80: month-aware weather only when the config actually carries a month — null preserves
        // the exact prior flat-clear-sky behavior for every caller that hasn't opted in.
        val weatherCurve = config.installMonth?.let { WeatherEngine.generate(scenario, it) }

        // A80: extended from the prior implicit 24h to 30h so "Midnight"/"6 AM" below are real,
        // continuously-simulated frames (the following overnight discharge, after the day's
        // recharge) rather than SimulationEngine.frameAt's own `.mod(24.0)` wrapping them back
        // into the SAME first day (which would silently mislabel pre-sunrise hours as "midnight
        // after sunset"/"6 AM the next morning").
        val timeline = SimulationEngine.buildDayTimeline(
            config = config,
            gridConnected = config.gridConnectable,
            startSocFraction = config.batteryDepthOfDischargeFraction,
            applianceStates = defaultApplianceStates(inputs),
            dayType = dayType,
            resolutionMinutes = 5,
            durationHours = 30.0,
            installMonth = config.installMonth,
            weatherCurve = weatherCurve
        )

        val hourReached = timeline.firstOrNull { it.hour <= 24.0 && it.batterySocPercent >= TARGET_SOC_PERCENT }?.hour
        val socAtTarget = SimulationEngine.frameAt(timeline, TARGET_HOUR).batterySocPercent
        val targetMet = hourReached != null && hourReached <= TARGET_HOUR

        val sunTimes = config.installMonth?.let { SolarPosition.sunTimesForMonth(it) }
        val sunriseHour = sunTimes?.sunriseHour ?: SimulationEngine.SUNRISE_HOUR
        val sunsetHour = sunTimes?.sunsetHour ?: SimulationEngine.SUNSET_HOUR
        val checkpointHours = listOf(
            "Sunrise" to sunriseHour,
            "10 AM" to 10.0,
            "Noon" to 12.0,
            "2 PM" to 14.0,
            "4 PM" to 16.0,
            "Sunset" to sunsetHour,
            "Midnight" to 24.0,
            "6 AM" to 30.0
        )
        // Direct nearest-frame lookup, NOT SimulationEngine.frameAt (which always wraps its input
        // via `.mod(24.0)`, so it can't distinguish this run's real hour 30 from hour 6 of the
        // same first day) — every frame's own .hour is already a real, unwrapped elapsed hour.
        val checkpoints = checkpointHours.map { (label, hour) ->
            val frame = timeline.minByOrNull { kotlin.math.abs(it.hour - hour) } ?: timeline.last()
            RechargeCheckpoint(label, hour, frame.batterySocPercent)
        }

        return RechargeResult(
            targetMet = targetMet,
            socAtTargetHourPercent = socAtTarget,
            hourReachedTarget = hourReached,
            checkpoints = checkpoints
        )
    }
}
