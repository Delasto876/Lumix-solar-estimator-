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

    fun checksFor(result: QuoteResult): List<DiagnosticCheck> {
        val requiredInverterKw = result.peakWatts * 1.25 / 1000.0
        val peakLoadKw = result.peakWatts / 1000.0
        val batteryMaxDischargeKw = if (result.totalBatteryKwh > 0) {
            min(result.totalBatteryKwh * 0.5, result.inverterKw.coerceAtLeast(0.1))
        } else 0.0
        val pvCompat = EquipmentSelectionEngine.checkPanelInverterCompatibility(
            result.panelWatts, result.panelCount, result.inverterKw, result.inverterName
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
            DiagnosticCheck(
                label = "BATTERY POWER — suitable for peak load",
                pass = result.totalBatteryKwh <= 0.0 || batteryMaxDischargeKw >= peakLoadKw - 0.05,
                detail = if (result.totalBatteryKwh > 0.0 && batteryMaxDischargeKw < peakLoadKw - 0.05) {
                    "Peak load (%.1f kW) may exceed the battery's typical continuous discharge rate (%.1f kW)."
                        .format(peakLoadKw, batteryMaxDischargeKw)
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
            DiagnosticCheck(
                label = "MPPT — series current within current limits",
                pass = pvCompat.iscOk,
                detail = if (!pvCompat.iscOk) {
                    "Series Isc %.1fA exceeds the inverter's implied max PV current — this is the panel's own current (voltage adds in series, current does not multiply by panel count)."
                        .format(pvCompat.stringIscA)
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
