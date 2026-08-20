package com.lumix.estimator.domain.commercial

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * Phase 27 §14 ("Single phase / split phase / three phase... Do not assume residential 120/240 V
 * split phase for commercial/industrial projects"): the electrical-service phase topology, tracked
 * per load too (§3 — each load's own [LoadInstance.phase]) since a commercial building's loads are
 * rarely all the same phase configuration (e.g. lighting/office equipment on single-phase branch
 * circuits, a compressor motor on three-phase).
 */
@Serializable
enum class LoadPhaseType { SINGLE_PHASE, SPLIT_PHASE, THREE_PHASE }

/** Phase 27 §3 ("Continuous/intermittent"). */
@Serializable
enum class LoadOperationType { CONTINUOUS, INTERMITTENT }

/** Phase 27 §3 ("Critical/non-critical... Load priority") — priority is the same two-tier signal; a separate numeric priority isn't warranted without a real use for more than two tiers yet. */
@Serializable
enum class LoadPriority { CRITICAL, NON_CRITICAL }

/** Which catalog ([CommercialIndustrialLoadCatalog]) a [LoadDefinition] belongs to — filters the picker by [SystemType][com.lumix.estimator.domain.SystemType]. */
@Serializable
enum class LoadCategory { COMMERCIAL, INDUSTRIAL }

/**
 * Phase 27 §2 ("The load catalog must be extensible rather than hard-coded to only these
 * examples"): a catalog ENTRY (the template an installer picks from) — [LoadInstance] is the
 * per-quote, per-installer-edited usage of one. Deliberately a plain data class the catalog is a
 * `List<LoadDefinition>` of, not a closed enum — a new load only needs a new list entry (or, later,
 * an installer-added custom one via [isCustom]), never a code change to a `when` branch anywhere
 * that reads [LoadInstance].
 */
@Serializable
data class LoadDefinition(
    /** Stable identity — kotlinx.serialization-safe key a [LoadInstance] is keyed by; never reused across renames (add a new id instead, the same convention [com.lumix.estimator.domain.ApplianceType]'s own doc already establishes for this app). */
    val id: String,
    val label: String,
    val category: LoadCategory,
    val defaultRatedWatts: Double,
    val defaultVoltage: Double? = null,
    val defaultPhase: LoadPhaseType = LoadPhaseType.SINGLE_PHASE,
    val defaultFrequencyHz: Double = 60.0,
    val defaultPowerFactor: Double = 1.0,
    val defaultOperationType: LoadOperationType = LoadOperationType.INTERMITTENT,
    val defaultPriority: LoadPriority = LoadPriority.NON_CRITICAL,
    /** Phase 27 §3 ("For motors and other inductive loads, power factor and starting requirements must be represented"). */
    val isMotorLoad: Boolean = false,
    /** Typical starting/surge multiplier of [defaultRatedWatts] for this load type (e.g. ~3x for a motor's locked-rotor surge) — a starting point [LoadInstance.startingSurgeWatts] can override with a real nameplate/datasheet figure. Null for non-motor loads with no meaningful surge. */
    val defaultStartingSurgeMultiplier: Double? = null,
    /** True only for the catalog's own "Custom Load" entries (e.g. `commercial_custom`/`industrial_custom` in [CommercialIndustrialLoadCatalog]) — the installer fills in [LoadInstance.label]/[LoadInstance.ratedWatts]/etc. themselves rather than picking a preset. */
    val isCustom: Boolean = false
)

/**
 * Phase 27 §3: one load, as actually configured on a quote — [definition] is the catalog template,
 * every other field is editable per §3's full characteristics list ("Do not assume every load
 * operates at 100% simultaneously").
 */
@Serializable
data class LoadInstance(
    val definitionId: String,
    val label: String,
    val quantity: Int = 1,
    val ratedWatts: Double,
    val voltage: Double? = null,
    val phase: LoadPhaseType = LoadPhaseType.SINGLE_PHASE,
    val frequencyHz: Double = 60.0,
    /** 0 < powerFactor <= 1. [totalApparentPowerKva] coerces defensively, but a value outside this range is itself worth flagging — see [com.lumix.estimator.domain.commercial.EngineeringWarning.MissingLoadInformation]. */
    val powerFactor: Double = 1.0,
    val operatingHoursPerDay: Double = 0.0,
    /** Fraction (0..1) of "on" time this load actually draws its rated power (vs. idling/cycling) — e.g. a compressor's real run-time fraction within its "on" window. Distinct from [simultaneousFactor], which is about THIS load overlapping with every OTHER load, not its own internal cycling. */
    val dutyCycleFraction: Double = 1.0,
    /** Real starting/surge watts for this load, if known — overrides [LoadDefinition.defaultStartingSurgeMultiplier] × [ratedWatts] when set. Null means "use the catalog default multiplier," itself null for a non-motor load (no surge modeled). */
    val startingSurgeWatts: Double? = null,
    val operationType: LoadOperationType = LoadOperationType.INTERMITTENT,
    val priority: LoadPriority = LoadPriority.NON_CRITICAL,
    /** Phase 27 §3 ("Simultaneous operation/diversity factor") applied AT THE INDIVIDUAL LOAD level — e.g. "only 1 of these 4 identical compressors ever runs at once" (0.25). Separate from and multiplicative with the SYSTEM-level [DiversityFactor] (§5), which covers "not every load group peaks together." */
    val simultaneousFactor: Double = 1.0,
    val notes: String = ""
) {
    val totalConnectedWatts: Double get() = ratedWatts * quantity

    /** This load's real-power contribution to Connected Load — quantity x rating, with no duty-cycle/simultaneity reduction (Connected Load is the raw nameplate sum, per §5's own "Connected Load" definition). */
    val connectedRealPowerKw: Double get() = totalConnectedWatts / 1000.0

    /** This load's contribution to Maximum Expected Load — Connected Load reduced by its own duty cycle and per-load simultaneity, before the system-level [DiversityFactor] is applied. */
    val maximumExpectedRealPowerKw: Double get() = connectedRealPowerKw * dutyCycleFraction.coerceIn(0.0, 1.0) * simultaneousFactor.coerceIn(0.0, 1.0)

    val effectivePowerFactor: Double get() = powerFactor.coerceIn(0.01, 1.0)

    val connectedApparentPowerKva: Double get() = ElectricalPower.apparentPowerKva(connectedRealPowerKw, effectivePowerFactor)
    val maximumExpectedApparentPowerKva: Double get() = ElectricalPower.apparentPowerKva(maximumExpectedRealPowerKw, effectivePowerFactor)

    val startingSurgeKw: Double? get() = startingSurgeWatts?.let { it * quantity / 1000.0 }
}

/** Phase 27 §4: `kVA = kW / powerFactor`, `kVAR = sqrt(kVA^2 - kW^2)` — the exact formulas the spec names. */
object ElectricalPower {
    fun apparentPowerKva(realPowerKw: Double, powerFactor: Double): Double {
        val pf = powerFactor.coerceIn(0.01, 1.0)
        return realPowerKw / pf
    }

    fun reactivePowerKvar(realPowerKw: Double, apparentPowerKva: Double): Double {
        val underRoot = (apparentPowerKva * apparentPowerKva) - (realPowerKw * realPowerKw)
        return sqrt(underRoot.coerceAtLeast(0.0))
    }
}
