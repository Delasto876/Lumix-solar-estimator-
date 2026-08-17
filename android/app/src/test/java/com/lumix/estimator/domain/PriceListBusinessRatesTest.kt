package com.lumix.estimator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A79 (spec Phase 16 — "improve settings/materials", §40's own "Labour rates" / "Tax settings"):
 * regression tests for [PriceList.serviceRatePercent]/[PriceList.taxRatePercent] and their
 * [PriceFieldSpec] entries — both previously hard-coded/nonexistent, now editable via the same
 * Materials & Pricing settings section as every other price.
 */
class PriceListBusinessRatesTest {

    @Test
    fun `default service and tax rates match the app's pre-existing behavior exactly`() {
        assertEquals(15.0, PriceList.DEFAULT.serviceRatePercent, 0.001)
        assertEquals(0.0, PriceList.DEFAULT.taxRatePercent, 0.001)
    }

    @Test
    fun `exactly two percentage-suffixed fields exist, in the Business Rates group`() {
        val percentFields = PriceFields.all.filter { it.suffix == "%" }
        assertEquals(2, percentFields.size)
        assertTrue(percentFields.all { it.group == "Business Rates" })
        assertTrue(percentFields.any { it.key == "serviceRatePercent" })
        assertTrue(percentFields.any { it.key == "taxRatePercent" })
    }

    @Test
    fun `every other field still defaults to the J dollar suffix`() {
        val currencyFields = PriceFields.all.filter { it.suffix == "J$" }
        assertEquals(PriceFields.all.size - 2, currencyFields.size)
    }

    @Test
    fun `the service rate field getter and setter round-trip correctly`() {
        val field = PriceFields.all.first { it.key == "serviceRatePercent" }
        val updated = field.set(PriceList.DEFAULT, 22.0)
        assertEquals(22.0, field.get(updated), 0.001)
    }
}
