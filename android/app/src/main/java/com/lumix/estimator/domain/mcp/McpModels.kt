package com.lumix.estimator.domain.mcp

import com.lumix.estimator.domain.MaterialLine
import kotlinx.serialization.Serializable

/**
 * A85 (Phase 24): the response shape for each tool in [McpToolRegistry] — one data class per
 * "Potential tool" the spec names, each a thin read-only projection of fields that already exist
 * on [com.lumix.estimator.domain.QuoteResult]/[com.lumix.estimator.domain.QuoteInputs]/
 * [com.lumix.estimator.domain.simulation.TechnicalReadout]/[com.lumix.estimator.domain.simulation
 * .SimFrame]/[com.lumix.estimator.domain.DiagnosticCheck] — never a new figure computed here. Kept
 * separate from those domain models themselves so a future MCP transport's serialization shape
 * (what a client actually sees) can evolve independently of the app's own internal models.
 */
@Serializable
data class McpSystemDesign(
    val pvKw: Double,
    val panelCount: Int,
    val panelWatts: Int,
    val inverterName: String,
    val inverterKw: Double,
    val batteryName: String?,
    val totalBatteryKwh: Double,
    val isRoofConstrained: Boolean
)

@Serializable
data class McpCustomer(
    val name: String,
    val contact: String,
    val email: String,
    val address: String
)

@Serializable
data class McpLoadProfile(
    val designDailyKwh: Double,
    val peakWatts: Double
)

@Serializable
data class McpPvConfiguration(
    val panelCount: Int,
    val panelWatts: Int,
    val pvKw: Double,
    val rows: Int,
    val railsPerRow: Int,
    val totalRails: Int
)

@Serializable
data class McpBatteryStatus(
    val socPercent: Float,
    val voltage: Double,
    val current: Double,
    val name: String?,
    val totalCapacityKwh: Double
)

@Serializable
data class McpInverterStatus(
    val name: String,
    val ratedKw: Double,
    val outputKw: Double,
    val frequencyHz: Double
)

@Serializable
data class McpSimulationState(
    val hour: Double,
    val pvKw: Double,
    val houseLoadKw: Double,
    val batteryPowerKw: Double,
    val batterySocPercent: Float,
    val gridPowerKw: Double,
    val status: String
)

@Serializable
data class McpSystemWarning(val label: String, val detail: String?)

@Serializable
data class McpSystemWarnings(val failing: List<McpSystemWarning>, val allPassed: Boolean)

@Serializable
data class McpQuoteSummary(
    val effectiveSystemMode: String,
    val materialsTotal: Double,
    val serviceCharge: Double,
    val deliveryCharge: Double,
    val discountAmount: Double,
    val grandTotal: Double
)

@Serializable
data class McpMaterialTakeoff(
    val lines: List<MaterialLine>,
    val materialsTotal: Double
)
