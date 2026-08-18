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

    data class Deye(
        val apiKey: String = "",
        val clientId: String = "",
        val clientSecret: String = ""
    ) : MonitoringCredentials() {
        override val isConfigured: Boolean
            get() = apiKey.isNotBlank() && clientId.isNotBlank() && clientSecret.isNotBlank()
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

    fun configure(
        deye: MonitoringCredentials.Deye,
        luxPower: MonitoringCredentials.LuxPower,
        growatt: MonitoringCredentials.Growatt,
        solarman: MonitoringCredentials.Solarman,
        solarOfThings: MonitoringCredentials.SolarOfThings
    ) {
        this.deye = deye
        this.luxPower = luxPower
        this.growatt = growatt
        this.solarman = solarman
        this.solarOfThings = solarOfThings
    }

    fun credentialsFor(manufacturer: MonitoringManufacturer): MonitoringCredentials = when (manufacturer) {
        MonitoringManufacturer.DEYE -> deye
        MonitoringManufacturer.LUXPOWER -> luxPower
        MonitoringManufacturer.GROWATT -> growatt
        MonitoringManufacturer.SOLARMAN -> solarman
        MonitoringManufacturer.SOLAR_OF_THINGS -> solarOfThings
    }
}
