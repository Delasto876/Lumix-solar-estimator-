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
}
