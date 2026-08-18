package com.lumix.estimator.domain.ai

import com.lumix.estimator.domain.MaterialLine
import com.lumix.estimator.domain.QuoteResult
import com.lumix.estimator.domain.SystemMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * A85 (Phase 24): confirms [AiExplanationService] honors [AiConfig] — disabled by default (no API
 * key configured, the state every unit test runs in) — matching the spec's own "when disabled, the
 * application must work normally" requirement, and that enabling without a real provider still
 * never fabricates an explanation.
 */
class AiExplanationServiceTest {

    private fun context() = EngineeringExplanationContext(
        quote = QuoteResult(
            effectiveSystemMode = SystemMode.HYBRID,
            designDailyKwh = 20.0, peakWatts = 3000.0,
            panelCount = 6, panelWatts = 615,
            inverterName = "Deye SUN-10K-SG01LP1-US", inverterKw = 10.0,
            batteryName = "10kWh (SRNE SR-EOS10B)", batteryRequiredKwh = 10.0, totalBatteryKwh = 10.24,
            rows = 2, railsPerRow = 2, totalRails = 4, totalMidClamps = 8, totalEndClamps = 4,
            totalBackLegs = 6, totalFrontLegs = 6, totalBolts = 20, totalLFoot = 12,
            materials = listOf(MaterialLine("Panel", 6.0, 300.0)), materialsTotal = 1800.0,
            serviceCharge = 200.0, deliveryCharge = 100.0, discountAmount = 0.0, grandTotal = 2100.0
        ),
        diagnostics = emptyList()
    )

    @Test
    fun `explain returns Disabled when AiConfig has no API key`() {
        AiConfig.configure("")
        val result = runBlocking { AiExplanationService.explain(context()) }
        assertEquals(AiExplanationResult.Disabled, result)
    }

    @Test
    fun `explain never returns a fabricated Explained result even when a key is configured`() {
        // A85: no real AI provider is implemented yet (see AiExplanationService's own doc) — a
        // configured key only proves the app *would* call one, not that this stub invents text.
        AiConfig.configure("test-key-not-a-real-credential")
        try {
            val result = runBlocking { AiExplanationService.explain(context()) }
            assertFalse("must never fabricate an explanation without a real provider", result is AiExplanationResult.Explained)
            assertEquals(AiExplanationResult.NotConfigured, result)
        } finally {
            AiConfig.configure("") // restore the default so other tests in this suite see AiConfig disabled
        }
    }
}
