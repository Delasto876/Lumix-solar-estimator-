package com.lumix.estimator.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A78/A79 (spec Phase 15/16 — "improve quote engine"/"improve settings/materials", §39's own "Show:
 * Original subtotal, Discount, Final subtotal, Tax/fees, Grand total"): regression tests for
 * [QuoteResult.subtotalBeforeDiscount]/[QuoteResult.taxAmount] — the two fields added in A78 so
 * every quote-facing screen/export reads the same already-computed figures instead of re-adding
 * materialsTotal/serviceCharge/deliveryCharge itself — plus A79's [PriceList.serviceRatePercent]/
 * [PriceList.taxRatePercent], which made the service and tax rates those fields depend on actually
 * configurable instead of a hard-coded 0.15/0.0.
 */
class SystemCalculatorPricingTest {

    private fun manualHybridInputs(discountType: DiscountType = DiscountType.NONE, discountValue: Double = 0.0) = QuoteInputs(
        quoteMode = QuoteMode.MANUAL,
        systemMode = SystemMode.HYBRID,
        manualModeType = ManualModeType.BATTERY_LED,
        manualBatt10k = 1,
        deliveryCharge = 5000.0,
        discountType = discountType,
        discountValue = discountValue
    )

    @Test
    fun `subtotalBeforeDiscount is exactly materials plus service plus delivery`() {
        val result = SystemCalculator.calculate(manualHybridInputs(), PriceList.DEFAULT)
        val expected = result.materialsTotal + result.serviceCharge + result.deliveryCharge
        assertEquals(expected, result.subtotalBeforeDiscount, 0.01)
    }

    @Test
    fun `taxAmount is zero under the default 0 percent tax rate`() {
        val result = SystemCalculator.calculate(manualHybridInputs(), PriceList.DEFAULT)
        assertEquals(0.0, result.taxAmount, 0.0)
    }

    @Test
    fun `a configured tax rate applies to the post-discount subtotal, not the pre-discount one`() {
        val prices = PriceList.DEFAULT.copy(taxRatePercent = 10.0)
        val result = SystemCalculator.calculate(manualHybridInputs(DiscountType.PERCENT, 20.0), prices)
        val postDiscountSubtotal = result.subtotalBeforeDiscount - result.discountAmount
        assertEquals(postDiscountSubtotal * 0.10, result.taxAmount, 0.01)
        assertEquals(postDiscountSubtotal + result.taxAmount, result.grandTotal, 0.01)
    }

    @Test
    fun `a configured service rate replaces the old hard-coded 15 percent`() {
        val defaultResult = SystemCalculator.calculate(manualHybridInputs(), PriceList.DEFAULT)
        assertEquals(defaultResult.materialsTotal * 0.15, defaultResult.serviceCharge, 0.01)

        val prices = PriceList.DEFAULT.copy(serviceRatePercent = 20.0)
        val result = SystemCalculator.calculate(manualHybridInputs(), prices)
        assertEquals(result.materialsTotal * 0.20, result.serviceCharge, 0.01)
    }

    @Test
    fun `grandTotal equals subtotal minus discount plus tax for every discount type`() {
        val none = SystemCalculator.calculate(manualHybridInputs(), PriceList.DEFAULT)
        assertEquals(none.subtotalBeforeDiscount - none.discountAmount + none.taxAmount, none.grandTotal, 0.01)

        val percent = SystemCalculator.calculate(manualHybridInputs(DiscountType.PERCENT, 10.0), PriceList.DEFAULT)
        assertEquals(percent.subtotalBeforeDiscount - percent.discountAmount + percent.taxAmount, percent.grandTotal, 0.01)
        assertEquals(percent.subtotalBeforeDiscount * 0.10, percent.discountAmount, 0.01)

        val fixed = SystemCalculator.calculate(manualHybridInputs(DiscountType.FIXED, 15000.0), PriceList.DEFAULT)
        assertEquals(fixed.subtotalBeforeDiscount - fixed.discountAmount + fixed.taxAmount, fixed.grandTotal, 0.01)
        assertEquals(15000.0, fixed.discountAmount, 0.01)
    }
}
