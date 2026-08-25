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
        // [SimulationEngine.buildDayTimeline] takes installMonth as its OWN parameter, separate
        // from [SimSystemConfig.installMonth] (deliberately — see that parameter's own "purely
        // additive... null reproduces the exact prior behavior byte-for-byte" doc, a backward-
        // compatibility guarantee other existing callers rely on). Setting installMonth on the
        // config alone, without also passing it to buildDayTimeline, silently runs the
        // annual-average curve for BOTH calls below — this test's real purpose is to exercise the
        // real solarResourceFactor wiring, so it must pass installMonth through explicitly too.
        val annualAverage = SimulationEngine.buildDayTimeline(config(installMonth = null), installMonth = null)
        val october = SimulationEngine.buildDayTimeline(config(installMonth = 10), installMonth = 10)

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
        val pshScaleRatio = (SimulationEngine.REFERENCE_CURVE_PSH_HOURS / effectiveCurveSunHoursOctober) * octoberFactor
        // installMonth also swaps the fixed annual SUNRISE_HOUR/SUNSET_HOUR (midpoint 11.75, not
        // exactly noon) for October's real, ~symmetric-about-noon sun times, which by itself shifts
        // irradianceFactor's sin(pi*x)^1.2 shape curve's value AT noon — a second, genuine
        // multiplicative factor independent of pshScale that a pshScale-only ratio can't predict.
        // Replicating it here (rather than widening the tolerance) is what makes this comparison
        // exact rather than approximate.
        val annualShape = SimulationEngine.irradianceFactor(12.0, SimulationEngine.SUNRISE_HOUR, SimulationEngine.SUNSET_HOUR)
        val octoberShape = SimulationEngine.irradianceFactor(12.0, octoberSunTimes.sunriseHour, octoberSunTimes.sunsetHour)
        val expectedRatio = pshScaleRatio * (octoberShape / annualShape)
        // Compare potentialPvKw (irradianceFraction * pvCapacityKw, pre-temperature-derate), not
        // harvestablePvKw — SystemLosses.temperatureDerate is a nonlinear function of irradiance
        // itself, so a pure amplitude/shape ratio can't exactly predict the post-derate figure, only
        // the linear pre-derate ceiling. Both noon readings are well under maxPvInputKw, so neither
        // run is clipped, keeping this comparison exact.
        assertEquals(annualNoon.potentialPvKw * expectedRatio, octoberNoon.potentialPvKw, 0.001)
    }

    @Test
    fun `no install month reproduces exactly annual-average behavior (factor 1_0, no-op)`() {
        val factor = JamaicaClimatology.profileFor(null).solarResourceFactor
        assertEquals(1.0, factor, 0.0)
    }
}
