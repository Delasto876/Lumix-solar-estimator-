package com.lumix.estimator.domain.commercial

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 32 ("for the appliance section... if commercial or industrial choose those in appliances
 * picker"): regression tests for [commercialLoadKwAt] — the Commercial/Industrial analogue of
 * [com.lumix.estimator.domain.simulation.totalApplianceLoadKwAt] that now drives the live
 * simulation dial for a Commercial/Industrial quote.
 */
class Phase32CommercialLoadSimulationTest {

    private fun load(watts: Double, qty: Int = 1, start: Double? = 8.0, hours: Double = 4.0) = LoadInstance(
        definitionId = "test_load", label = "Test Load", quantity = qty, ratedWatts = watts,
        operatingHoursPerDay = hours, typicalStartHour = start
    )

    @Test
    fun `a load contributes its real power only while its window is active`() {
        val loads = listOf(load(watts = 2000.0, start = 8.0, hours = 4.0)) // 8am-12pm
        assertEquals(0.0, commercialLoadKwAt(loads, 7.99), 0.001)
        assertEquals(2.0, commercialLoadKwAt(loads, 8.0), 0.001)
        assertEquals(2.0, commercialLoadKwAt(loads, 11.99), 0.001)
        assertEquals(0.0, commercialLoadKwAt(loads, 12.0), 0.001)
    }

    @Test
    fun `quantity multiplies the contribution`() {
        val loads = listOf(load(watts = 1000.0, qty = 5, start = 9.0, hours = 1.0))
        assertEquals(5.0, commercialLoadKwAt(loads, 9.5), 0.001)
    }

    @Test
    fun `a window wrapping past midnight is active on both sides of the boundary`() {
        val loads = listOf(load(watts = 3000.0, start = 22.0, hours = 5.0)) // 10pm-3am
        assertEquals(3.0, commercialLoadKwAt(loads, 23.0), 0.001)
        assertEquals(3.0, commercialLoadKwAt(loads, 2.0), 0.001)
        assertEquals(0.0, commercialLoadKwAt(loads, 3.0), 0.001)
        assertEquals(0.0, commercialLoadKwAt(loads, 21.99), 0.001)
    }

    @Test
    fun `null typicalStartHour defaults to midnight, matching the wizard's own display default`() {
        val loads = listOf(load(watts = 1000.0, start = null, hours = 6.0)) // midnight-6am
        assertEquals(1.0, commercialLoadKwAt(loads, 0.0), 0.001)
        assertEquals(1.0, commercialLoadKwAt(loads, 5.99), 0.001)
        assertEquals(0.0, commercialLoadKwAt(loads, 6.0), 0.001)
    }

    @Test
    fun `zero operatingHoursPerDay never contributes - a freshly-added load with no runtime set yet`() {
        val loads = listOf(load(watts = 5000.0, hours = 0.0))
        assertEquals(0.0, commercialLoadKwAt(loads, 8.0), 0.001)
        assertEquals(0.0, commercialLoadKwAt(loads, 0.0), 0.001)
    }

    @Test
    fun `multiple loads sum, only counting the ones active at that hour`() {
        val loads = listOf(
            load(watts = 2000.0, start = 8.0, hours = 4.0), // 8am-12pm, active at 9
            load(watts = 3000.0, start = 13.0, hours = 2.0) // 1pm-3pm, NOT active at 9
        )
        assertEquals(2.0, commercialLoadKwAt(loads, 9.0), 0.001)
        assertEquals(3.0, commercialLoadKwAt(loads, 13.5), 0.001)
        assertEquals(0.0, commercialLoadKwAt(loads, 20.0), 0.001)
    }
}
