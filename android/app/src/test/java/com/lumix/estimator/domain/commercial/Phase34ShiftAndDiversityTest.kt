package com.lumix.estimator.domain.commercial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 34: regression tests for the overnight-shift duration wraparound fix and the diversity
 * factor's new 60% default, using the installer's own worked example verbatim: "starts at 8am to
 * 3pm second shift 3pm to 11pm and next shift 11pm to 8am."
 */
class Phase34ShiftAndDiversityTest {

    @Test
    fun `a normal same-day shift duration is unaffected`() {
        assertEquals(7.0, Shift(startHour = 8.0, endHour = 15.0).durationHours, 0.001) // 8am-3pm
        assertEquals(8.0, Shift(startHour = 15.0, endHour = 23.0).durationHours, 0.001) // 3pm-11pm
    }

    @Test
    fun `an overnight shift crossing midnight is 9 hours, not 0`() {
        // 11pm(23.0) -> 8am(8.0): before the Phase 34 fix, endHour - startHour = 8 - 23 = -15,
        // coerced to 0.0 - an overnight shift silently contributed zero production hours.
        assertEquals(9.0, Shift(startHour = 23.0, endHour = 8.0).durationHours, 0.001)
    }

    @Test
    fun `three shifts covering a full 24-hour rotation sum to exactly 24 hours`() {
        val schedule = IndustrialShiftSchedule(
            numberOfShifts = 3,
            shift1 = Shift(8.0, 15.0),
            shift2 = Shift(15.0, 23.0),
            shift3 = Shift(23.0, 8.0),
            workingDayTypes = setOf(com.lumix.estimator.domain.simulation.DayType.WEEKDAY)
        )
        assertEquals(24.0, schedule.productionHoursPerDay, 0.001)
        assertTrue(schedule.isConfigured)
    }

    @Test
    fun `diversity factor defaults to 60 percent, not 100`() {
        val factor = DiversityFactor()
        assertEquals(DiversityFactorPreset.PERCENT_60, factor.preset)
        assertEquals(0.6, factor.fraction, 0.001)
    }

    @Test
    fun `diversity factor still reaches the full 85-100 percent range when explicitly set`() {
        val allRunning = DiversityFactor(preset = DiversityFactorPreset.CUSTOM, customFraction = 1.0)
        assertEquals(1.0, allRunning.fraction, 0.001)
        val nearlyAll = DiversityFactor(preset = DiversityFactorPreset.CUSTOM, customFraction = 0.85)
        assertEquals(0.85, nearlyAll.fraction, 0.001)
    }

    @Test
    fun `the untouched default warns, but a confirmed value (even the same 60 percent via CUSTOM) does not`() {
        val design = CommercialIndustrialDesign(diversityFactor = DiversityFactor())
        val inputs = com.lumix.estimator.domain.QuoteInputs(
            systemCategory = com.lumix.estimator.domain.SystemType.COMMERCIAL,
            commercialIndustrialDesign = design
        )
        val untouchedResult = com.lumix.estimator.domain.SystemCalculator.calculate(inputs, com.lumix.estimator.domain.PriceList.DEFAULT)
        assertTrue(untouchedResult.commercialIndustrialWarnings.any { it.contains("diversity factor", ignoreCase = true) })

        val confirmedInputs = inputs.copy(
            commercialIndustrialDesign = design.copy(
                diversityFactor = DiversityFactor(preset = DiversityFactorPreset.CUSTOM, customFraction = 0.6)
            )
        )
        val confirmedResult = com.lumix.estimator.domain.SystemCalculator.calculate(confirmedInputs, com.lumix.estimator.domain.PriceList.DEFAULT)
        assertFalse(confirmedResult.commercialIndustrialWarnings.any { it.contains("diversity factor", ignoreCase = true) })
    }
}
