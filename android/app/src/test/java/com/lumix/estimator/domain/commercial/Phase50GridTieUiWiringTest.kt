package com.lumix.estimator.domain.commercial

import com.lumix.estimator.domain.Catalog
import com.lumix.estimator.domain.EquipmentSpecs
import com.lumix.estimator.domain.InverterArchitecture
import com.lumix.estimator.domain.PriceList
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.SystemCalculator
import com.lumix.estimator.domain.SystemMode
import com.lumix.estimator.domain.SystemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 50 (Inverter Engine spec — "GRID-TIE = PV + GRID ONLY. NO BATTERY. Hide/disable battery
 * sizing, battery count, backup-hours and BMS fields... Do NOT ask for battery per inverter when
 * GRID-TIE is selected"): [StepCommercialIndustrialDesign]'s BatteryPerInverterSection now hides
 * itself and clears `batteryPerInverterDesign` whenever [SystemMode.GRIDTIE] is selected (a
 * Compose-only change this domain-level test suite can't exercise directly) — what this test
 * confirms is the domain-level consequence that gating relies on: [CommercialIndustrialCalculator]
 * already treats a null `batteryPerInverterDesign` as "no battery" end to end, so the UI-level
 * clear is sufficient on its own with no calculator change required. Also confirms
 * [Catalog.poolFor]/[ParallelInverterSection]'s new mode-scoped inverter dropdown resolves the two
 * real Solis grid-tie models added in Phase 48 correctly.
 */
class Phase50GridTieUiWiringTest {

    @Test
    fun `a grid-tie commercial design with no battery design produces no battery output`() {
        val gc30kLv = EquipmentSpecs.inverters.first { it.model == "S5-GC30K-LV" }
        val parallelDesign = ParallelInverterDesign(
            inverterModelId = gc30kLv.model,
            ratedKwPerUnit = gc30kLv.ratedOutputW / 1000.0,
            panelWattage = 615,
            inverterCount = 1,
            unitPvDesigns = listOf(InverterUnitPvDesign(unitIndex = 0, strings = (0..3).map { StringAssignment(it, 20) }))
        )
        val design = CommercialIndustrialDesign(
            loads = listOf(
                LoadInstance(definitionId = "commercial_pump", label = "Pump", quantity = 3, ratedWatts = 5000.0, powerFactor = 0.9, operatingHoursPerDay = 10.0)
            ),
            parallelInverterDesign = parallelDesign,
            batteryPerInverterDesign = null
        )
        val inputs = QuoteInputs(systemCategory = SystemType.COMMERCIAL, systemMode = SystemMode.GRIDTIE, commercialIndustrialDesign = design)

        val result = SystemCalculator.calculate(inputs, PriceList.DEFAULT)

        assertEquals(SystemMode.GRIDTIE, result.effectiveSystemMode)
        assertNull("no battery design means no battery name, exactly what a grid-tie design should show", result.batteryName)
        assertEquals(0.0, result.totalBatteryKwh, 0.001)
        assertEquals(30.0, result.inverterKw, 0.001)
    }

    @Test
    fun `Catalog poolFor GRIDTIE contains only grid-tie-architecture real entries, plus the legacy placeholder`() {
        val pool = Catalog.poolFor(SystemMode.GRIDTIE)
        assertEquals(setOf("grid15k", "solisGc30kLv", "solisGc50k"), pool.map { it.id }.toSet())
        pool.filter { it.id != "grid15k" }.forEach { option ->
            val spec = EquipmentSpecs.inverterSpecFor(option.kw, option.name)
            assertEquals("${option.id} should resolve to a real GRID_TIE spec", InverterArchitecture.GRID_TIE, spec?.architecture)
        }
    }

    @Test
    fun `Catalog poolFor HYBRID never includes a grid-tie model`() {
        val pool = Catalog.poolFor(SystemMode.HYBRID)
        assertTrue(pool.none { it.id == "solisGc30kLv" || it.id == "solisGc50k" })
    }
}
