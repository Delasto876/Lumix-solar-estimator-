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
    val grandTotal: Double
) {
    val pvKw: Double get() = panelCount * panelWatts / 1000.0
}
