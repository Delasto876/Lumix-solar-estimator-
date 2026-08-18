package com.lumix.estimator.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 2026-08-18 audit fix: the off-grid panel-count cap ("this catalog's off-grid inverter tier —
 * 3/3.2kW units — is scoped to small stand-alone arrays, capped at 4 panels") used to only fire
 * for MANUAL mode when the installer picked the smallest catalog wattage (595W) — picking any
 * bigger real panel bypassed the cap entirely, letting an installer configure an arbitrarily
 * large off-grid array this catalog's off-grid hardware was never sized for. GUIDED/LOAD were
 * never affected (they always force off-grid to the smallest wattage), so this is a MANUAL-only
 * regression test.
 */
class SystemCalculatorOffgridPanelCapTest {

    private fun manualOffgridInputs(manualPanelWatts: Int, manualPanelCount: Int) = QuoteInputs(
        quoteMode = QuoteMode.MANUAL,
        systemMode = SystemMode.OFFGRID,
        manualModeType = ManualModeType.FULL_MANUAL,
        manualPanelWatts = manualPanelWatts,
        manualPanelCount = manualPanelCount
    )

    @Test
    fun `the smallest catalog wattage was already capped at 4 panels before this fix`() {
        val result = SystemCalculator.calculate(manualOffgridInputs(595, 20), PriceList.DEFAULT)
        assertEquals(4, result.panelCount)
    }

    @Test
    fun `picking a bigger real panel no longer bypasses the 4-panel off-grid cap`() {
        // Pre-fix, 700W (bigger than the smallest 595W wattage) let the full 20-panel manual
        // count through untouched — a 14kW off-grid array on 3.2kW-class off-grid hardware.
        val result = SystemCalculator.calculate(manualOffgridInputs(700, 20), PriceList.DEFAULT)
        assertEquals(4, result.panelCount)
        assertEquals(4 * 700 / 1000.0, result.pvKw, 0.001)
    }

    @Test
    fun `a request already at or under 4 panels is unaffected`() {
        val result = SystemCalculator.calculate(manualOffgridInputs(720, 4), PriceList.DEFAULT)
        assertEquals(4, result.panelCount)
    }
}
