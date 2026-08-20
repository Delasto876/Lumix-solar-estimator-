package com.lumix.estimator.domain.commercial

/**
 * Phase 27 §17 ("Add engineering-grade warnings... each warning must explain the problem and the
 * value causing it"): one sealed hierarchy covering every validation surface this phase introduces
 * (§4-§12) — 15 categories, each variant carrying the concrete number(s) that triggered it rather
 * than a bare category label. [message] is what actually gets stored — see
 * [com.lumix.estimator.domain.QuoteResult.commercialIndustrialWarnings]'s own doc for why this
 * sealed type itself is never serialized directly (kotlinx.serialization polymorphism avoidance;
 * [message] is computed once, here, so every producer/consumer of a warning renders it identically).
 */
sealed class EngineeringWarning {
    abstract val message: String

    /** §4/§9: total design load (kW) exceeds the system's total inverter AC capacity. */
    data class InverterUndersizedForRealPower(val designLoadKw: Double, val totalInverterCapacityKw: Double) : EngineeringWarning() {
        override val message get() = "Design load %.1f kW exceeds total inverter capacity %.1f kW.".format(designLoadKw, totalInverterCapacityKw)
    }

    /** §4 ("Do not size an inverter from kW alone when the load's kVA requirement is materially different"): design kVA exceeds capacity even though kW alone might not. */
    data class InverterUndersizedForApparentPower(val designApparentPowerKva: Double, val totalInverterCapacityKva: Double, val blendedPowerFactor: Double) : EngineeringWarning() {
        override val message get() = "Design apparent power %.1f kVA exceeds total inverter apparent-power capacity %.1f kVA (blended power factor %.2f) — kW alone would understate this.".format(designApparentPowerKva, totalInverterCapacityKva, blendedPowerFactor)
    }

    /** §8: N parallel units requested but the catalog has no confirmed parallel-operation support for this model. */
    data class ParallelCapabilityUnconfirmed(val inverterModel: String, val requestedUnits: Int) : EngineeringWarning() {
        override val message get() = "$inverterModel has no confirmed parallel-operation support in the catalog — $requestedUnits units requested."
    }

    /** §8: requested unit count exceeds the model's own confirmed maximum. */
    data class ParallelUnitCountExceedsLimit(val inverterModel: String, val requestedUnits: Int, val maxUnits: Int) : EngineeringWarning() {
        override val message get() = "$inverterModel supports at most $maxUnits parallel units — $requestedUnits requested."
    }

    /** §7: a string's cold-corrected Voc exceeds the inverter's max PV input voltage. */
    data class StringVoltageExceedsLimit(val unitIndex: Int, val stringVocV: Double, val maxPvV: Double) : EngineeringWarning() {
        override val message get() = "Unit ${unitIndex + 1}: string Voc %.0f V exceeds inverter max PV voltage %.0f V.".format(stringVocV, maxPvV)
    }

    /** §7: a string's Vmp falls below the inverter's minimum MPPT tracking voltage. */
    data class StringVoltageBelowMinimum(val unitIndex: Int, val stringVmpV: Double, val minMpptV: Double) : EngineeringWarning() {
        override val message get() = "Unit ${unitIndex + 1}: string Vmp %.0f V is below the inverter's minimum MPPT operating voltage %.0f V.".format(stringVmpV, minMpptV)
    }

    /** §7: a string's Isc/Imp exceeds the inverter's max current per MPPT tracker. */
    data class StringCurrentExceedsLimit(val unitIndex: Int, val stringCurrentA: Double, val maxCurrentA: Double) : EngineeringWarning() {
        override val message get() = "Unit ${unitIndex + 1}: string current %.1f A exceeds the inverter's max current per MPPT tracker %.1f A.".format(stringCurrentA, maxCurrentA)
    }

    /** §10: a string was assigned to an MPPT index the inverter model doesn't have. */
    data class MpptTrackerIndexInvalid(val unitIndex: Int, val mpptIndex: Int, val availableTrackers: Int) : EngineeringWarning() {
        override val message get() = "Unit ${unitIndex + 1}: string assigned to MPPT index $mpptIndex, but this inverter only has $availableTrackers tracker(s)."
    }

    /** §12: a unit's combined battery-bank max discharge power is below what its inverter can draw. */
    data class BatteryDischargeInsufficient(val unitIndex: Int, val batteryMaxDischargeKw: Double, val inverterMaxDischargeKw: Double) : EngineeringWarning() {
        override val message get() = "Unit ${unitIndex + 1}: battery bank max discharge %.1f kW is below this inverter's max discharge rating %.1f kW.".format(batteryMaxDischargeKw, inverterMaxDischargeKw)
    }

    /** §12: the inverter's max battery current exceeds the combined battery bank's max discharge current. */
    data class BatteryCurrentExceedsCapability(val unitIndex: Int, val inverterMaxBatteryA: Double, val batteryMaxDischargeA: Double) : EngineeringWarning() {
        override val message get() = "Unit ${unitIndex + 1}: inverter's max battery current %.0f A exceeds the battery bank's combined max discharge current %.0f A.".format(inverterMaxBatteryA, batteryMaxDischargeA)
    }

    /** §11: batteries-in-parallel count exceeds (or has no confirmed) catalog support for this model. */
    data class BatteryParallelCapabilityUnconfirmed(val unitIndex: Int, val batteryModel: String, val requestedCount: Int, val maxUnits: Int?) : EngineeringWarning() {
        override val message get() = if (maxUnits != null)
            "Unit ${unitIndex + 1}: $batteryModel supports at most $maxUnits parallel units — $requestedCount requested."
        else
            "Unit ${unitIndex + 1}: $batteryModel has no confirmed parallel/multi-unit support in the catalog — $requestedCount requested."
    }

    /** §3: a motor load's starting/surge power exceeds the inverter's surge capability. */
    data class MotorStartingSurgeExceedsCapacity(val loadLabel: String, val startingSurgeKw: Double, val inverterSurgeCapacityKw: Double) : EngineeringWarning() {
        override val message get() = "\"$loadLabel\" starting/surge draw %.1f kW exceeds the inverter's surge capacity %.1f kW.".format(startingSurgeKw, inverterSurgeCapacityKw)
    }

    /** §14: a load's phase configuration doesn't match the site's own electrical service phase. */
    data class LoadPhaseMismatch(val loadLabel: String, val loadPhase: LoadPhaseType, val servicePhase: LoadPhaseType) : EngineeringWarning() {
        override val message get() = "\"$loadLabel\" is configured as $loadPhase, but the site's electrical service is $servicePhase."
    }

    /** §3: a load's power factor is outside the physically valid 0-1 range, or another required field is missing/implausible. */
    data class MissingLoadInformation(val loadLabel: String, val field: String, val value: String) : EngineeringWarning() {
        override val message get() = "\"$loadLabel\": $field ($value) is missing or outside its valid range — verify against the real nameplate/datasheet."
    }

    /** §5 ("Do not silently apply a residential assumption"): the system-level diversity factor is still at its 100% default — an explicit confirmation, not silently accepted as final. */
    data class DiversityFactorNotConfirmed(val currentFraction: Double) : EngineeringWarning() {
        override val message get() = "System-level diversity factor is still at its default (%.0f%%) — confirm the actual simultaneous-use factor for this site before finalizing.".format(currentFraction * 100.0)
    }

    /** §15 ("Do not invent manufacturer specifications... mark it unknown and require the value to be entered/verified"): a required spec the calculation needed wasn't available in the catalog. */
    data class ManufacturerSpecUnknown(val equipmentModel: String, val field: String) : EngineeringWarning() {
        override val message get() = "$equipmentModel: $field is not confirmed in the equipment catalog — verify against the manufacturer's datasheet before finalizing."
    }

    /**
     * Pass-through for an already-formatted note from a lower-level validator ([ParallelInverterValidator]/
     * [BatteryPerInverterValidator]'s own [ParallelInverterValidator.UnitValidationResult.notes]/
     * [BatteryPerInverterValidator.UnitBatteryValidationResult.notes]) — those functions already
     * state the problem and the value causing it inline, so wrapping them in one of the more specific
     * variants above would either duplicate or mismatch their own wording. Not one of the phase's 15
     * named categories itself; a deliberate escape hatch so a caller collecting warnings never has to
     * force a validator's own precise text into a category that doesn't quite fit it.
     */
    data class ValidatorNote(val text: String) : EngineeringWarning() {
        override val message get() = text
    }
}
