package com.lumix.estimator.domain.simulation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 2026-08-18 audit fix: [JamaicaClimatology.MonthProfile.solarResourceFactor] was defined,
 * documented ("combines multiplicatively with the site's per-parish annual PSH estimate — this
 * table supplies the seasonal shape"), and unit-tested in [JamaicaClimatologyTest] — but
 * [SimulationEngine.buildDayTimeline] never actually read it, so no simulation ever showed the
 * documented seasonal PV variation. This verifies the wiring: with everything else held fixed, an
 * October-dated simulation's midday PV output is scaled by exactly October's real
 * `solarResourceFactor` (0.82) relative to an install-month-less (annual-average, factor 1.0) run.
 */
class SolarResourceFactorWiringTest {

    private fun config(installMonth: Int?) = SimSystemConfig(
        pvCapacityKw = 6 * 0.615,
        panelCount = 6,
        panelWatts = 615,
        inverterKw = 10.0,
        inverterName = "Deye SUN-10K-SG01LP1-US",
        batteryCapacityKwh = 10.24,
        batteryName = "10kWh (SRNE SR-EOS10B)",
        hasBattery = true,
        gridConnectable = true,
        avgDailyLoadKwh = 20.0,
        peakLoadKw = 8.0,
        batteryMaxChargeKw = 5.0,
        batteryMaxDischargeKw = 5.0,
        batteryChargeEfficiency = 0.95,
        batteryDepthOfDischargeFraction = SimulationEngine.BATTERY_MIN_SOC_FRACTION,
        installMonth = installMonth
    )

    @Test
    fun `an October simulation's midday PV is scaled by October's real solarResourceFactor`() {
        val annualAverage = SimulationEngine.buildDayTimeline(config(installMonth = null))
        val october = SimulationEngine.buildDayTimeline(config(installMonth = 10))

        val annualNoon = annualAverage.first { kotlin.math.abs(it.hour - 12.0) < 0.05 }
        val octoberNoon = october.first { kotlin.math.abs(it.hour - 12.0) < 0.05 }

        val octoberFactor = JamaicaClimatology.profileFor(10).solarResourceFactor
        assertEquals(0.82, octoberFactor, 0.001)

        // pvKw's amplitude scale is pshScale = pshHours / effectiveCurveSunHours * solarResourceFactor.
        // installMonth also changes effectiveCurveSunHours (real day length replaces the fixed
        // reference window), so isolating solarResourceFactor's own contribution means replicating
        // that second factor here too, not just asserting the seasonal factor alone.
        val octoberSunTimes = SolarPosition.sunTimesForMonth(10)
        val curveShapeIntegral = SimulationEngine.REFERENCE_CURVE_PSH_HOURS / 12.0
        val effectiveCurveSunHoursOctober = octoberSunTimes.dayLengthHours * curveShapeIntegral
        val expectedRatio = (SimulationEngine.REFERENCE_CURVE_PSH_HOURS / effectiveCurveSunHoursOctober) * octoberFactor
        assertEquals(annualNoon.pvKw * expectedRatio, octoberNoon.pvKw, 0.01)
    }

    @Test
    fun `no install month reproduces exactly annual-average behavior (factor 1_0, no-op)`() {
        val factor = JamaicaClimatology.profileFor(null).solarResourceFactor
        assertEquals(1.0, factor, 0.0)
    }
}
