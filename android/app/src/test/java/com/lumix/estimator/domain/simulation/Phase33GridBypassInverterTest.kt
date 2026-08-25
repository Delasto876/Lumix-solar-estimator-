package com.lumix.estimator.domain.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 33 ("for commercial and industrial in simulation we can choose higher jps amp to 100A
 * 200A, 300A 400A 600A or 800A these are through a automatic change over switch rather than
 * through the inverter so would be from jps straight to load but keep the amp for residential the
 * same"): regression tests for [SimulationEngine.buildDayTimeline]'s new `gridBypassesInverter`
 * parameter and the new Commercial/Industrial amp preset list.
 */
class Phase33GridBypassInverterTest {

    // 2026-08-25 fix: this factory's own `gridServiceAmps` parameter was dead — SimSystemConfig has
    // no such field (gridServiceAmps is buildDayTimeline's own separate parameter, see its ATS-cap
    // block), so every call below that passed a value here was silently building a config that
    // still simulated against buildDayTimeline's real default (DEFAULT_GRID_SERVICE_AMPS = 30A,
    // 6.6kW) regardless. Harmless for the two tests whose demand (~0.46kW) clears 6.6kW either way,
    // but it meant "the ATS amp cap still throttles" below was never actually exercising a 1A cap —
    // real JVM run confirms it silently passed on an untouched 6.6kW ceiling. Fixed by threading
    // gridServiceAmps through to buildDayTimeline's own parameter directly at each call site instead.
    private fun configFor() = SimSystemConfig(
        pvCapacityKw = 0.0, // isolate the grid-vs-battery-vs-unmet decision from solar entirely
        panelCount = 1,
        panelWatts = 1,
        inverterKw = 50.0,
        inverterName = "Test Inverter",
        batteryCapacityKwh = 0.0,
        batteryName = null,
        hasBattery = false, // no battery either - SOL mode with no PV/battery is the starkest case
        gridConnectable = true,
        avgDailyLoadKwh = 24.0, // backgroundPerHourKw = 24/24 * 0.4 = 0.4kW flat-ish floor-adjusted load
        peakLoadKw = 5.0,
        batteryMaxChargeKw = 0.0,
        batteryMaxDischargeKw = 0.0,
        batteryChargeEfficiency = 0.95,
        batteryDepthOfDischargeFraction = SimulationEngine.BATTERY_MIN_SOC_FRACTION
    )

    @Test
    fun `residential presets are unchanged`() {
        assertEquals(listOf(15.0, 30.0, 60.0, 100.0), SimulationEngine.RESIDENTIAL_GRID_SERVICE_AMP_PRESETS)
    }

    @Test
    fun `commercial presets are the six new higher amp options`() {
        assertEquals(listOf(100.0, 200.0, 300.0, 400.0, 600.0, 800.0), SimulationEngine.COMMERCIAL_GRID_SERVICE_AMP_PRESETS)
    }

    @Test
    fun `SOL mode without the bypass leaves load unmet even though grid is connected - residential, unchanged`() {
        val frame = SimulationEngine.buildDayTimeline(
            config = configFor(), gridConnected = true, gridServiceAmps = 200.0,
            inverterMode = InverterMode.SOL, resolutionMinutes = 60, startHour = 12.0, durationHours = 0.0
            // gridBypassesInverter omitted -> defaults false, exactly the pre-Phase-33 residential behavior.
        ).first()
        assertEquals(0.0, frame.gridToHouseKw, 0.001)
        assertTrue("SOL mode must never touch JPS, even with grid connected, for a residential quote", frame.unmetLoadKw > 0.0)
    }

    @Test
    fun `SOL mode WITH the bypass serves load from JPS via the ATS instead of leaving it unmet`() {
        val frame = SimulationEngine.buildDayTimeline(
            config = configFor(), gridConnected = true, gridServiceAmps = 200.0,
            inverterMode = InverterMode.SOL, gridBypassesInverter = true,
            resolutionMinutes = 60, startHour = 12.0, durationHours = 0.0
        ).first()
        assertEquals(0.0, frame.unmetLoadKw, 0.001)
        assertTrue("the ATS bypass must serve the house load from JPS even in SOL mode", frame.gridToHouseKw > 0.0)
        // Grid-to-house power bypasses the inverter's own inverting stage either way (pre-existing
        // behavior, not something Phase 33 changes) - confirms the ATS path doesn't newly start
        // counting against inverterLoadKw.
        assertEquals(0.0, frame.inverterLoadKw, 0.001)
    }

    @Test
    fun `the ATS amp cap still throttles - a too-small service limit still produces unmet load`() {
        // avgDailyLoadKwh=24 -> backgroundPerHourKw floors near 0.4kW; a tiny 1A/220V ATS
        // (~0.22kW) can't cover even that, so some of it must still go unmet - the cap is real,
        // not a rubber-stamped bypass.
        val frame = SimulationEngine.buildDayTimeline(
            config = configFor(), gridConnected = true, gridServiceAmps = 1.0,
            inverterMode = InverterMode.SOL, gridBypassesInverter = true,
            resolutionMinutes = 60, startHour = 12.0, durationHours = 0.0
        ).first()
        assertTrue("an undersized ATS must still throttle, not become an unlimited supply", frame.unmetLoadKw > 0.0)
        assertTrue(frame.gridToHouseKw <= 1.0 * SimulationEngine.GRID_SERVICE_VOLTAGE / 1000.0 + 0.001)
    }
}
