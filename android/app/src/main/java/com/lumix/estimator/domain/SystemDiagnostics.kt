package com.lumix.estimator.domain

import kotlin.math.min

/** One PASS/FAIL engineering check, with the plain-language reason for a failure. */
data class DiagnosticCheck(val label: String, val pass: Boolean, val detail: String?)

/**
 * A58 (spec §37–38 — "add a diagnostics panel... WHY WAS THIS SYSTEM SELECTED? ... PV → PASS,
 * INVERTER → PASS, BATTERY ENERGY → PASS, BATTERY POWER → PASS, MPPT → PASS, VOC → PASS,
 * VMP → PASS"): the ONE place these checks are built, so `StepSystemReview.kt` (during design) and
 * `SystemResultScreen.kt` (right after Calculate System, per the A56 flow) show the identical
 * verdicts for the identical system rather than each recomputing its own copy. Every individual
 * check already existed (A49's inverter/battery checks, A52's real electrical validation, A54's
 * recharge-target check) — this only consolidates them under one name and one shared function, per
 * the spec's own framing that these should read together as "why was this system selected."
 */
object SystemDiagnostics {

    fun checksFor(result: QuoteResult, targetBackupHours: Double): List<DiagnosticCheck> {
        val requiredInverterKw = result.peakWatts * 1.25 / 1000.0
        val peakLoadKw = result.peakWatts / 1000.0
        // A72 (spec Phase 7 — "fix battery calculations"): reads the real per-model figure
        // SystemCalculator.calculate() already resolved (result.batteryMaxDischargeKw — via
        // resolvedBatteryPowerKw, now including the real DC battery-port ceiling A72 added) instead
        // of independently recomputing a generic 0.5C estimate here. Before this fix, this check
        // (and StepSystemReview's identical duplicate) always showed the flat 0.5C figure
        // regardless of which real battery/inverter was actually matched — the exact "computed
        // twice and risking drift" this file's own doc comment says it exists to prevent, which had
        // silently crept back in for this one figure. Falls back to the same 0.5C estimate only
        // when no confirmed match exists at all (result.batteryMaxDischargeKw is null).
        val batteryMaxDischargeKw = if (result.totalBatteryKwh > 0) {
            result.batteryMaxDischargeKw ?: min(result.totalBatteryKwh * 0.5, result.inverterKw.coerceAtLeast(0.1))
        } else 0.0
        val pvCompat = EquipmentSelectionEngine.checkPanelInverterCompatibility(
            result.panelWatts, result.panelCount, result.inverterKw, result.inverterName
        )
        val batteryVoltageCompat = EquipmentSelectionEngine.checkBatteryVoltageCompatibility(
            result.batteryName, result.inverterKw, result.inverterName
        )

        return listOf(
            DiagnosticCheck(
                label = "INVERTER — suitable for peak load",
                pass = result.inverterKw >= requiredInverterKw - 0.05,
                detail = if (result.inverterKw < requiredInverterKw - 0.05) {
                    "Selected inverter (%s, %.1f kW) is below the calculated peak-load requirement (%.2f kW)."
                        .format(result.inverterName, result.inverterKw, requiredInverterKw)
                } else null
            ),
            DiagnosticCheck(
                label = "INVERTER — suitable for backup coverage",
                pass = result.backupCapacityWarningKw == null,
                detail = result.backupCapacityWarningKw?.let {
                    "Requested backup coverage implies about %.1f kW — above what the %s (%.1f kW) can deliver."
                        .format(it, result.inverterName, result.inverterKw)
                }
            ),
            DiagnosticCheck(
                label = "BATTERY ENERGY — suitable for backup target",
                pass = result.totalBatteryKwh >= result.batteryRequiredKwh - 0.05,
                detail = if (result.totalBatteryKwh < result.batteryRequiredKwh - 0.05) {
                    "About %.1f kWh is needed for the requested backup; %.1f kWh is selected."
                        .format(result.batteryRequiredKwh, result.totalBatteryKwh)
                } else null
            ),
            // A75 (spec Phase 12 — "improve System Review"): this screen's own check was missing
            // even though StepSystemReview.kt's pre-Calculate wizard step already had it (A66) —
            // a real system could reach the Results screen and show "All checks pass" here while
            // the wizard step had already flagged a real simulated-backup-duration shortfall.
            // Ported over unchanged so both screens agree on the same real simulated figure.
            DiagnosticCheck(
                label = "BATTERY BACKUP DURATION — meets requested backup time (simulated)",
                pass = result.batteryBackupTargetMet != false,
                detail = if (result.batteryBackupTargetMet == false) {
                    "Simulated backup: %.1f h — %.0f h requested. %s"
                        .format(result.estimatedBackupHours, targetBackupHours, result.estimatedBackupReason)
                } else null
            ),
            DiagnosticCheck(
                label = "BATTERY POWER — suitable for peak load",
                pass = result.totalBatteryKwh <= 0.0 || batteryMaxDischargeKw >= peakLoadKw - 0.05,
                detail = if (result.totalBatteryKwh > 0.0 && batteryMaxDischargeKw < peakLoadKw - 0.05) {
                    "Peak load (%.1f kW) may exceed the battery's typical continuous discharge rate (%.1f kW)."
                        .format(peakLoadKw, batteryMaxDischargeKw)
                } else null
            ),
            // A72: real per-model battery voltage window vs. the inverter's real accepted
            // battery-port voltage window — see EquipmentSelectionEngine
            // .checkBatteryVoltageCompatibility's own doc. Always passes on today's catalog (every
            // real combination happens to be compatible) but protects against the next equipment
            // addition that isn't.
            DiagnosticCheck(
                label = "BATTERY — voltage compatible with inverter's battery port",
                pass = result.totalBatteryKwh <= 0.0 || batteryVoltageCompat.ok,
                detail = if (result.totalBatteryKwh > 0.0 && !batteryVoltageCompat.ok) {
                    "Battery voltage window (%.1f-%.1fV) falls outside the inverter's accepted battery-port range (%.1f-%.1fV)."
                        .format(batteryVoltageCompat.batteryMinV, batteryVoltageCompat.batteryMaxV, batteryVoltageCompat.inverterMinV, batteryVoltageCompat.inverterMaxV)
                } else null
            ),
            DiagnosticCheck(
                label = "PV — within inverter's max PV input power",
                pass = pvCompat.powerOk,
                detail = if (!pvCompat.powerOk) {
                    "%.2f kWp of panels exceeds the inverter's real maximum PV input, %.2f kW."
                        .format(pvCompat.arrayKw, pvCompat.requiredMaxPvKw)
                } else "%.2f kWp of %.2f kW max PV input.".format(pvCompat.arrayKw, pvCompat.requiredMaxPvKw)
            ),
            DiagnosticCheck(
                label = "VOC — series string within inverter's max PV voltage",
                pass = pvCompat.vocOk,
                detail = if (!pvCompat.vocOk) {
                    "Cold-corrected series Voc %.0fV exceeds the inverter's maximum PV voltage %.0fV."
                        .format(pvCompat.stringVocV, pvCompat.mpptVoltageMaxV)
                } else null
            ),
            DiagnosticCheck(
                label = "VMP — series string within MPPT operating range",
                pass = pvCompat.vmpOk,
                detail = if (!pvCompat.vmpOk) {
                    "Series Vmp %.0fV is below the inverter's minimum MPPT operating voltage.".format(pvCompat.stringVmpV)
                } else null
            ),
            // A71 (spec Phase 6): a genuinely separate check from the one above — vmpOk is the
            // MPPT range's FLOOR, this is its real per-model CEILING (a lower, different figure
            // from the VOC check's own maxPvV ceiling — see PanelCompatibilityResult.vmpUpperOk's
            // own doc). Without this check, a design failing only this one would show every other
            // check passing while pvCompat.valid (and SystemCalculator's own validity gate) was
            // actually false — a misleading "all green" diagnostics panel.
            DiagnosticCheck(
                label = "VMP — series string within MPPT tracking-range ceiling",
                pass = pvCompat.vmpUpperOk,
                detail = if (!pvCompat.vmpUpperOk) {
                    "Series Vmp %.0fV exceeds the inverter's real MPPT tracking-range ceiling.".format(pvCompat.stringVmpV)
                } else null
            ),
            DiagnosticCheck(
                label = "MPPT — short-circuit current within limits",
                pass = pvCompat.iscOk,
                detail = if (!pvCompat.iscOk) {
                    "Series Isc %.1fA exceeds the inverter's implied max PV current — this is the panel's own current (voltage adds in series, current does not multiply by panel count)."
                        .format(pvCompat.stringIscA)
                } else null
            ),
            // A71: separate from the short-circuit check above — the string's real OPERATING
            // current (Imp) against the inverter's real continuous max input current per tracker,
            // a different (and typically lower) datasheet figure — see PanelCompatibilityResult
            // .impOk's own doc.
            DiagnosticCheck(
                label = "MPPT — continuous operating current within limits",
                pass = pvCompat.impOk,
                detail = if (!pvCompat.impOk) {
                    "Series Imp %.1fA exceeds the inverter's real continuous max PV input current per tracker."
                        .format(pvCompat.stringImpA)
                } else null
            ),
            DiagnosticCheck(
                label = "BATTERY RECHARGE — reaches a usable SOC by ~2 PM",
                pass = result.batteryRechargeTargetMet != false,
                detail = if (result.batteryRechargeTargetMet == false) {
                    val socText = "%.0f%%".format(result.batteryRechargeSocAt2pmPercent ?: 0f)
                    "⚠ BATTERY RECHARGE TARGET NOT MET — only $socText SOC by 2 PM. Consider more PV, a smaller battery, or reduced daytime load."
                } else null
            )
        )
    }
}
