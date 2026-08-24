package com.lumix.estimator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 38 ("do not parallel more than 2 5kwh battery and do not parallel more than 2 10kwh
 * battery... if the backup required is 13kwh use a 16kwh battery instead... if the backup is over
 * 20kwh instead of paralleling 3 10kwh battery use 2 15kwh or 2 16kwh"): regression tests for
 * [EquipmentSelectionEngine.selectBestHybridBattery]'s new real-world parallel-module cap.
 *
 * The catalog's own "15 kWh" tier (`Catalog.hybridBatteries`) is backed by a verified spec sheet
 * ([EquipmentSpecs.batteries], SRNE SR-EOS15B) whose own rating label is "16kWh class" — rated
 * 16.07 kWh, 15.42 kWh usable. So "use a 16kwh battery" and "2 15kwh or 2 16kwh" both resolve to
 * this exact same already-verified catalog tier; no fabricated battery model was added.
 */
class Phase38BatteryParallelCapTest {

    @Test
    fun `13 kWh usable required no longer proposes 3 parallel 5 kWh modules - a single 15 kWh (16 kWh class) module covers it alone`() {
        val choice = EquipmentSelectionEngine.selectBestHybridBattery(requiredUsableKwh = 13.0, requiredDischargeKw = 2.0, inverterCeilingKw = 8.0)
        assertEquals("15 kWh LiFePO4 (SRNE SR-EOS15B)", choice.option?.name)
        assertEquals(1, choice.moduleCount)
    }

    @Test
    fun `22 kWh usable required no longer proposes 3 parallel 10 kWh modules - uses 2x the 15 kWh (16 kWh class) tier instead`() {
        val choice = EquipmentSelectionEngine.selectBestHybridBattery(requiredUsableKwh = 22.0, requiredDischargeKw = 4.0, inverterCeilingKw = 12.0)
        assertEquals("15 kWh LiFePO4 (SRNE SR-EOS15B)", choice.option?.name)
        assertEquals(2, choice.moduleCount)
    }

    @Test
    fun `8 kWh usable required stays within the 2-module cap - no regression for a small requirement`() {
        val choice = EquipmentSelectionEngine.selectBestHybridBattery(requiredUsableKwh = 8.0, requiredDischargeKw = 1.5, inverterCeilingKw = 6.0)
        // Either a single 10 kWh module or 2x 5 kWh modules is a compliant answer - the user's own
        // "you can use 2 5kwh battery" was permissive, not a mandate that 5 kWh must be chosen over
        // a more efficient single 10 kWh module. What matters is neither capped tier exceeds 2.
        val kwh = choice.option?.kwh
        if (kwh == 5.0 || kwh == 10.0) {
            assertTrue("a capped tier must never need more than 2 parallel modules", choice.moduleCount <= 2)
        }
    }

    @Test
    fun `a requirement small enough for a single 5 kWh module is unaffected by the new cap`() {
        val choice = EquipmentSelectionEngine.selectBestHybridBattery(requiredUsableKwh = 3.0, requiredDischargeKw = 1.0, inverterCeilingKw = 5.0)
        assertEquals("5 kWh LiFePO4 (SRNE SR-EOS05B)", choice.option?.name)
        assertEquals(1, choice.moduleCount)
    }

    @Test
    fun `no combination of required usable kWh ever proposes more than 2 parallel modules of a capped tier`() {
        var kwh = 0.5
        while (kwh <= 60.0) {
            val choice = EquipmentSelectionEngine.selectBestHybridBattery(requiredUsableKwh = kwh, requiredDischargeKw = kwh * 0.3, inverterCeilingKw = 15.0)
            val tierKwh = choice.option?.kwh
            if (tierKwh == 5.0 || tierKwh == 10.0) {
                assertTrue(
                    "requiredUsableKwh=$kwh chose ${choice.moduleCount}x ${tierKwh}kWh - exceeds the 2-module parallel cap",
                    choice.moduleCount <= 2
                )
            }
            kwh += 0.5
        }
    }
}
