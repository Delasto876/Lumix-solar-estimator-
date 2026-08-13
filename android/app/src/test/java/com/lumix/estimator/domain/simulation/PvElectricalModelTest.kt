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
        val sixPanels = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 6, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG01LP1-US",
            cellTempC = 25.0, potentialPvKw = 3.0, realizedPvKw = 3.0
        )
        val twelvePanels = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 12, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG01LP1-US",
            cellTempC = 25.0, potentialPvKw = 6.0, realizedPvKw = 6.0
        )
        val voltage6 = PvElectricalModel.blendedVoltage(sixPanels)
        val voltage12 = PvElectricalModel.blendedVoltage(twelvePanels)
        assertTrue("voltage must scale with string length, not be a fixed constant", voltage12 > voltage6)
        assertFalse("this exact bug: a flat 380V regardless of configuration", voltage6 == 380.0 || voltage12 == 380.0)
    }

    // ---- 2. 2-MPPT inverter shows two independent MPPT values ----
    @Test
    fun `2-MPPT inverter splits panels into two independent tracker strings`() {
        val readouts = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 6, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG01LP1-US",
            cellTempC = 25.0, potentialPvKw = 3.0, realizedPvKw = 3.0
        )
        assertEquals(2, readouts.size)
        assertEquals(3, readouts[0].panelCount)
        assertEquals(3, readouts[1].panelCount)
        assertEquals(137.28, readouts[0].vmpV, 0.5)
        assertEquals(137.28, readouts[1].vmpV, 0.5)
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
            panelWatts = 615, panelCount = 13, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG01LP1-US",
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
            panelWatts = 615, panelCount = 6, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG01LP1-US",
            cellTempC = 25.0, potentialPvKw = 3.0, realizedPvKw = 3.0
        )
        val hot = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 6, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG01LP1-US",
            cellTempC = 45.0, potentialPvKw = 3.0, realizedPvKw = 3.0
        )
        assertTrue(
            "hotter cells should produce lower Vmp, got cool=${cool[0].vmpV} hot=${hot[0].vmpV}",
            hot[0].vmpV < cool[0].vmpV
        )
        assertEquals(129.32, hot[0].vmpV, 0.5)
    }

    // ---- 6. PV output is zero at night ----
    @Test
    fun `zero potential production means zero voltage - night`() {
        val readouts = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 6, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG01LP1-US",
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
            panelWatts = 615, panelCount = 6, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG01LP1-US",
            cellTempC = 35.0, potentialPvKw = 3.0, realizedPvKw = 0.1
        )
        val fullyUtilized = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 6, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG01LP1-US",
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
            panelWatts = 615, panelCount = 6, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG01LP1-US",
            cellTempC = 25.0, potentialPvKw = 3.0, realizedPvKw = 3.0
        )
        val twelvePanels = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 12, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG01LP1-US",
            cellTempC = 25.0, potentialPvKw = 6.0, realizedPvKw = 6.0
        )
        // 615W DAS panel's real Isc is 14.11A regardless of how many panels are in the string.
        assertEquals(14.11, sixPanels[0].iscA, 0.01)
        assertEquals(14.11, twelvePanels[0].iscA, 0.01)
        assertTrue("current must never scale with panel count in series", sixPanels[0].iscA < 20.0 && twelvePanels[0].iscA < 20.0)
    }

    // ---- 9. Blended headline voltage matches the (equal-split) per-tracker figure ----
    @Test
    fun `blended voltage averages active trackers weighted by panel count`() {
        val readouts = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 6, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG01LP1-US",
            cellTempC = 25.0, potentialPvKw = 3.0, realizedPvKw = 3.0
        )
        assertEquals(137.28, PvElectricalModel.blendedVoltage(readouts), 0.5)
    }

    // ---- No panels configured returns an empty breakdown rather than throwing ----
    @Test
    fun `zero panel count returns an empty breakdown`() {
        val readouts = PvElectricalModel.mpptReadouts(
            panelWatts = 615, panelCount = 0, inverterKw = 6.0, inverterNameHint = "Deye SUN-6K-SG01LP1-US",
            cellTempC = 25.0, potentialPvKw = 0.0, realizedPvKw = 0.0
        )
        assertTrue(readouts.isEmpty())
        assertEquals(0.0, PvElectricalModel.blendedVoltage(readouts), 0.001)
    }
}
