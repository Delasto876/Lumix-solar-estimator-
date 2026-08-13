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
    val batteryMaxDischargeKw: Double? = null
) {
    val pvKw: Double get() = panelCount * panelWatts / 1000.0
}
