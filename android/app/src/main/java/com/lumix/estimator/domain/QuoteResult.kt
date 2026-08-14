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
    val discountAmount: Double,
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
    val batteryRechargeHour: Double? = null
) {
    val pvKw: Double get() = panelCount * panelWatts / 1000.0
}
