package com.lumix.estimator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A64 (2026-08-14 "FIX 12-HOUR OVERNIGHT BACKUP SIZING" §1-9, §32-33): regression tests for
 * [SystemCalculator.sizeHybridBatteryForBackup] — the real-load-curve-driven,
 * simulation-verified replacement for the old flat `criticalDailyKwh * (backupHours/24) / DOD`
 * battery formula the installer's own bug report was about.
 *
 * A full precise trace of the escalation loop's exact outcome (which battery tier, how many
 * simulation attempts) needs a hand-accurate Python port of [SimulationEngine.buildDayTimeline]'s
 * complete physics — background load curve, per-appliance duty cycling, SOC-dependent charge/
 * discharge tapering — which this round didn't build (the same honest limitation
 * [SystemCalculatorRechargeAwareSizingTest] already disclosed for A63's own escalation loop).
 * What's tested here instead: exact numbers where the margin is wide enough to be certain without
 * a trace (a trivially oversized case relative to load), and the structural invariants the
 * function guarantees regardless of the underlying simulation's exact numbers (bounded search,
 * self-consistent required-energy figure, never a negative/nonsensical result).
 */
class SystemCalculatorBatteryBackupSizingTest {

    private fun noApplianceInputs(
        backupHoursPreset: Int?,
        backupCoverage: BackupCoverage = BackupCoverage.MOST_LOAD
    ) = QuoteInputs(
        appliances = ApplianceType.entries.associateWith { ApplianceLoad(qty = 0) },
        backupHoursPreset = backupHoursPreset,
        backupCoverage = backupCoverage
    )

    @Test
    fun `zero backup hours requested - no battery, no simulation run`() {
        val input = noApplianceInputs(backupHoursPreset = 0)
        val sizing = SystemCalculator.sizeHybridBatteryForBackup(
            input = input, designDailyKwh = 30.0, inverterKw = 12.0, inverterName = "Test 12K"
        )
        assertNull(sizing.choice.option)
        assertEquals(0, sizing.choice.moduleCount)
        assertEquals(0.0, sizing.requiredUsableKwh, 0.0)
        assertNull(sizing.backupEstimate)
    }

    @Test
    fun `a trivial overnight load against the smallest battery tier meets target on the first attempt`() {
        // avgDailyLoadKwh=5 (a very small house) with NO appliances selected -> only the
        // background-load curve contributes, floored at 0.15kW (SimulationEngine's own constant).
        // Over a 1-hour window that's roughly 0.15-0.2kWh required - the smallest catalog tier
        // (5kWh nominal, real SRNE SR-EOS05B, ~4kWh usable even under the simulation's flat 20%
        // reserve-floor convention) is wildly oversized for this, so the very first attempt should
        // already clear the 1-hour target with no escalation needed.
        val input = noApplianceInputs(backupHoursPreset = 1)
        val sizing = SystemCalculator.sizeHybridBatteryForBackup(
            input = input, designDailyKwh = 5.0, inverterKw = 12.0, inverterName = "Test 12K"
        )
        assertNotNull(sizing.choice.option)
        assertEquals(5.0, sizing.choice.option!!.kwh, 0.001)
        assertEquals(1, sizing.choice.moduleCount)
        assertNotNull(sizing.backupEstimate)
        assertTrue(
            "expected the trivially oversized battery to clear the 1-hour target, got ${sizing.backupEstimate!!.hours}h",
            sizing.backupEstimate!!.hours >= 1.0
        )
    }

    @Test
    fun `the required-usable-energy figure returned always matches what actually drove the winning attempt`() {
        // Whatever the escalation loop lands on, requiredUsableKwh must be internally consistent
        // with choice - this is the exact "displayed requirement disagrees with the actual
        // selection" bug category the installer's report was about (spec §6's "ONE centralized
        // battery-energy calculation").
        val input = noApplianceInputs(backupHoursPreset = 12)
        val sizing = SystemCalculator.sizeHybridBatteryForBackup(
            input = input, designDailyKwh = 40.0, inverterKw = 12.0, inverterName = "Test 12K"
        )
        assertTrue("expected a non-negative required-energy figure", sizing.requiredUsableKwh >= 0.0)
        if (sizing.choice.option != null) {
            // The tier search's own module-count formula (ceil(target / usablePerModule), then
            // maxed against whatever power alone would need) guarantees usableTotal >=
            // requiredUsableKwh unconditionally - re-asserted here as the contract this function's
            // caller relies on, so a regression that breaks it fails loudly.
            assertTrue(
                "expected the chosen tier's usable energy to cover the target it was searched against",
                sizing.choice.totalUsableKwh >= sizing.requiredUsableKwh - 0.01
            )
        }
    }

    @Test
    fun `escalation never searches indefinitely - bounded regardless of how demanding the load is`() {
        // A deliberately heavy, long backup request - whether or not the real simulation confirms
        // the pick meets target (not re-traced this round), the function must still terminate with
        // a real, usable result rather than looping forever or throwing.
        val input = noApplianceInputs(backupHoursPreset = 12, backupCoverage = BackupCoverage.FULL)
        val sizing = SystemCalculator.sizeHybridBatteryForBackup(
            input = input, designDailyKwh = 60.0, inverterKw = 12.0, inverterName = "Test 12K"
        )
        assertNotNull(sizing.choice.option)
        assertTrue("expected a real positive battery bank for a substantial 60kWh/day load", sizing.choice.totalKwh > 0.0)
        // Bounded catalog tiers x a bounded module count from a bounded number of escalation
        // attempts - not an unbounded bank size.
        assertTrue(
            "expected a realistic bounded module count, got ${sizing.choice.moduleCount}",
            sizing.choice.moduleCount in 1..20
        )
    }

    @Test
    fun `section 32 scenario - real LuxPower 12K plus SRNE battery tiers against a 27point1kWh day, 12h Most Load`() {
        // The installer's own explicit regression scenario (spec §32): PV 7x700W / LuxPower
        // LXP-LB-US 12K / battery originally 15kWh / 27.1kWh daily load / ~9kW peak / 12h backup /
        // Most Load coverage. This exercises the real catalog inverter and battery tiers (not
        // synthetic test doubles) end to end through the sizing+verification loop. Per spec §32's
        // own explicit instruction ("Do NOT assume 15kWh provides 12 hours... the simulation
        // determines the answer"), this deliberately does NOT assert a specific tier or an exact
        // backup-hours number - that would require the same full day-simulation trace disclosed as
        // out of scope above. It asserts that the pipeline actually ran a real simulation and
        // produced a self-consistent, structurally sound answer either way.
        val input = noApplianceInputs(backupHoursPreset = 12, backupCoverage = BackupCoverage.MOST_LOAD)
        val luxPower12k = Catalog.hybridInverters.first { it.kw == 12.0 }
        val sizing = SystemCalculator.sizeHybridBatteryForBackup(
            input = input, designDailyKwh = 27.1, inverterKw = luxPower12k.kw, inverterName = luxPower12k.name
        )
        assertNotNull("expected a real battery bank to be selected for a 27.1kWh/day load", sizing.choice.option)
        assertNotNull("expected the pick to actually be verified by a real simulation", sizing.backupEstimate)
        assertTrue("expected a finite, non-negative simulated backup duration", sizing.backupEstimate!!.hours >= 0.0)
        assertTrue(
            "expected the required-usable-energy figure to be a real, positive draw for a household this size",
            sizing.requiredUsableKwh > 0.0
        )
    }
}
