package com.lumix.estimator.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** A78 (spec Phase 15 — "improve quote engine"): regression tests for the new shared quote-number/validity helpers. */
class FormattingTest {

    @Test
    fun `quote number is zero-padded and stable for the same id`() {
        assertEquals("LMX-Q-00001", quoteNumberFor(1L))
        assertEquals("LMX-Q-00042", quoteNumberFor(42L))
        assertEquals("LMX-Q-12345", quoteNumberFor(12345L))
    }

    @Test
    fun `quote validity is exactly the default window after issue`() {
        val issued = 0L
        val validUntil = quoteValidUntil(issued)
        val expectedMillis = DEFAULT_QUOTE_VALIDITY_DAYS.toLong() * 24 * 60 * 60 * 1000
        assertEquals(expectedMillis, validUntil.time)
    }
}
