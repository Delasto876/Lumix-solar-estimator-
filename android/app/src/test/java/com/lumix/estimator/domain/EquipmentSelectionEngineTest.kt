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

    // ---- 1. Odd panel count is evaluated but even is preferred when a comparable option exists ----
    @Test
    fun `even panel count preferred over an odd one at similar headroom`() {
        // Generous electrical ceiling (nothing here should bind) so only the headroom/parity
        // scoring is under test.
        val result = EquipmentSelectionEngine.selectBestPanelConfigurationForLimits(
            requiredPvKw = 7.0, maxPvW = 50_000.0, maxPvV = 500.0, mpptTrackers = 4
        )
        assertTrue("expected an electrically valid pick", result.electricallyValid)
        assertEquals("expected an even panel count", 0, result.panelCount % 2)
    }

    // ---- 2. A real 10-20% headroom decision ----
    @Test
    fun `panel selection lands within the preferred 10-20 percent headroom band when achievable`() {
        val result = EquipmentSelectionEngine.selectBestPanelConfigurationForLimits(
            requiredPvKw = 7.0, maxPvW = 50_000.0, maxPvV = 500.0, mpptTrackers = 4
        )
        assertTrue(
            "expected 10-20%% headroom, got %.1f%%".format(result.oversizePercent),
            result.oversizePercent in 9.9..20.1
        )
    }

    // ---- 3. A case requiring more than 20% because of electrical constraints ----
    @Test
    fun `panel selection exceeds 20 percent headroom when the only available wattage is too coarse`() {
        // Only one wattage available and the requirement doesn't divide into it cleanly — 3 panels
        // is the smallest count that meets 1.5kW, and that alone is already 40% over. This also
        // guards the real bug this round caught: the engine must NOT jump to 4 panels (even, but
        // 86.7% over) just to get an even count — spec §3 explicitly forbids that trade.
        val result = EquipmentSelectionEngine.selectBestPanelConfigurationForLimits(
            requiredPvKw = 1.5, maxPvW = 50_000.0, maxPvV = 500.0, mpptTrackers = 1,
            wattages = listOf(700)
        )
        assertTrue("expected an electrically valid pick", result.electricallyValid)
        assertTrue(
            "expected oversizing beyond the 10-20%% band, got %.1f%%".format(result.oversizePercent),
            result.oversizePercent > 20.05
        )
        assertEquals("expected the closer 3-panel fit, not a farther even one", 3, result.panelCount)
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
    @Test
    fun `Vmp invalidates a panel configuration whose string is too short for the inverter's MPPT floor`() {
        // Small requirement + many MPPT trackers -> very short strings (1-2 panels) whose Vmp
        // never reaches a typical MPPT start voltage, even though Voc/power/current are generous.
        val result = EquipmentSelectionEngine.selectBestPanelConfigurationForLimits(
            requiredPvKw = 0.6, maxPvW = 100_000.0, maxPvV = 500.0, mpptTrackers = 3,
            wattages = listOf(595)
        )
        assertFalse("expected this configuration to be flagged electrically invalid", result.electricallyValid)
        assertTrue("expected the reason to explain the Vmp failure: ${result.reason}", result.reason.contains("Vmp", ignoreCase = true))
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
}
