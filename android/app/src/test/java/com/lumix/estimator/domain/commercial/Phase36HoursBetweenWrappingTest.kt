package com.lumix.estimator.domain.commercial

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 36 ("if I choose 6 hours it assume 6am to 12pm but what if its 6 hours from 9am to 3pm I
 * should be able to choose when to when just like residential"): regression tests for
 * [hoursBetweenWrapping], the shared formula now backing both [Shift.durationHours] and the
 * commercial/industrial load rows' own explicit "Starts"/"Ends" time pickers.
 */
class Phase36HoursBetweenWrappingTest {

    @Test
    fun `the installer's own worked example - 9am to 3pm is exactly 6 hours`() {
        assertEquals(6.0, hoursBetweenWrapping(9.0, 15.0), 0.001)
    }

    @Test
    fun `a same-day window is a plain subtraction`() {
        assertEquals(7.0, hoursBetweenWrapping(8.0, 15.0), 0.001)
    }

    @Test
    fun `an overnight window wraps past midnight instead of going negative`() {
        assertEquals(9.0, hoursBetweenWrapping(23.0, 8.0), 0.001)
    }

    @Test
    fun `identical start and end is zero hours, not a full day`() {
        assertEquals(0.0, hoursBetweenWrapping(9.0, 9.0), 0.001)
    }

    @Test
    fun `Shift durationHours still matches the shared formula after the Phase 36 refactor`() {
        assertEquals(hoursBetweenWrapping(8.0, 15.0), Shift(8.0, 15.0).durationHours, 0.001)
        assertEquals(hoursBetweenWrapping(23.0, 8.0), Shift(23.0, 8.0).durationHours, 0.001)
    }
}
