package com.lumix.estimator.domain.commercial

/**
 * Phase 32 ("for the appliance section... if commercial or industrial choose those in appliances
 * picker"): the Commercial/Industrial analogue of
 * [com.lumix.estimator.domain.simulation.totalApplianceLoadKwAt] — sums real power draw at a given
 * hour of day from a set of configured [LoadInstance]s, so the live simulation dial reflects the
 * SAME loads the installer configured on the Commercial/Industrial design step, not the residential
 * [com.lumix.estimator.domain.simulation.SimApplianceType] catalog.
 *
 * Each load is treated as a single contiguous daily window — [LoadInstance.typicalStartHour]
 * (defaulting to midnight when unset, matching the wizard's own display default) through
 * [LoadInstance.operatingHoursPerDay] later, wrapping past midnight the same way
 * [com.lumix.estimator.domain.simulation.ApplianceRun.isActiveAt] does. A load with zero
 * [LoadInstance.operatingHoursPerDay] (the default for a freshly-added load whose runtime hasn't
 * been set yet) never contributes — an honest empty window, not a silent all-day assumption.
 *
 * Phase 35 ("remember the half cycle... of these equipment"): while a load's window is active, it
 * contributes [LoadInstance.ratedWatts] scaled by [LoadInstance.dutyCycleFraction] — a compressor,
 * pump, or other cycling motor doesn't draw its full nameplate power continuously for the entire
 * time it's "on" (it cycles), the same real-vs-nameplate distinction
 * [com.lumix.estimator.domain.simulation.SimApplianceType.dutyFactor] already models for
 * residential appliances. [LoadInstance.dutyCycleFraction] itself already existed (used in the
 * wizard's own connected/design-load totals since Phase 27) — this was the one place in the code
 * that read [LoadInstance.ratedWatts] directly without it.
 */
/**
 * Phase 36 ("I should be able to choose when to when just like residential"): hours from
 * [startHour] to [endHour], wrapping past midnight when [endHour] is at or before [startHour] (an
 * overnight window, e.g. 11pm-8am = 9 hours, not -15) — the same convention [Shift.durationHours]
 * uses for shift start/end, shared here so a load's own "Starts"/"Ends" time pickers ([LoadInstance
 * .typicalStartHour]/[LoadInstance.operatingHoursPerDay], entered as two explicit clock times
 * rather than a start plus a separately-typed duration) resolve identically.
 */
fun hoursBetweenWrapping(startHour: Double, endHour: Double): Double {
    val raw = endHour - startHour
    return if (raw >= 0.0) raw else raw + 24.0
}

fun commercialLoadKwAt(loads: List<LoadInstance>, hour: Double): Double {
    val h = hour.mod(24.0)
    return loads.sumOf { load ->
        val duration = load.operatingHoursPerDay.coerceIn(0.0, 24.0)
        if (duration <= 0.0) return@sumOf 0.0
        val start = (load.typicalStartHour ?: 0.0).mod(24.0)
        val end = start + duration
        val active = if (end <= 24.0) h >= start && h < end else h >= start || h < (end - 24.0)
        if (active) load.quantity * load.ratedWatts * load.dutyCycleFraction.coerceIn(0.0, 1.0) / 1000.0 else 0.0
    }
}
