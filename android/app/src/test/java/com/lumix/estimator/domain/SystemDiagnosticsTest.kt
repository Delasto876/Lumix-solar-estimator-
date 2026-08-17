package com.lumix.estimator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A58 (spec §37–38 — "add a diagnostics panel... PV → PASS, INVERTER → PASS, BATTERY ENERGY →
 * PASS, BATTERY POWER → PASS, MPPT → PASS, VOC → PASS, VMP → PASS"): regression tests for
 * [SystemDiagnostics.checksFor]. Uses the exact 6×615W / 14×615W + real Deye SUN-6K-SG01LP1-US
 * regression pair already established in `EquipmentSelectionEngineTest.kt` (A52) — 6 panels pass
 * every electrical check, 14 panels exceed the inverter's real max PV input power — so the expected
 * PASS/FAIL outcomes here are exact, not estimated.
 */
class SystemDiagnosticsTest {

    private fun minimalResult(
        panelCount: Int,
        panelWatts: Int = 615,
        inverterKw: Double = 6.0,
        inverterName: String = "Deye SUN-6K-SG01LP1-US",
        peakWatts: Double = 4000.0,
        totalBatteryKwh: Double = 0.0,
        batteryRequiredKwh: Double = 0.0,
        backupCapacityWarningKw: Double? = null,
        batteryRechargeTargetMet: Boolean? = null,
        batteryRechargeSocAt2pmPercent: Float? = null,
        batteryBackupTargetMet: Boolean? = null,
        estimatedBackupHours: Double = 0.0,
        estimatedBackupReason: String = ""
    ) = QuoteResult(
        effectiveSystemMode = SystemMode.HYBRID,
        designDailyKwh = 10.0,
        peakWatts = peakWatts,
        panelCount = panelCount,
        panelWatts = panelWatts,
        inverterName = inverterName,
        inverterKw = inverterKw,
        batteryName = if (totalBatteryKwh > 0) "10kWh (SRNE SR-EOS10B)" else null,
        batteryRequiredKwh = batteryRequiredKwh,
        totalBatteryKwh = totalBatteryKwh,
        rows = 1, railsPerRow = 3, totalRails = 3, totalMidClamps = 0, totalEndClamps = 0,
        totalBackLegs = 0, totalFrontLegs = 0, totalBolts = 0, totalLFoot = 0,
        materials = emptyList(), materialsTotal = 0.0, serviceCharge = 0.0, deliveryCharge = 0.0,
        discountAmount = 0.0, grandTotal = 0.0,
        backupCapacityWarningKw = backupCapacityWarningKw,
        batteryRechargeTargetMet = batteryRechargeTargetMet,
        batteryRechargeSocAt2pmPercent = batteryRechargeSocAt2pmPercent,
        batteryBackupTargetMet = batteryBackupTargetMet,
        estimatedBackupHours = estimatedBackupHours,
        estimatedBackupReason = estimatedBackupReason
    )

    @Test
    fun `a well-sized 6x615W system with no battery passes every check`() {
        val checks = SystemDiagnostics.checksFor(minimalResult(panelCount = 6), targetBackupHours = 12.0)
        assertTrue("every check should pass: ${checks.filter { !it.pass }}", checks.all { it.pass })
        // A71: 9 -> 11 — added the real MPPT tracking-range ceiling (Vmp upper bound) and real
        // continuous operating current (Imp) checks, previously not surfaced at all even though
        // pvCompat.valid already silently depended on both once EquipmentSpecs had the data.
        // A72: 11 -> 12 — added the real battery-voltage-window-vs-inverter-battery-port check.
        // A75: 12 -> 13 — added the real simulated battery-backup-duration check that
        // StepSystemReview.kt already had (A66) but this shared panel was missing.
        assertEquals(13, checks.size)
    }

    @Test
    fun `an oversized 14x615W array on the same 6K inverter fails the PV power check`() {
        val checks = SystemDiagnostics.checksFor(minimalResult(panelCount = 14), targetBackupHours = 12.0)
        val pvCheck = checks.first { it.label.startsWith("PV") }
        assertFalse(pvCheck.pass)
        assertTrue(pvCheck.detail!!.contains("exceeds"))
        // Everything else about this system (inverter sizing, no battery) is still fine.
        assertTrue(checks.filter { it.label.startsWith("INVERTER") }.all { it.pass })
    }

    @Test
    fun `undersized battery energy fails its own check without touching PV or inverter checks`() {
        val checks = SystemDiagnostics.checksFor(
            minimalResult(panelCount = 6, totalBatteryKwh = 5.0, batteryRequiredKwh = 10.0), targetBackupHours = 12.0
        )
        val batteryEnergyCheck = checks.first { it.label.startsWith("BATTERY ENERGY") }
        assertFalse(batteryEnergyCheck.pass)
        assertTrue(batteryEnergyCheck.detail!!.contains("10.0"))
        assertTrue(checks.first { it.label.startsWith("PV") }.pass)
    }

    @Test
    fun `battery recharge target not met surfaces the exact SOC in its detail`() {
        val checks = SystemDiagnostics.checksFor(
            minimalResult(panelCount = 6, totalBatteryKwh = 10.0, batteryRequiredKwh = 5.0, batteryRechargeTargetMet = false, batteryRechargeSocAt2pmPercent = 45f),
            targetBackupHours = 12.0
        )
        val rechargeCheck = checks.first { it.label.startsWith("BATTERY RECHARGE") }
        assertFalse(rechargeCheck.pass)
        assertTrue(rechargeCheck.detail!!.contains("45%"))
        assertTrue(rechargeCheck.detail!!.contains("BATTERY RECHARGE TARGET NOT MET"))
    }

    @Test
    fun `backup capacity warning fails the backup-coverage inverter check`() {
        val checks = SystemDiagnostics.checksFor(minimalResult(panelCount = 6, backupCapacityWarningKw = 9.5), targetBackupHours = 12.0)
        val backupCheck = checks.first { it.label.contains("backup coverage") }
        assertFalse(backupCheck.pass)
        assertTrue(backupCheck.detail!!.contains("9.5"))
    }

    // A75 (spec Phase 12): regression test for the newly-added check this round ported over from
    // StepSystemReview.kt's identical A66 check, proving it fires correctly here too and surfaces
    // both the real simulated hours and the requested target in its detail text.
    @Test
    fun `battery backup duration not met surfaces simulated vs requested hours`() {
        val checks = SystemDiagnostics.checksFor(
            minimalResult(
                panelCount = 6, totalBatteryKwh = 10.0, batteryRequiredKwh = 5.0,
                batteryBackupTargetMet = false, estimatedBackupHours = 8.1, estimatedBackupReason = "Battery reached reserve floor overnight."
            ),
            targetBackupHours = 12.0
        )
        val durationCheck = checks.first { it.label.startsWith("BATTERY BACKUP DURATION") }
        assertFalse(durationCheck.pass)
        assertTrue(durationCheck.detail!!.contains("8.1"))
        assertTrue(durationCheck.detail!!.contains("12"))
        assertTrue(durationCheck.detail!!.contains("reserve floor"))
    }

    @Test
    fun `battery backup duration check passes when target is met or unknown`() {
        val metChecks = SystemDiagnostics.checksFor(
            minimalResult(panelCount = 6, totalBatteryKwh = 10.0, batteryBackupTargetMet = true), targetBackupHours = 12.0
        )
        assertTrue(metChecks.first { it.label.startsWith("BATTERY BACKUP DURATION") }.pass)

        val unknownChecks = SystemDiagnostics.checksFor(
            minimalResult(panelCount = 6, totalBatteryKwh = 10.0, batteryBackupTargetMet = null), targetBackupHours = 12.0
        )
        assertTrue(unknownChecks.first { it.label.startsWith("BATTERY BACKUP DURATION") }.pass)
    }
}
