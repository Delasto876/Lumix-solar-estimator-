package com.lumix.estimator.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A79 (spec Phase 16): regression tests for [BusinessInfo.isBlank] — the flag every export generator uses to decide whether to render business-info sections at all. */
class BusinessInfoTest {

    @Test
    fun `a fresh install with no Settings entered is blank`() {
        assertTrue(BusinessInfo().isBlank)
    }

    @Test
    fun `any single non-blank field makes it not blank`() {
        assertFalse(BusinessInfo(companyName = "Lumix Technologies").isBlank)
        assertFalse(BusinessInfo(address = "123 Main St").isBlank)
        assertFalse(BusinessInfo(phone = "876-555-0100").isBlank)
        assertFalse(BusinessInfo(email = "info@example.com").isBlank)
        assertFalse(BusinessInfo(warranty = "5 years").isBlank)
        assertFalse(BusinessInfo(paymentTerms = "50% deposit").isBlank)
    }
}
