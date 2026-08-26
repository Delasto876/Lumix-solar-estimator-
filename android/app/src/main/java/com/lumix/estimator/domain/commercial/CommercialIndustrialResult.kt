package com.lumix.estimator.domain.commercial

import kotlinx.serialization.Serializable

/**
 * Phase 27 output counterpart to [CommercialIndustrialDesign] (the input) — what
 * [CommercialIndustrialCalculator] actually determined once it exists (task-tracked separately;
 * this file only establishes the [com.lumix.estimator.domain.QuoteResult] storage shape so
 * [com.lumix.estimator.domain.QuoteResult] itself only needs one additive edit for the whole
 * phase). Every field here is a restatement of what [CommercialIndustrialDesign]'s own computed
 * properties already derive from the input — frozen at calculation time for the same
 * reproducibility reason every other frozen figure on [com.lumix.estimator.domain.QuoteResult]
 * exists (see e.g. that class's own [com.lumix.estimator.domain.QuoteResult.designPeakSunHours]
 * doc for the established pattern). §7-§12's per-inverter-unit/per-string/per-battery-bank
 * breakdown is added here as further fields once those models exist (parallel-inverter/
 * battery-per-inverter tasks) — additive, same pattern as [CommercialIndustrialDesign] itself.
 */
@Serializable
data class CommercialIndustrialResultSummary(
    val connectedLoadKw: Double,
    val maximumExpectedLoadKw: Double,
    val designLoadKw: Double,
    val connectedApparentPowerKva: Double,
    val maximumExpectedApparentPowerKva: Double,
    val designApparentPowerKva: Double,
    val blendedPowerFactor: Double,
    /** Site Survey round: frozen copy of [CommercialIndustrialDesign.totalRoofSurveyCapacityKw] at calculation time — null when no roof survey has been attached to this design. */
    val roofSurveyCapacityKw: Double? = null
)
