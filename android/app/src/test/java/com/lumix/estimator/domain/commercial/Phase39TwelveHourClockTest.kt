package com.lumix.estimator.domain.commercial

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 39 ("for the time and shift let me use 12hr clock instead of the 24hr clock type... for
 * industrial load use 12hr clock right throughout 12hr am 12hr pm"): regression tests for
 * [decimalHourTo12Hour]/[twelveHourToDecimalHour], the conversion `TimeField` now uses so every
 * Commercial/Industrial shift and load Starts/Ends entry reads and types as a 12-hour clock while
 * the underlying decimal-hour domain model (0.0-23.99) stays exactly as it always was.
 */
class Phase39TwelveHourClockTest {

    @Test
    fun `midnight is 12 AM, not 0 o'clock`() {
        val clock = decimalHourTo12Hour(0.0)
        assertEquals(12, clock.hour12)
        assertEquals(0, clock.minute)
        assertEquals(false, clock.isPm)
    }

    @Test
    fun `noon is 12 PM, not 0 or 13 o'clock`() {
        val clock = decimalHourTo12Hour(12.0)
        assertEquals(12, clock.hour12)
        assertEquals(0, clock.minute)
        assertEquals(true, clock.isPm)
    }

    @Test
    fun `2pm (14 00) reads as 2 PM`() {
        val clock = decimalHourTo12Hour(14.0)
        assertEquals(2, clock.hour12)
        assertEquals(0, clock.minute)
        assertEquals(true, clock.isPm)
    }

    @Test
    fun `9 30am reads as 9 30 AM`() {
        val clock = decimalHourTo12Hour(9.5)
        assertEquals(9, clock.hour12)
        assertEquals(30, clock.minute)
        assertEquals(false, clock.isPm)
    }

    @Test
    fun `11pm reads as 11 PM`() {
        val clock = decimalHourTo12Hour(23.0)
        assertEquals(11, clock.hour12)
        assertEquals(0, clock.minute)
        assertEquals(true, clock.isPm)
    }

    @Test
    fun `12 AM converts back to decimal hour 0`() {
        assertEquals(0.0, twelveHourToDecimalHour(12, 0, isPm = false), 0.001)
    }

    @Test
    fun `12 PM converts back to decimal hour 12`() {
        assertEquals(12.0, twelveHourToDecimalHour(12, 0, isPm = true), 0.001)
    }

    @Test
    fun `2 00 PM converts back to decimal hour 14`() {
        assertEquals(14.0, twelveHourToDecimalHour(2, 0, isPm = true), 0.001)
    }

    @Test
    fun `9 30 AM converts back to decimal hour 9_5`() {
        assertEquals(9.5, twelveHourToDecimalHour(9, 30, isPm = false), 0.001)
    }

    @Test
    fun `an out-of-range 13 o'clock is clamped to 12 on the 12-hour face`() {
        assertEquals(12.0, twelveHourToDecimalHour(13, 0, isPm = true), 0.001)
        assertEquals(0.0, twelveHourToDecimalHour(13, 0, isPm = false), 0.001)
    }

    @Test
    fun `every whole hour of the day round-trips exactly through the 12-hour conversion`() {
        var hour = 0.0
        while (hour < 24.0) {
            val clock = decimalHourTo12Hour(hour)
            val roundTripped = twelveHourToDecimalHour(clock.hour12, clock.minute, clock.isPm)
            assertEquals("hour=$hour did not round-trip", hour, roundTripped, 0.001)
            hour += 1.0
        }
    }

    @Test
    fun `the installer's own worked example - 9am to 3pm shift still resolves to 6 hours through the 12-hour entry`() {
        val startDecimal = twelveHourToDecimalHour(9, 0, isPm = false)
        val endDecimal = twelveHourToDecimalHour(3, 0, isPm = true)
        assertEquals(6.0, hoursBetweenWrapping(startDecimal, endDecimal), 0.001)
    }

    @Test
    fun `an overnight shift entered as 11pm to 8am still wraps correctly through the 12-hour entry`() {
        val startDecimal = twelveHourToDecimalHour(11, 0, isPm = true)
        val endDecimal = twelveHourToDecimalHour(8, 0, isPm = false)
        assertEquals(9.0, hoursBetweenWrapping(startDecimal, endDecimal), 0.001)
    }
}
