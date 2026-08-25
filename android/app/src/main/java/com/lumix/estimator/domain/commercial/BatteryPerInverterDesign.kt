package com.lumix.estimator.domain.commercial

import com.lumix.estimator.domain.BatterySpecSheet
import com.lumix.estimator.domain.EquipmentSpecs
import com.lumix.estimator.domain.InverterSpec
import kotlinx.serialization.Serializable

/**
 * Phase 27 §11 ("batteries per inverter must be explicitly represented... Do not simply calculate
 * one large battery number and ignore how batteries are distributed across parallel inverter
 * units"): one inverter unit's own battery bank, keyed by [unitIndex] (matching
 * [InverterUnitPvDesign.unitIndex]/[ParallelInverterDesign.unitPvDesigns]) — never a single
 * system-wide battery count.
 */
@Serializable
data class BatteryPerInverterAllocation(
    val unitIndex: Int,
    val batteryModelId: String,
    val batteryCount: Int
)

/**
 * Phase 27 §11 worked example ("3 x 12kW inverters, 2 x 15kWh batteries per inverter -> Per
 * inverter: 30kWh, Total: 90kWh"): the whole system's battery allocation as a list of per-unit
 * banks rather than one aggregate figure — [totalBatteryCapacityKwh] IS the aggregate, always
 * derived from the per-unit breakdown, never entered separately (so the two can never drift apart).
 */
@Serializable
data class BatteryPerInverterDesign(
    val allocations: List<BatteryPerInverterAllocation> = emptyList()
) {
    val totalBatteryCount: Int get() = allocations.sumOf { it.batteryCount }

    val totalBatteryCapacityKwh: Double get() = allocations.sumOf { alloc ->
        val spec = EquipmentSpecs.batteries.firstOrNull { it.model == alloc.batteryModelId }
        (spec?.usableEnergyKwh ?: 0.0) * alloc.batteryCount
    }
}

/**
 * Phase 27 §12 ("Calculate DC battery current requirements using REAL inverter and battery
 * specifications... Check: Maximum inverter battery current, Battery continuous discharge current,
 * BMS discharge current limit, Number of batteries in parallel, Required current for the
 * load/inverter, Protection requirements... Flag insufficient battery discharge capability"):
 * validates each unit's [BatteryPerInverterAllocation] against the matched
 * [EquipmentSpecs.InverterSpec] (by model name, shared with [ParallelInverterDesign
 * .inverterModelId]) and [EquipmentSpecs.BatterySpecSheet], reusing those catalog entries' own
 * datasheet current/power fields rather than assuming inverter and battery current limits match
 * (spec's own explicit warning against exactly that assumption).
 */
object BatteryPerInverterValidator {

    data class UnitBatteryValidationResult(
        val unitIndex: Int,
        val batteryCount: Int,
        val totalCapacityKwh: Double,
        val totalMaxDischargeA: Double?,
        val totalMaxDischargeKw: Double?,
        val inverterMaxBatteryA: Double?,
        val inverterMaxDischargeKw: Double?,
        val valid: Boolean,
        val notes: List<String>
    )

    data class BatteryPerInverterValidationResult(
        val unitResults: List<UnitBatteryValidationResult>,
        val totalBatteryCapacityKwh: Double
    ) {
        val valid: Boolean get() = unitResults.all { it.valid }
    }

    /** [inverterCatalog]/[batteryCatalog] default to the real production catalogs; overridable for tests — same reasoning as [ParallelInverterValidator.validate]'s own `inverterCatalog` parameter. */
    fun validate(
        design: BatteryPerInverterDesign, inverterModelId: String,
        inverterCatalog: List<InverterSpec> = EquipmentSpecs.inverters,
        batteryCatalog: List<BatterySpecSheet> = EquipmentSpecs.batteries
    ): BatteryPerInverterValidationResult {
        val invSpec = inverterCatalog.firstOrNull { it.model == inverterModelId }
        val inverterMaxBatteryA = invSpec?.maxBatteryA?.toDouble()
        val inverterMaxDischargeKw = invSpec?.maxDischargePowerKw

        val results = design.allocations.map { alloc ->
            val battSpec = batteryCatalog.firstOrNull { it.model == alloc.batteryModelId }
            if (battSpec == null) {
                return@map UnitBatteryValidationResult(
                    alloc.unitIndex, alloc.batteryCount, 0.0, null, null,
                    inverterMaxBatteryA, inverterMaxDischargeKw, false,
                    listOf("Battery model '${alloc.batteryModelId}' not found in the catalog — cannot validate Unit ${alloc.unitIndex + 1}'s battery bank.")
                )
            }
            val notes = mutableListOf<String>()
            if (!battSpec.parallelSupported && alloc.batteryCount > 1) {
                notes += "${battSpec.brand} ${battSpec.model} has no confirmed parallel/multi-unit support in the catalog — ${alloc.batteryCount} requested for Unit ${alloc.unitIndex + 1}."
            }
            if (battSpec.maxParallelUnits > 0 && alloc.batteryCount > battSpec.maxParallelUnits) {
                notes += "${battSpec.brand} ${battSpec.model} supports at most ${battSpec.maxParallelUnits} parallel units — ${alloc.batteryCount} requested for Unit ${alloc.unitIndex + 1}."
            }
            val totalCapacityKwh = battSpec.usableEnergyKwh * alloc.batteryCount
            val totalMaxDischargeA = battSpec.maxDischargeA.toDouble() * alloc.batteryCount
            val totalMaxDischargeKw = battSpec.maxDischargePowerKw * alloc.batteryCount
            if (inverterMaxDischargeKw != null && totalMaxDischargeKw < inverterMaxDischargeKw) {
                notes += "Unit ${alloc.unitIndex + 1}: battery bank's combined max discharge %.1fkW is below this inverter's max discharge rating %.1fkW — the battery bank cannot supply the inverter's full rated output."
                    .format(totalMaxDischargeKw, inverterMaxDischargeKw)
            }
            if (inverterMaxBatteryA != null && inverterMaxBatteryA > totalMaxDischargeA + 0.5) {
                notes += "Unit ${alloc.unitIndex + 1}: inverter's max battery current %.0fA exceeds the battery bank's combined max discharge current %.0fA."
                    .format(inverterMaxBatteryA, totalMaxDischargeA)
            }
            UnitBatteryValidationResult(
                alloc.unitIndex, alloc.batteryCount, totalCapacityKwh, totalMaxDischargeA, totalMaxDischargeKw,
                inverterMaxBatteryA, inverterMaxDischargeKw, notes.isEmpty(), notes
            )
        }

        return BatteryPerInverterValidationResult(results, results.sumOf { it.totalCapacityKwh })
    }
}
