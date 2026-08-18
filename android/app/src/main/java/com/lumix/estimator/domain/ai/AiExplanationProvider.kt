package com.lumix.estimator.domain.ai

import com.lumix.estimator.domain.DiagnosticCheck
import com.lumix.estimator.domain.QuoteResult

/**
 * A85 (Phase 24 — "AI can explain the results produced by the engineering engine... AI must NEVER
 * secretly replace: PV sizing, battery sizing, inverter sizing, MPPT calculations, Voc/Vmp/Isc/Imp
 * validation, battery SOC calculations, PV production calculations, weather/PSH calculations,
 * power-flow calculations, backup calculations, equipment recommendations"): the ONLY input an
 * [AiExplanationProvider] is ever given — [quote] and [diagnostics] are both ALREADY-COMPUTED
 * output of this app's deterministic engine ([com.lumix.estimator.domain.SystemCalculator] /
 * [com.lumix.estimator.domain.SystemDiagnostics]), never raw inputs the AI could size a system
 * from itself. This is enforced by the type signature, not just a comment: there is no
 * `SystemCalculator` or equipment catalog reachable from this package, so an implementation
 * physically cannot perform sizing even if it tried — it can only narrate numbers someone else
 * already computed. [question] is an optional free-text prompt from the installer/customer (e.g.
 * "why is the battery this size?"); the response is always plain-language text, never a value fed
 * back into any calculation.
 */
data class EngineeringExplanationContext(
    val quote: QuoteResult,
    val diagnostics: List<DiagnosticCheck>,
    val question: String? = null
)

/** Result of asking an [AiExplanationProvider] to narrate a [EngineeringExplanationContext]. */
sealed class AiExplanationResult {
    data class Explained(val text: String) : AiExplanationResult()
    /** [AiConfig.enabled] is false — no API key configured. The normal, default state today. */
    data object Disabled : AiExplanationResult()
    /** Enabled but no real provider implementation exists yet — see [AiExplanationService]'s own doc. */
    data object NotConfigured : AiExplanationResult()
    data class Error(val message: String) : AiExplanationResult()
}

/** The contract a real AI provider integration would implement. Read-only over already-computed engineering output — see [EngineeringExplanationContext]'s own doc for why that's structural, not just a convention. */
interface AiExplanationProvider {
    suspend fun explain(context: EngineeringExplanationContext): AiExplanationResult
}
