package com.lumix.estimator.domain.commercial

import com.lumix.estimator.domain.EquipmentSpecs
import com.lumix.estimator.domain.PriceList
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.SystemCalculator
import com.lumix.estimator.domain.SystemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 29 ("when I do and select simulate it shows 0 panels and battery fix that"): a full,
 * end-to-end regression test for the new parallel-inverter + battery-per-inverter wizard UI
 * (StepCommercialIndustrialDesign's ParallelInverterSection/BatteryPerInverterSection), using the
 * installer's own worked example verbatim: 3 x 12kW inverters (36kW total), each with 3 MPPTs, 10 x
 * 720W panels per MPPT string, and 3 x 16kWh batteries per inverter. Before this round,
 * QuoteInputs.commercialIndustrialDesign had no way to actually populate parallelInverterDesign/
 * batteryPerInverterDesign, so CommercialIndustrialCalculator's own (already-correct) panelCount/
 * inverterName/batteryName/totalBatteryKwh derivation always saw null and produced 0/empty — this
 * confirms that path end to end now that the UI exists to fill it in.
 */
class Phase29ParallelInverterUiWiringTest {

    @Test
    fun `3 x 12kW inverters with 3 MPPT x 10 x 720W panels and 3 x 16kWh batteries per unit produces real, nonzero results`() {
        val battery = EquipmentSpecs.batteries.first { it.model == "SR-EOS15B" } // "16kWh class"

        val parallelDesign = ParallelInverterDesign(
            inverterModelId = "Test 12kW Commercial Inverter",
            ratedKwPerUnit = 12.0,
            panelWattage = 720,
            inverterCount = 3,
            unitPvDesigns = (0..2).map { unitIndex ->
                InverterUnitPvDesign(
                    unitIndex = unitIndex,
                    strings = (0..2).map { mpptIndex -> StringAssignment(mpptIndex, 10) }
                )
            }
        )
        val batteryDesign = BatteryPerInverterDesign(
            allocations = (0..2).map { unitIndex -> BatteryPerInverterAllocation(unitIndex, battery.model, 3) }
        )
        val load = LoadInstance(
            definitionId = "commercial_pump", label = "Pump", quantity = 5, ratedWatts = 2000.0,
            powerFactor = 0.85, operatingHoursPerDay = 8.0
        )
        val design = CommercialIndustrialDesign(
            loads = listOf(load),
            diversityFactor = DiversityFactor(preset = DiversityFactorPreset.PERCENT_80),
            parallelInverterDesign = parallelDesign,
            batteryPerInverterDesign = batteryDesign
        )
        val inputs = QuoteInputs(systemCategory = SystemType.COMMERCIAL, commercialIndustrialDesign = design)

        val result = SystemCalculator.calculate(inputs, PriceList.DEFAULT)

        // Matches the installer's own worked example exactly.
        assertEquals(90, result.panelCount) // 3 inverters x 3 MPPT x 10 panels
        assertEquals(720, result.panelWatts)
        assertEquals(36.0, result.inverterKw, 0.001) // 3 x 12kW
        assertEquals(90.0 * 720.0 / 1000.0, result.pvKw, 0.001) // 64.8kW PV array

        assertNotNull("battery name must not be null once a battery-per-inverter design is set", result.batteryName)
        assertEquals(9, batteryDesign.totalBatteryCount) // 3 inverters x 3 batteries
        assertEquals(battery.usableEnergyKwh * 9, result.totalBatteryKwh, 0.001)
        assertTrue("totalBatteryKwh must be real and nonzero, not the old 0.0 default", result.totalBatteryKwh > 0.0)

        // The parallel-capability warning is still expected (no catalog inverter has confirmed
        // parallel-operation data yet) — a non-blocking, honest warning, not a crash or a zeroed result.
        assertTrue(result.commercialIndustrialWarnings.isNotEmpty())
    }
}
