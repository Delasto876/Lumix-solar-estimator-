package com.lumix.estimator.domain.commercial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Phase 46 (spec §18 — "Do NOT automatically select a transformer unless the voltage/phase
 * mismatch requires one... Transformer losses must be represented in the engineering model"):
 * regression tests for [Transformer] and [CommercialIndustrialDesign]'s transformer-aware
 * computed properties.
 */
class TransformerTest {

    @Test
    fun `a fresh Transformer defaults to not required, contributing zero loss regardless of throughput`() {
        val transformer = Transformer()
        assertFalse(transformer.required)
        assertEquals(0.0, transformer.lossKw(throughputKw = 100.0), 0.0001)
    }

    @Test
    fun `a fresh CommercialIndustrialDesign has no transformer required`() {
        assertFalse(CommercialIndustrialDesign().transformer.required)
    }

    @Test
    fun `a required transformer at 97 percent efficiency loses 3 percent of throughput`() {
        val transformer = Transformer(required = true, efficiencyPercent = 97.0)
        // 10kW throughput -> 0.3kW loss (3% of 10kW).
        assertEquals(0.3, transformer.lossKw(throughputKw = 10.0), 0.0001)
    }

    @Test
    fun `a 100 percent efficient transformer loses nothing, a 0 percent one loses everything`() {
        assertEquals(0.0, Transformer(required = true, efficiencyPercent = 100.0).lossKw(10.0), 0.0001)
        assertEquals(10.0, Transformer(required = true, efficiencyPercent = 0.0).lossKw(10.0), 0.0001)
    }

    @Test
    fun `CommercialIndustrialDesign folds the transformer's loss into the load the system must supply`() {
        val load = LoadInstance(definitionId = "commercial_pump", label = "Pump", quantity = 1, ratedWatts = 10000.0, powerFactor = 1.0)
        val design = CommercialIndustrialDesign(
            loads = listOf(load),
            diversityFactor = DiversityFactor(preset = DiversityFactorPreset.PERCENT_100),
            transformer = Transformer(required = true, efficiencyPercent = 95.0)
        )
        // designLoadKw = 10.0kW (100% diversity). 95% efficient -> 5% loss = 0.5kW.
        assertEquals(10.0, design.designLoadKw, 0.0001)
        assertEquals(0.5, design.transformerLossKw, 0.0001)
        assertEquals(10.5, design.designLoadKwIncludingTransformerLoss, 0.0001)
    }

    @Test
    fun `an un-required transformer never changes the design's own load-supply figure`() {
        val load = LoadInstance(definitionId = "commercial_pump", label = "Pump", quantity = 1, ratedWatts = 10000.0, powerFactor = 1.0)
        val design = CommercialIndustrialDesign(
            loads = listOf(load),
            diversityFactor = DiversityFactor(preset = DiversityFactorPreset.PERCENT_100)
            // transformer left at its default (required = false).
        )
        assertEquals(0.0, design.transformerLossKw, 0.0001)
        assertEquals(design.designLoadKw, design.designLoadKwIncludingTransformerLoss, 0.0001)
    }
}
