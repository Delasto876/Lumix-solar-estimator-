package com.lumix.estimator.domain.commercial

import com.lumix.estimator.domain.PriceList
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteResult
import com.lumix.estimator.domain.SystemType

/**
 * Phase 27 ("COMMERCIAL & INDUSTRIAL SYSTEM ARCHITECTURE") entry point for
 * [SystemType.COMMERCIAL]/[SystemType.INDUSTRIAL] quotes — reached only via
 * [com.lumix.estimator.domain.SystemCalculator.calculate]'s own dispatch guard when
 * [QuoteInputs.systemCategory] is not [SystemType.RESIDENTIAL]; that guard is the ONLY change to
 * the residential code path this whole phase makes.
 *
 * §1/§13 ("Industrial = MANUAL MODE ONLY... Commercial = manual design primary"): this calculator
 * only ever computes from the installer's own [QuoteInputs.commercialIndustrialDesign] — there is
 * no auto-recommendation/auto-search here for either system type (a real commercial/industrial
 * recommendation engine, mirroring [com.lumix.estimator.domain.EquipmentSelectionEngine]'s search
 * for residential, is deliberately NOT built this round — see the Phase 27 completion report's own
 * "deferred" section). §16's "commercial recommendations must explain WHY... do not silently
 * replace the deterministic engineering engine with AI" is honored by there being no recommendation
 * output at all yet, rather than a shortcut one.
 *
 * §18 (flow into the existing quotation engine): also NOT implemented this round — [MaterialTakeoffEngine]
 * integration, mounting-hardware takeoff, and BOS pricing for commercial/industrial designs remain
 * residential-only; every pricing-related [QuoteResult] field here is a deliberate zero/empty, never
 * an invented figure. See the completion report for the full list of what this round covers versus
 * defers.
 */
object CommercialIndustrialCalculator {

    private const val NOT_YET_DESIGNED = "No manual design entered yet"

    fun calculate(input: QuoteInputs, prices: PriceList): QuoteResult {
        val design = input.commercialIndustrialDesign
            ?: return emptyResult(input, listOf("No commercial/industrial design has been entered yet — this system type requires the manual design workflow (Phase 27 §13)."))

        val warnings = mutableListOf<EngineeringWarning>()

        if (design.diversityFactor.preset == DiversityFactorPreset.PERCENT_100) {
            warnings += EngineeringWarning.DiversityFactorNotConfirmed(design.diversityFactor.fraction)
        }

        design.loads.forEach { load ->
            if (load.powerFactor <= 0.0 || load.powerFactor > 1.0) {
                warnings += EngineeringWarning.MissingLoadInformation(load.label, "power factor", load.powerFactor.toString())
            }
            if (load.phase != design.electricalService.phase) {
                warnings += EngineeringWarning.LoadPhaseMismatch(load.label, load.phase, design.electricalService.phase)
            }
        }

        val parallelDesign = design.parallelInverterDesign
        val parallelValidation = parallelDesign?.let { ParallelInverterValidator.validate(it) }
        parallelValidation?.let { validation ->
            validation.warnings.forEach { note -> warnings += EngineeringWarning.ValidatorNote(note) }
            validation.unitResults.filterNot { it.valid }.forEach { unit ->
                unit.notes.forEach { note -> warnings += EngineeringWarning.ValidatorNote(note) }
            }
        }

        val batteryDesign = design.batteryPerInverterDesign
        if (parallelDesign != null && batteryDesign != null) {
            val batteryValidation = BatteryPerInverterValidator.validate(batteryDesign, parallelDesign.inverterModelId)
            batteryValidation.unitResults.filterNot { it.valid }.forEach { unit ->
                unit.notes.forEach { note -> warnings += EngineeringWarning.ValidatorNote(note) }
            }
        }

        if (parallelDesign != null) {
            val totalCapacityKw = parallelDesign.totalInverterCapacityKw
            if (totalCapacityKw > 0.0 && design.designLoadKw > totalCapacityKw) {
                warnings += EngineeringWarning.InverterUndersizedForRealPower(design.designLoadKw, totalCapacityKw)
            }
            val totalCapacityKva = if (design.blendedPowerFactor > 0.0) totalCapacityKw / design.blendedPowerFactor else totalCapacityKw
            if (totalCapacityKva > 0.0 && design.designApparentPowerKva > totalCapacityKva) {
                warnings += EngineeringWarning.InverterUndersizedForApparentPower(design.designApparentPowerKva, totalCapacityKva, design.blendedPowerFactor)
            }
        }

        val summary = CommercialIndustrialResultSummary(
            connectedLoadKw = design.connectedLoadKw,
            maximumExpectedLoadKw = design.maximumExpectedLoadKw,
            designLoadKw = design.designLoadKw,
            connectedApparentPowerKva = design.connectedApparentPowerKva,
            maximumExpectedApparentPowerKva = design.maximumExpectedApparentPowerKva,
            designApparentPowerKva = design.designApparentPowerKva,
            blendedPowerFactor = design.blendedPowerFactor
        )

        return QuoteResult(
            effectiveSystemMode = input.systemMode,
            designDailyKwh = design.estimatedDailyEnergyKwh,
            peakWatts = design.designLoadKw * 1000.0,
            panelCount = parallelDesign?.totalPanelCount ?: 0,
            panelWatts = parallelDesign?.panelWattage ?: 0,
            inverterName = parallelDesign?.let { "${it.inverterModelId} x${it.inverterCount}" } ?: NOT_YET_DESIGNED,
            inverterKw = parallelDesign?.totalInverterCapacityKw ?: 0.0,
            batteryName = batteryDesign?.let { "${it.allocations.size} bank(s), ${it.totalBatteryCount} unit(s)" },
            batteryRequiredKwh = 0.0,
            totalBatteryKwh = batteryDesign?.totalBatteryCapacityKwh ?: 0.0,
            rows = 0, railsPerRow = 0, totalRails = 0, totalMidClamps = 0, totalEndClamps = 0,
            totalBackLegs = 0, totalFrontLegs = 0, totalBolts = 0, totalLFoot = 0,
            materials = emptyList(), materialsTotal = 0.0, serviceCharge = 0.0,
            deliveryCharge = input.deliveryCharge,
            installationCommissioningCharge = input.installationCommissioningCharge,
            subtotalBeforeDiscount = 0.0, discountAmount = 0.0, taxAmount = 0.0, grandTotal = 0.0,
            missingPriceItems = emptyList(), canFinalize = false,
            commercialIndustrialSummary = summary,
            commercialIndustrialWarnings = warnings.map { it.message }
        )
    }

    private fun emptyResult(input: QuoteInputs, warnings: List<String>): QuoteResult = QuoteResult(
        effectiveSystemMode = input.systemMode,
        designDailyKwh = 0.0, peakWatts = 0.0,
        panelCount = 0, panelWatts = 0,
        inverterName = NOT_YET_DESIGNED, inverterKw = 0.0,
        batteryName = null, batteryRequiredKwh = 0.0, totalBatteryKwh = 0.0,
        rows = 0, railsPerRow = 0, totalRails = 0, totalMidClamps = 0, totalEndClamps = 0,
        totalBackLegs = 0, totalFrontLegs = 0, totalBolts = 0, totalLFoot = 0,
        materials = emptyList(), materialsTotal = 0.0, serviceCharge = 0.0,
        deliveryCharge = 0.0, installationCommissioningCharge = 0.0,
        subtotalBeforeDiscount = 0.0, discountAmount = 0.0, taxAmount = 0.0, grandTotal = 0.0,
        missingPriceItems = emptyList(), canFinalize = false,
        commercialIndustrialSummary = null,
        commercialIndustrialWarnings = warnings
    )
}
