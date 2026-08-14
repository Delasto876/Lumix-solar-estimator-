package com.lumix.estimator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A57 (spec §11 — "remove the separate 'use discounted price' option... there should be ONE
 * original price... do not maintain two competing price systems"): regression tests confirming
 * [SystemCalculator.calculate] now takes exactly one [PriceList] and applies a discount only via
 * [QuoteInputs.discountType]/[QuoteInputs.discountValue] on top of that one list's subtotal.
 */
class DiscountTest {

    private fun baseInputs() = QuoteInputs(
        quoteMode = QuoteMode.MANUAL,
        systemMode = SystemMode.HYBRID,
        manualModeType = ManualModeType.BATTERY_LED,
        manualBatt10k = 1
    )

    @Test
    fun `no discount leaves the grand total equal to the pre-discount subtotal`() {
        val result = SystemCalculator.calculate(baseInputs(), PriceList.DEFAULT)
        val preDiscountTotal = result.materialsTotal + result.serviceCharge + result.deliveryCharge
        assertEquals(0.0, result.discountAmount, 0.01)
        assertEquals(preDiscountTotal, result.grandTotal, 0.01)
    }

    @Test
    fun `percent discount reduces the grand total by exactly that percentage of the subtotal`() {
        val inputs = baseInputs().copy(discountType = DiscountType.PERCENT, discountValue = 10.0)
        val result = SystemCalculator.calculate(inputs, PriceList.DEFAULT)
        val preDiscountTotal = result.materialsTotal + result.serviceCharge + result.deliveryCharge
        assertEquals(preDiscountTotal * 0.10, result.discountAmount, 0.01)
        assertEquals(preDiscountTotal * 0.90, result.grandTotal, 0.01)
    }

    @Test
    fun `fixed discount subtracts a flat amount, clamped at the subtotal`() {
        val inputs = baseInputs().copy(discountType = DiscountType.FIXED, discountValue = 5000.0)
        val result = SystemCalculator.calculate(inputs, PriceList.DEFAULT)
        val preDiscountTotal = result.materialsTotal + result.serviceCharge + result.deliveryCharge
        assertEquals(5000.0, result.discountAmount, 0.01)
        assertEquals(preDiscountTotal - 5000.0, result.grandTotal, 0.01)

        // A fixed discount larger than the whole subtotal never drives the total negative.
        val hugeDiscount = baseInputs().copy(discountType = DiscountType.FIXED, discountValue = preDiscountTotal + 1_000_000.0)
        val hugeResult = SystemCalculator.calculate(hugeDiscount, PriceList.DEFAULT)
        assertEquals(0.0, hugeResult.grandTotal, 0.01)
    }

    @Test
    fun `materials pricing is identical regardless of discount - one price list, not two`() {
        // The base prices (before any discount) must never move just because a discount was
        // configured — the exact bug shape "two competing price systems" would have produced here.
        val none = SystemCalculator.calculate(baseInputs(), PriceList.DEFAULT)
        val percent = SystemCalculator.calculate(baseInputs().copy(discountType = DiscountType.PERCENT, discountValue = 25.0), PriceList.DEFAULT)
        val fixed = SystemCalculator.calculate(baseInputs().copy(discountType = DiscountType.FIXED, discountValue = 1000.0), PriceList.DEFAULT)
        assertEquals(none.materialsTotal, percent.materialsTotal, 0.001)
        assertEquals(none.materialsTotal, fixed.materialsTotal, 0.001)
        assertTrue("materials total should be a real positive figure for this to be a meaningful check", none.materialsTotal > 0)
    }
}
