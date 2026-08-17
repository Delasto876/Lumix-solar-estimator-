package com.lumix.estimator.domain.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A80 (spec Phase 17 §"SOLAR POSITION" — "calculate sunrise/sunset/day length... day length must
 * change seasonally, do not assume sunrise and sunset occur at exactly the same time every
 * month"): [SolarPosition] is standard published solar-declination/hour-angle geometry (Cooper
 * 1969 approximation), not fabricated location data — these expected values were hand-computed
 * from the same formula, not read off any measured Jamaica dataset.
 */
class SolarPositionTest {

    @Test
    fun `June (near summer solstice) has the longest day of the year`() {
        val june = SolarPosition.sunTimesForMonth(6)
        val december = SolarPosition.sunTimesForMonth(12)
        assertTrue("June's day should be longer than December's at 18N", june.dayLengthHours > december.dayLengthHours)
    }

    @Test
    fun `day length at 18N stays within the real seasonal range`() {
        // Hand-computed from the declination/hour-angle formula at latitude 18N: the seasonal
        // swing is modest this close to the equator — roughly 10.9h (Dec) to 13.1h (Jun) — not
        // measured Jamaica data, just the geometry any latitude this close to the equator has.
        for (month in 1..12) {
            val sunTimes = SolarPosition.sunTimesForMonth(month)
            assertTrue("month $month day length ${sunTimes.dayLengthHours} out of plausible 18N range", sunTimes.dayLengthHours in 10.5..13.5)
        }
    }

    @Test
    fun `sunrise and sunset are symmetric around noon`() {
        for (month in 1..12) {
            val sunTimes = SolarPosition.sunTimesForMonth(month)
            assertEquals(12.0, (sunTimes.sunriseHour + sunTimes.sunsetHour) / 2.0, 0.01)
        }
    }

    @Test
    fun `an unmapped month index falls back to a mid-year representative day rather than crashing`() {
        val fallback = SolarPosition.sunTimesForMonth(0)
        assertTrue(fallback.dayLengthHours > 0)
    }
}
