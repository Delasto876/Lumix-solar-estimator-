package com.lumix.estimator.domain.commercial

import com.lumix.estimator.domain.simulation.DayType
import kotlin.math.roundToInt

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

/**
 * Phase 39 ("for the time and shift let me use 12hr clock instead of the 24hr clock type... for
 * industrial load use 12hr clock right throughout 12hr am 12hr pm"): the installer-facing 12-hour
 * shape (1-12, minute, AM/PM) of a decimal hour — every Shift/BusinessHours/LoadInstance
 * .typicalStartHour field underneath is unchanged (still 0.0-23.99, midnight-based), this is purely
 * the display/entry conversion `TimeField` uses so the installer types "2:00 PM" instead of "14:00".
 */
data class Clock12(val hour12: Int, val minute: Int, val isPm: Boolean)

/** Decimal hour (0.0-23.99...) -> 12-hour clock shape. See [Clock12]'s own doc. */
fun decimalHourTo12Hour(hour: Double): Clock12 {
    val h24 = hour.toInt().coerceIn(0, 23)
    val minute = ((hour - h24) * 60.0).roundToInt().coerceIn(0, 59)
    val isPm = h24 >= 12
    val hour12 = when {
        h24 == 0 -> 12
        h24 > 12 -> h24 - 12
        else -> h24
    }
    return Clock12(hour12, minute, isPm)
}

/** 12-hour clock shape -> decimal hour (0.0-23.99...). Inverse of [decimalHourTo12Hour]; [hour12] is clamped to 1-12 (there is no "0 o'clock" or "13 o'clock" on a 12-hour face). */
fun twelveHourToDecimalHour(hour12: Int, minute: Int, isPm: Boolean): Double {
    val clampedHour12 = hour12.coerceIn(1, 12)
    val hour24 = when {
        !isPm && clampedHour12 == 12 -> 0
        isPm && clampedHour12 != 12 -> clampedHour12 + 12
        else -> clampedHour12
    }
    return hour24 + minute.coerceIn(0, 59) / 60.0
}

/**
 * Phase 37 ("this is how I want it exactly" — matching the residential Appliances sheet's own
 * multi-run, day-type-aware schedule editor): reads [LoadInstance.effectiveRuns] instead of
 * [LoadInstance.typicalStartHour]/[LoadInstance.operatingHoursPerDay] directly, so a load with a
 * custom multi-run schedule (several distinct windows, each possibly restricted to a subset of
 * [DayType]s — e.g. a shift-only load that doesn't run weekends) is simulated exactly as
 * configured, the same way [com.lumix.estimator.domain.simulation.totalApplianceLoadKwAt] already
 * does for residential appliances. A load with [LoadInstance.enabled] = false (the simulation's
 * own session Switch — distinct from removing it from the design entirely) never contributes,
 * regardless of its configured runs.
 */
fun commercialLoadKwAt(loads: List<LoadInstance>, hour: Double, dayType: DayType = DayType.WEEKDAY): Double {
    val h = hour.mod(24.0)
    return loads.sumOf { load ->
        if (!load.enabled) return@sumOf 0.0
        val activeQty = load.effectiveRuns.filter { it.isActiveAt(h, dayType) }.sumOf { it.quantity }
        if (activeQty <= 0) return@sumOf 0.0
        activeQty * load.ratedWatts * load.dutyCycleFraction.coerceIn(0.0, 1.0) / 1000.0
    }
}
