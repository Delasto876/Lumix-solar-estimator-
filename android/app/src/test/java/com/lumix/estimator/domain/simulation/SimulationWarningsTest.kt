package com.lumix.estimator.domain.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A59 (spec §33–34 — "Inverter: 80% HIGH LOAD, 90% NEAR LIMIT, 100% LIMIT REACHED. Battery: 25%
 * LOW BATTERY, 20% RESERVE. PV: Morning/Evening LOW PV OUTPUT, Night PV OFF — NO SUN"):
 * regression tests for [SimulationWarnings.warningsFor]. Irradiance figures at each test hour were
 * hand-traced with a Python port of [SimulationEngine.irradianceFactor] first (this project's
 * standard practice) — 6:00am ≈ 0.038, 7:00am ≈ 0.256, 12:00pm ≈ 0.997, 4:30pm ≈ 0.256 — so the
 * shoulder-vs-midday PV warning boundary is exact, not estimated.
 */
class SimulationWarningsTest {

    private fun config(hasBattery: Boolean = true, inverterKw: Double = 10.0) = SimSystemConfig(
        pvCapacityKw = 3.69, panelCount = 6, panelWatts = 615, inverterKw = inverterKw, inverterName = "Deye SUN-10K-SG01LP1-US",
        batteryCapacityKwh = 10.24, batteryName = "10kWh (SRNE SR-EOS10B)", hasBattery = hasBattery, gridConnectable = true,
        avgDailyLoadKwh = 30.0, peakLoadKw = 8.0, batteryMaxChargeKw = 7.68, batteryMaxDischargeKw = 10.0,
        batteryChargeEfficiency = 0.95, batteryDepthOfDischargeFraction = SimulationEngine.BATTERY_MIN_SOC_FRACTION
    )

    private fun frame(
        hour: Double = 12.0,
        inverterLoadKw: Double = 2.0,
        batterySocPercent: Float = 60f,
        potentialPvKw: Double = 3.0
    ) = SimFrame(
        hour = hour, pvKw = potentialPvKw, potentialPvKw = potentialPvKw, cellTempC = 30.0, temperatureDerateFraction = 1.0,
        houseLoadKw = inverterLoadKw, solarToHouseKw = inverterLoadKw, solarToBatteryKw = 0.0, batteryToHouseKw = 0.0,
        gridToHouseKw = 0.0, gridToBatteryKw = 0.0, batterySocKwh = 0.0, batterySocPercent = batterySocPercent,
        batteryPowerKw = 0.0, gridPowerKw = 0.0, unmetLoadKw = 0.0, curtailedSolarKw = 0.0,
        inverterLoadKw = inverterLoadKw, status = SystemStatus.SOLAR_POWERING_HOME
    )

    @Test
    fun `no warnings at midday with moderate load and healthy battery`() {
        val warnings = SimulationWarnings.warningsFor(frame(hour = 12.0, inverterLoadKw = 2.0, batterySocPercent = 60f), config())
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `inverter load thresholds fire at exactly 80, 90, and 100 percent`() {
        val cfg = config(inverterKw = 10.0)
        assertTrue(SimulationWarnings.warningsFor(frame(inverterLoadKw = 7.9), cfg).none { it.label.startsWith("INVERTER") })
        assertEquals("INVERTER HIGH LOAD", SimulationWarnings.warningsFor(frame(inverterLoadKw = 8.0), cfg).first { it.label.startsWith("INVERTER") }.label)
        assertEquals("INVERTER NEAR LIMIT", SimulationWarnings.warningsFor(frame(inverterLoadKw = 9.0), cfg).first { it.label.startsWith("INVERTER") }.label)
        assertEquals("INVERTER LIMIT REACHED", SimulationWarnings.warningsFor(frame(inverterLoadKw = 10.0), cfg).first { it.label.startsWith("INVERTER") }.label)
        assertEquals(WarningLevel.CAUTION, SimulationWarnings.warningsFor(frame(inverterLoadKw = 8.0), cfg).first { it.label.startsWith("INVERTER") }.level)
        assertEquals(WarningLevel.ALERT, SimulationWarnings.warningsFor(frame(inverterLoadKw = 10.0), cfg).first { it.label.startsWith("INVERTER") }.level)
    }

    @Test
    fun `battery thresholds fire at 25 percent low and the real reserve floor`() {
        val cfg = config()
        assertTrue(SimulationWarnings.warningsFor(frame(batterySocPercent = 26f), cfg).none { it.label.contains("BATTERY") })
        assertEquals("LOW BATTERY", SimulationWarnings.warningsFor(frame(batterySocPercent = 25f), cfg).first { it.label.contains("BATTERY") }.label)
        // Reserve floor is 20% (SimulationEngine.BATTERY_MIN_SOC_FRACTION), not a separate hardcoded number.
        assertEquals("BATTERY AT RESERVE", SimulationWarnings.warningsFor(frame(batterySocPercent = 20f), cfg).first { it.label.contains("BATTERY") }.label)
    }

    @Test
    fun `no battery in the system never produces a battery warning regardless of SOC`() {
        val cfg = config(hasBattery = false)
        val warnings = SimulationWarnings.warningsFor(frame(batterySocPercent = 5f), cfg)
        assertTrue(warnings.none { it.label.contains("BATTERY") })
    }

    @Test
    fun `PV is off at night regardless of the hour's irradiance shape`() {
        val warnings = SimulationWarnings.warningsFor(frame(hour = 2.0, potentialPvKw = 0.0), config())
        assertEquals("PV OFF — NO SUN", warnings.first { it.label.contains("PV") }.label)
    }

    @Test
    fun `PV shows low output during the morning and evening shoulders, not at midday`() {
        val cfg = config()
        assertEquals("LOW PV OUTPUT", SimulationWarnings.warningsFor(frame(hour = 6.0, potentialPvKw = 0.5), cfg).first { it.label.contains("PV") }.label)
        assertEquals("LOW PV OUTPUT", SimulationWarnings.warningsFor(frame(hour = 7.0, potentialPvKw = 1.0), cfg).first { it.label.contains("PV") }.label)
        assertEquals("LOW PV OUTPUT", SimulationWarnings.warningsFor(frame(hour = 16.5, potentialPvKw = 1.0), cfg).first { it.label.contains("PV") }.label)
        assertTrue(SimulationWarnings.warningsFor(frame(hour = 12.0, potentialPvKw = 3.0), cfg).none { it.label.contains("PV") })
    }

    @Test
    fun `multiple warnings can fire at once - night plus a critically low battery`() {
        val warnings = SimulationWarnings.warningsFor(frame(hour = 2.0, potentialPvKw = 0.0, batterySocPercent = 18f), config())
        assertEquals(2, warnings.size)
        assertTrue(warnings.any { it.label.contains("PV") })
        assertTrue(warnings.any { it.label.contains("BATTERY") })
    }
}
