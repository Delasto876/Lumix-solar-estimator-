package com.lumix.estimator.domain.simulation

import com.lumix.estimator.domain.AcLoad
import com.lumix.estimator.domain.ApplianceLoad
import com.lumix.estimator.domain.ApplianceType
import com.lumix.estimator.domain.QuoteInputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A68 (spec Phase 4/66's own "whatever system the installer designs is EXACTLY the system the
 * simulation models... no hard-coded substitute values"): before [ApplianceState.wattsOverride]
 * existed, [defaultApplianceStates] collapsed every selected AC unit's real BTU-derived wattage
 * (the same figure [com.lumix.estimator.domain.SystemCalculator]'s own wizard sizing already uses,
 * `btu / 10`) into [SimApplianceType.AIR_CONDITIONER]'s flat 1500W catalog placeholder — a unit
 * sized as a 9000 BTU/900W AC was simulated as if it were a 1500W one regardless.
 *
 * Phase 38 ("show separate in appliances... so i can schedule them") rebuilt this further: instead
 * of blending every selected BTU tier into one shared "Air Conditioner" entry with an AVERAGED
 * wattsOverride, each tier now gets its own [SimApplianceType] entry (`AC_9000`..`AC_60000`) with
 * its own EXACT (not blended) wattsOverride — these tests were rewritten to match: no more
 * averaging math to verify, just that each tier's own state carries its own real per-unit figure.
 */
class ApplianceWattsOverrideTest {

    private fun noApplianceInputs(ac: AcLoad) = QuoteInputs(
        appliances = ApplianceType.entries.associateWith { ApplianceLoad(qty = 0) },
        ac = ac
    )

    @Test
    fun `a mixed BTU selection gives each tier its own exact wattsOverride, not a shared blended average`() {
        val inputs = noApplianceInputs(
            AcLoad(hasAc = true, counts = mapOf(9000 to 2, 12000 to 1, 18000 to 0, 24000 to 0))
        )
        val states = defaultApplianceStates(inputs)
        val ac9000 = states.getValue(SimApplianceType.AC_9000)
        val ac12000 = states.getValue(SimApplianceType.AC_12000)
        assertEquals(900.0, ac9000.wattsOverride!!, 0.01)
        assertEquals(2, ac9000.totalQuantity)
        assertEquals(1200.0, ac12000.wattsOverride!!, 0.01)
        assertEquals(1, ac12000.totalQuantity)
        // An untouched tier stays disabled with no wattsOverride claim about a unit that was never selected.
        assertFalse(states.getValue(SimApplianceType.AC_18000).enabled)
    }

    @Test
    fun `a uniform single-tier selection carries exactly that tier's own wattage`() {
        val inputs = noApplianceInputs(
            AcLoad(hasAc = true, counts = mapOf(9000 to 3, 12000 to 0, 18000 to 0, 24000 to 0))
        )
        val states = defaultApplianceStates(inputs)
        val ac9000 = states.getValue(SimApplianceType.AC_9000)
        assertEquals(900.0, ac9000.wattsOverride!!, 0.01)
        assertEquals(3, ac9000.totalQuantity)
        assertTrue(ac9000.enabled)
    }

    @Test
    fun `no AC selected leaves every tier disabled`() {
        val inputs = noApplianceInputs(AcLoad(hasAc = false, counts = mapOf(9000 to 0, 12000 to 0, 18000 to 0, 24000 to 0)))
        val states = defaultApplianceStates(inputs)
        SimApplianceType.entries.filter { it.name.startsWith("AC_") }.forEach { tier ->
            assertFalse("$tier should be off when no AC is selected", states.getValue(tier).enabled)
        }
    }

    @Test
    fun `AIR_CONDITIONER itself is no longer a populated map key - superseded by the per-tier entries`() {
        val inputs = noApplianceInputs(AcLoad(hasAc = true, counts = mapOf(9000 to 1, 12000 to 0, 18000 to 0, 24000 to 0)))
        val states = defaultApplianceStates(inputs)
        assertNull(states[SimApplianceType.AIR_CONDITIONER])
    }

    @Test
    fun `the simulated timestep load reflects each tier's own real wattage, summed across tiers`() {
        // 2x 9000 BTU (900W each) + 1x 12000 BTU (1200W). AC's own schedule (defaultScheduleFor)
        // runs 19:00-03:00 with a 0.60 duty factor - hour 20.0 is well inside that window.
        val inputs = noApplianceInputs(
            AcLoad(hasAc = true, counts = mapOf(9000 to 2, 12000 to 1, 18000 to 0, 24000 to 0))
        )
        val states = defaultApplianceStates(inputs)
        val loadKw = totalApplianceLoadKwAt(states, hour = 20.0, dayType = DayType.WEEKDAY)
        // (2 x 900W + 1 x 1200W) x 0.60 duty factor = 1.8kW - same total the old blended model gave,
        // now correctly attributed per tier instead of averaged.
        assertEquals(1.8, loadKw, 0.01)
    }

    @Test
    fun `worst-case startup surge also reflects each tier's own real wattage, summed across tiers`() {
        val inputs = noApplianceInputs(
            AcLoad(hasAc = true, counts = mapOf(9000 to 2, 12000 to 1, 18000 to 0, 24000 to 0))
        )
        val states = defaultApplianceStates(inputs)
        val surgeKw = worstCaseStartupSurgeKw(states, hour = 20.0, dayType = DayType.WEEKDAY)
        // (2 x 900W + 1 x 1200W) x 3.0 startup multiplier = 9.0kW - unchanged from the old blended total.
        assertEquals(9.0, surgeKw, 0.01)
    }

    @Test
    fun `each tier is independently schedulable - disabling one tier does not affect another`() {
        val inputs = noApplianceInputs(
            AcLoad(hasAc = true, counts = mapOf(9000 to 1, 12000 to 1, 18000 to 0, 24000 to 0))
        )
        val states = defaultApplianceStates(inputs)
        val ac9000Off = states.getValue(SimApplianceType.AC_9000).copy(enabled = false)
        val updated = states + (SimApplianceType.AC_9000 to ac9000Off)
        val loadKw = totalApplianceLoadKwAt(updated, hour = 20.0, dayType = DayType.WEEKDAY)
        // Only the 12000 BTU unit still contributes: 1200W x 0.60 duty factor = 0.72kW.
        assertEquals(0.72, loadKw, 0.01)
    }
}
