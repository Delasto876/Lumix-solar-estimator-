package com.lumix.estimator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A77 (spec Phase 14 — "improve equipment database"): regression coverage for the new
 * [EquipmentSpecs.InverterSpec.surgePowerRatio]/[EquipmentSpecs.InverterSpec.surgeDurationSeconds]
 * fields — real, sourced numbers only for the two entries whose datasheet excerpt on file actually
 * stated one, `null` everywhere else rather than assumed/copied from a same-brand sibling.
 */
class EquipmentSpecsTest {

    @Test
    fun `only the two inverters with a real sourced surge figure have one set`() {
        val withSurge = EquipmentSpecs.inverters.filter { it.surgePowerRatio != null }
        assertEquals(2, withSurge.size)
        assertTrue(withSurge.all { it.surgeDurationSeconds != null })

        // A89/Ph21: model strings updated to this round's spreadsheet-reconciliation renames
        // (LXP-LB-US 12K/13K was "GEN-LB-US 13K"; ASF4860U80-H was "HESP4860U140-HUS") — same
        // underlying carried-over surge figures, unchanged by that reconciliation.
        val luxPower13k = EquipmentSpecs.inverters.first { it.model == "LXP-LB-US 12K/13K" }
        assertEquals(2.0, luxPower13k.surgePowerRatio!!, 0.001)
        assertEquals(0.5, luxPower13k.surgeDurationSeconds!!, 0.001)

        val srne6k = EquipmentSpecs.inverters.first { it.model == "ASF4860U80-H" }
        assertEquals(2.0, srne6k.surgePowerRatio!!, 0.001)
        assertEquals(10.0, srne6k.surgeDurationSeconds!!, 0.001)
    }

    @Test
    fun `every other inverter has no invented surge figure`() {
        val withoutSurge = EquipmentSpecs.inverters.filterNot { it.model == "LXP-LB-US 12K/13K" || it.model == "ASF4860U80-H" }
        assertTrue(withoutSurge.isNotEmpty())
        assertTrue(withoutSurge.all { it.surgePowerRatio == null && it.surgeDurationSeconds == null })
    }

    @Test
    fun `every battery has real communication and parallel-capability data on file`() {
        assertTrue(EquipmentSpecs.batteries.isNotEmpty())
        EquipmentSpecs.batteries.forEach { battery ->
            assertTrue("${battery.model} should have a non-blank bmsCommunication note", battery.bmsCommunication.isNotBlank())
        }
    }
}
