package com.lumix.estimator.domain.simulation

import com.lumix.estimator.domain.AcLoad
import com.lumix.estimator.domain.ApplianceLoad
import com.lumix.estimator.domain.ApplianceType
import com.lumix.estimator.domain.QuoteInputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A68 (spec Phase 4/66's own "whatever system the installer designs is EXACTLY the system the
 * simulation models... no hard-coded substitute values"): before [ApplianceState.wattsOverride]
 * existed, [defaultApplianceStates] collapsed every selected AC unit's real BTU-derived wattage
 * (the same figure [com.lumix.estimator.domain.SystemCalculator]'s own wizard sizing already uses,
 * `btu / 10`) into [SimApplianceType.AIR_CONDITIONER]'s flat 1500W catalog placeholder — a unit
 * sized as a 9000 BTU/900W AC was simulated as if it were a 1500W one regardless. Every other
 * appliance type's catalog wattage already IS its real figure, so this override is exercised only
 * by AC here.
 */
class ApplianceWattsOverrideTest {

    private fun noApplianceInputs(ac: AcLoad) = QuoteInputs(
        appliances = ApplianceType.entries.associateWith { ApplianceLoad(qty = 0) },
        ac = ac
    )

    @Test
    fun `a mixed BTU selection produces a real blended wattsOverride, not the flat catalog placeholder`() {
        // 2 x 9000 BTU (900W each) + 1 x 12000 BTU (1200W) = 3000W total / 3 units = 1000W/unit -
        // nowhere near the catalog's flat 1500W.
        val inputs = noApplianceInputs(
            AcLoad(hasAc = true, counts = mapOf(9000 to 2, 12000 to 1, 18000 to 0, 24000 to 0))
        )
        val states = defaultApplianceStates(inputs)
        val acState = states.getValue(SimApplianceType.AIR_CONDITIONER)
        assertNotNull("expected a real blended wattsOverride for a mixed BTU selection", acState.wattsOverride)
        assertEquals(1000.0, acState.wattsOverride!!, 0.01)
        assertEquals(3, acState.totalQuantity)
    }

    @Test
    fun `a uniform single-tier selection blends to exactly that tier's own wattage`() {
        val inputs = noApplianceInputs(
            AcLoad(hasAc = true, counts = mapOf(9000 to 3, 12000 to 0, 18000 to 0, 24000 to 0))
        )
        val states = defaultApplianceStates(inputs)
        val acState = states.getValue(SimApplianceType.AIR_CONDITIONER)
        assertEquals(900.0, acState.wattsOverride!!, 0.01)
    }

    @Test
    fun `no AC selected leaves wattsOverride null and the appliance disabled`() {
        val inputs = noApplianceInputs(AcLoad(hasAc = false, counts = mapOf(9000 to 0, 12000 to 0, 18000 to 0, 24000 to 0)))
        val states = defaultApplianceStates(inputs)
        val acState = states.getValue(SimApplianceType.AIR_CONDITIONER)
        assertNull(acState.wattsOverride)
        assertFalse(acState.enabled)
    }

    @Test
    fun `the simulated timestep load actually reflects the real blended wattage, not 1500W flat`() {
        // Same 2x9000+1x12000 -> 1000W/unit mix as above. AC's own schedule (defaultScheduleFor)
        // runs 19:00-03:00 with a 0.60 duty factor - hour 20.0 is well inside that window.
        val inputs = noApplianceInputs(
            AcLoad(hasAc = true, counts = mapOf(9000 to 2, 12000 to 1, 18000 to 0, 24000 to 0))
        )
        val states = defaultApplianceStates(inputs)
        val loadKw = totalApplianceLoadKwAt(states, hour = 20.0, dayType = DayType.WEEKDAY)
        // 3 units x 1000W x 0.60 duty factor = 1.8kW - would have been 3 x 1500 x 0.60 = 2.7kW
        // under the old flat-placeholder bug, a 50% overstatement.
        assertEquals(1.8, loadKw, 0.01)
    }

    @Test
    fun `worst-case startup surge also reflects the real blended wattage`() {
        val inputs = noApplianceInputs(
            AcLoad(hasAc = true, counts = mapOf(9000 to 2, 12000 to 1, 18000 to 0, 24000 to 0))
        )
        val states = defaultApplianceStates(inputs)
        val surgeKw = worstCaseStartupSurgeKw(states, hour = 20.0, dayType = DayType.WEEKDAY)
        // 3 units x 1000W x 3.0 startup multiplier = 9.0kW - would have been 3 x 1500 x 3.0 = 13.5kW.
        assertEquals(9.0, surgeKw, 0.01)
    }
}
