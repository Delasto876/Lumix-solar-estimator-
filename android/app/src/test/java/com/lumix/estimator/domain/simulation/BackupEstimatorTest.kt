package com.lumix.estimator.domain.simulation

import com.lumix.estimator.domain.ApplianceLoad
import com.lumix.estimator.domain.ApplianceType
import com.lumix.estimator.domain.BackupCoverage
import com.lumix.estimator.domain.QuoteInputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A54: regression tests for the real simulation-driven backup estimate that replaced a closed-form
 * `targetHours × (selectedBatteryKwh / requiredBatteryKwh)` ratio (2026-08-14 "CORRECT SOLAR
 * SIZING..." report: a LOAD-BASED system showed "11.9 hours" while its own simulation showed the
 * battery falling back to utility around 8:30pm — the two numbers had no relationship). Every
 * figure below was hand-traced first with an equivalent Python port of
 * [SimulationEngine.buildDayTimeline]'s outage branch (this project's standard practice — no
 * Gradle/JVM in this sandbox to run `./gradlew test` directly) — see the round's README note for
 * the exact script and its output.
 *
 * Every scenario below zeroes out every wizard-linked appliance so [defaultApplianceStates] (which
 * [BackupEstimator.estimate] calls internally, the same call [SimulationViewModel] makes) produces
 * no appliance load at all — isolating the test to just the day-shaped background load, which is
 * fully determined by [SimSystemConfig.avgDailyLoadKwh] and therefore exactly hand-computable.
 */
class BackupEstimatorTest {

    private fun noApplianceInputs(
        coverage: BackupCoverage = BackupCoverage.MOST_LOAD,
        customFraction: Double = 0.8
    ) = QuoteInputs(
        appliances = ApplianceType.entries.associateWith { ApplianceLoad(qty = 0) },
        backupCoverage = coverage,
        customBackupCoverageFraction = customFraction
    )

    // SRNE SR-EOS10B: 51.2V, 150A max charge, 200A max discharge (EquipmentSpecs.kt) — the real
    // battery a "10kWh class" LOAD-BASED selection resolves to, matching the reported scenario.
    private val srneEos10bChargeKw = (150 * 51.2) / 1000.0 // 7.68 kW
    private val srneEos10bDischargeKw = (200 * 51.2) / 1000.0 // 10.24 kW, coerced to inverter ceiling below

    private fun configFor(avgDailyLoadKwh: Double) = SimSystemConfig(
        pvCapacityKw = 6 * 0.615, // 6 x 615W
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
    fun `no battery returns zero hours with an explanatory reason, not a crash`() {
        val config = configFor(60.0).copy(hasBattery = false, batteryCapacityKwh = 0.0)
        val inputs = noApplianceInputs()
        val result = BackupEstimator.estimate(config, inputs)
        assertEquals(0.0, result.hours, 0.001)
        assertFalse(result.sufficientForFullWindow)
        assertTrue(result.reason.isNotBlank())
    }

    @Test
    fun `coverageFraction matches the exact fractions SystemCalculator sizes the battery against`() {
        assertEquals(0.6, BackupEstimator.coverageFraction(BackupCoverage.ESSENTIALS, 0.8), 0.001)
        assertEquals(0.6, BackupEstimator.coverageFraction(BackupCoverage.CRITICAL_LOADS, 0.8), 0.001)
        assertEquals(1.0, BackupEstimator.coverageFraction(BackupCoverage.FULL, 0.8), 0.001)
        assertEquals(1.0, BackupEstimator.coverageFraction(BackupCoverage.MOST_LOAD, 0.8), 0.001)
        assertEquals(0.35, BackupEstimator.coverageFraction(BackupCoverage.CUSTOM, 0.35), 0.001)
        // Clamped even if a stray value outside 0..1 ever reached this.
        assertEquals(1.0, BackupEstimator.coverageFraction(BackupCoverage.CUSTOM, 1.4), 0.001)
    }

    @Test
    fun `a real overnight load exhausts the battery in hours, not the flat ratio's inflated figure`() {
        // Python hand-trace (backup_sim.py, this round): avgDailyLoadKwh=60 with Most Load
        // coverage (fraction 1.0) hits the 20% reserve floor about 5.92h after the dusk outage
        // starts — a real, bounded number derived from the actual load curve and battery power
        // taper, not a ratio of nominal capacities.
        //
        // A73 note: this figure moved from 6.25h to 5.92h in this round — battery-to-house power
        // now pays the same inverter DC->AC conversion loss (SystemLosses.INVERTER_EFFICIENCY,
        // 0.97) the PV->house path already paid, so the same AC energy delivered to the house now
        // draws slightly more real DC energy from the battery's own SOC, depleting it faster. Not
        // a regression — a real physics fix (installer's explicit decision, see README's A73
        // section) applied uniformly to every hybrid-with-battery outage scenario.
        val config = configFor(60.0)
        val inputs = noApplianceInputs(BackupCoverage.MOST_LOAD)
        val result = BackupEstimator.estimate(config, inputs)
        assertFalse("a 60 kWh/day background load should exhaust a 10.24 kWh battery well within the search window", result.sufficientForFullWindow)
        assertEquals(5.92, result.hours, 0.05)
        assertTrue(result.reason.contains("reserve", ignoreCase = true))
    }

    @Test
    fun `reducing coverage to Critical Loads extends the same system past the same shortfall`() {
        // Same physical system, same underlying load shape, only the coverage fraction differs
        // (1.0 vs 0.6) — this is the ONE place that fraction is applied, shared with sizing.
        val config = configFor(60.0)
        val mostLoadInputs = noApplianceInputs(BackupCoverage.MOST_LOAD)
        val criticalInputs = noApplianceInputs(BackupCoverage.CRITICAL_LOADS)
        val mostLoad = BackupEstimator.estimate(config, mostLoadInputs)
        val critical = BackupEstimator.estimate(config, criticalInputs)
        assertFalse(mostLoad.sufficientForFullWindow)
        assertTrue("scaling load down by the Critical Loads fraction must extend runtime", critical.sufficientForFullWindow || critical.hours > mostLoad.hours)
    }

    @Test
    fun `a light load with real PV recharge survives the full search window`() {
        val config = configFor(5.0)
        val inputs = noApplianceInputs(BackupCoverage.MOST_LOAD)
        val result = BackupEstimator.estimate(config, inputs)
        assertTrue("a tiny background load against 3.69 kWp of daily PV recharge should not exhaust the battery in 72h", result.sufficientForFullWindow)
        assertEquals(72.0, result.hours, 0.01)
    }

    @Test
    fun `buildDayTimeline's new startHour and loadMultiplier parameters actually take effect`() {
        val config = configFor(60.0)
        val defaultDay = SimulationEngine.buildDayTimeline(config, gridConnected = true, startSocFraction = 0.6)
        // Existing 24h/midnight-start behavior is unchanged when the new params are left at their defaults.
        assertEquals(0.0, defaultDay.first().hour, 0.001)
        assertEquals(289, defaultDay.size) // (24*60/5) + 1, same frame count as before this round

        val shifted = SimulationEngine.buildDayTimeline(
            config, gridConnected = false, startSocFraction = 1.0,
            startHour = SimulationEngine.SUNSET_HOUR, durationHours = 4.0, loadMultiplier = 0.5
        )
        assertEquals(SimulationEngine.SUNSET_HOUR, shifted.first().hour, 0.001)
        assertEquals(SimulationEngine.SUNSET_HOUR + 4.0, shifted.last().hour, 0.01)
        // Halving loadMultiplier must actually halve the reported load at a fixed hour, not be ignored.
        val fullLoad = SimulationEngine.buildDayTimeline(
            config, gridConnected = false, startSocFraction = 1.0,
            startHour = SimulationEngine.SUNSET_HOUR, durationHours = 4.0, loadMultiplier = 1.0
        )
        assertEquals(fullLoad[10].houseLoadKw / 2.0, shifted[10].houseLoadKw, 0.001)
    }

    /**
     * A80: a config with no [SimSystemConfig.installMonth] must estimate identically regardless
     * of [BackupEstimator.estimate]'s new `scenario` parameter — null month means [weatherCurve]
     * stays null inside `estimate`, which preserves the exact prior flat-clear-sky behavior no
     * matter which scenario is passed.
     */
    @Test
    fun `scenario has no effect without an install month`() {
        val config = configFor(5.0)
        val inputs = noApplianceInputs(BackupCoverage.MOST_LOAD)
        val typical = BackupEstimator.estimate(config, inputs, scenario = WeatherScenario.TYPICAL)
        val rainy = BackupEstimator.estimate(config, inputs, scenario = WeatherScenario.RAINY)
        assertEquals(typical.hours, rainy.hours, 0.0)
        assertEquals(typical.sufficientForFullWindow, rainy.sufficientForFullWindow)
    }

    /**
     * A80 (spec Phase 17 §"SIZING MUST USE WEATHER LOGIC" — "evaluate ... at minimum Typical Case
     * and Conservative Case"): with a real install month, a RAINY-scenario estimate must never
     * outperform a TYPICAL one for the same system — less available solar across the multi-day
     * search window can only hold the battery up as long or shorter, never longer.
     */
    @Test
    fun `a cloudier scenario never outlasts typical for the same month-aware system`() {
        val config = configFor(30.0).copy(installMonth = 10) // October, a heavier daytime load
        val inputs = noApplianceInputs(BackupCoverage.MOST_LOAD)
        val typical = BackupEstimator.estimate(config, inputs, scenario = WeatherScenario.TYPICAL)
        val rainy = BackupEstimator.estimate(config, inputs, scenario = WeatherScenario.RAINY)
        assertTrue("rainy (${rainy.hours}h) should not outlast typical (${typical.hours}h)", rainy.hours <= typical.hours + 0.01)
    }
}
