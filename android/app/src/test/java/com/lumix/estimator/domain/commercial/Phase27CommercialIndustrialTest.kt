package com.lumix.estimator.domain.commercial

import com.lumix.estimator.domain.EquipmentSpecs
import com.lumix.estimator.domain.InverterSpec
import com.lumix.estimator.domain.PriceList
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.SystemCalculator
import com.lumix.estimator.domain.SystemMode
import com.lumix.estimator.domain.SystemType
import com.lumix.estimator.domain.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 27 ("COMMERCIAL & INDUSTRIAL SYSTEM ARCHITECTURE") §20 regression tests — Residential
 * (unchanged), Commercial (single/multi inverter, diversity, multi-string/MPPT, battery-per-
 * inverter), Industrial (manual-only, three-phase, multi-inverter, multi-battery-bank), plus edge/
 * invalid cases. Real catalog fixtures are used wherever the production catalog already has the
 * needed data (e.g. SR-EOS05B/10B batteries, both `parallelSupported = true`); a local test-only
 * inverter fixture with `supportsParallel = true` is used for the parallel-inverter happy path,
 * since no catalog inverter has confirmed parallel-operation data yet (see
 * [ParallelInverterValidator]'s own doc) — the same "test fixture, not added to production data"
 * pattern this codebase already uses elsewhere (see e.g. [com.lumix.estimator.domain.simulation
 * .Phase24EngineeringValidationTest]'s "Test 20kW Inverter").
 */
class Phase27CommercialIndustrialTest {

    // ---- Residential regression: the dispatch guard must not touch this path at all ----

    @Test
    fun `a default RESIDENTIAL quote produces no commercial-industrial output`() {
        val result = SystemCalculator.calculate(
            QuoteInputs(quoteMode = com.lumix.estimator.domain.QuoteMode.MANUAL, manualBatt10k = 1),
            PriceList.DEFAULT
        )
        assertNull(result.commercialIndustrialSummary)
        assertTrue(result.commercialIndustrialWarnings.isEmpty())
    }

    @Test
    fun `systemCategory defaults to RESIDENTIAL so an older decoded quote keeps its old behavior`() {
        assertEquals(SystemType.RESIDENTIAL, QuoteInputs().systemCategory)
        assertNull(QuoteInputs().commercialIndustrialDesign)
    }

    // ---- Industrial is manual-only: no design entered yet must not crash or invent numbers ----

    @Test
    fun `INDUSTRIAL with no design entered returns a deterministic empty result, not a crash`() {
        val result = SystemCalculator.calculate(
            QuoteInputs(systemCategory = SystemType.INDUSTRIAL),
            PriceList.DEFAULT
        )
        assertNull(result.commercialIndustrialSummary)
        assertFalse(result.canFinalize)
        assertTrue(result.commercialIndustrialWarnings.isNotEmpty())
    }

    // ---- §4/§5: real/apparent power and the Connected/Maximum-Expected/Design Load breakdown ----

    @Test
    fun `kVA equals kW divided by power factor, and reactive power follows the right-triangle formula`() {
        val kva = ElectricalPower.apparentPowerKva(realPowerKw = 8.0, powerFactor = 0.8)
        assertEquals(10.0, kva, 0.0001)
        val kvar = ElectricalPower.reactivePowerKvar(realPowerKw = 8.0, apparentPowerKva = 10.0)
        assertEquals(6.0, kvar, 0.0001) // 3-4-5 triangle scaled: sqrt(10^2 - 8^2) = 6
    }

    @Test
    fun `connected load, maximum expected load, and design load are three distinct numbers`() {
        // 3 loads x 2kW connected each = 6kW connected. Duty cycle 0.5 and simultaneity 1.0 halves
        // it to 3kW maximum expected. A 90% diversity factor then reduces that to 2.7kW design load.
        val load = LoadInstance(
            definitionId = "commercial_pump", label = "Pump", quantity = 3, ratedWatts = 2000.0,
            powerFactor = 1.0, dutyCycleFraction = 0.5
        )
        val design = CommercialIndustrialDesign(
            loads = listOf(load),
            diversityFactor = DiversityFactor(preset = DiversityFactorPreset.PERCENT_90)
        )
        assertEquals(6.0, design.connectedLoadKw, 0.0001)
        assertEquals(3.0, design.maximumExpectedLoadKw, 0.0001)
        assertEquals(2.7, design.designLoadKw, 0.0001)
    }

    @Test
    fun `a low power factor load makes kVA materially exceed kW`() {
        val load = LoadInstance(
            definitionId = "industrial_welder", label = "Welder", quantity = 1, ratedWatts = 6000.0,
            powerFactor = 0.7
        )
        val design = CommercialIndustrialDesign(loads = listOf(load))
        assertEquals(6.0, design.connectedLoadKw, 0.0001)
        assertEquals(6.0 / 0.7, design.connectedApparentPowerKva, 0.0001)
        assertTrue(design.connectedApparentPowerKva > design.connectedLoadKw)
    }

    // ---- §8/§9/§10: parallel inverters + per-unit PV/MPPT validation ----

    private val testParallelCapableInverter = InverterSpec(
        brand = "TestBrand", model = "TEST-PARALLEL-12K", series = "TEST", region = "TEST",
        ratingLabel = "12kW three-phase", ratedOutputW = 12000, acVoltage = "208/120 V three-phase",
        frequencyHzRaw = "60", splitPhase = false,
        maxPvW = 18000, maxPvV = 550, mpptVoltageMinV = 120, mpptVoltageMaxV = 440, startupVoltageV = 140,
        mpptCount = 2, stringsPerMppt = 1, maxInputCurrentPerMpptA = 26.0, maxShortCircuitCurrentPerMpptA = 31.0,
        batteryVoltageRange = "40-60 V", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
        maxBatteryA = 200, maxChargePowerKw = 12.0, maxDischargePowerKw = 12.0, acOutputA = null, efficiencyPercent = null,
        surgePowerRatio = 2.0, surgeDurationSeconds = 5.0,
        type = "Hybrid", verificationStatus = VerificationStatus.VERIFIED,
        dataQualityNote = "Test fixture — not a real manufacturer spec.", engineeringNote = "", sourceUrl = "",
        supportsParallel = true, maxParallelUnits = 4
    )

    @Test
    fun `3 parallel 12kW inverters sum to 36kW total capacity, per the spec's own worked example`() {
        val design = ParallelInverterDesign(
            inverterModelId = testParallelCapableInverter.model, ratedKwPerUnit = 12.0,
            panelWattage = 595, inverterCount = 3
        )
        assertEquals(36.0, design.totalInverterCapacityKw, 0.0001)
    }

    @Test
    fun `a parallel-capable inverter within its confirmed max units validates each unit independently`() {
        // 8 panels/string x 44.6Vmp = 356.8V, comfortably inside the fixture's 140-440V MPPT
        // tracking range; cold-corrected Voc (8 x 52.6V x 1.045 = 439.7V) stays under the 550V
        // absolute ceiling too.
        val design = ParallelInverterDesign(
            inverterModelId = testParallelCapableInverter.model, ratedKwPerUnit = 12.0,
            panelWattage = 595, inverterCount = 2,
            unitPvDesigns = listOf(
                InverterUnitPvDesign(0, listOf(StringAssignment(0, 8), StringAssignment(1, 8))),
                InverterUnitPvDesign(1, listOf(StringAssignment(0, 8), StringAssignment(1, 8)))
            )
        )
        val result = ParallelInverterValidator.validate(design, inverterCatalog = listOf(testParallelCapableInverter))
        assertTrue("expected parallel capability confirmed: ${result.warnings}", result.parallelCapabilityOk)
        assertEquals(2, result.unitResults.size)
        assertTrue("expected both units valid: ${result.unitResults.flatMap { it.notes }}", result.valid)
    }

    @Test
    fun `requesting more parallel units than the model's confirmed maximum is flagged, not silently allowed`() {
        val design = ParallelInverterDesign(
            inverterModelId = testParallelCapableInverter.model, ratedKwPerUnit = 12.0,
            panelWattage = 595, inverterCount = 5 // model's own maxParallelUnits is 4
        )
        val result = ParallelInverterValidator.validate(design, inverterCatalog = listOf(testParallelCapableInverter))
        assertFalse(result.parallelCapabilityOk)
        assertTrue(result.warnings.any { it.contains("at most 4") })
    }

    @Test
    fun `GEN-LB-US 8K now has real, sourced parallel support confirmed (Phase 41), so a 2-unit request validates cleanly`() {
        // Phase 41 (inverter datasheet compendium): every inverter in the catalog now has an
        // explicit, sourced answer for parallel operation — GEN-LB-US 8K's own real datasheet
        // confirms "yes, up to 10 units," so a 2-unit request is no longer flagged unconfirmed.
        val realInverter = EquipmentSpecs.inverters.first { it.model == "GEN-LB-US 8K" }
        val design = ParallelInverterDesign(
            inverterModelId = realInverter.model, ratedKwPerUnit = 8.0, panelWattage = 595, inverterCount = 2
        )
        val result = ParallelInverterValidator.validate(design) // real production catalog
        assertTrue("expected confirmed parallel support: ${result.warnings}", result.parallelCapabilityOk)
    }

    @Test
    fun `an inverter the compendium could not confirm parallel support for still reports unconfirmed`() {
        // HES48100U200-H's own real datasheet (Phase 41) explicitly doesn't state a parallel
        // capacity — this app's standing rule keeps that "not confirmed," never "assume yes."
        val realInverter = EquipmentSpecs.inverters.first { it.model == "HES48100U200-H" }
        val design = ParallelInverterDesign(
            inverterModelId = realInverter.model, ratedKwPerUnit = 10.0, panelWattage = 595, inverterCount = 2
        )
        val result = ParallelInverterValidator.validate(design) // real production catalog
        assertFalse(result.parallelCapabilityOk)
        assertTrue(result.warnings.any { it.contains("no confirmed parallel-operation support") })
    }

    @Test
    fun `a string assigned to an MPPT index the inverter doesn't have is flagged`() {
        val design = ParallelInverterDesign(
            inverterModelId = testParallelCapableInverter.model, ratedKwPerUnit = 12.0,
            panelWattage = 595, inverterCount = 1,
            unitPvDesigns = listOf(InverterUnitPvDesign(0, listOf(StringAssignment(5, 10))))
        )
        val result = ParallelInverterValidator.validate(design, inverterCatalog = listOf(testParallelCapableInverter))
        val unit = result.unitResults.single()
        assertFalse(unit.valid)
        assertTrue(unit.notes.any { it.contains("outside this inverter's 2 available trackers") })
    }

    @Test
    fun `an oversized array on one inverter unit is rejected by the same electrical checks residential uses`() {
        // Way more panels than 18000W maxPvW can take (595W x 60 = 35.7kW).
        val design = ParallelInverterDesign(
            inverterModelId = testParallelCapableInverter.model, ratedKwPerUnit = 12.0,
            panelWattage = 595, inverterCount = 1,
            unitPvDesigns = listOf(InverterUnitPvDesign(0, listOf(StringAssignment(0, 60))))
        )
        val result = ParallelInverterValidator.validate(design, inverterCatalog = listOf(testParallelCapableInverter))
        val unit = result.unitResults.single()
        assertFalse(unit.valid)
    }

    // ---- §11/§12: battery-per-inverter allocation + DC current validation ----

    @Test
    fun `2 batteries per inverter across 3 inverters aggregates from the per-unit breakdown, not a separate total`() {
        // Structure matches the spec's own worked example (3 inverters x 2 batteries/inverter = 6
        // total) exactly; the exact kWh figure differs from the spec's illustrative round "15kWh"
        // because the real catalog's SR-EOS15B usable capacity is 15.42kWh, not a round 15 — see
        // BatterySpecSheet's own dataQualityNote for that model.
        val eos15b = EquipmentSpecs.batteries.first { it.model == "SR-EOS15B" }
        val design = BatteryPerInverterDesign(
            allocations = (0..2).map { unit -> BatteryPerInverterAllocation(unit, eos15b.model, 2) }
        )
        assertEquals(6, design.totalBatteryCount)
        assertEquals(eos15b.usableEnergyKwh * 6, design.totalBatteryCapacityKwh, 0.001)
    }

    @Test
    fun `a battery bank whose combined discharge power is below the inverter's rating is flagged`() {
        val eos05b = EquipmentSpecs.batteries.first { it.model.startsWith("SR-EOS05B") } // 5.12kW max discharge
        val luxInverter = EquipmentSpecs.inverters.first { it.model == "GEN-LB-US 8K" } // 8.0kW max discharge
        val design = BatteryPerInverterDesign(
            allocations = listOf(BatteryPerInverterAllocation(0, eos05b.model, 1)) // only 5.12kW available
        )
        val result = BatteryPerInverterValidator.validate(design, luxInverter.model)
        val unit = result.unitResults.single()
        assertFalse(unit.valid)
        assertTrue(unit.notes.any { it.contains("below this inverter's max discharge rating") })
    }

    @Test
    fun `enough parallel batteries to meet the inverter's discharge rating validates cleanly`() {
        val eos05b = EquipmentSpecs.batteries.first { it.model.startsWith("SR-EOS05B") } // 5.12kW/unit
        val luxInverter = EquipmentSpecs.inverters.first { it.model == "GEN-LB-US 8K" } // needs 8.0kW
        val design = BatteryPerInverterDesign(
            allocations = listOf(BatteryPerInverterAllocation(0, eos05b.model, 2)) // 10.24kW available
        )
        val result = BatteryPerInverterValidator.validate(design, luxInverter.model)
        val unit = result.unitResults.single()
        assertTrue("expected a valid battery bank: ${unit.notes}", unit.valid)
    }

    // ---- End-to-end: CommercialIndustrialCalculator wiring ----

    @Test
    fun `a COMMERCIAL quote with a manual design routes through the dispatch guard and returns a summary`() {
        val load = LoadInstance(
            definitionId = "commercial_pump", label = "Pump", quantity = 2, ratedWatts = 1500.0, powerFactor = 0.85
        )
        val design = CommercialIndustrialDesign(loads = listOf(load))
        val result = SystemCalculator.calculate(
            QuoteInputs(systemCategory = SystemType.COMMERCIAL, systemMode = SystemMode.HYBRID, commercialIndustrialDesign = design),
            PriceList.DEFAULT
        )
        val summary = result.commercialIndustrialSummary
        assertTrue(summary != null)
        assertEquals(design.designLoadKw, summary!!.designLoadKw, 0.0001)
        // Default diversity factor (100%) hasn't been explicitly confirmed -> must be flagged.
        assertTrue(result.commercialIndustrialWarnings.any { it.contains("diversity factor") })
    }

    @Test
    fun `a load whose phase doesn't match the site's electrical service phase is flagged`() {
        val load = LoadInstance(
            definitionId = "industrial_motor", label = "3-phase motor", ratedWatts = 5000.0,
            phase = LoadPhaseType.THREE_PHASE
        )
        val design = CommercialIndustrialDesign(
            electricalService = ElectricalService(phase = LoadPhaseType.SINGLE_PHASE),
            loads = listOf(load),
            diversityFactor = DiversityFactor(preset = DiversityFactorPreset.PERCENT_90) // avoid the default-diversity warning noise
        )
        val result = SystemCalculator.calculate(
            QuoteInputs(systemCategory = SystemType.INDUSTRIAL, commercialIndustrialDesign = design),
            PriceList.DEFAULT
        )
        assertTrue(result.commercialIndustrialWarnings.any { it.contains("is configured as THREE_PHASE") })
    }
}
