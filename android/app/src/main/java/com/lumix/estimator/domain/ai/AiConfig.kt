package com.lumix.estimator.domain.ai

/**
 * A85 (Phase 24 — "Do NOT require a paid AI API right now. Build the architecture so an AI
 * provider can be connected later... Build the AI layer as an optional service that can be
 * disabled. When disabled, the application must work normally"): a single blank-by-default API
 * key, same shape as [com.lumix.estimator.domain.monitoring.MonitoringConfig] — kept out of the
 * generated `BuildConfig` here too, for the same testability reason (see that file's own doc).
 * [com.lumix.estimator.LumixApp.onCreate] calls [configure] once at startup with
 * `BuildConfig.AI_API_KEY`, itself sourced from `android/local.properties` (see
 * `app/build.gradle.kts`) — never hardcoded, never committed.
 *
 * [enabled] is the ONE switch every AI-layer consumer checks. Blank key (the default, and every
 * unit test's state) means disabled — [AiExplanationService] returns [AiExplanationResult
 * .Disabled] and the rest of the app is entirely unaffected, per the spec's own requirement above.
 */
object AiConfig {
    @Volatile private var apiKey: String = ""

    fun configure(apiKey: String) {
        this.apiKey = apiKey
    }

    val enabled: Boolean get() = apiKey.isNotBlank()
}
