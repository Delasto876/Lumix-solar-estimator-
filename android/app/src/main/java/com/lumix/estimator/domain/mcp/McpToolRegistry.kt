package com.lumix.estimator.domain.mcp

import com.lumix.estimator.domain.DiagnosticCheck
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteResult
import com.lumix.estimator.domain.ai.AiExplanationResult
import com.lumix.estimator.domain.ai.AiExplanationService
import com.lumix.estimator.domain.ai.EngineeringExplanationContext
import com.lumix.estimator.domain.simulation.SimFrame
import com.lumix.estimator.domain.simulation.TechnicalReadout

/**
 * A85 (Phase 24 — "Potential tools: get_system_design, get_customer, get_load_profile,
 * get_pv_configuration, get_battery_status, get_inverter_status, get_simulation_state,
 * get_system_warnings, get_quote, get_material_takeoff, explain_calculation... The AI/MCP layer
 * should READ information from the deterministic application. It should not silently modify the
 * engineering design"): one pure, read-only function per named tool. Every function is a thin
 * projection over real data the caller already has — this app has no standing global "current
 * session" object, so each tool takes the relevant already-computed domain object(s) as parameters
 * rather than reaching into hidden mutable state, and returns a plain data snapshot. No function
 * here can mutate a [QuoteResult]/[TechnicalReadout]/[SimFrame] or feed a result back into
 * [com.lumix.estimator.domain.SystemCalculator] — there is no such call anywhere in this file.
 *
 * "Optional local/development interface": [McpConfig.enabled] gates whether a future MCP host
 * exposes these tools to a client at all — a host built against this registry should check that
 * flag before registering any tool, not call these functions directly when it's false. The
 * functions themselves stay pure and side-effect-free either way, since there's no live MCP
 * transport in this Android sandbox for them to be reachable through yet.
 */
object McpToolRegistry {

    fun getSystemDesign(quote: QuoteResult): McpSystemDesign = McpSystemDesign(
        pvKw = quote.pvKw,
        panelCount = quote.panelCount,
        panelWatts = quote.panelWatts,
        inverterName = quote.inverterName,
        inverterKw = quote.inverterKw,
        batteryName = quote.batteryName,
        totalBatteryKwh = quote.totalBatteryKwh,
        isRoofConstrained = quote.isRoofConstrained
    )

    fun getCustomer(inputs: QuoteInputs): McpCustomer = McpCustomer(
        name = inputs.customerName,
        contact = inputs.customerContact,
        email = inputs.customerEmail,
        address = inputs.customerAddress
    )

    fun getLoadProfile(quote: QuoteResult): McpLoadProfile = McpLoadProfile(
        designDailyKwh = quote.designDailyKwh,
        peakWatts = quote.peakWatts
    )

    fun getPvConfiguration(quote: QuoteResult): McpPvConfiguration = McpPvConfiguration(
        panelCount = quote.panelCount,
        panelWatts = quote.panelWatts,
        pvKw = quote.pvKw,
        rows = quote.rows,
        railsPerRow = quote.railsPerRow,
        totalRails = quote.totalRails
    )

    fun getBatteryStatus(quote: QuoteResult, readout: TechnicalReadout): McpBatteryStatus = McpBatteryStatus(
        socPercent = readout.batterySocPercent,
        voltage = readout.batteryVoltage,
        current = readout.batteryCurrent,
        name = quote.batteryName,
        totalCapacityKwh = quote.totalBatteryKwh
    )

    fun getInverterStatus(quote: QuoteResult, readout: TechnicalReadout): McpInverterStatus = McpInverterStatus(
        name = quote.inverterName,
        ratedKw = quote.inverterKw,
        outputKw = readout.inverterOutputKw,
        frequencyHz = readout.frequencyHz
    )

    fun getSimulationState(frame: SimFrame): McpSimulationState = McpSimulationState(
        hour = frame.hour,
        pvKw = frame.pvKw,
        houseLoadKw = frame.houseLoadKw,
        batteryPowerKw = frame.batteryPowerKw,
        batterySocPercent = frame.batterySocPercent,
        gridPowerKw = frame.gridPowerKw,
        status = frame.status.name
    )

    fun getSystemWarnings(diagnostics: List<DiagnosticCheck>): McpSystemWarnings {
        val failing = diagnostics.filter { !it.pass }.map { McpSystemWarning(it.label, it.detail) }
        return McpSystemWarnings(failing = failing, allPassed = failing.isEmpty())
    }

    fun getQuote(quote: QuoteResult): McpQuoteSummary = McpQuoteSummary(
        effectiveSystemMode = quote.effectiveSystemMode.name,
        materialsTotal = quote.materialsTotal,
        serviceCharge = quote.serviceCharge,
        deliveryCharge = quote.deliveryCharge,
        discountAmount = quote.discountAmount,
        grandTotal = quote.grandTotal
    )

    fun getMaterialTakeoff(quote: QuoteResult): McpMaterialTakeoff = McpMaterialTakeoff(
        lines = quote.materials,
        materialsTotal = quote.materialsTotal
    )

    /** Delegates to [AiExplanationService] — see that object's own doc for why this stays read-only (narrates, never recomputes) regardless of what [context] contains. */
    suspend fun explainCalculation(context: EngineeringExplanationContext): AiExplanationResult =
        AiExplanationService.explain(context)
}
