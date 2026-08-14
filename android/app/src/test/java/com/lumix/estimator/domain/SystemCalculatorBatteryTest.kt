package com.lumix.estimator.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A54: regression tests for the expanded MANUAL-mode battery bank ("make it 5, 10, 15, 16, 20 and
 * a custom field to add the capacity and it ties into the calculation", 2026-08-14) — previously
 * only 5/10/15 kWh tiers existed in [com.lumix.estimator.ui.wizard.steps.StepBatteryBank]. Verifies
 * the new 16kWh/20kWh tiers and the custom-capacity/count field actually flow into
 * [QuoteResult.totalBatteryKwh] and the priced materials list, using [PriceList.DEFAULT]'s own
 * placeholder prices so the expected numbers are exact and hand-computable (215000 + 420000 +
 * 520000 + 555000 + 680000 + 8kWh x2 units x34000/kWh = 2,934,000; totalKwh = 5+10+15+16+20+16 = 82).
 */
class SystemCalculatorBatteryTest {

    private fun manualHybridInputs() = QuoteInputs(
        quoteMode = QuoteMode.MANUAL,
        systemMode = SystemMode.HYBRID,
        manualModeType = ManualModeType.BATTERY_LED,
        manualBatt5k = 1,
        manualBatt10k = 1,
        manualBatt15k = 1,
        manualBatt16k = 1,
        manualBatt20k = 1,
        manualBattCustomKwh = 8.0,
        manualBattCustomCount = 2
    )

    @Test
    fun `all six battery tiers sum into totalBatteryKwh`() {
        val result = SystemCalculator.calculate(manualHybridInputs(), PriceList.DEFAULT, PriceList.DEFAULT)
        assertEquals(82.0, result.totalBatteryKwh, 0.01)
    }

    @Test
    fun `each tier prices at its own placeholder rate, custom priced per kWh`() {
        val result = SystemCalculator.calculate(manualHybridInputs(), PriceList.DEFAULT, PriceList.DEFAULT)
        val batteryCost = result.materials.filter { it.name.contains("LiFePO4") }.sumOf { it.subtotal }
        assertEquals(2_934_000.0, batteryCost, 1.0)
    }

    @Test
    fun `custom field contributes nothing when either side is left at zero`() {
        val zeroCount = manualHybridInputs().copy(manualBattCustomKwh = 12.0, manualBattCustomCount = 0)
        val zeroKwh = manualHybridInputs().copy(manualBattCustomKwh = 0.0, manualBattCustomCount = 3)
        val resultZeroCount = SystemCalculator.calculate(zeroCount, PriceList.DEFAULT, PriceList.DEFAULT)
        val resultZeroKwh = SystemCalculator.calculate(zeroKwh, PriceList.DEFAULT, PriceList.DEFAULT)
        // 82 - 16 (the custom 8kWh x2 contribution) = 66 from the five fixed tiers alone.
        assertEquals(66.0, resultZeroCount.totalBatteryKwh, 0.01)
        assertEquals(66.0, resultZeroKwh.totalBatteryKwh, 0.01)
    }

    @Test
    fun `battery module count includes every tier's units`() {
        val result = SystemCalculator.calculate(manualHybridInputs(), PriceList.DEFAULT, PriceList.DEFAULT)
        // 1 (5k) + 1 (10k) + 1 (15k) + 1 (16k) + 1 (20k) + 2 (custom) = 7 module quantities in the priced materials list.
        val moduleCount = result.materials.filter { it.name.contains("LiFePO4") }.sumOf { it.qty }
        assertEquals(7.0, moduleCount, 0.01)
    }
}
