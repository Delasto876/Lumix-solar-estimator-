package com.lumix.estimator.domain.commercial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 47 (spec §5 — "School operating schedule should default to daytime operation"; §6 — Call
 * Centre 24-hour capability): regression tests for [FacilityScheduleLibrary].
 */
class FacilityScheduleLibraryTest {

    @Test
    fun `school suggests weekday-only daytime hours, no weekend operation`() {
        val suggested = FacilityScheduleLibrary.suggestedBusinessHoursFor(CommercialFacilityType.SCHOOL)!!
        assertEquals(7.5, suggested.weekdayOpenHour!!, 0.0)
        assertEquals(15.5, suggested.weekdayCloseHour!!, 0.0)
        assertNull(suggested.saturdayOpenHour)
        assertNull(suggested.sundayOpenHour)
    }

    @Test
    fun `call centre suggests a true 24-hour window every day`() {
        val suggested = FacilityScheduleLibrary.suggestedBusinessHoursFor(CommercialFacilityType.CALL_CENTRE)!!
        assertTrue(suggested.isOpen(com.lumix.estimator.domain.simulation.DayType.WEEKDAY))
        assertEquals(24.0, suggested.hoursOpen(com.lumix.estimator.domain.simulation.DayType.WEEKDAY), 0.0001)
        assertEquals(24.0, suggested.hoursOpen(com.lumix.estimator.domain.simulation.DayType.SATURDAY), 0.0001)
        assertEquals(24.0, suggested.hoursOpen(com.lumix.estimator.domain.simulation.DayType.SUNDAY), 0.0001)
    }

    @Test
    fun `a facility type with no explicit suggestion falls back to null, not a guess`() {
        assertNull(FacilityScheduleLibrary.suggestedBusinessHoursFor(CommercialFacilityType.RETAIL_STORE))
        assertNull(FacilityScheduleLibrary.suggestedBusinessHoursFor(CommercialFacilityType.CUSTOM))
    }

    @Test
    fun `a suggestion never mutates the design's own default businessHours on its own`() {
        // Nothing in FacilityScheduleLibrary itself writes to a CommercialIndustrialDesign - a
        // fresh design keeps its plain Phase 28 generic default regardless of facility type.
        val design = CommercialIndustrialDesign(facility = FacilitySelection(commercialType = CommercialFacilityType.SCHOOL))
        assertEquals(7.0, design.businessHours.weekdayOpenHour!!, 0.0)
        assertEquals(18.0, design.businessHours.weekdayCloseHour!!, 0.0)
    }
}
