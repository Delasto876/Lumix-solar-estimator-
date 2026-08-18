package com.lumix.estimator.domain

import com.lumix.estimator.domain.simulation.SimSystemConfig
import com.lumix.estimator.domain.simulation.SimulationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A81 (Phase 18, restored — spec's original "Map/Roof Survey"/"Google Maps" sections, removed at
 * A20 and rebuilt this round per the installer's explicit "yes, rebuild it" / "Google Maps SDK"
 * choice): regression tests for the roof-constrained sizing path — [RoofConstraint] capping
 * [SystemCalculator]'s panel count, [QuoteResult.isRoofConstrained]/[QuoteResult.energyOptimalPvKw],
 * and [SimSystemConfig.isSiteAware] — plus [SimulationEngine.sitePlaneOfArrayFactor]'s pure
 * geometry, none of which had unit coverage before this feature was originally removed.
 */
class RoofConstraintTest {

    private fun manualPanelLedInputs(manualPanelCount: Int, roofConstraint: RoofConstraint? = null) = QuoteInputs(
        quoteMode = QuoteMode.MANUAL,
        systemMode = SystemMode.HYBRID,
        manualModeType = ManualModeType.PANEL_LED,
        manualPanelCount = manualPanelCount,
        roofConstraint = roofConstraint
    )

    private fun constraintFor(maxPanelCount: Int, azimuth: Double? = 180.0, pitch: Double? = 18.0) = RoofConstraint(
        sourceSiteId = "site-1",
        sourceRoofPlaneId = "roof-1",
        roofLabel = "Main Roof",
        maxPanelCount = maxPanelCount,
        panelWattage = 595,
        azimuthDegrees = azimuth,
        pitchDegrees = pitch,
        latitude = 18.0,
        longitude = -77.0
    )

    @Test
    fun `no roof constraint leaves panel count and energyOptimalPanelCount equal`() {
        val result = SystemCalculator.calculate(manualPanelLedInputs(10), PriceList.DEFAULT)
        assertEquals(10, result.panelCount)
        assertEquals(10, result.energyOptimalPanelCount)
        assertFalse(result.isRoofConstrained)
        assertEquals(result.pvKw, result.energyOptimalPvKw, 0.001)
    }

    @Test
    fun `a roof constraint below the electricity-driven count caps panelCount and rounds down to even`() {
        // maxPanelCount=5 is odd — rounds DOWN to 4, never up (never overclaim what fits).
        val result = SystemCalculator.calculate(manualPanelLedInputs(10, constraintFor(maxPanelCount = 5)), PriceList.DEFAULT)
        assertEquals(4, result.panelCount)
        assertEquals(10, result.energyOptimalPanelCount)
        assertTrue(result.isRoofConstrained)
        assertEquals(10 * 595 / 1000.0, result.energyOptimalPvKw, 0.001)
        assertEquals(4 * 595 / 1000.0, result.pvKw, 0.001)
    }

    @Test
    fun `an even roof cap applies exactly, no extra rounding`() {
        val result = SystemCalculator.calculate(manualPanelLedInputs(10, constraintFor(maxPanelCount = 6)), PriceList.DEFAULT)
        assertEquals(6, result.panelCount)
        assertTrue(result.isRoofConstrained)
    }

    @Test
    fun `a roof constraint at or above the electricity-driven count has no effect`() {
        val result = SystemCalculator.calculate(manualPanelLedInputs(10, constraintFor(maxPanelCount = 10)), PriceList.DEFAULT)
        assertEquals(10, result.panelCount)
        assertFalse(result.isRoofConstrained)
    }

    @Test
    fun `a roof surveyed against a smaller panel caps count by kWp, not raw count, when a bigger panel is actually selected`() {
        // 2026-08-18 audit fix: the roof was traced assuming 20 x 595W panels fit (11.9 kWp).
        // The installer then explicitly selects real 700W panels (manualPanelWatts) for the
        // actual system — a bigger physical footprint per panel than the roof survey assumed.
        // The count-only cap (pre-fix) would have let all 20 x 700W panels through (14.0 kWp,
        // overclaiming the mapped roof's real capacity); the kWp-based cap must instead limit to
        // floor(11.9 kWp * 1000 / 700W) = 17 panels, rounded down to 16 (even).
        val inputs = QuoteInputs(
            quoteMode = QuoteMode.MANUAL,
            systemMode = SystemMode.HYBRID,
            manualModeType = ManualModeType.PANEL_LED,
            manualPanelCount = 20,
            manualPanelWatts = 700,
            roofConstraint = constraintFor(maxPanelCount = 20)
        )
        val result = SystemCalculator.calculate(inputs, PriceList.DEFAULT)
        assertEquals(16, result.panelCount)
        assertTrue(result.isRoofConstrained)
        assertEquals(16 * 700 / 1000.0, result.pvKw, 0.001)
    }

    @Test
    fun `SimSystemConfig is site-aware only when built from an input carrying a full roof constraint`() {
        val plain = SystemCalculator.calculate(manualPanelLedInputs(10), PriceList.DEFAULT)
        val plainConfig = SimSystemConfig.from(plain, manualPanelLedInputs(10))
        assertFalse(plainConfig.isSiteAware)
        assertNull(plainConfig.siteLatitude)

        val constrainedInputs = manualPanelLedInputs(10, constraintFor(maxPanelCount = 6))
        val constrainedResult = SystemCalculator.calculate(constrainedInputs, PriceList.DEFAULT)
        val siteConfig = SimSystemConfig.from(constrainedResult, constrainedInputs)
        assertTrue(siteConfig.isSiteAware)
        assertEquals(18.0, siteConfig.siteLatitude!!, 0.0)
        assertEquals(180.0, siteConfig.roofAzimuthDegrees!!, 0.0)
        assertEquals(18.0, siteConfig.roofPitchDegrees!!, 0.0)
    }

    @Test
    fun `a roof constraint with no confirmed azimuth pitch keeps the config not site-aware`() {
        val inputs = manualPanelLedInputs(10, constraintFor(maxPanelCount = 6, azimuth = null, pitch = null))
        val result = SystemCalculator.calculate(inputs, PriceList.DEFAULT)
        val config = SimSystemConfig.from(result, inputs)
        assertFalse(config.isSiteAware)
    }

    @Test
    fun `sitePlaneOfArrayFactor is exactly sin(elevation) for a flat roof, independent of azimuth`() {
        // pitch=0 (flat) roof: the standard tilted-surface formula reduces to sin(elevation) —
        // an algebraic identity, true for any date, so this isn't a flaky "today's sun" test.
        val hour = 12.0 // near solar noon
        val position = SimulationEngine.sitePosition(hour, latitude = 18.0, longitude = -77.0)
        val expected = if (position.elevationDegrees <= 0.0) 0.0 else Math.sin(Math.toRadians(position.elevationDegrees)).coerceIn(0.0, 1.0)
        val actual = SimulationEngine.sitePlaneOfArrayFactor(hour, latitude = 18.0, longitude = -77.0, roofAzimuthDegrees = 90.0, roofPitchDegrees = 0.0)
        assertEquals(expected, actual, 0.0001)
    }

    @Test
    fun `sitePlaneOfArrayFactor is zero when the sun is below the horizon`() {
        val actual = SimulationEngine.sitePlaneOfArrayFactor(
            hour = 0.0, latitude = 18.0, longitude = -77.0, roofAzimuthDegrees = 180.0, roofPitchDegrees = 18.0
        )
        assertEquals(0.0, actual, 0.0)
    }
}
