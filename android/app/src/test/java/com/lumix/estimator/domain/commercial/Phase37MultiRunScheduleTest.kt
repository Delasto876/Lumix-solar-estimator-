package com.lumix.estimator.domain.commercial

import com.lumix.estimator.domain.simulation.ApplianceRun
import com.lumix.estimator.domain.simulation.DayType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 37 ("this is how I want it exactly" — the residential Appliances sheet's own multi-run,
 * day-type-aware schedule editor, applied identically to Commercial/Industrial loads): regression
 * tests for [LoadInstance.effectiveRuns], [LoadInstance.enabled], and the resulting day-type/multi-
 * run-aware [commercialLoadKwAt].
 */
class Phase37MultiRunScheduleTest {

    private fun singleWindowLoad(watts: Double, start: Double, hours: Double, qty: Int = 1) = LoadInstance(
        definitionId = "test_load", label = "Test Load", quantity = qty, ratedWatts = watts,
        operatingHoursPerDay = hours, typicalStartHour = start
    )

    @Test
    fun `a load with no explicit runs derives exactly one run from typicalStartHour and operatingHoursPerDay - no regression`() {
        val load = singleWindowLoad(watts = 2000.0, start = 8.0, hours = 4.0)
        assertEquals(1, load.effectiveRuns.size)
        assertEquals(8.0, load.effectiveRuns[0].startHour, 0.001)
        assertEquals(4.0, load.effectiveRuns[0].durationHours, 0.001)
        assertEquals(2.0, commercialLoadKwAt(listOf(load), 9.0), 0.001)
    }

    @Test
    fun `existing single-window scenarios still pass with the default WEEKDAY dayType`() {
        val load = singleWindowLoad(watts = 3000.0, start = 23.0, hours = 3.0) // 11pm-2am
        assertEquals(3.0, commercialLoadKwAt(listOf(load), 0.0), 0.001) // no dayType arg, defaults to WEEKDAY
    }

    @Test
    fun `an explicit multi-run schedule replaces the single-window derivation entirely`() {
        val load = singleWindowLoad(watts = 1000.0, start = 8.0, hours = 4.0).copy(
            runs = listOf(
                ApplianceRun(quantity = 1, startHour = 6.0, durationHours = 2.0), // 6-8am
                ApplianceRun(quantity = 1, startHour = 18.0, durationHours = 2.0) // 6-8pm
            )
        )
        // The old typicalStartHour/operatingHoursPerDay window (8am-12pm) no longer applies.
        assertEquals(0.0, commercialLoadKwAt(listOf(load), 9.0), 0.001)
        assertEquals(1.0, commercialLoadKwAt(listOf(load), 7.0), 0.001)
        assertEquals(1.0, commercialLoadKwAt(listOf(load), 19.0), 0.001)
    }

    @Test
    fun `a run restricted to Saturday only does not contribute on a weekday`() {
        val load = singleWindowLoad(watts = 5000.0, start = 0.0, hours = 0.0).copy(
            runs = listOf(ApplianceRun(quantity = 1, startHour = 9.0, durationHours = 3.0, dayTypes = setOf(DayType.SATURDAY)))
        )
        assertEquals(0.0, commercialLoadKwAt(listOf(load), 10.0, DayType.WEEKDAY), 0.001)
        assertEquals(5.0, commercialLoadKwAt(listOf(load), 10.0, DayType.SATURDAY), 0.001)
    }

    @Test
    fun `a disabled load contributes nothing regardless of its configured runs`() {
        val load = singleWindowLoad(watts = 4000.0, start = 8.0, hours = 8.0).copy(enabled = false)
        assertEquals(0.0, commercialLoadKwAt(listOf(load), 9.0), 0.001)
    }

    @Test
    fun `re-enabling a load restores its previously configured schedule exactly`() {
        val runs = listOf(ApplianceRun(quantity = 2, startHour = 10.0, durationHours = 5.0))
        val load = singleWindowLoad(watts = 1000.0, start = 0.0, hours = 0.0).copy(enabled = false, runs = runs)
        assertEquals(0.0, commercialLoadKwAt(listOf(load), 12.0), 0.001)
        val reEnabled = load.copy(enabled = true)
        assertEquals(2.0, commercialLoadKwAt(listOf(reEnabled), 12.0), 0.001)
    }

    @Test
    fun `multi-run quantity sums independently per run, matching the residential appliance model`() {
        val load = singleWindowLoad(watts = 500.0, start = 0.0, hours = 0.0).copy(
            runs = listOf(
                ApplianceRun(quantity = 3, startHour = 8.0, durationHours = 4.0),
                ApplianceRun(quantity = 2, startHour = 10.0, durationHours = 2.0)
            )
        )
        // At 10:30, both runs overlap: (3 + 2) units x 500W = 2500W = 2.5kW
        assertEquals(2.5, commercialLoadKwAt(listOf(load), 10.5), 0.001)
        // At 9:00, only the first run is active: 3 x 500W = 1.5kW
        assertEquals(1.5, commercialLoadKwAt(listOf(load), 9.0), 0.001)
    }

    @Test
    fun `LoadInstance defaults enabled to true so every pre-Phase-37 load keeps contributing unchanged`() {
        val load = singleWindowLoad(watts = 1000.0, start = 8.0, hours = 1.0)
        assertTrue(load.enabled)
    }
}
