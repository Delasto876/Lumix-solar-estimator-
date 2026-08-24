package com.lumix.estimator.domain.commercial

import com.lumix.estimator.ui.components.newInstanceFrom
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Load-Sheet round (user-supplied "Lumix Load Sheet Defaults" spreadsheet): regression tests
 * locking in the corrected [LoadDefinition.defaultRatedWatts]/[LoadDefinition.defaultPowerFactor]/
 * [LoadDefinition.defaultHoursPerDay] figures for a representative sample of entries, plus the new
 * [LoadDefinition.defaultHoursPerDay] -> [LoadInstance.operatingHoursPerDay] wiring in
 * [newInstanceFrom] itself.
 */
class LoadSheetDefaultsTest {

    private fun def(id: String) = CommercialIndustrialLoadCatalog.definitionById(id)!!

    @Test
    fun `newInstanceFrom seeds operatingHoursPerDay from a sourced default, still zero when none exists`() {
        val withDefault = newInstanceFrom(def("commercial_server"))
        assertEquals(24.0, withDefault.operatingHoursPerDay, 0.0)

        // commercial_kitchen_equipment predates this round and has no sheet-sourced hours figure.
        val withoutDefault = newInstanceFrom(def("commercial_kitchen_equipment"))
        assertEquals(0.0, withoutDefault.operatingHoursPerDay, 0.0)
    }

    @Test
    fun `commercial refrigeration trio matches the sheet's own three-way split`() {
        assertEquals(800.0, def("commercial_display_fridge").defaultRatedWatts, 0.0)
        assertEquals(1200.0, def("commercial_freezer").defaultRatedWatts, 0.0)
        assertEquals(2500.0, def("commercial_refrigeration").defaultRatedWatts, 0.0)
    }

    @Test
    fun `commercial IT loads match the sheet's real operating watts and power factor`() {
        val server = def("commercial_server")
        assertEquals(1500.0, server.defaultRatedWatts, 0.0)
        assertEquals(0.95, server.defaultPowerFactor, 0.0)
        assertEquals(24.0, server.defaultHoursPerDay)

        val computer = def("commercial_computer")
        assertEquals(275.0, computer.defaultRatedWatts, 0.0)
        assertEquals(0.9, computer.defaultPowerFactor, 0.0)
    }

    @Test
    fun `industrial motor-driven loads match the sheet's real operating watts`() {
        assertEquals(20000.0, def("industrial_compressor").defaultRatedWatts, 0.0)
        assertEquals(0.8, def("industrial_compressor").defaultPowerFactor, 0.0)
        assertEquals(40000.0, def("industrial_large_hvac").defaultRatedWatts, 0.0)
        assertEquals(500.0, def("industrial_plc_controls").defaultRatedWatts, 0.0)
        assertEquals(24.0, def("industrial_plc_controls").defaultHoursPerDay)
    }

    @Test
    fun `signage is now modeled as intermittent (evening-only), not continuous`() {
        assertEquals(LoadOperationType.INTERMITTENT, def("commercial_signage").defaultOperationType)
        assertEquals(6.0, def("commercial_signage").defaultHoursPerDay)
    }

    @Test
    fun `an untouched pre-existing entry keeps its original figures unchanged`() {
        // commercial_elevator and industrial_electronics have no matching sheet row this round.
        assertEquals(5000.0, def("commercial_elevator").defaultRatedWatts, 0.0)
        assertEquals(800.0, def("industrial_electronics").defaultRatedWatts, 0.0)
    }
}
