package com.lumix.estimator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A77 (spec Phase 14 — "improve equipment database"): regression coverage for
 * [EquipmentSpecs.InverterSpec.surgePowerRatio]/[EquipmentSpecs.InverterSpec.surgeDurationSeconds]
 * — real, sourced numbers only for entries whose datasheet excerpt on file actually stated one,
 * `null` everywhere else rather than assumed/copied from a same-brand sibling.
 *
 * Phase 41 (inverter datasheet compendium, 2026-08-24): a real manufacturer-sourced datasheet was
 * located for every inverter in the catalog, expanding surge coverage from the original 2 confirmed
 * entries to 8, and adding [EquipmentSpecs.InverterSpec.supportsParallel]/[EquipmentSpecs
 * .InverterSpec.maxParallelUnits] confirmation for 10 of the 13 — see EquipmentSpecs.kt's own
 * per-entry Phase 41 comments for what each source did and didn't confirm.
 */
class EquipmentSpecsTest {

    @Test
    fun `exactly the models with a real sourced surge figure on file have one set`() {
        val withSurge = EquipmentSpecs.inverters.filter { it.surgePowerRatio != null }.map { it.model }.toSet()
        val expected = setOf(
            "GEN-LB-US 8K", "LXP-LB-US 12K/13K", "SNA-US 12K", "SUN-6K-SG02LP2-US",
            "SUN-8K-SG01LP1-US", "ASF4860U80-H", "SPH 8000TL-HU-US", "SPH 10000TL-HU-US"
        )
        assertEquals(expected, withSurge)
        assertTrue(EquipmentSpecs.inverters.filter { it.surgePowerRatio != null }.all { it.surgeDurationSeconds != null })
    }

    @Test
    fun `every other inverter has no invented surge figure`() {
        // Inverter Engine round: the two new GRID_TIE entries join this set too - the source
        // spreadsheet gave no surge figure for either, so neither invents one.
        val expectedWithout = setOf(
            "GEN-LB-US 6K", "GEN-LB-US 10K", "ASF4880S180-H", "HES48100U200-H", "HESP 12K-US",
            "S5-GC30K-LV", "S5-GC50K"
        )
        val withoutSurge = EquipmentSpecs.inverters.filter { it.surgePowerRatio == null }.map { it.model }.toSet()
        assertEquals(expectedWithout, withoutSurge)
        assertTrue(EquipmentSpecs.inverters.filter { it.surgePowerRatio == null }.all { it.surgeDurationSeconds == null })
    }

    @Test
    fun `the two entries this app's own price-list renaming carried surge data across still have it`() {
        val luxPower13k = EquipmentSpecs.inverters.first { it.model == "LXP-LB-US 12K/13K" }
        assertEquals(2.0, luxPower13k.surgePowerRatio!!, 0.001)
        assertEquals(0.5, luxPower13k.surgeDurationSeconds!!, 0.001)

        val srne6k = EquipmentSpecs.inverters.first { it.model == "ASF4860U80-H" }
        assertEquals(2.0, srne6k.surgePowerRatio!!, 0.001)
        assertEquals(10.0, srne6k.surgeDurationSeconds!!, 0.001)
    }

    @Test
    fun `every battery has real communication and parallel-capability data on file`() {
        assertTrue(EquipmentSpecs.batteries.isNotEmpty())
        EquipmentSpecs.batteries.forEach { battery ->
            assertTrue("${battery.model} should have a non-blank bmsCommunication note", battery.bmsCommunication.isNotBlank())
        }
    }

    /**
     * Phase 41 ("some inverters show unverified or do not know if can be paralleled, what
     * information do you need to update those"): every inverter in the catalog now has an explicit,
     * sourced answer for parallel operation — confirmed true (with a real max-unit count) or
     * confirmed/left false, never a silent guess. This test locks in exactly which models are
     * confirmed parallel-capable, so a future catalog change can't silently add or drop one without
     * a test failure calling it out.
     */
    @Test
    fun `exactly the compendium-confirmed models are marked parallel-capable, each with a real max-unit count`() {
        val parallelCapable = EquipmentSpecs.inverters.filter { it.supportsParallel }
        // Inverter Engine round: the two new GRID_TIE entries are confirmed parallel-capable
        // ("subject to manufacturer/site design") but the source gives no specific max-unit count,
        // unlike every prior entry here — hence the map's value type widening to Int?.
        val expected: Map<String, Int?> = mapOf(
            "GEN-LB-US 6K" to 10, "GEN-LB-US 8K" to 10, "GEN-LB-US 10K" to 10,
            "LXP-LB-US 12K/13K" to 10, "SNA-US 12K" to 16,
            "SUN-6K-SG02LP2-US" to 16, "SUN-8K-SG01LP1-US" to 16,
            "HESP 12K-US" to 6, "SPH 8000TL-HU-US" to 6, "SPH 10000TL-HU-US" to 6,
            "S5-GC30K-LV" to null, "S5-GC50K" to null
        )
        assertEquals(expected.keys, parallelCapable.map { it.model }.toSet())
        parallelCapable.forEach { spec ->
            assertEquals("${spec.model}'s maxParallelUnits", expected.getValue(spec.model), spec.maxParallelUnits)
        }
    }

    @Test
    fun `every non-parallel-capable inverter has no invented max-unit count`() {
        EquipmentSpecs.inverters.filterNot { it.supportsParallel }.forEach { spec ->
            assertNull("${spec.model} isn't marked parallel-capable, so maxParallelUnits should stay unset", spec.maxParallelUnits)
        }
    }

    @Test
    fun `ASF4880S180-H is explicitly confirmed non-parallel by its own manufacturer manual, not just unconfirmed`() {
        val spec = EquipmentSpecs.inverters.first { it.model == "ASF4880S180-H" }
        assertFalse(spec.supportsParallel)
        // Distinct from an inverter nobody has checked yet - this one's dataQualityNote explicitly
        // records the manufacturer's own "does not support parallel connection" statement.
        assertTrue(spec.dataQualityNote.contains("NOT supported", ignoreCase = false))
    }
}
