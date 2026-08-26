package com.lumix.estimator.site.solarapi

/**
 * Site Survey / Solar Mapping round: same read-once `BuildConfig` pattern every other credential in
 * this app already uses (see [com.lumix.estimator.map.GoogleMapsConfig]/[com.lumix.estimator.domain
 * .ai.AiConfig]'s own docs) — [com.lumix.estimator.LumixApp.onCreate] calls [configure] once at
 * startup with `BuildConfig.SOLAR_API_KEY`, itself sourced from `android/local.properties` (see
 * `app/build.gradle.kts`'s `solarApiKey` doc) — never hardcoded, never committed. Unlike
 * [com.lumix.estimator.map.GoogleMapsConfig] (which never exposes the raw key — the Maps SDK reads
 * its own key straight from the manifest), [GoogleSolarApiClient] is a plain REST call this app
 * makes itself, so it genuinely needs the raw key value — [apiKey] stays `internal` (readable only
 * from within this module, never logged in full) rather than `private`, for exactly that one caller.
 */
object GoogleSolarApiConfig {
    @Volatile private var key: String = ""

    fun configure(apiKey: String) {
        key = apiKey
    }

    val isConfigured: Boolean get() = key.isNotBlank()

    internal val apiKey: String get() = key
}
