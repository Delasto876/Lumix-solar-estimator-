package com.lumix.estimator.domain

import com.lumix.estimator.domain.simulation.RechargeFeasibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A63 (installer's own detailed spec §24-28 follow-up, 2026-08-14 — "ADD ONE OR TWO PANELS...
 * choose the smallest candidate that provides a meaningful engineering improvement... Do NOT add
 * panels indefinitely"): regression tests for [SystemCalculator.recheckPanelCountForRecharge].
 *
 * Reuses, rather than re-traces, [com.lumix.estimator.domain.simulation.RechargeFeasibilityTest]'s
 * own already-hand-traced day-simulation results for the exact same real hardware (6 x 615W,
 * 10kWh SRNE SR-EOS10B: `avgDailyLoadKwh=60` reaches 90% SOC by 12:40pm; `avgDailyLoadKwh=150`
 * never reaches it, 40.7% at 2pm) — [RechargeFeasibility.evaluate]'s own physics don't depend on
 * which [InverterOption] name is attached to a [com.lumix.estimator.domain.simulation.SimSystemConfig],
 * only its numeric fields, so those already-verified outcomes are exact, not re-derived, inputs
 * here. Uses the real catalog Growatt 10k inverter (not RechargeFeasibilityTest's synthetic "Deye
 * SUN-10K" name) so [EquipmentSelectionEngine.checkPanelInverterCompatibility]'s own real
 * EquipmentSpecs match resolves correctly for the electrical-validity re-check every trial panel
 * count goes through.
 */
class SystemCalculatorRechargeAwareSizingTest {

    private val growatt10k = Catalog.hybridInverters.first { it.kw == 10.0 }
    private val srneEos10bBattery = BatteryOption("10kWh (SRNE SR-EOS10B)", 10.24) { 0.0 }

    // Matches RechargeFeasibilityTest's own already-traced real hardware figures exactly.
    private val srneEos10bChargeKw = ((150 * 51.2) / 1000.0).coerceAtMost(10.0)
    private val srneEos10bDischargeKw = ((200 * 51.2) / 1000.0).coerceAtMost(10.0)

    private fun noApplianceInputs() = QuoteInputs(
        appliances = ApplianceType.entries.associateWith { ApplianceLoad(qty = 0) }
    )

    private fun baselineChoice(panelCount: Int, stringCounts: List<Int>) = EquipmentSelectionEngine.PanelChoice(
        panelWatts = 615,
        panelCount = panelCount,
        totalPvKw = panelCount * 0.615,
        oversizePercent = 0.0,
        electricallyValid = true,
        reason = "test baseline: $panelCount x 615W panels",
        stringCounts = stringCounts
    )

    @Test
    fun `an already-adequate baseline is returned completely unchanged - no unnecessary oversizing`() {
        // Reuses RechargeFeasibilityTest's own traced "adequately sized array" case verbatim
        // (avgDailyLoadKwh=60 with 6 x 615W / 10.24kWh reaches 90% SOC by hour 12.67, well before
        // the 2pm target) as the baseline here — since it already meets the target, the function
        // must not run a single extra simulation or add a single extra panel.
        val baseline = baselineChoice(panelCount = 6, stringCounts = listOf(2, 2, 2))
        val result = SystemCalculator.recheckPanelCountForRecharge(
            baseline = baseline,
            inverter = growatt10k,
            chosenBattery = srneEos10bBattery,
            totalBatteryKwh = 10.24,
            batteryMaxChargeKw = srneEos10bChargeKw,
            batteryMaxDischargeKw = srneEos10bDischargeKw,
            peakWatts = 8000.0,
            designDailyKwh = 60.0,
            input = noApplianceInputs()
        )
        assertEquals(baseline, result)
    }

    @Test
    fun `a baseline that misses the recharge target never regresses and never searches past baseline plus 2`() {
        // Reuses RechargeFeasibilityTest's own traced "heavily undersized array" case
        // (avgDailyLoadKwh=150 with 6 x 615W / 10.24kWh never reaches 90% SOC that day, only
        // 40.7% at 2pm) as the baseline. Panel counts 6/7/8 are all independently confirmed
        // electrically valid against the real Growatt 10k spec (maxPvW 15000W, maxPvV 525V, 3
        // MPPT trackers — hand-traced: 615W panel Vmp 45.76V, shortest string at any of these
        // three counts is 2 panels = 91.5V, just clears the 90V floor; longest string Voc never
        // exceeds ~174V, nowhere near the 525V ceiling), so every trial the function tries here
        // is a real, valid electrical candidate, not one filtered out for an unrelated reason.
        //
        // What ISN'T re-traced here: exactly how much extra SOC-by-2pm 1-2 more panels buys
        // against a load this heavy (150kWh/day, 6.25kW average) — that depends on how much of
        // the morning's added solar the load itself absorbs before any reaches the battery, which
        // needs a full day-simulation trace this round didn't run for this specific count. So
        // this only asserts what's true regardless of that number: the function never returns
        // fewer panels than the baseline, and never searches beyond baseline + 2 — the two hard
        // invariants "ADD ONE OR TWO PANELS" bakes in structurally, whatever the simulated
        // improvement (if any) turns out to be.
        val baseline = baselineChoice(panelCount = 6, stringCounts = listOf(2, 2, 2))
        val result = SystemCalculator.recheckPanelCountForRecharge(
            baseline = baseline,
            inverter = growatt10k,
            chosenBattery = srneEos10bBattery,
            totalBatteryKwh = 10.24,
            batteryMaxChargeKw = srneEos10bChargeKw,
            batteryMaxDischargeKw = srneEos10bDischargeKw,
            peakWatts = 8000.0,
            designDailyKwh = 150.0,
            input = noApplianceInputs()
        )
        assertTrue("expected the count to never shrink below the baseline", result.panelCount >= baseline.panelCount)
        assertTrue("expected the search to never exceed baseline + 2 panels", result.panelCount <= baseline.panelCount + 2)
        // Self-consistency: a changed count must be explained; an unchanged count must be the
        // exact, untouched baseline object (not a copy with an unrelated field flipped).
        if (result.panelCount != baseline.panelCount) {
            assertTrue("expected the reason to explain the adjustment: ${result.reason}", result.reason.contains("Adjusted"))
        } else {
            assertEquals(baseline, result)
        }
    }

    @Test
    fun `no battery means no recharge target - baseline is returned completely unchanged`() {
        val baseline = baselineChoice(panelCount = 6, stringCounts = listOf(2, 2, 2))
        val result = SystemCalculator.recheckPanelCountForRecharge(
            baseline = baseline,
            inverter = growatt10k,
            chosenBattery = null,
            totalBatteryKwh = 0.0,
            batteryMaxChargeKw = null,
            batteryMaxDischargeKw = null,
            peakWatts = 8000.0,
            designDailyKwh = 150.0,
            input = noApplianceInputs()
        )
        assertEquals(baseline, result)
    }

    @Test
    fun `zero baseline panel count is returned unchanged rather than searching from zero`() {
        val baseline = baselineChoice(panelCount = 0, stringCounts = emptyList())
        val result = SystemCalculator.recheckPanelCountForRecharge(
            baseline = baseline,
            inverter = growatt10k,
            chosenBattery = srneEos10bBattery,
            totalBatteryKwh = 10.24,
            batteryMaxChargeKw = srneEos10bChargeKw,
            batteryMaxDischargeKw = srneEos10bDischargeKw,
            peakWatts = 8000.0,
            designDailyKwh = 60.0,
            input = noApplianceInputs()
        )
        assertEquals(baseline, result)
    }
}
