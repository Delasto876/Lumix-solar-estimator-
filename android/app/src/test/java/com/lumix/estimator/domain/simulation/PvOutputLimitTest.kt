package com.lumix.estimator.domain.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A69 (spec Phase 10/66's own "the simulation must never generate more PV power than the
 * selected system physically permits... whatever system the installer designs is EXACTLY the
 * system the simulation models"): regression tests for [SimSystemConfig.maxPvInputKw] and its use
 * in [SimulationEngine.buildDayTimeline].
 *
 * Before this fix, PV production was capped at `config.inverterKw` (the inverter's continuous AC
 * output rating) — but a real hybrid inverter's PV DC input stage typically accepts meaningfully
 * more than that, specifically to allow economical DC-side oversizing (a common, deliberate design
 * practice, not an edge case). A design with more PV capacity than the inverter's AC rating —
 * entirely realistic, since GUIDED/LOAD panel sizing is driven by daily energy need
 * (`designDailyKwh / psh`) while inverter sizing is driven by peak instantaneous load
 * (`peakWatts * 1.25`), two independent figures — would have its legitimate solar-noon production
 * silently understated by the old AC-side clamp.
 */
class PvOutputLimitTest {

    private fun configFor(pvCapacityKw: Double, inverterKw: Double, maxPvInputKw: Double = inverterKw * 1.3) = SimSystemConfig(
        pvCapacityKw = pvCapacityKw,
        panelCount = 1,
        panelWatts = (pvCapacityKw * 1000).toInt(),
        inverterKw = inverterKw,
        inverterName = "Test Inverter",
        batteryCapacityKwh = 0.0,
        batteryName = null,
        hasBattery = false,
        gridConnectable = false,
        avgDailyLoadKwh = 20.0,
        peakLoadKw = inverterKw,
        batteryMaxChargeKw = 0.0,
        batteryMaxDischargeKw = 0.0,
        batteryChargeEfficiency = 0.95,
        batteryDepthOfDischargeFraction = SimulationEngine.BATTERY_MIN_SOC_FRACTION,
        maxPvInputKw = maxPvInputKw
    )

    /** Solar noon (irradianceFactor ~0.9974, hand-traced separately) — the frame where clamping actually bites. */
    private fun frameAtSolarNoon(config: SimSystemConfig): SimFrame =
        SimulationEngine.buildDayTimeline(config = config, gridConnected = false, startHour = 12.0, durationHours = 0.0, resolutionMinutes = 5).first()

    @Test
    fun `a DC-oversized array produces above the inverter's AC rating, up to its real PV input ceiling`() {
        // 12kW array on a 10kW inverter (a legitimate, common DC-oversized design) with a 15kW
        // real PV input ceiling (matching a real Growatt SPH 10000TL-HU-US's own maxPvW).
        // irradianceFactor(12.0) = 0.9974 (hand-traced via the same sin(pi*x)^1.2 formula) ->
        // potentialPv = 0.9974 x 12.0 = 11.97kW - above the OLD 10kW AC-side clamp, below the
        // 15kW real DC ceiling, so it must NOT be clamped at all here.
        val config = configFor(pvCapacityKw = 12.0, inverterKw = 10.0, maxPvInputKw = 15.0)
        val frame = frameAtSolarNoon(config)
        assertEquals(11.97, frame.potentialPvKw, 0.02)
        assertTrue(
            "expected potential PV to exceed the inverter's AC rating for a DC-oversized array",
            frame.potentialPvKw > config.inverterKw
        )
    }

    @Test
    fun `PV production is still capped once it exceeds the real DC input ceiling, not left unbounded`() {
        // A wildly oversized 20kW array against the same 10kW inverter / 15kW real DC ceiling -
        // potentialPv must still clamp at 15kW, not the naive 0.9974 x 20 = 19.95kW.
        val config = configFor(pvCapacityKw = 20.0, inverterKw = 10.0, maxPvInputKw = 15.0)
        val frame = frameAtSolarNoon(config)
        assertEquals(15.0, frame.potentialPvKw, 0.01)
    }

    @Test
    fun `a normal (not DC-oversized) array behaves exactly as before - well under either ceiling`() {
        val config = configFor(pvCapacityKw = 6.0, inverterKw = 10.0, maxPvInputKw = 13.0)
        val frame = frameAtSolarNoon(config)
        // 0.9974 x 6.0 = 5.98 - nowhere near either the 10kW or 13kW ceiling, so this is
        // unaffected by which ceiling is used - a direct regression guard for ordinary designs.
        assertEquals(5.98, frame.potentialPvKw, 0.02)
    }

    @Test
    fun `maxPvInputKw defaults to 1point3x the inverter rating when not explicitly resolved`() {
        val config = SimSystemConfig(
            pvCapacityKw = 6.0, panelCount = 1, panelWatts = 6000, inverterKw = 10.0, inverterName = "Test",
            batteryCapacityKwh = 0.0, batteryName = null, hasBattery = false, gridConnectable = false,
            avgDailyLoadKwh = 20.0, peakLoadKw = 10.0, batteryMaxChargeKw = 0.0, batteryMaxDischargeKw = 0.0,
            batteryChargeEfficiency = 0.95, batteryDepthOfDischargeFraction = SimulationEngine.BATTERY_MIN_SOC_FRACTION
            // maxPvInputKw intentionally omitted - exercises the data class's own default.
        )
        assertEquals(13.0, config.maxPvInputKw, 0.001)
    }
}
