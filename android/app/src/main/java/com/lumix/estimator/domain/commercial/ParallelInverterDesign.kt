package com.lumix.estimator.domain.commercial

import com.lumix.estimator.domain.EquipmentSelectionEngine
import com.lumix.estimator.domain.EquipmentSpecs
import kotlinx.serialization.Serializable

/** Phase 27 §10: one PV string, assigned to one specific MPPT input, on one inverter unit. */
@Serializable
data class StringAssignment(
    /** 0-based index into the inverter model's own MPPT trackers ([EquipmentSpecs.InverterSpec.mpptCount]). */
    val mpptIndex: Int,
    val panelCount: Int
)

/** Phase 27 §7/§9/§10: one inverter unit's own PV string configuration within a [ParallelInverterDesign]. */
@Serializable
data class InverterUnitPvDesign(
    /** 0-based position among [ParallelInverterDesign.inverterCount] identical units. */
    val unitIndex: Int,
    val strings: List<StringAssignment> = emptyList()
) {
    val totalPanels: Int get() = strings.sumOf { it.panelCount }
}

/**
 * Phase 27 §8/§9 ("Support 1, 2, 3, 4+ parallel inverters (where the model supports it)... Do not
 * assume every inverter can be paralleled... calculate at BOTH: SYSTEM LEVEL... and PER-INVERTER
 * LEVEL"): [inverterCount] identical units of the model named by [inverterModelId] (matched against
 * [EquipmentSpecs.InverterSpec.model]), each with its own [InverterUnitPvDesign] — installer-
 * specified per spec §10 ("installer specifies/confirms panels-per-string, strings-per-inverter,
 * MPPT assignment"). Worked example from the spec: 3 x 12kW inverters -> [totalInverterCapacityKw]
 * = 36kW.
 */
@Serializable
data class ParallelInverterDesign(
    val inverterModelId: String,
    val ratedKwPerUnit: Double,
    val panelWattage: Int,
    val inverterCount: Int = 1,
    val unitPvDesigns: List<InverterUnitPvDesign> = emptyList()
) {
    val totalInverterCapacityKw: Double get() = ratedKwPerUnit * inverterCount
    val totalPanelCount: Int get() = unitPvDesigns.sumOf { it.totalPanels }
    val totalPvKw: Double get() = totalPanelCount * panelWattage / 1000.0
}

/**
 * Phase 27 §7/§8/§9/§10 validation. Two checks, kept explicitly separate:
 *
 * 1. PARALLEL CAPABILITY (§8) — [EquipmentSpecs.InverterSpec.supportsParallel]/[EquipmentSpecs
 *    .InverterSpec.maxParallelUnits]. "Never recommend/allow a parallel count the catalog hasn't
 *    confirmed" — see [ParallelInverterDesign.inverterCount] itself.
 * 2. PER-UNIT PV/MPPT ELECTRICAL LIMITS (§7/§10) — reuses [EquipmentSelectionEngine]'s own
 *    single-inverter check ([EquipmentSelectionEngine.checkPanelInverterCompatibilityForLimits]),
 *    called once per unit, rather than re-deriving Voc/Vmp/Isc physics here a second time (one
 *    source of truth for that math — see that engine's own doc). This validates each unit's TOTAL
 *    panel count against the inverter's real MPPT/voltage/current limits using the shared engine's
 *    own optimal-tracker-split logic; it does NOT re-derive Voc/Vmp for the installer's own exact
 *    [StringAssignment] split beyond checking each assignment's [StringAssignment.mpptIndex] is a
 *    tracker this inverter actually has. A future round could extend
 *    [EquipmentSelectionEngine.checkPanelInverterCompatibilityForLimits] (or a sibling function) to
 *    accept an explicit, arbitrary per-tracker split rather than always computing its own — flagged
 *    as a known limitation, not silently glossed over.
 */
object ParallelInverterValidator {

    data class UnitValidationResult(
        val unitIndex: Int,
        val valid: Boolean,
        val notes: List<String>
    )

    data class ParallelValidationResult(
        val parallelCapabilityOk: Boolean,
        val unitResults: List<UnitValidationResult>,
        val warnings: List<String>
    ) {
        val valid: Boolean get() = parallelCapabilityOk && unitResults.all { it.valid }
    }

    fun validate(design: ParallelInverterDesign): ParallelValidationResult {
        val invSpec = EquipmentSpecs.inverters.firstOrNull { it.model == design.inverterModelId }
        val warnings = mutableListOf<String>()

        val parallelCapabilityOk = when {
            design.inverterCount <= 1 -> true
            invSpec == null -> {
                warnings += "Inverter model '${design.inverterModelId}' not found in the catalog — parallel capability unconfirmed, cannot validate ${design.inverterCount} units."
                false
            }
            !invSpec.supportsParallel -> {
                warnings += "${invSpec.brand} ${invSpec.model} has no confirmed parallel-operation support in the catalog — cannot validate ${design.inverterCount} units in parallel."
                false
            }
            invSpec.maxParallelUnits != null && design.inverterCount > invSpec.maxParallelUnits -> {
                warnings += "${invSpec.brand} ${invSpec.model} supports at most ${invSpec.maxParallelUnits} parallel units — ${design.inverterCount} requested."
                false
            }
            else -> true
        }

        val maxPvW = invSpec?.maxPvW?.toDouble()
        val maxPvV = invSpec?.maxPvV?.toDouble()
        val mpptTrackers = invSpec?.mpptCount?.coerceAtLeast(1)
        val mpptTrackingMinV = EquipmentSelectionEngine.effectiveMpptFloorV(invSpec)
        val mpptTrackingMaxV = invSpec?.mpptVoltageMaxV?.toDouble()
        val maxContinuousCurrentPerMpptA = invSpec?.maxInputCurrentPerMpptA
        val maxShortCircuitCurrentPerMpptA = invSpec?.maxShortCircuitCurrentPerMpptA

        val unitResults = design.unitPvDesigns.map { unit ->
            if (maxPvW == null || maxPvV == null || mpptTrackers == null) {
                return@map UnitValidationResult(
                    unit.unitIndex, false,
                    listOf("Inverter model '${design.inverterModelId}' has no confirmed PV/MPPT specification in the catalog — cannot validate Unit ${unit.unitIndex + 1}'s PV configuration.")
                )
            }
            val indexNotes = if (unit.strings.any { it.mpptIndex < 0 || it.mpptIndex >= mpptTrackers }) {
                listOf("Unit ${unit.unitIndex + 1}: one or more strings reference an MPPT index outside this inverter's $mpptTrackers available trackers.")
            } else emptyList()
            val result = EquipmentSelectionEngine.checkPanelInverterCompatibilityForLimits(
                panelWatts = design.panelWattage, panelCount = unit.totalPanels,
                maxPvW = maxPvW, maxPvV = maxPvV, mpptTrackers = mpptTrackers,
                mpptTrackingMinV = mpptTrackingMinV, mpptTrackingMaxV = mpptTrackingMaxV,
                maxContinuousCurrentPerMpptA = maxContinuousCurrentPerMpptA,
                maxShortCircuitCurrentPerMpptA = maxShortCircuitCurrentPerMpptA
            )
            val resultNotes = result.notes.map { "Unit ${unit.unitIndex + 1}: $it" }
            UnitValidationResult(unit.unitIndex, result.valid && indexNotes.isEmpty(), indexNotes + resultNotes)
        }

        return ParallelValidationResult(parallelCapabilityOk, unitResults, warnings)
    }
}
