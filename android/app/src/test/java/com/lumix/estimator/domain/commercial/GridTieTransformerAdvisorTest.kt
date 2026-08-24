package com.lumix.estimator.domain.commercial

import com.lumix.estimator.domain.EquipmentSpecs
import com.lumix.estimator.domain.InverterArchitecture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 49 (Inverter Engine spec, "GRID-TIE LOGIC"): the spec's own two worked examples,
 * reproduced exactly, plus coverage of the "no data on file" and "not grid-tie" edge cases
 * [GridTieTransformerAdvisor] must not crash or invent an answer for.
 */
class GridTieTransformerAdvisorTest {

    private val gc30kLv = EquipmentSpecs.inverters.first { it.model == "S5-GC30K-LV" }
    private val gc50k = EquipmentSpecs.inverters.first { it.model == "S5-GC50K" }
    private val hybrid = EquipmentSpecs.inverters.first { it.architecture == InverterArchitecture.HYBRID }

    @Test
    fun `spec worked example 1 - S5-GC30K-LV 220V into a 400V three-phase site is STEP-UP`() {
        val site = ElectricalService(phase = LoadPhaseType.THREE_PHASE, nominalVoltage = 400.0)
        val rec = GridTieTransformerAdvisor.recommend(gc30kLv, inverterCount = 1, site = site)
        assertTrue(rec.applicable)
        assertEquals(true, rec.required)
        assertEquals(TransformerDirection.STEP_UP, rec.direction)
        assertEquals(220.0, rec.inverterVoltage!!, 0.001)
        assertEquals(400.0, rec.siteVoltage!!, 0.001)
    }

    @Test
    fun `spec worked example 2 - S5-GC50K 230-400V into a compatible 400V three-phase site is NO transformer`() {
        val site = ElectricalService(phase = LoadPhaseType.THREE_PHASE, nominalVoltage = 400.0)
        val rec = GridTieTransformerAdvisor.recommend(gc50k, inverterCount = 1, site = site)
        assertTrue(rec.applicable)
        assertEquals(false, rec.required)
        assertNull(rec.direction)
        assertEquals(400.0, rec.inverterVoltage!!, 0.001)
        assertNull(rec.recommendedKvaRating)
    }

    @Test
    fun `S5-GC50K also matches its other real variant, 380V`() {
        val site = ElectricalService(phase = LoadPhaseType.THREE_PHASE, nominalVoltage = 380.0)
        val rec = GridTieTransformerAdvisor.recommend(gc50k, inverterCount = 1, site = site)
        assertEquals(false, rec.required)
        assertEquals(380.0, rec.inverterVoltage!!, 0.001)
    }

    @Test
    fun `transformer sizing uses maxApparentPowerKva with a 125 percent continuous-duty margin, not PV wattage`() {
        val site = ElectricalService(phase = LoadPhaseType.THREE_PHASE, nominalVoltage = 400.0)
        val rec = GridTieTransformerAdvisor.recommend(gc30kLv, inverterCount = 1, site = site)
        // 33kVA (gc30kLv's real maxApparentPowerKva) x 1.25 = 41.25kVA - not derived from maxPvW (45000W) at all.
        assertEquals(41.25, rec.recommendedKvaRating!!, 0.001)
    }

    @Test
    fun `parallel grid-tie sizing multiplies by inverter count`() {
        val site = ElectricalService(phase = LoadPhaseType.THREE_PHASE, nominalVoltage = 400.0)
        val rec = GridTieTransformerAdvisor.recommend(gc30kLv, inverterCount = 3, site = site)
        assertEquals(33.0 * 3 * 1.25, rec.recommendedKvaRating!!, 0.001)
    }

    @Test
    fun `a site voltage matching within a small real-world tolerance still counts as matched`() {
        // 401V is well within ANSI-style utility voltage tolerance of a 400V nominal service.
        val site = ElectricalService(phase = LoadPhaseType.THREE_PHASE, nominalVoltage = 401.0)
        val rec = GridTieTransformerAdvisor.recommend(gc50k, inverterCount = 1, site = site)
        assertEquals(false, rec.required)
    }

    @Test
    fun `a non-three-phase site is flagged even when the nominal voltage number happens to match`() {
        val site = ElectricalService(phase = LoadPhaseType.SINGLE_PHASE, nominalVoltage = 220.0)
        val rec = GridTieTransformerAdvisor.recommend(gc30kLv, inverterCount = 1, site = site)
        assertEquals(true, rec.required)
        assertTrue(rec.reason.contains("not three-phase"))
    }

    @Test
    fun `is not applicable to a hybrid inverter`() {
        val site = ElectricalService(phase = LoadPhaseType.THREE_PHASE, nominalVoltage = 400.0)
        val rec = GridTieTransformerAdvisor.recommend(hybrid, inverterCount = 1, site = site)
        assertFalse(rec.applicable)
        assertNull(rec.required)
    }

    @Test
    fun `returns unknown rather than a guessed answer when the site voltage is unset`() {
        val site = ElectricalService(phase = LoadPhaseType.THREE_PHASE, nominalVoltage = 0.0)
        val rec = GridTieTransformerAdvisor.recommend(gc30kLv, inverterCount = 1, site = site)
        assertTrue(rec.applicable)
        assertNull(rec.required)
        assertNull(rec.direction)
    }

    @Test
    fun `a mismatched voltage below the inverter's own voltage is STEP-DOWN, not STEP-UP`() {
        val site = ElectricalService(phase = LoadPhaseType.THREE_PHASE, nominalVoltage = 120.0)
        val rec = GridTieTransformerAdvisor.recommend(gc30kLv, inverterCount = 1, site = site)
        assertEquals(true, rec.required)
        assertEquals(TransformerDirection.STEP_DOWN, rec.direction)
    }
}
