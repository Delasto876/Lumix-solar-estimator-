package com.lumix.estimator.domain.commercial

import com.lumix.estimator.domain.simulation.DayType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 28 §1 tests for the new Commercial [BusinessHours] and Industrial [IndustrialShiftSchedule]
 * models, plus a spot-check that the expanded commercial/industrial catalogs resolve.
 */
class Phase28BusinessScheduleTest {

    @Test
    fun `default business hours match the spec's own worked example`() {
        val hours = BusinessHours()
        assertTrue(hours.isOpen(DayType.WEEKDAY))
        assertEquals(11.0, hours.hoursOpen(DayType.WEEKDAY), 0.0) // 7am-6pm
        assertTrue(hours.isOpen(DayType.SATURDAY))
        assertEquals(5.0, hours.hoursOpen(DayType.SATURDAY), 0.0) // 8am-1pm
        assertFalse(hours.isOpen(DayType.SUNDAY))
        assertEquals(0.0, hours.hoursOpen(DayType.SUNDAY), 0.0)
    }

    @Test
    fun `isOpenAt respects the open-close window and closed days`() {
        val hours = BusinessHours()
        assertTrue(hours.isOpenAt(12.0, DayType.WEEKDAY))
        assertFalse(hours.isOpenAt(19.0, DayType.WEEKDAY)) // after 6pm close
        assertFalse(hours.isOpenAt(6.0, DayType.WEEKDAY)) // before 7am open
        assertFalse(hours.isOpenAt(10.0, DayType.SUNDAY)) // Sunday closed by default
    }

    @Test
    fun `an unconfigured industrial shift schedule is not configured and has zero production hours`() {
        val schedule = IndustrialShiftSchedule()
        assertFalse(schedule.isConfigured)
        assertEquals(0.0, schedule.productionHoursPerDay, 0.0)
    }

    @Test
    fun `a real two-shift schedule is configured and sums its production hours correctly`() {
        val schedule = IndustrialShiftSchedule(
            numberOfShifts = 2,
            shift1 = Shift(startHour = 6.0, endHour = 14.0),
            shift2 = Shift(startHour = 14.0, endHour = 22.0),
            workingDayTypes = setOf(DayType.WEEKDAY)
        )
        assertTrue(schedule.isConfigured)
        assertEquals(16.0, schedule.productionHoursPerDay, 0.0) // 8h + 8h
    }

    @Test
    fun `an explicit production-hours override wins over the summed shifts`() {
        val schedule = IndustrialShiftSchedule(
            numberOfShifts = 1,
            shift1 = Shift(startHour = 6.0, endHour = 14.0),
            workingDayTypes = setOf(DayType.WEEKDAY),
            productionHoursPerDayOverride = 20.0
        )
        assertEquals(20.0, schedule.productionHoursPerDay, 0.0)
    }

    @Test
    fun `the expanded commercial and industrial catalogs resolve their new entries`() {
        assertNotNull(CommercialIndustrialLoadCatalog.definitionById("commercial_ice_machine"))
        assertNotNull(CommercialIndustrialLoadCatalog.definitionById("commercial_cctv_nvr"))
        assertNotNull(CommercialIndustrialLoadCatalog.definitionById("commercial_interior_lighting"))
        assertNotNull(CommercialIndustrialLoadCatalog.definitionById("industrial_cnc_machine"))
        assertNotNull(CommercialIndustrialLoadCatalog.definitionById("industrial_servers_network"))
        // The original Phase 27 entries are untouched — an already-saved design referencing one
        // by id must still resolve.
        assertNotNull(CommercialIndustrialLoadCatalog.definitionById("commercial_lighting"))
        assertNotNull(CommercialIndustrialLoadCatalog.definitionById("industrial_motor"))
    }
}
