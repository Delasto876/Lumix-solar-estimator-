package com.lumix.estimator.domain.commercial

import com.lumix.estimator.domain.simulation.DayType
import kotlinx.serialization.Serializable

/** One shift's start/end hour — no default times, always explicitly entered. */
@Serializable
data class Shift(
    val startHour: Double,
    val endHour: Double
) {
    /**
     * Phase 34 ("starts at 8am to 3pm second shift 3pm to 11pm and next shift 11pm to 8am"): a
     * shift crossing midnight (e.g. this exact 11pm-8am example) wraps rather than going negative
     * — the same midnight-wraparound convention [com.lumix.estimator.domain.simulation
     * .ApplianceRun.isActiveAt]/[com.lumix.estimator.domain.commercial.commercialLoadKwAt] already
     * use elsewhere in this app. Before this fix, `endHour - startHour` for 11pm(23.0)-8am(8.0)
     * gave -15, coerced to 0 — an overnight shift silently contributed zero production hours.
     */
    val durationHours: Double get() {
        val raw = endHour - startHour
        return if (raw >= 0.0) raw else raw + 24.0
    }
}

/**
 * Phase 28 §1 (Industrial — "DO NOT create assumed industrial working hours. When INDUSTRIAL is
 * selected, require the user to manually enter: Working days, Shift 1 start/end, Shift 2 start/end,
 * Shift 3 start/end, Number of shifts, Production hours, Weekend operation, Continuous/24-hour
 * loads, Equipment-specific operating schedules"): unlike [BusinessHours] (Commercial, which DOES
 * ship a sensible default), every field here defaults to "not yet entered" — [numberOfShifts] 0,
 * every [Shift] null, [workingDayTypes] empty. Industrial sizing must use ONLY what the installer
 * actually typed, never a guessed shift pattern — see [isConfigured], which
 * [CommercialIndustrialCalculator] reads to decide whether to warn instead of silently sizing
 * against an empty schedule.
 *
 * "Continuous/24-hour loads" and "equipment-specific operating schedules" (the spec's own last two
 * bullets) are represented per-load, not here — see [LoadDefinition.defaultOperationType]/
 * [LoadInstance.operationType] (`CONTINUOUS` already models a 24-hour load) — this class only
 * carries the SITE-level production schedule those individual loads run within.
 */
@Serializable
data class IndustrialShiftSchedule(
    val numberOfShifts: Int = 0,
    val shift1: Shift? = null,
    val shift2: Shift? = null,
    val shift3: Shift? = null,
    /** Which [DayType] buckets production actually runs on — empty means "not yet entered." */
    val workingDayTypes: Set<DayType> = emptySet(),
    val weekendOperation: Boolean = false,
    /** The installer's own stated total production hours/day, when they'd rather state this directly than have it derived by summing [shifts]. Null means "derive from the entered shifts." */
    val productionHoursPerDayOverride: Double? = null
) {
    val shifts: List<Shift> get() = listOfNotNull(shift1, shift2, shift3)

    /** True once the installer has entered enough to size against — at least one shift and at least one working day type. */
    val isConfigured: Boolean get() = numberOfShifts > 0 && shifts.isNotEmpty() && workingDayTypes.isNotEmpty()

    /** Total scheduled production hours/day from the entered shifts (or the explicit override) — 0.0 until something has actually been entered, never a default guess. */
    val productionHoursPerDay: Double get() = productionHoursPerDayOverride ?: shifts.sumOf { it.durationHours }
}
