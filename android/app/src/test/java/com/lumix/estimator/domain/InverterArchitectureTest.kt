package com.lumix.estimator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Inverter Engine round ("UPDATE THE INVERTER ENGINE... add a required inverter SYSTEM TYPE
 * selector... GRID-TIE = PV + GRID ONLY. NO BATTERY"): regression coverage for
 * [EquipmentSpecs.InverterSpec.architecture] and the two new Solis grid-tie entries the spec's
 * own datasheet excerpts named — S5-GC30K-LV and S5-GC50K.
 */
class InverterArchitectureTest {

    @Test
    fun `every pre-existing inverter defaults to HYBRID architecture, unchanged`() {
        val preExistingModels = setOf(
            "GEN-LB-US 6K", "GEN-LB-US 8K", "GEN-LB-US 10K", "LXP-LB-US 12K/13K", "SNA-US 12K",
            "SUN-6K-SG02LP2-US", "SUN-8K-SG01LP1-US", "ASF4860U80-H", "ASF4880S180-H",
            "HES48100U200-H", "HESP 12K-US", "SPH 8000TL-HU-US", "SPH 10000TL-HU-US"
        )
        preExistingModels.forEach { model ->
            val spec = EquipmentSpecs.inverters.first { it.model == model }
            assertEquals("$model should still default to HYBRID architecture", InverterArchitecture.HYBRID, spec.architecture)
        }
    }

    @Test
    fun `the two new Solis entries are classified GRID_TIE`() {
        val gc30k = EquipmentSpecs.inverters.first { it.model == "S5-GC30K-LV" }
        val gc50k = EquipmentSpecs.inverters.first { it.model == "S5-GC50K" }
        assertEquals(InverterArchitecture.GRID_TIE, gc30k.architecture)
        assertEquals(InverterArchitecture.GRID_TIE, gc50k.architecture)
    }

    @Test
    fun `GRID_TIE Solis entries have no battery fields populated`() {
        listOf("S5-GC30K-LV", "S5-GC50K").forEach { model ->
            val spec = EquipmentSpecs.inverters.first { it.model == model }
            assertNull("$model maxBatteryA should be null - grid-tie has no battery port", spec.maxBatteryA)
            assertNull("$model maxChargePowerKw should be null - grid-tie has no battery port", spec.maxChargePowerKw)
            assertNull("$model maxDischargePowerKw should be null - grid-tie has no battery port", spec.maxDischargePowerKw)
            assertNull("$model batteryVoltageMinV should be null - grid-tie has no battery port", spec.batteryVoltageMinV)
            assertNull("$model batteryVoltageMaxV should be null - grid-tie has no battery port", spec.batteryVoltageMaxV)
        }
    }

    @Test
    fun `S5-GC30K-LV real datasheet figures are on file`() {
        val spec = EquipmentSpecs.inverters.first { it.model == "S5-GC30K-LV" }
        assertEquals("Solis", spec.brand)
        assertEquals(30000, spec.ratedOutputW)
        assertEquals(45000, spec.maxPvW)
        assertEquals(1100, spec.maxPvV)
        assertEquals(180, spec.mpptVoltageMinV)
        assertEquals(1000, spec.mpptVoltageMaxV)
        assertEquals(4, spec.mpptCount)
        assertEquals(2, spec.stringsPerMppt)
        assertEquals(33.0, spec.maxApparentPowerKva!!, 0.001)
        assertEquals(listOf(220.0), spec.acLineToLineVoltageOptionsV)
        assertEquals(VerificationStatus.VERIFIED, spec.verificationStatus)
    }

    @Test
    fun `S5-GC50K real datasheet figures are on file, including both real voltage variants`() {
        val spec = EquipmentSpecs.inverters.first { it.model == "S5-GC50K" }
        assertEquals("Solis", spec.brand)
        assertEquals(50000, spec.ratedOutputW)
        assertEquals(66500, spec.maxPvW)
        assertEquals(5, spec.mpptCount)
        assertEquals(55.0, spec.maxApparentPowerKva!!, 0.001)
        assertEquals(listOf(380.0, 400.0), spec.acLineToLineVoltageOptionsV)
        assertEquals(VerificationStatus.VERIFIED, spec.verificationStatus)
    }

    @Test
    fun `neither new Solis entry has an invented surge figure`() {
        listOf("S5-GC30K-LV", "S5-GC50K").forEach { model ->
            val spec = EquipmentSpecs.inverters.first { it.model == model }
            assertNull("$model surgePowerRatio should stay unset - source gave none", spec.surgePowerRatio)
            assertNull("$model surgeDurationSeconds should stay unset - source gave none", spec.surgeDurationSeconds)
        }
    }

    @Test
    fun `every hybrid and off-grid inverter has an empty acLineToLineVoltageOptionsV`() {
        EquipmentSpecs.inverters.filter { it.architecture != InverterArchitecture.GRID_TIE }.forEach { spec ->
            assertTrue("${spec.model} should have no line-to-line voltage options - only grid-tie entries need this", spec.acLineToLineVoltageOptionsV.isEmpty())
            assertNull("${spec.model} maxApparentPowerKva should stay unset", spec.maxApparentPowerKva)
        }
    }

    @Test
    fun `Catalog gridtieInverters resolves both new real entries without throwing, plus keeps the prior placeholder`() {
        val ids = Catalog.gridtieInverters.map { it.id }.toSet()
        assertEquals(setOf("grid15k", "solisGc30kLv", "solisGc50k"), ids)
        Catalog.gridtieInverters.forEach { option ->
            assertEquals(SystemMode.GRIDTIE, option.mode)
        }
    }

    @Test
    fun `Catalog manualInverters includes both new grid-tie entries too`() {
        val ids = Catalog.manualInverters.map { it.id }.toSet()
        assertTrue(ids.contains("solisGc30kLv"))
        assertTrue(ids.contains("solisGc50k"))
    }

    @Test
    fun `Catalog poolFor GRIDTIE matches gridtieInverters exactly`() {
        assertEquals(Catalog.gridtieInverters, Catalog.poolFor(SystemMode.GRIDTIE))
    }

    @Test
    fun `new grid-tie inverters have no price entered yet - blank until manually entered`() {
        val blank = PriceList()
        assertNull(blank.inverterSolisGc30kLv)
        assertNull(blank.inverterSolisGc50k)
    }

    @Test
    fun `both new grid-tie entries support parallel operation with no invented unit-count limit`() {
        listOf("S5-GC30K-LV", "S5-GC50K").forEach { model ->
            val spec = EquipmentSpecs.inverters.first { it.model == model }
            assertTrue("$model should be confirmed parallel-capable", spec.supportsParallel)
            assertNull("$model has no confirmed max-unit count in the source data", spec.maxParallelUnits)
        }
    }
}
