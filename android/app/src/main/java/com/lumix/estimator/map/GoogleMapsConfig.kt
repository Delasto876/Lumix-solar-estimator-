package com.lumix.estimator.map

/**
 * 2026-08-19 ("change map to google map, i am going to use google map api in the app"): unlike
 * every other credential in this app, the Google Maps SDK itself does NOT read this value — it
 * reads its key straight from AndroidManifest.xml's `com.google.android.geo.API_KEY` meta-data
 * (a manifest placeholder resolved by Gradle from `android/local.properties`'s `MAPS_API_KEY` —
 * see `app/build.gradle.kts`'s `mapsApiKey` doc). This object exists purely so the rest of the
 * app — [com.lumix.estimator.site.map.SolarSiteMapScreen]'s pre-emptive "not configured" banner,
 * and a masked presence-check logged once at startup — can know whether a key is even set,
 * without duplicating the manifest-placeholder value or depending on a generated Android class
 * (`BuildConfig`) outside [com.lumix.estimator.LumixApp.onCreate], the same "read BuildConfig in
 * exactly one place" pattern [com.lumix.estimator.domain.ai.AiConfig] already uses.
 */
object GoogleMapsConfig {
    @Volatile private var apiKey: String = ""

    fun configure(apiKey: String) {
        this.apiKey = apiKey
    }

    val isConfigured: Boolean get() = apiKey.isNotBlank()
}
