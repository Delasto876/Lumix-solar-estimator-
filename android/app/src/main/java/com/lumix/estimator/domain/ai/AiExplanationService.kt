package com.lumix.estimator.domain.ai

/**
 * A85 (Phase 24): the one [AiExplanationProvider] the app actually calls — [AiConfig] is the
 * single gate. No real AI provider (OpenAI/Anthropic/etc.) is wired in yet: no API documentation
 * or account was provided to build against, the same "don't invent behavior this app has no way to
 * verify" reasoning [com.lumix.estimator.domain.monitoring.MonitoringProviderRegistry]'s own doc
 * already applies to manufacturer APIs. "READY FOR FUTURE ACTIVATION": once a real provider is
 * chosen and `AiConfig.enabled` is true, replace the body of the `else` branch below with a real
 * HTTP call — [EngineeringExplanationContext] and [AiExplanationResult] don't need to change, and
 * neither does any caller of [explain].
 */
object AiExplanationService : AiExplanationProvider {
    override suspend fun explain(context: EngineeringExplanationContext): AiExplanationResult {
        if (!AiConfig.enabled) return AiExplanationResult.Disabled
        return AiExplanationResult.NotConfigured
    }
}
