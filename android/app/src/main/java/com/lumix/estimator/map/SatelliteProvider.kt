package com.lumix.estimator.map

/**
 * Aerial/satellite imagery — deliberately a SEPARATE abstraction from [BaseMapProvider] per the
 * project owner's explicit instruction: "Do not pretend that OpenFreeMap provides satellite
 * imagery. Keep the base-map provider separate from the satellite imagery provider." OpenFreeMap
 * is a vector road-map style only; it has no aerial photography layer.
 *
 * [NoSatelliteProvider] is the fallback when no key is configured. [MapTilerSatelliteProvider] is
 * the real implementation (2026-08-18, "let satellite view be the default view" — the user's own
 * explicit choice of provider, superseding the earlier "do not implement a paid provider yet"
 * placeholder). Any future alternative (Esri/Google/other) only has to implement this interface and
 * be swapped in at the one call site that reads it.
 */
interface SatelliteProvider {
    val name: String
    val isConfigured: Boolean
    /** A MapLibre-compatible raster/style source URL for satellite imagery, or null when not configured — never a fabricated URL. */
    fun styleUrlOrNull(): String?
}

object NoSatelliteProvider : SatelliteProvider {
    override val name: String = "None configured"
    override val isConfigured: Boolean = false
    override fun styleUrlOrNull(): String? = null
}

/**
 * 2026-08-18 ("let satellite view be the default view"): MapTiler's hosted "satellite" style —
 * real aerial/satellite imagery, served as a MapLibre-compatible style-JSON URL (same format
 * [com.lumix.estimator.map.OpenFreeMapProvider] already uses), so it drops into the exact same
 * `MapLibreMapView`/`setStyle` plumbing with no special-casing. [configure] is called once at
 * startup ([com.lumix.estimator.LumixApp.onCreate]) with `BuildConfig.MAPTILER_API_KEY`
 * (itself sourced from `android/local.properties` — never hardcoded, never committed), the same
 * "read BuildConfig.* in exactly one place" pattern [com.lumix.estimator.domain.ai.AiConfig] and
 * [com.lumix.estimator.domain.monitoring.MonitoringConfig] already use, so this object stays plain
 * Kotlin and testable. A blank key (the default until a real MapTiler key is added to
 * `local.properties`) means [isConfigured] is false and [SolarSiteMapScreen] falls back to its
 * free OpenFreeMap "Streets" view as the default instead — the app never breaks for a build with no
 * key configured.
 *
 * To activate: add `MAPTILER_API_KEY=<your key>` to `android/local.properties` (create the file if
 * it doesn't already exist — it's gitignored, so it's never committed).
 */
object MapTilerSatelliteProvider : SatelliteProvider {
    @Volatile private var apiKey: String = ""

    fun configure(apiKey: String) {
        this.apiKey = apiKey
    }

    override val name: String = "MapTiler Satellite"
    override val isConfigured: Boolean get() = apiKey.isNotBlank()
    override fun styleUrlOrNull(): String? =
        apiKey.takeIf { it.isNotBlank() }?.let { "https://api.maptiler.com/maps/satellite/style.json?key=$it" }
}
