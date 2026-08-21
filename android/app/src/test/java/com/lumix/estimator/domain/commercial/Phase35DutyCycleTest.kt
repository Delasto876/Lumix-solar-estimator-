package com.lumix.estimator.domain.commercial

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 35 ("remember the half cycle... of these equipment"): regression tests confirming
 * [LoadInstance.dutyCycleFraction] — already used in the wizard's own connected/design-load totals
 * since Phase 27 — is now also applied by [commercialLoadKwAt], the function that drives the live
 * simulation dial. Before this fix, a cycling load (compressor, pump) reported its full nameplate
 * watts for the entire time its window was active, ignoring how much of that time it's genuinely
 * drawing power versus idling between cycles.
 */
class Phase35DutyCycleTest {

    private fun load(watts: Double, dutyCycle: Double, qty: Int = 1) = LoadInstance(
        definitionId = "test_compressor", label = "Test Compressor", quantity = qty, ratedWatts = watts,
        dutyCycleFraction = dutyCycle, operatingHoursPerDay = 4.0, typicalStartHour = 8.0 // 8am-12pm
    )

    @Test
    fun `full duty cycle (1_0) behaves exactly as before - no regression`() {
        val loads = listOf(load(watts = 2000.0, dutyCycle = 1.0))
        assertEquals(2.0, commercialLoadKwAt(loads, 9.0), 0.001)
    }

    @Test
    fun `a half duty cycle compressor reports half its nameplate power while its window is active`() {
        val loads = listOf(load(watts = 2000.0, dutyCycle = 0.5))
        assertEquals(1.0, commercialLoadKwAt(loads, 9.0), 0.001)
        // Still zero outside the window - duty cycle scales the ON contribution, it doesn't widen the window.
        assertEquals(0.0, commercialLoadKwAt(loads, 20.0), 0.001)
    }

    @Test
    fun `duty cycle combines correctly with quantity`() {
        val loads = listOf(load(watts = 1000.0, dutyCycle = 0.25, qty = 4))
        // 4 units x 1000W x 0.25 duty = 1000W = 1.0kW
        assertEquals(1.0, commercialLoadKwAt(loads, 9.0), 0.001)
    }

    @Test
    fun `a zero duty cycle contributes nothing even while the window is active`() {
        val loads = listOf(load(watts = 5000.0, dutyCycle = 0.0))
        assertEquals(0.0, commercialLoadKwAt(loads, 9.0), 0.001)
    }
}
