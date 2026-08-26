package com.lumix.estimator

import android.app.Application
import android.util.Log
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapsSdkInitializedCallback
import com.lumix.estimator.auth.GoogleIdentityConfig
import com.lumix.estimator.data.AppDatabase
import com.lumix.estimator.data.CodeStandardRepository
import com.lumix.estimator.data.PriceRepository
import com.lumix.estimator.data.QuoteRepository
import com.lumix.estimator.data.SettingsRepository
import com.lumix.estimator.domain.ai.AiConfig
import com.lumix.estimator.domain.monitoring.MonitoringConfig
import com.lumix.estimator.domain.monitoring.MonitoringCredentials
import com.lumix.estimator.map.GoogleMapsConfig
import com.lumix.estimator.site.SiteRepository
import com.lumix.estimator.site.solarapi.GoogleSolarApiConfig

class LumixApp : Application() {
    lateinit var quoteRepository: QuoteRepository
        private set
    lateinit var priceRepository: PriceRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    /** A81 (Phase 18): Solar Site's own saved sites/roof-planes — restored alongside the rest of that feature. */
    lateinit var siteRepository: SiteRepository
        private set
    /** A82 (Phase 19): administrator-entered electrical-code standards/citations — see the repository's own doc. */
    lateinit var codeStandardRepository: CodeStandardRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // 2026-08-19 ("map closes when i select it in app, it closes the apk"): the real cause —
        // com.google.android.gms.maps.model.BitmapDescriptorFactory (used by
        // SolarSiteMapScreen's buildMapMarkerIcons to draw the small dot marker icons) is backed
        // by a remote delegate the Maps SDK only wires up once its runtime has actually started;
        // calling it beforehand throws a NullPointerException. That call happens inside a
        // `remember` block that runs synchronously during the map screen's very first
        // composition — BEFORE the GoogleMap composable itself has had a chance to create the
        // native map view and trigger that startup. MapsInitializer.initialize eagerly starts the
        // Maps SDK runtime here at app launch instead, once, well before any screen could possibly
        // reach that code — the same role ensureMapLibreInitialized(this) played for the MapLibre
        // engine this replaced (removed in that switch; this restores the equivalent for Google's
        // own SDK, which was the actual gap, not something the map-specific fixes since then had
        // touched).
        MapsInitializer.initialize(this, MapsInitializer.Renderer.LATEST, object : OnMapsSdkInitializedCallback {
            override fun onMapsSdkInitialized(renderer: MapsInitializer.Renderer) {
                Log.d("LumixMapDiag", "Maps SDK initialized, renderer=$renderer")
            }
        })
        val db = AppDatabase.get(this)
        quoteRepository = QuoteRepository(db.quoteDao())
        priceRepository = PriceRepository(this)
        settingsRepository = SettingsRepository(this)
        siteRepository = SiteRepository(db.siteDao())
        codeStandardRepository = CodeStandardRepository(this)

        // A85 (Phase 23/24 — "BUILD NOW, ACTIVATE LATER"): the one place BuildConfig.* (itself
        // sourced from android/local.properties, blank by default — see app/build.gradle.kts)
        // gets read into the pure-Kotlin domain layer's config objects, so MonitoringConfig/
        // AiConfig stay testable without depending on a generated Android class themselves.
        MonitoringConfig.configure(
            deye = MonitoringCredentials.Deye(BuildConfig.DEYE_API_KEY, BuildConfig.DEYE_CLIENT_ID, BuildConfig.DEYE_CLIENT_SECRET),
            luxPower = MonitoringCredentials.LuxPower(BuildConfig.LUXPOWER_API_KEY),
            growatt = MonitoringCredentials.Growatt(BuildConfig.GROWATT_API_KEY),
            solarman = MonitoringCredentials.Solarman(BuildConfig.SOLARMAN_APP_ID, BuildConfig.SOLARMAN_APP_SECRET),
            solarOfThings = MonitoringCredentials.SolarOfThings(BuildConfig.SOLAR_OF_THINGS_API_KEY)
        )
        AiConfig.configure(BuildConfig.AI_API_KEY)
        // 2026-08-19 ("do this google sign in/OAuth"): same read-once pattern — see
        // GoogleIdentityConfig's own doc for why this is a Web (not Android) client ID.
        GoogleIdentityConfig.configure(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        // 2026-08-19 ("change map to google map"): same read-once pattern — see GoogleMapsConfig's
        // own doc for why this value is ALSO baked into AndroidManifest.xml as a separate manifest
        // placeholder (the Maps SDK reads the manifest directly, not BuildConfig).
        GoogleMapsConfig.configure(BuildConfig.MAPS_API_KEY)
        // Site Survey / Solar Mapping round: same read-once pattern — see GoogleSolarApiConfig's
        // own doc for why this is a separate key from MAPS_API_KEY.
        GoogleSolarApiConfig.configure(BuildConfig.SOLAR_API_KEY)
        // 2026-08-19 map diagnostics: length + first 4 chars only, NEVER the full key, so this is
        // safe to leave in even if Logcat output is ever pasted somewhere. `adb logcat -s
        // LumixMapDiag` filters to just this line on a real device/emulator.
        val mapsKey = BuildConfig.MAPS_API_KEY
        Log.d(
            "LumixMapDiag",
            "MAPS_API_KEY: configured=${mapsKey.isNotBlank()}, length=${mapsKey.length}, prefix=${mapsKey.take(4)}"
        )
    }
}
