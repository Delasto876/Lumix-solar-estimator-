package com.lumix.estimator.domain.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A54 (spec §31 — "if the battery switches to utility, the app must explain why, not silently
 * switch"): regression tests for [SimulationEngine.statusReason], the plain-language explanation
 * now shown under the Simulation screen's status line whenever a frame involves the grid or an
 * unmet load. Pure branching logic on already-computed frame/config fields — no numeric model to
 * hand-trace, just every distinct branch exercised once.
 */
class SimulationEngineStatusReasonTest {

    private fun frame(status: SystemStatus, socPercent: Float) = SimFrame(
        hour = 20.5, pvKw = 0.0, potentialPvKw = 0.0, cellTempC = 27.0, temperatureDerateFraction = 1.0,
        houseLoadKw = 1.5, solarToHouseKw = 0.0, solarToBatteryKw = 0.0, batteryToHouseKw = 0.0,
        gridToHouseKw = 1.5, gridToBatteryKw = 0.0, batterySocKwh = 0.0, batterySocPercent = socPercent,
        batteryPowerKw = 0.0, gridPowerKw = 1.5, unmetLoadKw = 0.0, curtailedSolarKw = 0.0,
        inverterLoadKw = 0.0, status = status
    )

    private fun config(hasBattery: Boolean = true, dod: Double = SimulationEngine.BATTERY_MIN_SOC_FRACTION) = SimSystemConfig(
        pvCapacityKw = 3.69, panelCount = 6, panelWatts = 615, inverterKw = 10.0, inverterName = "Deye SUN-10K-SG01LP1-US",
        batteryCapacityKwh = 10.24, batteryName = "10kWh (SRNE SR-EOS10B)", hasBattery = hasBattery, gridConnectable = true,
        avgDailyLoadKwh = 30.0, peakLoadKw = 8.0, batteryMaxChargeKw = 7.68, batteryMaxDischargeKw = 10.0,
        batteryChargeEfficiency = 0.95, batteryDepthOfDischargeFraction = dod
    )

    @Test
    fun `battery at reserve floor explains the SOC reason`() {
        val reason = SimulationEngine.statusReason(frame(SystemStatus.GRID_POWERING_HOME, socPercent = 20.0f), config())
        assertTrue(reason!!.contains("reserve", ignoreCase = true))
        assertTrue(reason.contains("20"))
    }

    @Test
    fun `grid plus battery status also gets a reason`() {
        val reason = SimulationEngine.statusReason(frame(SystemStatus.BATTERY_PLUS_GRID, socPercent = 19.5f), config())
        assertTrue(reason!!.contains("reserve", ignoreCase = true))
    }

    @Test
    fun `battery well above reserve but grid still powering home explains a power limit, not SOC`() {
        val reason = SimulationEngine.statusReason(frame(SystemStatus.GRID_POWERING_HOME, socPercent = 55.0f), config())
        assertTrue(reason!!.contains("discharge power", ignoreCase = true))
    }

    @Test
    fun `no battery in the system explains that directly`() {
        val reason = SimulationEngine.statusReason(frame(SystemStatus.GRID_POWERING_HOME, socPercent = 0.0f), config(hasBattery = false))
        assertTrue(reason!!.contains("No battery"))
    }

    @Test
    fun `grid charging battery explains the utility-first mode`() {
        val reason = SimulationEngine.statusReason(frame(SystemStatus.GRID_CHARGING_BATTERY, socPercent = 60.0f), config())
        assertTrue(reason!!.contains("JPS"))
    }

    @Test
    fun `power limited explains demand exceeding supply`() {
        val reason = SimulationEngine.statusReason(frame(SystemStatus.POWER_LIMITED, socPercent = 20.0f), config())
        assertEquals("Demand exceeds what solar, battery, and the grid connection can currently supply.", reason)
    }

    @Test
    fun `ordinary solar or battery statuses have nothing to explain`() {
        assertNull(SimulationEngine.statusReason(frame(SystemStatus.SOLAR_POWERING_HOME, socPercent = 80.0f), config()))
        assertNull(SimulationEngine.statusReason(frame(SystemStatus.BATTERY_POWERING_HOME, socPercent = 60.0f), config()))
        assertNull(SimulationEngine.statusReason(frame(SystemStatus.SOLAR_PLUS_BATTERY, socPercent = 70.0f), config()))
        assertNull(SimulationEngine.statusReason(frame(SystemStatus.IDLE, socPercent = 50.0f), config()))
    }
}
