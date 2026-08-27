package com.lumix.estimator.domain.monitoring

/**
 * A85 (Phase 23 continuation — "BUILD NOW, ACTIVATE LATER... Use... environment-variable
 * placeholders... Never hard-code secrets"): per-manufacturer credential shape, one subtype per
 * entry in [MonitoringManufacturer]. Every field defaults to blank; [isConfigured] is the ONE place
 * "do we have real credentials for this manufacturer yet" is decided, so [MonitoringConfig] and
 * [MonitoringProviderRegistry] never have to duplicate that blank-check logic themselves.
 */
sealed class MonitoringCredentials {
    abstract val isConfigured: Boolean

    /**
     * A149 (Deye integration round): revised from the original (apiKey/clientId/clientSecret)
     * placeholder shape once DeyeCloud's own sample code showed the real auth flow isn't a scoped
     * API key — it's the installer's actual DeyeCloud account login. [appId]/[appSecret] identify
     * the registered "App" (from developer.deyecloud.com); [email]/[password] are the account's own
     * login credentials, [companyId] optionally scopes the login to a company/installer account
     * (blank/"0" for a personal account) — see [com.lumix.estimator.domain.monitoring.deye
     * .DeyeAuthClient]'s own doc for exactly where each field is used and what's confirmed vs.
     * inferred about this flow. [password] is deliberately never persisted by this app (see
     * [com.lumix.estimator.data.SettingsRepository]'s own doc) — [isConfigured] only requires the
     * fields needed to *attempt* a login; a wrong password still surfaces as a real
     * [MonitoringResult.Error]/[DeviceListResult.Error], never a silent fallback.
     */
    data class Deye(
        val appId: String = "",
        val appSecret: String = "",
        val email: String = "",
        val password: String = "",
        val companyId: String = "0",
        /** A previously-obtained access token, restored (password-less) after a process restart — see [com.lumix.estimator.domain.monitoring.deye.RealDeyeProvider]'s own doc for why this lets a restored session keep working without the password. */
        val accessToken: String? = null,
        val tokenExpiresAtMillis: Long? = null
    ) : MonitoringCredentials() {
        override val isConfigured: Boolean
            get() = appId.isNotBlank() && appSecret.isNotBlank() && email.isNotBlank() && password.isNotBlank()
    }

    data class LuxPower(val apiKey: String = "") : MonitoringCredentials() {
        override val isConfigured: Boolean get() = apiKey.isNotBlank()
    }

    data class Growatt(val apiKey: String = "") : MonitoringCredentials() {
        override val isConfigured: Boolean get() = apiKey.isNotBlank()
    }

    data class Solarman(
        val appId: String = "",
        val appSecret: String = ""
    ) : MonitoringCredentials() {
        override val isConfigured: Boolean get() = appId.isNotBlank() && appSecret.isNotBlank()
    }

    data class SolarOfThings(val apiKey: String = "") : MonitoringCredentials() {
        override val isConfigured: Boolean get() = apiKey.isNotBlank()
    }
}

/**
 * A85: holds this session's [MonitoringCredentials] for every named manufacturer — blank
 * (unconfigured) by default, exactly what every unit test and any code path that never calls
 * [configure] sees. Deliberately NOT reading `BuildConfig` fields directly in this file: this is
 * the app's pure-Kotlin domain layer, and tying it to a generated Android class would make it
 * untestable outside a full Android build variant. Instead [com.lumix.estimator.LumixApp.onCreate]
 * (the one place this app already wires real Android-framework values into domain objects, e.g.
 * `PriceRepository(this)`) calls [configure] once at startup with the real `BuildConfig.*` values,
 * which themselves come from `android/local.properties` (see `app/build.gradle.kts`) — never
 * hardcoded, never committed.
 *
 * "READY FOR FUTURE ACTIVATION": until the installer drops real values into local.properties,
 * every manufacturer here reports [MonitoringCredentials.isConfigured] == false, which is what
 * routes [MonitoringProviderRegistry.providerFor] to a [MockMonitoringProvider] for every one of
 * them — see that object's own doc for what happens once real credentials do exist.
 */
object MonitoringConfig {
    @Volatile private var deye: MonitoringCredentials.Deye = MonitoringCredentials.Deye()
    @Volatile private var luxPower: MonitoringCredentials.LuxPower = MonitoringCredentials.LuxPower()
    @Volatile private var growatt: MonitoringCredentials.Growatt = MonitoringCredentials.Growatt()
    @Volatile private var solarman: MonitoringCredentials.Solarman = MonitoringCredentials.Solarman()
    @Volatile private var solarOfThings: MonitoringCredentials.SolarOfThings = MonitoringCredentials.SolarOfThings()

    /**
     * A149: [deye] dropped its required-BuildConfig-value default — unlike the other four
     * manufacturers, Deye's real credentials are entered by the installer at runtime (Settings'
     * "Connect Deye Account") rather than baked into the build via `local.properties`, so
     * [LumixApp.onCreate] no longer has a `BuildConfig.DEYE_*` value to pass here at all. Use
     * [updateDeye] once the installer's persisted (or freshly entered) credentials are available.
     */
    fun configure(
        luxPower: MonitoringCredentials.LuxPower,
        growatt: MonitoringCredentials.Growatt,
        solarman: MonitoringCredentials.Solarman,
        solarOfThings: MonitoringCredentials.SolarOfThings
    ) {
        this.luxPower = luxPower
        this.growatt = growatt
        this.solarman = solarman
        this.solarOfThings = solarOfThings
    }

    /** A149: sets/replaces the Deye credentials — called once from Settings after a successful "Connect Deye Account," and again on app start once persisted (password-less) fields have been restored, so a login already established survives a process restart without asking the installer to log in again on every app launch. */
    fun updateDeye(credentials: MonitoringCredentials.Deye) {
        this.deye = credentials
    }

    fun credentialsFor(manufacturer: MonitoringManufacturer): MonitoringCredentials = when (manufacturer) {
        MonitoringManufacturer.DEYE -> deye
        MonitoringManufacturer.LUXPOWER -> luxPower
        MonitoringManufacturer.GROWATT -> growatt
        MonitoringManufacturer.SOLARMAN -> solarman
        MonitoringManufacturer.SOLAR_OF_THINGS -> solarOfThings
    }
}
