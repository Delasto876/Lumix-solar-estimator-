package com.lumix.estimator.domain.commercial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 43 (spec §15-§17/§19 — the 50Hz default, the 7-preset Electrical Service picker, and the
 * real √3 three-phase kW/kVA/current math): regression tests for [ElectricalServicePreset],
 * [ElectricalService.totalCurrentAmps], [CommercialIndustrialDesign.totalServiceCurrentAmps], and
 * [CommercialIndustrialDesign.electricalServiceGuidance].
 */
class ElectricalServiceTest {

    @Test
    fun `a fresh ElectricalService defaults to 50Hz and an unconfirmed CUSTOM preset`() {
        val service = ElectricalService()
        assertEquals(50.0, service.frequencyHz, 0.0)
        assertEquals(ElectricalServicePreset.CUSTOM, service.preset)
    }

    @Test
    fun `there are exactly the spec's 7 electrical service options, ending in Custom`() {
        assertEquals(7, ElectricalServicePreset.entries.size)
        assertEquals(ElectricalServicePreset.CUSTOM, ElectricalServicePreset.entries.last())
    }

    @Test
    fun `three-phase current uses the spec's own sqrt-3 formula, not the single-phase one`() {
        // 120/208V three-phase preset, 10 kVA design load.
        // I = kVA x 1000 / (sqrt(3) x 208) = 10000 / 360.27 = 27.76A.
        val service = ElectricalService(phase = LoadPhaseType.THREE_PHASE, nominalVoltage = 208.0)
        val amps = service.totalCurrentAmps(designApparentPowerKva = 10.0)
        assertEquals(27.76, amps!!, 0.05)
    }

    @Test
    fun `single-phase current is a plain division, no sqrt-3 factor`() {
        // 220/240V single-phase, 5 kVA -> I = 5000 / 220 = 22.73A.
        val service = ElectricalService(phase = LoadPhaseType.SINGLE_PHASE, nominalVoltage = 220.0)
        val amps = service.totalCurrentAmps(designApparentPowerKva = 5.0)
        assertEquals(22.73, amps!!, 0.02)
    }

    @Test
    fun `split-phase uses the same single-voltage formula as single-phase`() {
        // 120/240V split-phase treated at its own 240V for this aggregate calc -> I = 5000/240 = 20.83A.
        val service = ElectricalService(phase = LoadPhaseType.SPLIT_PHASE, nominalVoltage = 240.0)
        val amps = service.totalCurrentAmps(designApparentPowerKva = 5.0)
        assertEquals(20.83, amps!!, 0.02)
    }

    @Test
    fun `current is null when nominal voltage isn't set to a usable figure`() {
        val service = ElectricalService(nominalVoltage = 0.0)
        assertNull(service.totalCurrentAmps(designApparentPowerKva = 5.0))
    }

    @Test
    fun `a named preset seeds phase, nominal voltage, and line-to-neutral voltage`() {
        assertEquals(LoadPhaseType.THREE_PHASE, ElectricalServicePreset.V220_380_THREE_PHASE.presetPhase)
        assertEquals(380.0, ElectricalServicePreset.V220_380_THREE_PHASE.presetNominalVoltage)
        assertEquals(220.0, ElectricalServicePreset.V220_380_THREE_PHASE.presetLineToNeutralVoltage)

        assertEquals(LoadPhaseType.SPLIT_PHASE, ElectricalServicePreset.V120_240_SPLIT_PHASE.presetPhase)
        assertEquals(240.0, ElectricalServicePreset.V120_240_SPLIT_PHASE.presetNominalVoltage)
        assertEquals(120.0, ElectricalServicePreset.V120_240_SPLIT_PHASE.presetLineToNeutralVoltage)

        // CUSTOM carries no figures of its own — it's the "installer enters their own" escape hatch.
        assertNull(ElectricalServicePreset.CUSTOM.presetPhase)
        assertNull(ElectricalServicePreset.CUSTOM.presetNominalVoltage)
        assertNull(ElectricalServicePreset.CUSTOM.presetLineToNeutralVoltage)
    }

    @Test
    fun `CommercialIndustrialDesign resolves the same current from its own real design load`() {
        // 3 x 2kW at PF 1.0, diversity forced to 100% so design load = connected load = 6kW / 6kVA.
        // 208V three-phase -> I = 6000 / (sqrt(3) x 208) = 16.65A.
        val load = LoadInstance(definitionId = "industrial_motor", label = "Motor", quantity = 3, ratedWatts = 2000.0, powerFactor = 1.0)
        val design = CommercialIndustrialDesign(
            loads = listOf(load),
            diversityFactor = DiversityFactor(preset = DiversityFactorPreset.PERCENT_100),
            electricalService = ElectricalService(phase = LoadPhaseType.THREE_PHASE, nominalVoltage = 208.0)
        )
        assertEquals(16.65, design.totalServiceCurrentAmps!!, 0.05)
    }

    @Test
    fun `guidance flags installer verification while the preset is still Custom`() {
        val design = CommercialIndustrialDesign(loads = listOf(
            LoadInstance(definitionId = "commercial_pump", label = "Pump", quantity = 1, ratedWatts = 1500.0)
        ))
        assertEquals(ElectricalServicePreset.CUSTOM, design.electricalService.preset)
        assertTrue(design.electricalServiceGuidance.contains("requires installer verification"))
    }

    @Test
    fun `guidance drops the verification line once a named preset is confirmed`() {
        val design = CommercialIndustrialDesign(
            loads = listOf(LoadInstance(definitionId = "commercial_pump", label = "Pump", quantity = 1, ratedWatts = 1500.0)),
            electricalService = ElectricalService(preset = ElectricalServicePreset.V120_208_THREE_PHASE, phase = LoadPhaseType.THREE_PHASE, nominalVoltage = 208.0)
        )
        assertTrue(!design.electricalServiceGuidance.contains("requires installer verification"))
        assertTrue(design.electricalServiceGuidance.contains("Design load"))
    }

    @Test
    fun `guidance surfaces the largest single load and starting-demand signals`() {
        val design = CommercialIndustrialDesign(loads = listOf(
            LoadInstance(definitionId = "industrial_compressor", label = "Compressor", quantity = 1, ratedWatts = 11000.0, startingSurgeWatts = 44000.0),
            LoadInstance(definitionId = "commercial_lighting", label = "Lighting", quantity = 10, ratedWatts = 40.0)
        ))
        assertTrue(design.electricalServiceGuidance.contains("largest single load 11.0 kW"))
        assertTrue(design.electricalServiceGuidance.contains("motor/starting demand"))
    }
}
