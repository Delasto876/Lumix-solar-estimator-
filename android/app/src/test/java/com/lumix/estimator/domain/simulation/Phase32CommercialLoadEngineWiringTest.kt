package com.lumix.estimator.domain.simulation

import com.lumix.estimator.domain.commercial.LoadInstance
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 32 ("for the appliance section... if commercial or industrial choose those in appliances
 * picker"): confirms [SimulationEngine.buildDayTimeline]'s new `commercialLoads` parameter is
 * genuinely wired into [SimFrame.houseLoadKw] (not just a picker cosmetic change), while every
 * existing residential caller — which never passes `commercialLoads` — sees byte-identical output.
 */
class Phase32CommercialLoadEngineWiringTest {

    private fun baseConfig(avgDailyLoadKwh: Double) = SimSystemConfig(
        pvCapacityKw = 0.0,
        panelCount = 1,
        panelWatts = 1,
        inverterKw = 20.0,
        inverterName = "Test Inverter",
        batteryCapacityKwh = 0.0,
        batteryName = null,
        hasBattery = false,
        gridConnectable = true,
        avgDailyLoadKwh = avgDailyLoadKwh,
        peakLoadKw = 20.0,
        batteryMaxChargeKw = 0.0,
        batteryMaxDischargeKw = 0.0,
        batteryChargeEfficiency = 0.95,
        batteryDepthOfDischargeFraction = SimulationEngine.BATTERY_MIN_SOC_FRACTION
    )

    @Test
    fun `omitting commercialLoads leaves every residential timeline byte-identical`() {
        val config = baseConfig(avgDailyLoadKwh = 15.0)
        val withoutParam = SimulationEngine.buildDayTimeline(config = config, resolutionMinutes = 60)
        val withEmptyList = SimulationEngine.buildDayTimeline(config = config, resolutionMinutes = 60, commercialLoads = emptyList())
        assertEquals(withoutParam.map { it.houseLoadKw }, withEmptyList.map { it.houseLoadKw })
    }

    @Test
    fun `a configured commercial load adds its real power to houseLoadKw only during its window`() {
        val config = baseConfig(avgDailyLoadKwh = 15.0)
        val load = LoadInstance(
            definitionId = "commercial_pump", label = "Pump", quantity = 2, ratedWatts = 1500.0,
            operatingHoursPerDay = 3.0, typicalStartHour = 9.0 // 9am-12pm, 2 x 1500W = 3kW
        )
        val baseline = SimulationEngine.buildDayTimeline(config = config, resolutionMinutes = 60)
        val withLoad = SimulationEngine.buildDayTimeline(config = config, resolutionMinutes = 60, commercialLoads = listOf(load))
        // Comparing against the same config's own baseline (rather than a hand-computed absolute
        // figure) isolates exactly the commercialLoads term regardless of what the background
        // day-shape curve happens to be at that hour — the two timelines are identical apart from
        // that one added term.
        val at10am = withLoad.first { it.hour == 10.0 }.houseLoadKw - baseline.first { it.hour == 10.0 }.houseLoadKw
        val at2pm = withLoad.first { it.hour == 14.0 }.houseLoadKw - baseline.first { it.hour == 14.0 }.houseLoadKw
        assertEquals(3.0, at10am, 0.001)
        assertEquals(0.0, at2pm, 0.001)
    }
}
