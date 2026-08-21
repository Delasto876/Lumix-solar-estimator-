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
 */
fun commercialLoadKwAt(loads: List<LoadInstance>, hour: Double): Double {
    val h = hour.mod(24.0)
    return loads.sumOf { load ->
        val duration = load.operatingHoursPerDay.coerceIn(0.0, 24.0)
        if (duration <= 0.0) return@sumOf 0.0
        val start = (load.typicalStartHour ?: 0.0).mod(24.0)
        val end = start + duration
        val active = if (end <= 24.0) h >= start && h < end else h >= start || h < (end - 24.0)
        if (active) load.quantity * load.ratedWatts / 1000.0 else 0.0
    }
}
