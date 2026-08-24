package com.lumix.estimator.domain.commercial

import com.lumix.estimator.domain.EquipmentSpecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 51 (Inverter Engine spec, "PARALLEL GRID-TIE... Show: Number of inverters, kW per
 * inverter, Total AC kW, Panels per inverter, Strings per inverter, Strings per MPPT, Total PV kW,
 * Total AC current, Transformer requirement, Transformer voltage, Transformer kVA, AC protection,
 * DC protection"): confirms [GridTieSystemSummary.summarize] produces every one of these fields
 * correctly from a real, worked 3-parallel-inverter S5-GC30K-LV design, and degrades honestly
 * (never a guess) when the inverter model can't be resolved to a real catalog spec.
 */
class GridTieSystemSummaryTest {

    private val gc30kLv = EquipmentSpecs.inverters.first { it.model == "S5-GC30K-LV" }

    @Test
    fun `3 parallel S5-GC30K-LV units into a mismatched 400V site produce every spec-required summary field`() {
        val design = ParallelInverterDesign(
            inverterModelId = gc30kLv.model,
            ratedKwPerUnit = 30.0,
            panelWattage = 615,
            inverterCount = 3,
            unitPvDesigns = (0..2).map { unitIndex ->
                InverterUnitPvDesign(unitIndex = unitIndex, strings = (0..3).map { mpptIndex -> StringAssignment(mpptIndex, 18) })
            }
        )
        val site = ElectricalService(phase = LoadPhaseType.THREE_PHASE, nominalVoltage = 400.0)

        val summary = GridTieSystemSummary.summarize(design, gc30kLv, site)

        assertEquals(3, summary.inverterCount)
        assertEquals(30.0, summary.kwPerInverter, 0.001)
        assertEquals(90.0, summary.totalAcKw, 0.001) // 3 x 30kW
        assertEquals(72, summary.panelsPerInverter) // 4 strings x 18 panels
        assertEquals(4, summary.stringsPerInverter) // one string per MPPT, 4 MPPTs used
        assertEquals(1, summary.stringsPerMppt)
        assertEquals(72.0 * 3 * 615.0 / 1000.0, summary.totalPvKw, 0.001)

        // acOutputA on file for the S5-GC30K-LV is 79 (rounded rated current) x 3 units.
        assertEquals(79.0 * 3, summary.totalAcCurrentA!!, 0.001)

        assertNotNull(summary.transformer)
        assertEquals(true, summary.transformer!!.required) // 220V inverter into a 400V site - mismatch
        assertEquals(TransformerDirection.STEP_UP, summary.transformer!!.direction)
        assertEquals(33.0 * 3 * 1.25, summary.transformer!!.recommendedKvaRating!!, 0.001)

        assertTrue(summary.acProtection.contains("79 A")) // this model's real rated AC output current
        assertTrue(summary.dcProtection.contains("40.0"))  // real maxShortCircuitCurrentPerMpptA on file
    }

    @Test
    fun `a matched-voltage site produces no transformer requirement in the summary`() {
        val gc50k = EquipmentSpecs.inverters.first { it.model == "S5-GC50K" }
        val design = ParallelInverterDesign(
            inverterModelId = gc50k.model, ratedKwPerUnit = 50.0, panelWattage = 615, inverterCount = 1,
            unitPvDesigns = listOf(InverterUnitPvDesign(unitIndex = 0, strings = (0..4).map { StringAssignment(it, 20) }))
        )
        val site = ElectricalService(phase = LoadPhaseType.THREE_PHASE, nominalVoltage = 400.0)

        val summary = GridTieSystemSummary.summarize(design, gc50k, site)

        assertEquals(false, summary.transformer!!.required)
    }

    @Test
    fun `an unresolved custom inverter model degrades to unconfirmed rather than a guess`() {
        val design = ParallelInverterDesign(
            inverterModelId = "Some Unlisted Grid-Tie Unit", ratedKwPerUnit = 25.0, panelWattage = 615, inverterCount = 2,
            unitPvDesigns = listOf(InverterUnitPvDesign(unitIndex = 0, strings = listOf(StringAssignment(0, 10))))
        )
        val site = ElectricalService(phase = LoadPhaseType.THREE_PHASE, nominalVoltage = 400.0)

        val summary = GridTieSystemSummary.summarize(design, inverterSpec = null, site = site)

        // Pure count/kW/panel figures still compute from the design alone.
        assertEquals(2, summary.inverterCount)
        assertEquals(50.0, summary.totalAcKw, 0.001)
        // Datasheet-derived fields honestly degrade instead of guessing.
        assertNull(summary.totalAcCurrentA)
        assertNull(summary.transformer)
        assertTrue(summary.acProtection.contains("no real rated AC output current on file"))
        assertTrue(summary.dcProtection.contains("no real max short-circuit current"))
    }
}
