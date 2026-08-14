package com.lumix.estimator.domain.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A64 (spec §1-6 — "the battery must NOT be sized from an arbitrary average load... integrate the
 * actual overnight curve"): regression tests for [OvernightLoadProfile.evaluate]. Every number
 * below was hand-traced with a direct Python port of [SimulationEngine]'s own `loadFactor` curve
 * and background-load formula (this round — no Gradle/JVM in this sandbox to run
 * `./gradlew test`) — [SimulationEngine.weekdayLoadShape] and its background-load constants
 * (0.4 fraction, 0.15kW floor) are read directly from that file's source, not re-derived.
 */
class OvernightLoadProfileTest {

    @Test
    fun `no appliances selected - only the background load curve contributes, integrated exactly`() {
        // avgDailyLoadKwh=24 -> backgroundPerHourKw = (24/24*0.4).coerceAtLeast(0.15) = 0.4kW.
        // Python trace: 1 hour window from SUNSET_HOUR (17.75) at 5-minute resolution, weekday
        // shape, left-Riemann-summed -> energyKwh=0.6421, peakKw=0.6754 (the window's final
        // 5-minute step, hour 18.75, where the evening ramp is steepest).
        val result = OvernightLoadProfile.evaluate(
            avgDailyLoadKwh = 24.0,
            applianceStates = emptyMap(),
            windowHours = 1.0,
            loadMultiplier = 1.0
        )
        assertEquals(0.6421, result.energyKwh, 0.001)
        assertEquals(0.6754, result.peakKw, 0.001)
    }

    @Test
    fun `zero window hours returns zero energy and zero peak without simulating anything`() {
        val result = OvernightLoadProfile.evaluate(
            avgDailyLoadKwh = 24.0,
            applianceStates = emptyMap(),
            windowHours = 0.0,
            loadMultiplier = 1.0
        )
        assertEquals(0.0, result.energyKwh, 0.0)
        assertEquals(0.0, result.peakKw, 0.0)
    }

    @Test
    fun `loadMultiplier scales both energy and peak linearly - same shape, smaller size`() {
        val full = OvernightLoadProfile.evaluate(
            avgDailyLoadKwh = 24.0, applianceStates = emptyMap(), windowHours = 2.0, loadMultiplier = 1.0
        )
        val half = OvernightLoadProfile.evaluate(
            avgDailyLoadKwh = 24.0, applianceStates = emptyMap(), windowHours = 2.0, loadMultiplier = 0.5
        )
        assertEquals(full.energyKwh / 2.0, half.energyKwh, 0.0001)
        assertEquals(full.peakKw / 2.0, half.peakKw, 0.0001)
    }

    @Test
    fun `a longer window never has less energy than a shorter one starting at the same hour`() {
        // The background curve is strictly positive everywhere, so extending the window can only
        // add energy, never remove it — a basic sanity invariant independent of the exact curve.
        val shorter = OvernightLoadProfile.evaluate(
            avgDailyLoadKwh = 30.0, applianceStates = emptyMap(), windowHours = 6.0, loadMultiplier = 1.0
        )
        val longer = OvernightLoadProfile.evaluate(
            avgDailyLoadKwh = 30.0, applianceStates = emptyMap(), windowHours = 12.0, loadMultiplier = 1.0
        )
        assertTrue(
            "expected a 12-hour window to contain at least as much energy as its own first 6 hours",
            longer.energyKwh >= shorter.energyKwh
        )
    }
}
