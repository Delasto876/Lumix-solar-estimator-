package com.lumix.estimator.domain.commercial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 44 (spec §4-§10, §20 — facility-type-driven default load lists): regression tests for
 * [FacilityLoadLibrary]. The main risk this catalog-driven design carries is a typo'd id — a
 * [FacilityLoadLibrary] entry referencing a [CommercialIndustrialLoadCatalog] id that doesn't
 * actually exist would silently vanish from the wizard's "Typical for this facility" group instead
 * of failing loudly, so every list is checked against the real catalog here.
 */
class FacilityLoadLibraryTest {

    private val catalogIds = CommercialIndustrialLoadCatalog.commercialLoads.map { it.id }.toSet()

    @Test
    fun `every facility type resolves to a list, and every id in it is a real catalog entry`() {
        CommercialFacilityType.entries.forEach { type ->
            val ids = FacilityLoadLibrary.defaultLoadIdsFor(type)
            ids.forEach { id ->
                assertTrue("$type references unknown catalog id '$id'", id in catalogIds)
            }
        }
    }

    @Test
    fun `Custom has no facility-specific list`() {
        assertTrue(FacilityLoadLibrary.defaultLoadIdsFor(CommercialFacilityType.CUSTOM).isEmpty())
    }

    @Test
    fun `every non-Custom facility type has at least one typical load`() {
        CommercialFacilityType.entries.filter { !it.isCustom }.forEach { type ->
            assertTrue("$type has no default loads", FacilityLoadLibrary.defaultLoadIdsFor(type).isNotEmpty())
        }
    }

    @Test
    fun `no facility type lists the same id twice`() {
        CommercialFacilityType.entries.forEach { type ->
            val ids = FacilityLoadLibrary.defaultLoadIdsFor(type)
            assertEquals("$type has a duplicate id", ids.size, ids.toSet().size)
        }
    }

    @Test
    fun `supermarket's list matches the spec's own emphasis on refrigeration, POS, and security`() {
        val ids = FacilityLoadLibrary.defaultLoadIdsFor(CommercialFacilityType.SUPERMARKET)
        assertTrue(ids.containsAll(listOf("commercial_display_fridge", "commercial_freezer", "commercial_pos_system", "commercial_cctv_nvr")))
    }

    @Test
    fun `clinic's list uses verify-equipment-specification medical entries, not invented wattages`() {
        val ids = FacilityLoadLibrary.defaultLoadIdsFor(CommercialFacilityType.CLINIC)
        val medicalIds = listOf("commercial_medical_refrigerator", "commercial_examination_equipment", "commercial_patient_monitoring", "commercial_sterilization_equipment", "commercial_autoclave")
        assertTrue(ids.containsAll(medicalIds))
        medicalIds.forEach { id ->
            val def = CommercialIndustrialLoadCatalog.commercialLoads.first { it.id == id }
            assertEquals("$id should default to 0W, not an invented figure", 0.0, def.defaultRatedWatts, 0.0)
            assertTrue("$id's label should carry the verification disclaimer", def.label.contains("verify equipment specification", ignoreCase = true))
        }
    }
}
