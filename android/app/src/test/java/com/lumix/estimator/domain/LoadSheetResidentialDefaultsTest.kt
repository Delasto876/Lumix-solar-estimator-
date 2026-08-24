package com.lumix.estimator.domain

import com.lumix.estimator.domain.simulation.SimApplianceType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Load-Sheet round (user-supplied "Lumix Load Sheet Defaults" spreadsheet): regression tests for
 * the corrected Residential [ApplianceType]/[SimApplianceType] wattage and duty-factor figures —
 * both the real sourced values for a representative sample, and (most important) that
 * [ApplianceType.watts] and [SimApplianceType.watts] stayed in exact agreement for every corrected
 * pair, the same invariant [ApplianceTypeConsistencyTest] enforces globally.
 */
class LoadSheetResidentialDefaultsTest {

    @Test
    fun `fridge and freezer nameplate watts match the sheet, with a duty factor recomputed from its real average`() {
        assertEquals(300, SimApplianceType.REFRIGERATOR.watts)
        assertEquals(0.75, SimApplianceType.REFRIGERATOR.dutyFactor, 0.001)
        assertEquals(300, ApplianceType.FRIDGE.watts)

        assertEquals(400, SimApplianceType.CHEST_FREEZER.watts)
        assertEquals(0.69, SimApplianceType.CHEST_FREEZER.dutyFactor, 0.001)
        assertEquals(400, ApplianceType.FREEZER.watts)
    }

    @Test
    fun `iron and television nameplate watts are corrected upward to their real peak figures`() {
        assertEquals(1800, SimApplianceType.IRON.watts)
        assertEquals(1800, ApplianceType.IRON.watts)

        assertEquals(200, SimApplianceType.TELEVISION.watts)
        assertEquals(200, ApplianceType.TV.watts)
    }

    @Test
    fun `every corrected pair keeps ApplianceType and SimApplianceType watts in exact agreement`() {
        val correctedTypes = listOf(
            ApplianceType.FRIDGE, ApplianceType.FREEZER, ApplianceType.SECURITY_SYSTEM, ApplianceType.LED_BEDROOM,
            ApplianceType.TV, ApplianceType.FAN, ApplianceType.IRON, ApplianceType.BLENDER, ApplianceType.WASHER,
            ApplianceType.MICROWAVE, ApplianceType.ELECTRIC_KETTLE, ApplianceType.STOVE, ApplianceType.OVEN,
            ApplianceType.WATER_HEATER, ApplianceType.WATER_PUMP, ApplianceType.LAPTOP, ApplianceType.COMPUTER,
            ApplianceType.HAIR_DRYER, ApplianceType.TOASTER
        )
        correctedTypes.forEach { type ->
            val simType = SystemCalculator.simTypeFor(type)
            assertEquals("${type.name} vs ${simType.name} drifted", type.watts, simType.watts)
        }
    }

    @Test
    fun `AC BTU tiers were left untouched - already close to the sheet's own figures`() {
        assertEquals(900, SimApplianceType.AC_9000.watts)
        assertEquals(0.60, SimApplianceType.AC_9000.dutyFactor, 0.001)
    }

    @Test
    fun `an appliance type with no sheet match keeps its original figures`() {
        // Vacuum cleaner has no matching row in the load sheet.
        assertEquals(900, SimApplianceType.VACUUM_CLEANER.watts)
        assertEquals(1.0, SimApplianceType.VACUUM_CLEANER.dutyFactor, 0.001)
    }
}
