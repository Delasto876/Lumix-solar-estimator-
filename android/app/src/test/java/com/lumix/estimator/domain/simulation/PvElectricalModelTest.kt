package com.lumix.estimator.domain.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A53: regression tests for the real per-MPPT PV electrical model that replaced a flat hardcoded
 * 380V constant (2026-08-13 "REBUILD THE PV VOLTAGE, MPPT, CURTAILMENT..." message). Covers a
 * representative subset of that message's 22 requested scenarios — the ones specific to what this
 * round actually changed (the voltage/MPPT model). The other requested scenarios (load-response,
 * battery-full curtailment, sudden-load-spike, midnight SOC, cloud smoothing, energy conservation,
 * quote-to-simulation sync) already have real coverage from prior rounds — see this round's README
 * note for exactly which ones and where. No Gradle/JVM in this sandbox to execute `./gradlew test`
 * (this project's own long-standing limitation); every number below was hand-traced with an
 * equivalent Python model first, the same discipline used for every engine test in this project.
 */
class PvElectricalModelTest {

    // ---- 1. PV voltage must not be hard-coded ----
    @Test
    fun `different panel counts produce different string Vmp`() {
        // A71: Deye SUN-6K's real MPPT floor is 150V, not the old flat 90V fallback — 6 panels no
        // longer splits 2-way here (3 x 45.76 = 137.28V would undervolt), so both counts below are
        // exercised as single-tracker strings (see the dedicated A71 consolidation test further
        // down) — still two genuinely different string lengths, so still a valid "not hardcoded"
        // demonstration.
        val threePanels = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 3, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG02LP2-US",
            cellTempC = 25.0, potentialPvKw = 1.5, realizedPvKw = 1.5
        )
        val sixPanels = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 6, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG02LP2-US",
            cellTempC = 25.0, potentialPvKw = 3.0, realizedPvKw = 3.0
        )
        val voltage3 = PvElectricalModel.blendedVoltage(threePanels)
        val voltage6 = PvElectricalModel.blendedVoltage(sixPanels)
        assertTrue("voltage must scale with string length, not be a fixed constant", voltage6 > voltage3)
        assertFalse("this exact bug: a flat 380V regardless of configuration", voltage3 == 380.0 || voltage6 == 380.0)
    }

    // ---- 2. 2-MPPT inverter shows two independent MPPT values ----
    @Test
    fun `2-MPPT inverter splits panels into two independent tracker strings`() {
        // A71: 8 panels (not 6 — see test 1's own note) is the smallest count on Deye SUN-6K's
        // real 150V MPPT floor that actually clears a 2-way split: 4 x 45.76 = 183.04V per tracker.
        val readouts = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 8, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG02LP2-US",
            cellTempC = 25.0, potentialPvKw = 4.0, realizedPvKw = 4.0
        )
        assertEquals(2, readouts.size)
        assertEquals(4, readouts[0].panelCount)
        assertEquals(4, readouts[1].panelCount)
        assertEquals(183.04, readouts[0].vmpV, 0.5)
        assertEquals(183.04, readouts[1].vmpV, 0.5)
    }

    // ---- A71 (spec Phase 6 — real per-model MPPT floor replaces a flat 90V constant) ----
    @Test
    fun `a string too short for the real inverter's MPPT floor consolidates onto one tracker, not the flat 90V fallback`() {
        // Before A71: 6 panels x 45.76V Vmp = 137.28V per string cleared the old flat 90V floor
        // this file used to check against, so this scenario split 3+3 across both trackers. Deye
        // SUN-6K's REAL MPPT floor (EquipmentSpecs.InverterSpec.mpptVoltageMinV) is 150V — 137.28V
        // would actually undervolt this real inverter's tracker, so MpptStringPlanner now correctly
        // consolidates the whole array onto a single tracker instead, leaving the second inactive —
        // exactly the "MPPT1=X, MPPT2=unused" case the spec itself describes, now driven by this
        // inverter's own real datasheet floor rather than a one-size-fits-all guess.
        val readouts = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 6, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG02LP2-US",
            cellTempC = 25.0, potentialPvKw = 3.0, realizedPvKw = 3.0
        )
        assertEquals(2, readouts.size)
        assertEquals(6, readouts[0].panelCount)
        assertEquals(274.56, readouts[0].vmpV, 0.5)
        assertTrue(readouts[0].isActive)
        assertEquals(0, readouts[1].panelCount)
        assertFalse("the unused second tracker must read inactive, not a phantom voltage", readouts[1].isActive)
    }

    // ---- 3. 3-MPPT inverter shows three independent MPPT values ----
    @Test
    fun `3-MPPT inverter splits panels into three independent tracker strings`() {
        val readouts = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 12, inverterKw = 12.0, inverterNameHint = "LuxPower LXP-LB-US 12K",
            cellTempC = 25.0, potentialPvKw = 6.0, realizedPvKw = 6.0
        )
        assertEquals(3, readouts.size)
        readouts.forEach { assertEquals(4, it.panelCount) }
        assertEquals(183.04, readouts[0].vmpV, 0.5)
    }

    // ---- 4. Uneven panel counts split as evenly as possible, not silently dropped ----
    @Test
    fun `uneven panel count distributes the remainder across the first trackers`() {
        val readouts = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 13, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG02LP2-US",
            cellTempC = 25.0, potentialPvKw = 3.0, realizedPvKw = 3.0
        )
        assertEquals(2, readouts.size)
        assertEquals(13, readouts.sumOf { it.panelCount })
        assertEquals(7, readouts[0].panelCount)
        assertEquals(6, readouts[1].panelCount)
    }

    // ---- 5. Higher cell temperature lowers Vmp/Voc (physically directional) ----
    @Test
    fun `higher cell temperature lowers string voltage`() {
        val cool = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 6, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG02LP2-US",
            cellTempC = 25.0, potentialPvKw = 3.0, realizedPvKw = 3.0
        )
        val hot = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 6, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG02LP2-US",
            cellTempC = 45.0, potentialPvKw = 3.0, realizedPvKw = 3.0
        )
        assertTrue(
            "hotter cells should produce lower Vmp, got cool=${cool[0].vmpV} hot=${hot[0].vmpV}",
            hot[0].vmpV < cool[0].vmpV
        )
        // A71: this scenario's 6 panels now consolidate onto one tracker (Deye 6K's real 150V
        // floor — see the dedicated consolidation test above), so the string is 6 panels long, not
        // 3 — 45.76 x 6 x (1 + (-0.29/100) x (45-25)) = 258.64V, not the old 3-panel-string 129.32V.
        assertEquals(258.64, hot[0].vmpV, 0.5)
    }

    // ---- 6. PV output is zero at night ----
    @Test
    fun `zero potential production means zero voltage - night`() {
        val readouts = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 6, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG02LP2-US",
            cellTempC = 25.0, potentialPvKw = 0.0, realizedPvKw = 0.0
        )
        readouts.forEach {
            assertFalse(it.isActive)
            assertEquals(0.0, it.vmpV, 0.001)
            assertEquals(0.0, it.powerKw, 0.001)
        }
    }

    // ---- 7. PV voltage during curtailment: stays near operating Vmp, does NOT track delivered power ----
    @Test
    fun `voltage does not collapse when downstream power is curtailed`() {
        // Same potential production and cell temperature, wildly different REALIZED (delivered)
        // power — representing full sun + full battery + tiny load (heavy curtailment) vs. full
        // sun + full utilization. Voltage must be identical in both; only power should differ.
        val heavilyCurtailed = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 6, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG02LP2-US",
            cellTempC = 35.0, potentialPvKw = 3.0, realizedPvKw = 0.1
        )
        val fullyUtilized = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 6, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG02LP2-US",
            cellTempC = 35.0, potentialPvKw = 3.0, realizedPvKw = 3.0
        )
        assertEquals(
            "voltage must be the array's operating Vmp regardless of how much power is actually used",
            fullyUtilized[0].vmpV, heavilyCurtailed[0].vmpV, 0.001
        )
        assertTrue(heavilyCurtailed[0].powerKw < fullyUtilized[0].powerKw)
    }

    // ---- 8. Series current is not multiplied by panel count ----
    @Test
    fun `string Isc and Imp are the panel's own values, never multiplied by panel count`() {
        val sixPanels = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 6, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG02LP2-US",
            cellTempC = 25.0, potentialPvKw = 3.0, realizedPvKw = 3.0
        )
        val twelvePanels = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 12, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG02LP2-US",
            cellTempC = 25.0, potentialPvKw = 6.0, realizedPvKw = 6.0
        )
        // 615W DAS panel's real Isc is 14.11A regardless of how many panels are in the string.
        assertEquals(14.11, sixPanels[0].iscA, 0.01)
        assertEquals(14.11, twelvePanels[0].iscA, 0.01)
        assertTrue("current must never scale with panel count in series", sixPanels[0].iscA < 20.0 && twelvePanels[0].iscA < 20.0)
    }

    // ---- 9. Blended headline voltage is a genuine panel-count-weighted average across UNEQUAL active trackers ----
    @Test
    fun `blended voltage averages active trackers weighted by panel count`() {
        // A71: reuses test 4's exact 13-panel/Deye-6K/7+6-split scenario (unaffected by the real
        // 150V floor fix — both strings already clear it) specifically because it has two ACTIVE
        // trackers of unequal length, which a 6-panel scenario no longer does (see the dedicated
        // consolidation test above) — this is what actually exercises weighted averaging rather
        // than trivially returning one tracker's own voltage.
        // Per-tracker Vmp: 45.76 x 7 = 320.32V, 45.76 x 6 = 274.56V.
        // Blended = (320.32 x 7 + 274.56 x 6) / 13 = 299.2V.
        val readouts = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 13, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG02LP2-US",
            cellTempC = 25.0, potentialPvKw = 6.5, realizedPvKw = 6.5
        )
        assertEquals(299.2, PvElectricalModel.blendedVoltage(readouts), 0.5)
    }

    // ---- No panels configured returns an empty breakdown rather than throwing ----
    @Test
    fun `zero panel count returns an empty breakdown`() {
        val readouts = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 0, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG02LP2-US",
            cellTempC = 25.0, potentialPvKw = 0.0, realizedPvKw = 0.0
        )
        assertTrue(readouts.isEmpty())
        assertEquals(0.0, PvElectricalModel.blendedVoltage(readouts), 0.001)
    }
}
