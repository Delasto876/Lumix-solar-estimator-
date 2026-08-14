package com.lumix.estimator.domain.simulation

import com.lumix.estimator.domain.ApplianceLoad
import com.lumix.estimator.domain.ApplianceType
import com.lumix.estimator.domain.PriceList
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteMode
import com.lumix.estimator.domain.SystemCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A54: regression tests for the wizard's appliance catalog expansion ("the section to choose
 * appliances update it with the same list in the simulation window, that list only show some
 * appliances", 2026-08-14). Previously [ApplianceType] (the wizard's picker,
 * `StepHouseholdAppliances.kt`) covered only 19 of [SimApplianceType]'s 46 entries — the other 26
 * (air fryer, security system, EV charger, pool pump, etc.) were only reachable from the
 * Simulation screen's own fuller picker ([com.lumix.estimator.ui.simulation.AppliancesSheet]).
 * [ApplianceType] now mirrors every non-AC [SimApplianceType] entry, and every one of them is
 * wired through [defaultApplianceStates] and [SystemCalculator]'s sizing — not just added to the
 * enum and left disconnected.
 */
class ApplianceCatalogParityTest {

    @Test
    fun `every non-AC SimApplianceType has exactly one ApplianceType counterpart`() {
        // 46 SimApplianceType entries total, minus AIR_CONDITIONER (its own dedicated wizard step/AcLoad).
        val simTypesExcludingAc = SimApplianceType.entries.filter { it != SimApplianceType.AIR_CONDITIONER }
        assertEquals(simTypesExcludingAc.size, ApplianceType.entries.size)
    }

    @Test
    fun `a previously simulation-only appliance is now selectable from the wizard and reaches the simulation`() {
        val inputs = QuoteInputs(
            appliances = ApplianceType.entries.associateWith { ApplianceLoad(qty = 0) }
                .toMutableMap().apply { this[ApplianceType.POOL_PUMP] = ApplianceLoad(qty = 2) }
        )
        val states = defaultApplianceStates(inputs)
        val poolPumpState = states.getValue(SimApplianceType.POOL_PUMP)
        assertTrue("a wizard-selected pool pump must actually enable the simulation's own appliance state", poolPumpState.enabled)
        assertEquals(2, poolPumpState.totalQuantity)
    }

    @Test
    fun `an appliance the wizard never touched stays off in the simulation`() {
        val inputs = QuoteInputs(appliances = ApplianceType.entries.associateWith { ApplianceLoad(qty = 0) })
        val states = defaultApplianceStates(inputs)
        // Every one of the 45 wizard-linked types should be off when every wizard qty is 0 -
        // there's no longer a separate "simulation-only" set that defaults on regardless.
        SimApplianceType.entries.filter { it != SimApplianceType.AIR_CONDITIONER }.forEach { simType ->
            assertTrue("$simType should be off when its wizard quantity is 0", states.getValue(simType).enabled.not())
        }
    }

    @Test
    fun `a newly reachable appliance actually contributes to daily energy sizing`() {
        val withoutIt = QuoteInputs(
            quoteMode = QuoteMode.LOAD,
            appliances = ApplianceType.entries.associateWith { ApplianceLoad(qty = 0) }
        )
        val withIt = withoutIt.copy(
            appliances = withoutIt.appliances.toMutableMap().apply { this[ApplianceType.SECURITY_SYSTEM] = ApplianceLoad(qty = 1) }
        )
        val resultWithout = SystemCalculator.calculate(withoutIt, PriceList.DEFAULT, PriceList.DEFAULT)
        val resultWith = SystemCalculator.calculate(withIt, PriceList.DEFAULT, PriceList.DEFAULT)
        assertTrue(
            "selecting a previously wizard-unreachable appliance must increase the calculated daily energy",
            resultWith.designDailyKwh > resultWithout.designDailyKwh
        )
    }
}
