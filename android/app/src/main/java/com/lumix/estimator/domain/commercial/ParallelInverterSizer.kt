package com.lumix.estimator.domain.commercial

import com.lumix.estimator.domain.EquipmentSelectionEngine
import com.lumix.estimator.domain.InverterSpec
import com.lumix.estimator.domain.MpptStringPlanner
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Site Survey / Solar Mapping round (spec "PARALLEL INVERTERS: If required PV/AC capacity exceeds
 * one inverter: calculate inverter quantity and show PV/string allocation PER INVERTER"): a pure
 * SUGGESTION, not an auto-decision — [ParallelInverterDesign.inverterCount]/[ParallelInverterDesign
 * .unitPvDesigns] stay installer-specified/confirmed per that class's own doc (Phase 27 §10,
 * "installer specifies/confirms panels-per-string, strings-per-inverter, MPPT assignment"); this
 * only proposes a starting point the installer can accept as-is or edit — the same "advisory,
 * never silently overrides" posture [GridTieTransformerAdvisor] already takes for the transformer
 * decision, and [ParallelInverterValidator] still runs against whatever the installer ends up with.
 */
object ParallelInverterSizer {

    data class Suggestion(
        val design: ParallelInverterDesign,
        /**
         * True when [Suggestion.design]'s own [ParallelInverterDesign.inverterCount] had to be
         * capped below what raw capacity math alone would call for, because the model's own
         * confirmed [InverterSpec.maxParallelUnits] (or a lack of confirmed parallel support at
         * all) couldn't reach it — the installer needs a bigger inverter model, a second
         * independent inverter bank, or an explicit override, not just "add more of this one."
         */
        val cappedByParallelLimit: Boolean,
        val notes: List<String>
    )

    /**
     * @param targetPvKw the PV array size to house (e.g. from a roof survey's real
     *   [com.lumix.estimator.domain.RoofConstraint.maxCapacityKw]/[CommercialIndustrialDesign
     *   .totalRoofSurveyCapacityKw], or a load-driven design target the installer entered by hand).
     * @param targetAcKw the AC capacity the inverter bank must deliver — usually the same figure
     *   as [targetPvKw] for a grid-tie design, or the design's own real load figure
     *   ([CommercialIndustrialDesign.designLoadKwIncludingTransformerLoss]) for a hybrid one; kept
     *   as a separate parameter rather than derived from [targetPvKw], since the two aren't always
     *   equal (a grid-tie PV array can be sized well above the site's own real load).
     * @param panelWattage the chosen panel's real nameplate wattage.
     * @param inverterSpec the chosen inverter model's real catalog entry.
     */
    fun suggest(targetPvKw: Double, targetAcKw: Double, panelWattage: Int, inverterSpec: InverterSpec): Suggestion {
        val ratedKwPerUnit = inverterSpec.ratedOutputW / 1000.0
        if (ratedKwPerUnit <= 0.0 || panelWattage <= 0) {
            return Suggestion(
                design = ParallelInverterDesign(inverterSpec.model, ratedKwPerUnit, panelWattage, inverterCount = 0),
                cappedByParallelLimit = false,
                notes = listOf("${inverterSpec.brand} ${inverterSpec.model} has no confirmed rated output — cannot size a quantity.")
            )
        }

        val notes = mutableListOf<String>()
        var inverterCount = ceil(targetAcKw / ratedKwPerUnit).toInt().coerceAtLeast(1)
        var cappedByParallelLimit = false
        if (inverterCount > 1) {
            if (!inverterSpec.supportsParallel) {
                notes += "${inverterSpec.brand} ${inverterSpec.model} has no confirmed parallel-operation support in the catalog — %d units would be needed for %.1f kW AC, but only 1 unit (%.1f kW) can be relied on here; choose a larger model or a second independent inverter bank.".format(inverterCount, targetAcKw, ratedKwPerUnit)
                inverterCount = 1
                cappedByParallelLimit = true
            } else if (inverterSpec.maxParallelUnits != null && inverterCount > inverterSpec.maxParallelUnits) {
                notes += "${inverterSpec.brand} ${inverterSpec.model} supports at most ${inverterSpec.maxParallelUnits} parallel units — %d would be needed for %.1f kW AC; the remaining capacity needs a larger model or a second inverter bank.".format(inverterCount, targetAcKw)
                inverterCount = inverterSpec.maxParallelUnits
                cappedByParallelLimit = true
            }
        }

        val totalPanelCount = floor((targetPvKw * 1000.0) / panelWattage).toInt().coerceAtLeast(0)
        val mpptTrackers = inverterSpec.mpptCount?.coerceAtLeast(1) ?: 1
        val vmpPerPanel = EquipmentSelectionEngine.estimatedOrRealVmpV(panelWattage)
        val minVmpPerString = EquipmentSelectionEngine.effectiveMpptFloorV(inverterSpec)

        // Spread panels as evenly as possible across units first (remainder to the first units,
        // same convention MpptStringPlanner itself uses for strings-within-a-unit), then split
        // each unit's own share across its MPPT trackers via the same shared planner every other
        // panel/inverter topology decision in this codebase already uses — never a second,
        // parallel string-splitting rule.
        val basePerUnit = totalPanelCount / inverterCount
        val remainder = totalPanelCount % inverterCount
        val unitPvDesigns = (0 until inverterCount).map { unitIndex ->
            val unitPanelCount = basePerUnit + if (unitIndex < remainder) 1 else 0
            val stringCounts = MpptStringPlanner.planStrings(unitPanelCount, mpptTrackers, vmpPerPanel, minVmpPerString)
            InverterUnitPvDesign(
                unitIndex = unitIndex,
                strings = stringCounts.mapIndexed { mpptIndex, count -> StringAssignment(mpptIndex, count) }
            )
        }

        val design = ParallelInverterDesign(
            inverterModelId = inverterSpec.model,
            ratedKwPerUnit = ratedKwPerUnit,
            panelWattage = panelWattage,
            inverterCount = inverterCount,
            unitPvDesigns = unitPvDesigns
        )
        return Suggestion(design, cappedByParallelLimit, notes)
    }
}
