package com.lumix.estimator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A50: the acceptance tests the "CRITICAL UPDATE — LOAD-BASED EQUIPMENT SELECTION LOGIC" message
 * explicitly asked for — one JVM unit test per requested scenario, plus a couple of structural
 * checks (single-vs-multi inverter, never-mixed battery banks) that hold by construction but are
 * worth asserting directly. These run as plain JVM tests (`./gradlew test`) — no Android framework
 * dependency in [EquipmentSelectionEngine] or anything it touches, so no emulator/device is needed.
 * This sandbox has no Gradle/JVM available to actually execute them (the project's own long-running
 * limitation — see README's build-verification notes); they're written to be run, not claimed as run.
 */
class EquipmentSelectionEngineTest {

    // ---- 1. A63: smallest electrically valid array wins — no headroom-band target, no evenness bonus ----
    // A50 originally scored toward a preferred 10-20% headroom band with an even-panel-count
    // tiebreak. A63 (spec §24-28's "SMALLEST PRACTICAL ARRAY... NOT always use the maximum number
    // of panels") replaced that scoring entirely — the search now returns the smallest
    // electrically valid array across every catalog wattage, full stop, deferring the "does it
    // actually recharge the battery on time" question to SystemCalculator's own follow-up
    // simulation (A63's other half — see SystemCalculatorRechargeAwareSizingTest.kt).
    @Test
    fun `the smallest electrically valid array wins across every wattage, not a percentage headroom target`() {
        // Among the default catalog's 5 wattages, 700W x 10 lands exactly on the 7.0kW requirement
        // (0% oversize, hand-traced: MpptStringPlanner falls back from 4 to 3 trackers here, since
        // a full 4-way split would undervolt two of the four strings — [4, 3, 3], shortest string
        // 3 x 40.42V = 121.3V, clears the 90V floor). Every other wattage's own smallest valid
        // count starts at a higher kW (595W: 7.14kW, 615W: 7.38kW, 620W: 7.44kW, 720W: 7.20kW), so
        // 700W x 10 is the global minimum regardless of panel-count parity.
        val result = EquipmentSelectionEngine.selectBestPanelConfigurationForLimits(
            requiredPvKw = 7.0, maxPvW = 50_000.0, maxPvV = 500.0, mpptTrackers = 4
        )
        assertTrue("expected an electrically valid pick: ${result.reason}", result.electricallyValid)
        assertEquals(700, result.panelWatts)
        assertEquals(10, result.panelCount)
        assertEquals(listOf(4, 3, 3), result.stringCounts)
        assertEquals(0.0, result.oversizePercent, 0.5)
    }

    // ---- 2. A63/A65: the preferred voltage margin outranks "smallest array" when they disagree ----
    @Test
    fun `a margin-compliant but larger array beats a smaller array that violates the preferred voltage margin`() {
        // 595W panel (Vmp 44.6V, Voc 52.6V), 2 MPPT trackers, 90V floor, 225V hard ceiling (A65:
        // widened from 250V to 225V so this scenario still demonstrates the margin tier under the
        // updated 0.95 fraction — see PREFERRED_VOC_MARGIN_FRACTION's own doc for why the fraction
        // changed). Hand-traced per count (Python port of MpptStringPlanner + the Voc check):
        //   4 panels -> [4] (2+2 would undervolt at 89.2V) -> Voc 219.9V: hard-valid (<=225V),
        //     OUTSIDE the 213.75V (95%) preferred margin — the smaller array (2.38kW) but the
        //     worse one.
        //   5 panels -> [5] -> Voc 274.8V: exceeds the 225V hard ceiling entirely — invalid.
        //   6 panels -> [3, 3] (now a 2-way split clears 90V: 3 x 44.6 = 133.8V) -> Voc 164.9V:
        //     hard-valid AND inside the 213.75V margin — a bigger array (3.57kW) but the safer one.
        //   7/8 panels -> longest string still 4 panels (same as the 4-panel case) -> Voc 219.9V:
        //     hard-valid but still outside the margin, same as 4 panels.
        // If "smallest array" were still the only criterion, 4 panels would win. It doesn't.
        val result = EquipmentSelectionEngine.selectBestPanelConfigurationForLimits(
            requiredPvKw = 2.0, maxPvW = 50_000.0, maxPvV = 225.0, mpptTrackers = 2,
            wattages = listOf(595)
        )
        assertTrue("expected an electrically valid pick: ${result.reason}", result.electricallyValid)
        assertEquals(6, result.panelCount)
        assertEquals(listOf(3, 3), result.stringCounts)
        assertTrue("expected the winning candidate to be within the preferred margin", result.withinPreferredVoltageMargin)
    }

    // ---- 3. A case requiring significant oversizing because of electrical constraints ----
    @Test
    fun `panel selection still returns the smallest valid array when that means significant oversizing`() {
        // Only one wattage available and the requirement doesn't divide into it cleanly — 3 panels
        // is the smallest count that meets 1.5kW, and that alone is already 40% over. The engine
        // must NOT jump to a larger count for any reason (evenness no longer even applies) when
        // the smaller, valid count is available.
        val result = EquipmentSelectionEngine.selectBestPanelConfigurationForLimits(
            requiredPvKw = 1.5, maxPvW = 50_000.0, maxPvV = 500.0, mpptTrackers = 1,
            wattages = listOf(700)
        )
        assertTrue("expected an electrically valid pick", result.electricallyValid)
        assertTrue(
            "expected oversizing beyond 20%%, got %.1f%%".format(result.oversizePercent),
            result.oversizePercent > 20.05
        )
        assertEquals("expected the smallest valid count, not a larger one", 3, result.panelCount)
    }

    // ---- 4. A single larger inverter is preferred over multiple smaller ones ----
    @Test
    fun `inverter selection returns one appropriately sized unit, never a pair of smaller ones`() {
        val choice = EquipmentSelectionEngine.selectBestInverter(
            requiredContinuousKw = 11.0, requiredSurgeKw = 11.0, pool = Catalog.hybridInverters
        )
        // The return type is a single InverterOption by construction — there is no code path in
        // this app that can return "2 x 6kW." This asserts the single unit chosen is the smallest
        // real tier that actually covers 11kW (12kW; two 6kW units were never on the table).
        assertEquals(12.0, choice.option.kw, 0.001)
    }

    // ---- 5. A surge requirement forces a larger inverter than the continuous load alone would ----
    @Test
    fun `worst-case surge forces a larger inverter even when continuous load alone would not`() {
        // 6kW alone covers 3.5kW continuous easily, and even its 2x surge tolerance (12kW) would
        // normally be plenty — until the worst-case surge figure itself is higher than that.
        val choice = EquipmentSelectionEngine.selectBestInverter(
            requiredContinuousKw = 3.5, requiredSurgeKw = 13.0, pool = Catalog.hybridInverters
        )
        assertEquals(8.0, choice.option.kw, 0.001)
    }

    // ---- 6. A case requiring multiple identical batteries ----
    @Test
    fun `battery selection uses multiple identical modules when one is not enough`() {
        val choice = EquipmentSelectionEngine.selectBestHybridBattery(
            requiredUsableKwh = 22.0, requiredDischargeKw = 1.0, inverterCeilingKw = 20.0
        )
        assertTrue("expected more than one module", choice.moduleCount > 1)
        // totalKwh must divide evenly by the single tier's nominal kWh — proof every module is the
        // same tier (see next test for the direct mixed-capacity check).
        assertEquals(0.0, choice.totalKwh % choice.option!!.kwh, 0.001)
    }

    // ---- 7. A case where mixed battery capacities would otherwise be selected ----
    @Test
    fun `battery bank never mixes capacities across a range of requirements`() {
        // Sweep a range of usable-energy requirements that would tempt a naive greedy algorithm
        // (largest-first, then fill the remainder with a smaller tier) into mixing tiers — e.g.
        // "1 x 15kWh + 1 x 5kWh" for something just over one 15kWh module's usable capacity.
        for (requiredKwh in listOf(2.0, 6.0, 9.0, 13.0, 16.0, 22.0, 30.0, 41.0)) {
            val choice = EquipmentSelectionEngine.selectBestHybridBattery(
                requiredUsableKwh = requiredKwh, requiredDischargeKw = 1.0, inverterCeilingKw = 20.0
            )
            val tierKwh = choice.option!!.kwh
            assertTrue(
                "tier $tierKwh kWh x ${choice.moduleCount} should reconstruct totalKwh ${choice.totalKwh} for requirement $requiredKwh",
                kotlin.math.abs(choice.totalKwh - tierKwh * choice.moduleCount) < 0.001
            )
        }
    }

    // ---- 8. A case where required discharge power drives module count above what energy alone needs ----
    @Test
    fun `battery module count is driven up by discharge power, not just usable energy`() {
        // 6 kWh usable alone would fit in a single 5kWh-tier module (~4.8kWh usable); a 15kW
        // discharge-power requirement cannot be met by one ~5.1kW-capable module, so power should
        // be the binding constraint pushing the module count to 3.
        val choice = EquipmentSelectionEngine.selectBestHybridBattery(
            requiredUsableKwh = 6.0, requiredDischargeKw = 15.0, inverterCeilingKw = 20.0
        )
        assertTrue(
            "expected discharge power to require more modules than usable energy alone (got ${choice.moduleCount})",
            choice.moduleCount >= 3
        )
        assertTrue(
            "expected the resulting bank to actually cover the discharge requirement, got %.2f kW".format(choice.totalMaxDischargeKw),
            choice.totalMaxDischargeKw >= 15.0 - 0.05
        )
    }

    // ---- 9. Voc makes an otherwise attractive panel configuration invalid ----
    @Test
    fun `Voc invalidates a panel configuration whose string exceeds the inverter's max PV voltage`() {
        // A single-tracker inverter with a very low max PV voltage — any panel count that actually
        // meets the PV requirement pushes the (single) string's cold-corrected Voc well past it.
        val result = EquipmentSelectionEngine.selectBestPanelConfigurationForLimits(
            requiredPvKw = 5.0, maxPvW = 100_000.0, maxPvV = 250.0, mpptTrackers = 1
        )
        assertFalse("expected this configuration to be flagged electrically invalid", result.electricallyValid)
        assertTrue("expected the reason to explain the Voc failure: ${result.reason}", result.reason.contains("Voc", ignoreCase = true))
    }

    // ---- 10. Vmp makes a string invalid (too short to reach the inverter's minimum MPPT operating voltage) ----
    // A62 note: this scenario used to be driven through the panel-COUNT search
    // (selectBestPanelConfigurationForLimits), relying on the old always-split-across-every-
    // tracker rule to force short, undervolted strings for a small requirement. A62 replaced that
    // rule with MpptStringPlanner, which now correctly falls back to fewer/longer strings (even a
    // single string on one tracker) whenever that's what it takes to clear the Vmp floor — so the
    // search now finds a genuinely valid single-tracker consolidation for any count above ~2-3
    // panels, and the old scenario no longer produces an invalid result (correctly — that was
    // exactly the "MPPT1=10, MPPT2=unused" case the new spec asked for). Genuine Vmp invalidity —
    // a panel count too small to reach the floor even fully consolidated onto one string — is
    // still real and still checked; testing it directly against one specific count (rather than
    // through the search, which would just avoid the invalid count entirely) is what actually
    // exercises that floor.
    @Test
    fun `Vmp invalidates a single panel whose string can't reach the inverter's minimum MPPT floor even fully consolidated`() {
        val result = EquipmentSelectionEngine.checkPanelInverterCompatibilityForLimits(
            panelWatts = 595, panelCount = 1, maxPvW = 100_000.0, maxPvV = 500.0, mpptTrackers = 3
        )
        // Real 595W panel Vmp is 44.6V (EquipmentSpecs) — even the best possible topology for one
        // panel (all of it on a single tracker, stringCounts = [1]) can't reach a 90V MPPT floor.
        assertEquals(listOf(1), result.stringCounts)
        assertFalse("expected this configuration to be flagged electrically invalid", result.valid)
        assertTrue("expected the reason to explain the Vmp failure: ${result.notes}", result.notes.any { it.contains("Vmp", ignoreCase = true) })
    }

    // ---- A62: MPPT string allocation is genuinely flexible, not always an even split ----

    @Test
    fun `13 panels on a 2-MPPT inverter split 7 and 6, matching the spec's own worked example`() {
        // Real 595W panel (Vmp 44.6V) comfortably clears the 90V floor even split 6-7, so full
        // 2-tracker utilization is expected here — no fallback needed.
        val result = EquipmentSelectionEngine.checkPanelInverterCompatibilityForLimits(
            panelWatts = 595, panelCount = 13, maxPvW = 100_000.0, maxPvV = 500.0, mpptTrackers = 2
        )
        assertTrue("expected this configuration to be valid: ${result.notes}", result.valid)
        assertEquals(listOf(7, 6), result.stringCounts)
        assertEquals(2, result.trackersUsed)
    }

    @Test
    fun `10 panels use a single MPPT string when splitting 5 and 5 would undervolt, leaving the second tracker unused`() {
        // A synthetic low panel Vmp (via maxPvV/mpptTrackers alone can't force this — Vmp comes
        // from EquipmentSpecs) isn't available through the public search API, so this is exercised
        // at the MpptStringPlanner level directly (the exact function EquipmentSelectionEngine and
        // PvElectricalModel both now share) — the spec's own worked example: "MPPT1=10, MPPT2=
        // unused... IF the 5-panel strings would operate too close to or below the inverter's MPPT
        // minimum voltage."
        val counts = MpptStringPlanner.planStrings(panelCount = 10, maxTrackers = 2, vmpPerPanel = 12.0, minVmpPerString = 90.0)
        assertEquals(listOf(10), counts)
    }

    @Test
    fun `10 panels split 5 and 5 across both trackers when that split is not undervolted`() {
        // Same panel count and tracker count as above, but with a real-scale panel Vmp (595W's
        // 44.6V) where 5 x 44.6 = 223V comfortably clears the 90V floor — full 2-tracker
        // utilization is preferred whenever it's valid, per the spec's own ranking ("GOOD MPPT
        // UTILIZATION").
        val counts = MpptStringPlanner.planStrings(panelCount = 10, maxTrackers = 2, vmpPerPanel = 44.6, minVmpPerString = 90.0)
        assertEquals(listOf(5, 5), counts)
    }

    // ---- A62/A65: the preferred MPPT voltage design margin is a distinct, softer signal from the hard Voc ceiling ----
    // A65: the margin fraction changed from 0.85 to 0.95 (installer's explicit choice between the
    // two spec messages' conflicting worked examples — see PREFERRED_VOC_MARGIN_FRACTION's own
    // doc) — both scenarios below happen to land on the same side of the threshold either way
    // (384.8V/329.8V vs. a 380V line instead of a 340V one), so only the comments/messages needed
    // updating, not the panel counts.

    @Test
    fun `a string within the hard voltage ceiling but outside the preferred design margin is still valid, just flagged`() {
        // Real 595W panel Voc 52.6V, cold-corrected: 7 x 52.6 x 1.045 = 384.77V. maxPvV=400V, so
        // this is comfortably under the hard ceiling (vocOk) but past the preferred design target
        // of 400 x 0.95 = 380V.
        val result = EquipmentSelectionEngine.checkPanelInverterCompatibilityForLimits(
            panelWatts = 595, panelCount = 7, maxPvW = 50_000.0, maxPvV = 400.0, mpptTrackers = 1
        )
        assertTrue("expected this configuration to still be electrically valid: ${result.notes}", result.valid)
        assertFalse("expected 384.8V to fall outside the preferred 380V (95%) design margin", result.withinPreferredVoltageMargin)
        assertTrue(
            "expected the notes to explain the margin, even though the string is still valid: ${result.notes}",
            result.notes.any { it.contains("margin", ignoreCase = true) }
        )
    }

    @Test
    fun `a string inside both the hard ceiling and the preferred design margin is flagged as within margin`() {
        // Same inverter limit as above, one fewer panel: 6 x 52.6 x 1.045 = 329.8V, under both the
        // 400V hard ceiling and the 380V (95%) preferred target.
        val result = EquipmentSelectionEngine.checkPanelInverterCompatibilityForLimits(
            panelWatts = 595, panelCount = 6, maxPvW = 50_000.0, maxPvV = 400.0, mpptTrackers = 1
        )
        assertTrue("expected this configuration to be electrically valid: ${result.notes}", result.valid)
        assertTrue("expected 329.8V to fall inside the preferred 380V (95%) design margin", result.withinPreferredVoltageMargin)
    }

    // ---- 11. Isc/Imp exceeds inverter limits ----
    @Test
    fun `Isc invalidates a panel configuration whose current exceeds the inverter's implied max PV current`() {
        // maxPvW and maxPvV both large (so neither the power nor the Voc/Vmp checks bind) but with
        // a ratio that implies a tiny max PV current — well under any real panel's Isc.
        val result = EquipmentSelectionEngine.selectBestPanelConfigurationForLimits(
            requiredPvKw = 3.0, maxPvW = 50_000.0, maxPvV = 50_000.0, mpptTrackers = 2
        )
        assertFalse("expected this configuration to be flagged electrically invalid", result.electricallyValid)
        assertTrue("expected the reason to explain the Isc failure: ${result.reason}", result.reason.contains("Isc", ignoreCase = true))
    }

    // ---- No PV/battery requirement short-circuits cleanly (regression guard, not from the spec list) ----
    @Test
    fun `zero requirements return an empty, valid selection instead of searching`() {
        val panels = EquipmentSelectionEngine.selectBestPanelConfigurationForLimits(0.0, 10_000.0, 500.0, 2)
        assertEquals(0, panels.panelCount)
        assertTrue(panels.electricallyValid)

        val battery = EquipmentSelectionEngine.selectBestHybridBattery(0.0, 0.0, 6.0)
        assertEquals(0, battery.moduleCount)
    }

    // ---- A52 regression: the reported "false PV input exceeded" bug ----
    // Scenario 10 (2026-08-13 "FIX PV INPUT VALIDATION BUG" message) — against the REAL, live
    // EquipmentSpecs data (not synthetic limits), via the same checkPanelInverterCompatibility
    // function StepSystemReview.kt now calls for every mode, including MANUAL.

    @Test
    fun `scenario 10 - 6 x 615W on Deye SUN-6K-SG02LP2-US is PV-compatible, not exceeded`() {
        val result = EquipmentSelectionEngine.checkPanelInverterCompatibility(
            panelWatts = 615, panelCount = 6, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG02LP2-US"
        )
        // Phase 41 (inverter datasheet compendium): Deye 6K's real max PV input power was corrected
        // 7.8kW -> 9.0kW against a real SUN-6K-SG02LP2-US-AM2 datasheet — 3.69kW is comfortably
        // under either figure, so this scenario's own outcome is unaffected, only the message text.
        assertTrue("3.69 kW array should be well under Deye 6K's real 9.0 kW max PV input", result.powerOk)
        assertTrue("expected the full configuration to be valid: ${result.notes}", result.valid)
        assertEquals(3.69, result.arrayKw, 0.01)
        // Series topology: voltage adds (3 panels/string x 2 MPPT), current does NOT multiply by
        // panel count — this is the exact bug category the report warned about ("panelIsc * panelCount").
        assertEquals(14.11, result.stringIscA, 0.01)
        assertEquals(13.44, result.stringImpA, 0.01)
        assertTrue("string Isc must never be panelCount x panel Isc", result.stringIscA < 20.0)
    }

    @Test
    fun `scenario 11 - 16 x 615W on Deye SUN-6K-SG02LP2-US exceeds real max PV input power`() {
        // Phase 41: bumped from 14 to 16 panels — Deye 6K's real max PV input power was corrected
        // 7.8kW -> 9.0kW (a real SUN-6K-SG02LP2-US-AM2 datasheet states 9,000W, not the prior
        // estimate), so 14 x 615W (8.61kW) no longer exceeds it; 16 x 615W (9.84kW) still does.
        val result = EquipmentSelectionEngine.checkPanelInverterCompatibility(
            panelWatts = 615, panelCount = 16, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG02LP2-US"
        )
        assertEquals(9.84, result.arrayKw, 0.01)
        assertFalse("9.84 kW array should exceed Deye 6K's real 9.0 kW max PV input", result.powerOk)
        assertFalse(result.valid)
    }

    // ---- A71 (spec Phase 6 — "fix inverter/MPPT/string calculations"): real per-model MPPT/current data ----

    @Test
    fun `the effective MPPT floor is the HIGHER of a real tracking floor and a real startup threshold - LuxPower 6K`() {
        // LuxPower GEN-LB-US 6K's real datasheet figures (EquipmentSpecs): mpptVoltageMinV=120V
        // (continuous tracking floor) but startupVoltageV=140V (higher — the unit needs more
        // voltage just to wake its MPPT algorithm up than it needs to keep tracking once running).
        // 3 x 595W (Vmp 44.6V) consolidates onto a single string (MpptStringPlanner: a 2-way [2,1]
        // split would undervolt either threshold) at 3 x 44.6 = 133.8V — clears the 120V tracking
        // floor alone (the old, incomplete check) but NOT the real 140V binding floor this scenario
        // exists to prove is now actually enforced.
        val result = EquipmentSelectionEngine.checkPanelInverterCompatibility(
            panelWatts = 595, panelCount = 3, inverterKw = 6.0, inverterNameHint = "LuxPower GEN-LB-US 6K"
        )
        assertEquals(listOf(3), result.stringCounts)
        assertFalse(
            "expected 133.8V to fail the real 140V startup-driven floor, not just clear the 120V tracking floor",
            result.vmpOk
        )
        assertFalse(result.valid)
    }

    @Test
    fun `real per-model MPPT floor (150V) replaces the old flat 90V fallback for Deye SUN-6K`() {
        // Direct EquipmentSelectionEngine-level proof of the A71 fix (PvElectricalModelTest has the
        // simulation-display-side version of the same scenario): under the OLD flat 90V floor,
        // 6 x 615W (Vmp 45.76V) split 3+3 across Deye 6K's 2 trackers (3 x 45.76 = 137.28V, clears
        // 90V). Deye SUN-6K-SG02LP2-US's REAL MPPT floor (EquipmentSpecs) is 150V — 137.28V would
        // actually undervolt it, so the real per-model floor now correctly consolidates the whole
        // array onto a single tracker instead.
        val result = EquipmentSelectionEngine.checkPanelInverterCompatibility(
            panelWatts = 615, panelCount = 6, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG02LP2-US"
        )
        assertTrue("expected this configuration to still be electrically valid: ${result.notes}", result.valid)
        assertEquals(listOf(6), result.stringCounts)
    }

    @Test
    fun `Vmp upper bound invalidates a string within the hard Voc ceiling but above the real MPPT tracking-range ceiling`() {
        // 5 x 595W (Vmp 44.6V) on a single tracker = 223V — comfortably under a 500V absolute
        // maxPvV (vocOk: 52.6 x 5 x 1.045 = 274.8V) but above a synthetic 200V MPPT tracking-range
        // ceiling, the genuinely separate, lower figure this check exists to catch (see
        // EquipmentSelectionEngine.PanelCompatibilityResult.vmpUpperOk's own doc). maxPvW is large
        // enough that neither power nor the implied-current fallback binds, isolating this one check.
        val result = EquipmentSelectionEngine.checkPanelInverterCompatibilityForLimits(
            panelWatts = 595, panelCount = 5, maxPvW = 50_000.0, maxPvV = 500.0, mpptTrackers = 1,
            mpptTrackingMaxV = 200.0
        )
        assertTrue("expected the hard Voc ceiling to still be satisfied", result.vocOk)
        assertFalse("expected the real MPPT tracking-range ceiling to be violated", result.vmpUpperOk)
        assertFalse(result.valid)
        assertTrue(
            "expected the reason to explain the MPPT tracking-range failure: ${result.notes}",
            result.notes.any { it.contains("tracking-range", ignoreCase = true) }
        )
    }

    @Test
    fun `continuous operating current (Imp) invalidates a string whose short-circuit current still clears the real limit`() {
        // 2 x 615W (Imp 13.44A, Isc 14.11A) on a single tracker. A synthetic 10.0A real continuous
        // max input current is below the panel's own Imp — but a synthetic 20.0A real max
        // short-circuit current is well above its Isc, so only the continuous-current check should
        // fail, proving impOk and iscOk are genuinely independent real-figure checks, not the same
        // number doing both jobs.
        val result = EquipmentSelectionEngine.checkPanelInverterCompatibilityForLimits(
            panelWatts = 615, panelCount = 2, maxPvW = 50_000.0, maxPvV = 500.0, mpptTrackers = 1,
            maxContinuousCurrentPerMpptA = 10.0, maxShortCircuitCurrentPerMpptA = 20.0
        )
        assertTrue("expected Isc to still clear the real short-circuit limit", result.iscOk)
        assertFalse("expected Imp to violate the real continuous current limit", result.impOk)
        assertFalse(result.valid)
        assertTrue(
            "expected the reason to explain the Imp failure: ${result.notes}",
            result.notes.any { it.contains("Imp", ignoreCase = true) }
        )
    }

    // ---- A72 (spec Phase 7 — "fix battery calculations"): real battery/inverter voltage-window compatibility ----

    @Test
    fun `battery voltage compatibility passes when the battery's window sits fully inside the inverter's`() {
        assertTrue(
            EquipmentSelectionEngine.batteryVoltageCompatibleForLimits(
                batteryMinV = 44.8, batteryMaxV = 58.4, inverterMinV = 40.0, inverterMaxV = 60.0
            )
        )
    }

    @Test
    fun `battery voltage compatibility fails when the battery's own max exceeds the inverter's accepted max`() {
        assertFalse(
            EquipmentSelectionEngine.batteryVoltageCompatibleForLimits(
                batteryMinV = 44.8, batteryMaxV = 65.0, inverterMinV = 40.0, inverterMaxV = 60.0
            )
        )
    }

    @Test
    fun `battery voltage compatibility fails when the battery's own min is below the inverter's accepted min`() {
        assertFalse(
            EquipmentSelectionEngine.batteryVoltageCompatibleForLimits(
                batteryMinV = 35.0, batteryMaxV = 58.4, inverterMinV = 40.0, inverterMaxV = 60.0
            )
        )
    }

    @Test
    fun `battery voltage compatibility defaults to true when either side has no confirmed data`() {
        assertTrue(EquipmentSelectionEngine.batteryVoltageCompatibleForLimits(null, 58.4, 40.0, 60.0))
        assertTrue(EquipmentSelectionEngine.batteryVoltageCompatibleForLimits(44.8, 58.4, null, 60.0))
    }

    @Test
    fun `real SRNE SR-EOS10B on a real Deye SUN-6K is voltage-compatible`() {
        // SRNE SR-EOS10B: 44.8-58.4V. Deye SUN-6K-SG02LP2-US's real accepted battery-port range:
        // 40-60V (EquipmentSpecs). The 44.8-58.4V window sits fully inside it.
        val result = EquipmentSelectionEngine.checkBatteryVoltageCompatibility(
            batteryName = "10 kWh LiFePO4 (SRNE SR-EOS10B)", inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG02LP2-US"
        )
        assertTrue("expected the real SRNE/Deye combination to be voltage-compatible", result.ok)
        assertEquals(44.8, result.batteryMinV!!, 0.01)
        assertEquals(58.4, result.batteryMaxV!!, 0.01)
        assertEquals(40.0, result.inverterMinV!!, 0.01)
        assertEquals(60.0, result.inverterMaxV!!, 0.01)
    }
}
