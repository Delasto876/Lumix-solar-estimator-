package com.lumix.estimator.domain.simulation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A70 (installer's explicit decision on the open question A69 raised — "scale the simulation
 * curve to the installer's entered PSH" over "keep it a fixed representative clear-sky day"):
 * regression tests for [SimSystemConfig.pshHours] and [SimulationEngine.REFERENCE_CURVE_PSH_HOURS].
 */
class PshScalingTest {

    private fun configFor(pvCapacityKw: Double, pshHours: Double) = SimSystemConfig(
        pvCapacityKw = pvCapacityKw,
        panelCount = 1,
        panelWatts = (pvCapacityKw * 1000).toInt(),
        inverterKw = 20.0,
        inverterName = "Test Inverter",
        batteryCapacityKwh = 0.0,
        batteryName = null,
        hasBattery = false,
        gridConnectable = false,
        avgDailyLoadKwh = 20.0,
        peakLoadKw = 20.0,
        batteryMaxChargeKw = 0.0,
        batteryMaxDischargeKw = 0.0,
        batteryChargeEfficiency = 0.95,
        batteryDepthOfDischargeFraction = SimulationEngine.BATTERY_MIN_SOC_FRACTION,
        maxPvInputKw = 100.0, // Not what these tests are checking — kept far above anything produced.
        pshHours = pshHours
    )

    private fun frameAtSolarNoon(config: SimSystemConfig): SimFrame =
        SimulationEngine.buildDayTimeline(config = config, gridConnected = false, startHour = 12.0, durationHours = 0.0, resolutionMinutes = 5).first()

    @Test
    fun `a config at the reference PSH produces the curve's native, unscaled amplitude`() {
        // pshHours == REFERENCE_CURVE_PSH_HOURS -> pshScale == 1.0 -> unchanged from before A70.
        val config = configFor(pvCapacityKw = 10.0, pshHours = SimulationEngine.REFERENCE_CURVE_PSH_HOURS)
        val frame = frameAtSolarNoon(config)
        assertEquals(9.974, frame.potentialPvKw, 0.02) // irradianceFactor(12.0)=0.9974 x 10.0
    }

    @Test
    fun `a below-reference PSH (weaker solar resource) scales potential PV down proportionally`() {
        // National-default PSH 5.5h vs the curve's 7.2085h reference -> scale = 0.7629.
        val config = configFor(pvCapacityKw = 10.0, pshHours = 5.5)
        val frame = frameAtSolarNoon(config)
        val expectedScale = 5.5 / SimulationEngine.REFERENCE_CURVE_PSH_HOURS
        assertEquals(0.9974 * expectedScale * 10.0, frame.potentialPvKw, 0.02)
        assertTrueLess(frame.potentialPvKw, 9.974)
    }

    private fun assertTrueLess(actual: Double, bound: Double) {
        org.junit.Assert.assertTrue("expected $actual < $bound", actual < bound)
    }

    @Test
    fun `the scaled curve's own daily energy integral now equals pvCapacity times pshHours`() {
        // The whole point of A70: a system sized as pvKw = dailyKwh / psh should now simulate
        // approximately that same dailyKwh of production (before temperature/system-loss
        // derates) - not the fixed ~7.2kWh/kW/day every site got before this round, regardless
        // of the PSH it was actually designed against.
        val psh = 5.5
        val pvCapacityKw = 10.0
        val config = configFor(pvCapacityKw = pvCapacityKw, pshHours = psh)
        val frames = SimulationEngine.buildDayTimeline(config = config, gridConnected = false, resolutionMinutes = 5)
        val dt = 5.0 / 60.0
        val dailyPotentialKwh = frames.sumOf { it.potentialPvKw * dt }
        assertEquals(pvCapacityKw * psh, dailyPotentialKwh, 0.15)
    }
}
