package com.lumix.estimator.domain.commercial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 42 (spec §2/§3 — the 26 commercial + 25 industrial facility-type lists, each ending in a
 * "Custom ... Facility" escape hatch): regression tests for [CommercialFacilityType]/
 * [IndustrialFacilityType]/[FacilitySelection] — the exact counts the spec's own numbered lists
 * give, and the "not yet chosen" vs "custom name" resolution [FacilitySelection.displayLabel] must
 * get right for the wizard's facility picker (see [com.lumix.estimator.ui.wizard.steps
 * .StepQuoteMode]'s FacilityTypeSection) to show sensible text either way.
 */
class FacilityTypeTest {

    @Test
    fun `commercial facility list has exactly the spec's 26 entries, ending in Custom`() {
        assertEquals(26, CommercialFacilityType.entries.size)
        assertEquals(CommercialFacilityType.CUSTOM, CommercialFacilityType.entries.last())
        assertEquals("Custom Commercial Facility", CommercialFacilityType.CUSTOM.label)
        assertTrue(CommercialFacilityType.CUSTOM.isCustom)
        assertFalse(CommercialFacilityType.SUPERMARKET.isCustom)
    }

    @Test
    fun `industrial facility list has exactly the spec's 25 entries, ending in Custom`() {
        assertEquals(25, IndustrialFacilityType.entries.size)
        assertEquals(IndustrialFacilityType.CUSTOM, IndustrialFacilityType.entries.last())
        assertEquals("Custom Industrial Facility", IndustrialFacilityType.CUSTOM.label)
        assertTrue(IndustrialFacilityType.CUSTOM.isCustom)
        assertFalse(IndustrialFacilityType.FOOD_PROCESSING_FACILITY.isCustom)
    }

    @Test
    fun `no facility label is blank or duplicated within its own list`() {
        val commercialLabels = CommercialFacilityType.entries.map { it.label }
        assertTrue(commercialLabels.all { it.isNotBlank() })
        assertEquals(commercialLabels.size, commercialLabels.toSet().size)

        val industrialLabels = IndustrialFacilityType.entries.map { it.label }
        assertTrue(industrialLabels.all { it.isNotBlank() })
        assertEquals(industrialLabels.size, industrialLabels.toSet().size)
    }

    @Test
    fun `an unchosen FacilitySelection is not chosen and displays blank`() {
        val selection = FacilitySelection()
        assertFalse(selection.isChosen)
        assertEquals("", selection.displayLabel)
    }

    @Test
    fun `a preset commercial choice displays its own label`() {
        val selection = FacilitySelection(commercialType = CommercialFacilityType.SCHOOL)
        assertTrue(selection.isChosen)
        assertEquals("School", selection.displayLabel)
    }

    @Test
    fun `a custom commercial choice with a name displays that name, not the generic Custom label`() {
        val selection = FacilitySelection(commercialType = CommercialFacilityType.CUSTOM, customFacilityName = "Auto Parts Warehouse")
        assertTrue(selection.isChosen)
        assertEquals("Auto Parts Warehouse", selection.displayLabel)
    }

    @Test
    fun `a custom industrial choice with a blank name falls back to a generic label rather than blank`() {
        val selection = FacilitySelection(industrialType = IndustrialFacilityType.CUSTOM, customFacilityName = "")
        assertTrue(selection.isChosen)
        assertEquals("Custom Facility", selection.displayLabel)
    }

    @Test
    fun `CommercialIndustrialDesign defaults to an unchosen facility, never a specific preset`() {
        val design = CommercialIndustrialDesign()
        assertFalse(design.facility.isChosen)
    }
}
