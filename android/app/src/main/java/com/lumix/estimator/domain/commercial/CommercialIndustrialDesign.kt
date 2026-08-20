package com.lumix.estimator.domain.commercial

import kotlinx.serialization.Serializable

/**
 * Phase 27 §14 ("Single phase / split phase / three phase, nominal voltage, frequency, existing
 * utility service capacity, main breaker/service rating... Do not assume residential 120/240 V
 * split phase for commercial/industrial projects"): the site's utility service, as distinct from
 * any individual [LoadInstance]'s own [LoadInstance.phase]/[LoadInstance.voltage] (a building's
 * service can be three-phase while individual branch circuits are single-phase).
 */
@Serializable
data class ElectricalService(
    val phase: LoadPhaseType = LoadPhaseType.THREE_PHASE,
    val nominalVoltage: Double = 240.0,
    val frequencyHz: Double = 60.0,
    /** Amps, if known/confirmed — null means "not yet entered," never assumed. */
    val utilityServiceCapacityAmps: Double? = null,
    val mainBreakerRatingAmps: Double? = null
)

/**
 * Phase 27 §5 ("configurable Diversity/Simultaneous-Use Factor... Do not silently apply a
 * residential assumption"): the spec's own preset list plus a custom fraction. [fraction] is what
 * every calculation actually reads — [PERCENT_100]..[PERCENT_50] resolve directly, [CUSTOM] reads
 * [DiversityFactor.customFraction] instead.
 */
@Serializable
enum class DiversityFactorPreset(val fraction: Double?) {
    PERCENT_100(1.0),
    PERCENT_90(0.9),
    PERCENT_80(0.8),
    PERCENT_70(0.7),
    PERCENT_60(0.6),
    PERCENT_50(0.5),
    CUSTOM(null)
}

@Serializable
data class DiversityFactor(
    val preset: DiversityFactorPreset = DiversityFactorPreset.PERCENT_100,
    val customFraction: Double = 1.0
) {
    val fraction: Double get() = (preset.fraction ?: customFraction).coerceIn(0.0, 1.0)
}

/**
 * Phase 27 §13/§19 ("Do not create separate disconnected sizing engines for residential, commercial
 * and industrial. Use one SystemDesign architecture... extend it with: electricalService, phase,
 * nominalVoltage, powerFactor, diversityFactor, connectedLoad, designLoad..."): the single bundle
 * [com.lumix.estimator.domain.QuoteInputs.commercialIndustrialDesign] holds all of Phase 27's new
 * commercial/industrial-only data, rather than 15+ new flat fields directly on [com.lumix.estimator
 * .domain.QuoteInputs] — "Residential should simply use fewer fields where they are unnecessary" is
 * satisfied by this being entirely absent (null) for a RESIDENTIAL quote, not by empty fields sitting
 * unused on every quote regardless of [com.lumix.estimator.domain.SystemType].
 *
 * §7/§8/§11's parallel-inverter, PV string/MPPT, and battery-per-inverter data are added here as
 * further nullable fields once those models exist (see [com.lumix.estimator.domain.commercial]
 * package doc / the Phase 27 task list) — additive, same pattern as every other field on this class.
 */
@Serializable
data class CommercialIndustrialDesign(
    val electricalService: ElectricalService = ElectricalService(),
    val loads: List<LoadInstance> = emptyList(),
    val diversityFactor: DiversityFactor = DiversityFactor()
) {
    /** §5 "Connected Load" — raw nameplate sum, no duty-cycle/simultaneity/diversity reduction. */
    val connectedLoadKw: Double get() = loads.sumOf { it.connectedRealPowerKw }

    /** §5 "Maximum Expected Load" — Connected Load reduced by each load's own duty cycle and per-load simultaneity, before the system-level [diversityFactor] is applied. */
    val maximumExpectedLoadKw: Double get() = loads.sumOf { it.maximumExpectedRealPowerKw }

    /** §5 "Design Load" — Maximum Expected Load x the system-level [diversityFactor]. This is the figure equipment sizing should read, never [connectedLoadKw] alone. */
    val designLoadKw: Double get() = maximumExpectedLoadKw * diversityFactor.fraction

    /** §4: apparent-power (kVA) equivalents of the same three load figures, since kW alone can understate what the inverter must actually supply for a low-power-factor load mix. */
    val connectedApparentPowerKva: Double get() = loads.sumOf { it.connectedApparentPowerKva }
    val maximumExpectedApparentPowerKva: Double get() = loads.sumOf { it.maximumExpectedApparentPowerKva }
    val designApparentPowerKva: Double get() = maximumExpectedApparentPowerKva * diversityFactor.fraction

    /** §4: the blended power factor implied by [designLoadKw]/[designApparentPowerKva] — what actually drives inverter kVA selection, not any single load's own power factor. */
    val blendedPowerFactor: Double get() = if (designApparentPowerKva > 0.0) (designLoadKw / designApparentPowerKva).coerceIn(0.0, 1.0) else 1.0
}
