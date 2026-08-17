package com.lumix.estimator.domain.simulation

import com.lumix.estimator.domain.ApplianceLoad
import com.lumix.estimator.domain.ApplianceType
import com.lumix.estimator.domain.QuoteInputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A54 (spec §22–23 — "evaluate whether the battery can reach its target SOC by approximately
 * 2 PM ... do NOT force the result, calculate it"): regression tests for
 * [RechargeFeasibility.evaluate]. Every number below was hand-traced first with a Python port of
 * the grid-connected (SBU) day branch of [SimulationEngine.buildDayTimeline] (`recharge_sim.py`,
 * this round — no Gradle/JVM in this sandbox to run `./gradlew test`), against the same real
 * hardware used in [BackupEstimatorTest] (6 × 615W, 10kW inverter, real SRNE SR-EOS10B spec) with
 * every wizard-linked appliance zeroed out so [defaultApplianceStates] contributes nothing,
 * isolating the numbers to the day-shaped background load driven by [SimSystemConfig.avgDailyLoadKwh].
 */
class RechargeFeasibilityTest {

    private fun noApplianceInputs() = QuoteInputs(
        appliances = ApplianceType.entries.associateWith { ApplianceLoad(qty = 0) }
    )

    private val srneEos10bChargeKw = (150 * 51.2) / 1000.0
    private val srneEos10bDischargeKw = (200 * 51.2) / 1000.0

    private fun configFor(avgDailyLoadKwh: Double) = SimSystemConfig(
        pvCapacityKw = 6 * 0.615,
        panelCount = 6,
        panelWatts = 615,
        inverterKw = 10.0,
        inverterName = "Deye SUN-10K-SG01LP1-US",
        batteryCapacityKwh = 10.24,
        batteryName = "10kWh (SRNE SR-EOS10B)",
        hasBattery = true,
        gridConnectable = true,
        avgDailyLoadKwh = avgDailyLoadKwh,
        peakLoadKw = 8.0,
        batteryMaxChargeKw = srneEos10bChargeKw.coerceAtMost(10.0),
        batteryMaxDischargeKw = srneEos10bDischargeKw.coerceAtMost(10.0),
        batteryChargeEfficiency = 0.95,
        batteryDepthOfDischargeFraction = SimulationEngine.BATTERY_MIN_SOC_FRACTION
    )

    @Test
    fun `no battery returns null - nothing to check`() {
        val config = configFor(60.0).copy(hasBattery = false, batteryCapacityKwh = 0.0)
        val result = RechargeFeasibility.evaluate(config, noApplianceInputs())
        assertNull(result)
    }

    @Test
    fun `an adequately sized array reaches target well before 2pm`() {
        // Python trace: avgDailyLoadKwh=60 -> reaches 90% SOC at hour 12.67, 100% by 2pm.
        val result = RechargeFeasibility.evaluate(configFor(60.0), noApplianceInputs())!!
        assertTrue(result.targetMet)
        assertEquals(12.67, result.hourReachedTarget!!, 0.05)
        assertEquals(100.0f, result.socAtTargetHourPercent, 0.5f)
    }

    @Test
    fun `a heavier daytime load misses the 2pm target by a couple hours, not forever`() {
        // Python trace: avgDailyLoadKwh=90 -> reaches 90% SOC at hour 14.67 (after the 2pm cutoff),
        // 85.1% actually on hand at 2pm.
        val result = RechargeFeasibility.evaluate(configFor(90.0), noApplianceInputs())!!
        assertFalse("reaching the target at 2:40pm is still a miss against a 2pm target", result.targetMet)
        assertEquals(14.67, result.hourReachedTarget!!, 0.05)
        assertEquals(85.1f, result.socAtTargetHourPercent, 0.5f)
    }

    @Test
    fun `a heavily undersized array for this load never reaches target at all that day`() {
        // Python trace: avgDailyLoadKwh=150 -> never reaches 90% SOC within the simulated day;
        // only 40.7% on hand at 2pm.
        val result = RechargeFeasibility.evaluate(configFor(150.0), noApplianceInputs())!!
        assertFalse(result.targetMet)
        assertNull("the target was never reached at all, not just late", result.hourReachedTarget)
        assertEquals(40.7f, result.socAtTargetHourPercent, 0.5f)
    }

    /**
     * A80 (spec Phase 17 §"BATTERY RECHARGE TEST" — "Track SOC at sunrise, 10 AM, noon, 2 PM,
     * 4 PM, sunset, midnight, 6 AM"): a config with no [SimSystemConfig.installMonth] still gets
     * the 8-checkpoint list (annual-average sunrise/sunset), and `hourReachedTarget`/
     * `socAtTargetHourPercent` are completely unaffected by this round's changes — same numbers as
     * the pre-A80 hand trace above, confirming durationHours=30's extra frames and the
     * `it.hour <= 24.0` guard on hourReached didn't silently change existing behavior.
     */
    @Test
    fun `checkpoints are always present, in order, even without an install month`() {
        val result = RechargeFeasibility.evaluate(configFor(60.0), noApplianceInputs())!!
        assertEquals(
            listOf("Sunrise", "10 AM", "Noon", "2 PM", "4 PM", "Sunset", "Midnight", "6 AM"),
            result.checkpoints.map { it.label }
        )
        assertEquals(SimulationEngine.SUNRISE_HOUR, result.checkpoints.first().hour, 0.01)
        assertEquals(SimulationEngine.SUNSET_HOUR, result.checkpoints[5].hour, 0.01)
        assertEquals(24.0, result.checkpoints[6].hour, 0.0)
        assertEquals(30.0, result.checkpoints[7].hour, 0.0)
        // Unchanged from the pre-A80 hand trace: reaches 90% at hour 12.67, 100% by 2pm.
        assertTrue(result.targetMet)
        assertEquals(12.67, result.hourReachedTarget!!, 0.05)
    }

    /**
     * A80: an installMonth-bearing config generates a real month-specific weather curve (see
     * RechargeFeasibility.evaluate's own `weatherCurve` doc) — checkpoints still come back in the
     * same order/count, sunrise/sunset checkpoints now reflect that month's real day length
     * (SolarPosition), and the same config always simulates identically (spec's own "same
     * scenario should produce the same result when reopened").
     */
    @Test
    fun `an install month generates a real month-specific curve, reproducibly`() {
        val config = configFor(60.0).copy(installMonth = 10) // October
        val first = RechargeFeasibility.evaluate(config, noApplianceInputs())!!
        val second = RechargeFeasibility.evaluate(config, noApplianceInputs())!!
        assertEquals(first.targetMet, second.targetMet)
        assertEquals(first.hourReachedTarget, second.hourReachedTarget)
        assertEquals(first.socAtTargetHourPercent, second.socAtTargetHourPercent, 0.0f)
        assertEquals(8, first.checkpoints.size)
        val octoberSunTimes = SolarPosition.sunTimesForMonth(10)
        assertEquals(octoberSunTimes.sunriseHour, first.checkpoints.first().hour, 0.01)
        assertEquals(octoberSunTimes.sunsetHour, first.checkpoints[5].hour, 0.01)
    }
}
