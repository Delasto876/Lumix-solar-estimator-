package com.lumix.estimator.domain

import kotlinx.serialization.Serializable

@Serializable
data class MaterialLine(
    val name: String,
    val qty: Double,
    val unitPrice: Double
) {
    val subtotal: Double get() = qty * unitPrice
}

@Serializable
data class QuoteResult(
    val effectiveSystemMode: SystemMode,
    val designDailyKwh: Double,
    val peakWatts: Double,

    val panelCount: Int,
    val panelWatts: Int,

    val inverterName: String,
    val inverterKw: Double,

    val batteryName: String?,
    val batteryRequiredKwh: Double,
    val totalBatteryKwh: Double,

    val rows: Int,
    val railsPerRow: Int,
    val totalRails: Int,
    val totalMidClamps: Int,
    val totalEndClamps: Int,
    val totalBackLegs: Int,
    val totalFrontLegs: Int,
    val totalBolts: Int,
    val totalLFoot: Int,

    val materials: List<MaterialLine>,
    val materialsTotal: Double,
    val serviceCharge: Double,
    val deliveryCharge: Double,
    /**
     * A78 (spec Phase 15 — "improve quote engine", §39 "Show: Original subtotal, Discount, Final
     * subtotal..."): `materialsTotal + serviceCharge + deliveryCharge`, computed once here instead
     * of every consumer (PDF/CSV/HTML/`ResultsScreen`) re-adding the same three fields itself —
     * the same "computed twice, risks drift" pattern this codebase has fixed for other figures
     * (see e.g. A72's `SystemDiagnostics` doc). Defaults to 0.0 for quotes saved before this field
     * existed, which is a display-only backfill gap (nothing was ever computed wrong), not a real
     * historical inaccuracy.
     */
    val subtotalBeforeDiscount: Double = 0.0,
    val discountAmount: Double,
    /**
     * A78 (spec Phase 15, §38/§39 — "tax/fees if applicable"): infrastructure for a configurable
     * tax/fee line, always 0.0 today since no tax rate is configurable anywhere in the app yet
     * (the spec itself files "Tax settings" under §40 SETTINGS, not the quote engine) — adding a
     * real rate without the installer's explicit input on Jamaica's GCT applicability/whether
     * quoted prices are already tax-inclusive would be inventing business policy, not engineering
     * data. `grandTotal` already includes this term so enabling a real rate later needs no formula
     * change, only a nonzero value here.
     */
    val taxAmount: Double = 0.0,
    val grandTotal: Double,
    /**
     * Set when the requested backup coverage (Most Load / Custom) implies a load the actually-
     * selected inverter can't deliver (its rating was capped at the catalog's largest option).
     * The value is the shortfall-causing required kW. Null whenever coverage is Critical Loads/
     * Essentials (which never asks for the full peak) or the chosen inverter covers it fine.
     * Defaults to null so quotes saved before this field existed decode as "no warning on
     * record" — the accurate reading, since the check didn't exist yet either.
     */
    val backupCapacityWarningKw: Double? = null,
    /**
     * The matched real battery's max charge/discharge power (from [EquipmentSpecs], resolved
     * once at calculation time and frozen here), for the simulation to consume directly instead
     * of re-matching against the *current* equipment catalog every time it loads. Null when no
     * confirmed spec matched this battery tier at calculation time (falls back to a generic
     * estimate) or — same encoding as [backupCapacityWarningKw] — for quotes saved before this
     * field existed, which is also the historically accurate reading: no spec catalog existed
     * to match against yet either. This is what makes opening a 6-month-old quote's simulation
     * reproduce the same numbers regardless of any equipment-catalog updates released since.
     */
    val batteryMaxChargeKw: Double? = null,
    val batteryMaxDischargeKw: Double? = null,
    /**
     * A69: the matched real inverter's max PV DC input power (from [EquipmentSpecs], resolved
     * once at calculation time and frozen here — same reproducibility rationale as
     * [batteryMaxChargeKw]) — a real hybrid inverter's PV DC input stage often accepts
     * meaningfully more than its own continuous AC output rating ([inverterKw]), specifically to
     * allow economical DC-side oversizing. Before this field existed, the simulation capped PV
     * production at [inverterKw] directly (the AC-side rating), understating legitimate solar-noon
     * output for exactly the deliberately-oversized designs where the extra DC headroom matters
     * most. Null when no confirmed spec matched this inverter at calculation time (falls back to
     * the same generic ratio [EquipmentSelectionEngine] itself falls back to) or for quotes saved
     * before this field existed.
     */
    val inverterMaxPvKw: Double? = null,

    /**
     * A70: the site-specific PSH (peak sun hours) this system was actually sized against —
     * `QuoteInputs.peakSunHours` at calculation time, frozen here for the same reproducibility
     * reason as [inverterMaxPvKw] (a saved quote's simulation must always scale its production
     * curve to the PSH it was actually designed against, not whatever the parish table says if it
     * changes later). Drives [com.lumix.estimator.domain.simulation.SimSystemConfig.pshHours],
     * which scales the simulation's irradiance curve amplitude — see that field's own doc. Null
     * for quotes saved before this field existed, which falls back to the curve's native
     * (unscaled) reference amplitude rather than guessing a PSH that was never recorded.
     */
    val designPeakSunHours: Double? = null,
    /**
     * A80 (spec Phase 17): [QuoteInputs.installMonth] at calculation time, frozen here for the
     * same reproducibility reason as [designPeakSunHours] — a saved quote's simulation must always
     * evaluate against the month it was actually designed for, not whatever the wizard's current
     * default might be if this quote is reopened later. Null for quotes saved before this field
     * existed, or for a quote where the installer never picked a month — both fall back to the
     * annual-average weather/day-length assumption everywhere this is read.
     */
    val designInstallMonth: Int? = null,

    /**
     * A49: the engineering requirement GUIDED/LOAD's [EquipmentSelectionEngine] sized equipment
     * against (and MANUAL's warnings are checked against) — independent of what was actually
     * chosen, so the UI can show "required" next to "recommended"/"selected". Default 0.0 for
     * quotes saved before this field existed, the historically accurate reading since no such
     * figure was tracked separately yet either.
     */
    val requiredPvKw: Double = 0.0,
    val requiredInverterKw: Double = 0.0,
    val requiredBatteryUsableKwh: Double = 0.0,
    /** Plain-language "why this was picked" text — populated for GUIDED/LOAD only; null for MANUAL, where the installer picked it. */
    val panelSelectionReason: String? = null,
    val inverterSelectionReason: String? = null,
    val batterySelectionReason: String? = null,
    /** MANUAL only — set when the installer's own equipment choice may be undersized. Never set for GUIDED/LOAD, whose equipment is chosen specifically to avoid this. */
    val manualInverterWarning: String? = null,
    val manualBatteryWarning: String? = null,

    /**
     * A54: the ONE backup-runtime figure — computed once, here, by actually running
     * [com.lumix.estimator.domain.simulation.BackupEstimator] (a real grid-disconnected
     * simulation of the exact selected system and appliance schedule) rather than a per-screen
     * closed-form ratio. Every screen that shows "estimated backup" (System Review, Results,
     * the PDF) reads this same field so they can never disagree with each other or with what
     * the Simulation screen itself would show for the same outage. Defaults to 0.0/false/"" for
     * quotes saved before this field existed — the historically accurate reading, since no such
     * simulation-backed figure was computed for them either.
     */
    val estimatedBackupHours: Double = 0.0,
    /** True if the simulated outage stayed fully covered for the whole search window (a well-sized system) rather than hitting a real shortfall — see [estimatedBackupHours]. */
    val estimatedBackupSufficient: Boolean = false,
    /** Plain-language explanation of what ended the simulated backup window (or that it didn't within the window tested) — see [estimatedBackupHours]. */
    val estimatedBackupReason: String = "",
    /**
     * A64 (spec §8 — "Do not display '12-hour backup'... display 'Estimated backup: 8.1 hours'
     * and BACKUP TARGET NOT MET"): whether [estimatedBackupHours] actually reaches
     * [QuoteInputs.backupHours], the REQUESTED target — a distinct question from
     * [estimatedBackupSufficient], which only means "survived the full multi-day stress window
     * tested" (a much higher bar than any reasonably-sized backup battery is meant to clear). Null
     * when there's no battery to check backup for, or for quotes saved before this field existed.
     */
    val batteryBackupTargetMet: Boolean? = null,

    /**
     * A54 (spec §22–23): whether the selected PV array can realistically recharge the battery from
     * its reserve floor back to a real "recharged" SOC by early afternoon, computed once by
     * [com.lumix.estimator.domain.simulation.RechargeFeasibility] against this exact system. Null
     * when there's no battery to recharge (nothing to check) or for quotes saved before this field
     * existed — the historically accurate reading either way.
     */
    val batteryRechargeTargetMet: Boolean? = null,
    /** Battery SOC% at the 2 PM check hour — see [batteryRechargeTargetMet]. */
    val batteryRechargeSocAt2pmPercent: Float? = null,
    /** The hour SOC actually reached the recharge target, or null if it never did within the simulated day — see [batteryRechargeTargetMet]. */
    val batteryRechargeHour: Double? = null,

    /**
     * A80 (spec Phase 17 §"SIZING MUST USE WEATHER LOGIC" — "evaluate the selected system against
     * ... at minimum Typical Case and Conservative Case ... report: Estimated typical daily solar
     * production, Estimated conservative daily solar production"): the selected array's simulated
     * daily PV yield (kWh) integrated over one full [designInstallMonth]-aware day, under
     * [com.lumix.estimator.domain.simulation.WeatherScenario.TYPICAL] and
     * [com.lumix.estimator.domain.simulation.WeatherScenario.CLOUDIER] respectively — the same
     * weather model the recharge/backup checks above already ran against, not a separate estimate.
     * Null when [designInstallMonth] was never set (no month-specific day to integrate) or for
     * quotes saved before this field existed.
     */
    val estimatedTypicalDailyPvKwh: Double? = null,
    val estimatedConservativeDailyPvKwh: Double? = null
) {
    val pvKw: Double get() = panelCount * panelWatts / 1000.0
}
