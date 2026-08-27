package com.lumix.estimator.domain.monitoring

import kotlinx.serialization.Serializable

/**
 * A83 (spec Phase 22, original §63 — "FUTURE MONITORING": "prepare the architecture for Deye,
 * LuxPower, Growatt, SOLARMAN, Solar of Things... use a normalized internal model... manufacturer
 * APIs should translate into this model"): the ONE shape every manufacturer integration must
 * translate its own proprietary API response into, so every future consumer in this app reads one
 * schema regardless of which real device is connected. Field names match the spec's own list
 * verbatim. Units aren't specified by the spec itself — documented here, kept consistent with the
 * rest of this app's domain layer (kW for power, kWh for energy, °C, V, A).
 *
 * [energyTotal] and [faultCode] are nullable: this app has no persisted lifetime-energy counter or
 * fault-code concept of its own (see each field's own doc) — a real device integration would
 * populate them from the manufacturer's own API; [SimulatedMonitoringProvider] (the only provider
 * that exists today) cannot, and leaves them null rather than inventing a plausible-looking value.
 */
@Serializable
data class DeviceTelemetry(
    val pvPower: Double,
    val pvVoltage: Double,
    val pvCurrent: Double,
    /** 0..100. */
    val batterySoc: Float,
    /** Positive = net charging, negative = net discharging — same sign convention as [com.lumix.estimator.domain.simulation.SimFrame.batteryPowerKw]. */
    val batteryPower: Double,
    val batteryVoltage: Double,
    val loadPower: Double,
    /** Positive = importing from the grid. This app's own grid connection is strictly import-only (see SimulationEngine's own doc), so this is never negative here. */
    val gridPower: Double,
    val energyToday: Double,
    /** Lifetime cumulative energy — see this file's own doc for why this is null from every provider that exists today. */
    val energyTotal: Double?,
    /** A device-reported fault/error code — see this file's own doc for why this is null from every provider that exists today. */
    val faultCode: String?,
    val temperature: Double,
    val timestamp: Long
)

/** The manufacturers the spec explicitly names to prepare this architecture for. */
enum class MonitoringManufacturer(val label: String) {
    DEYE("Deye"),
    LUXPOWER("LuxPower"),
    GROWATT("Growatt"),
    SOLARMAN("SOLARMAN"),
    SOLAR_OF_THINGS("Solar of Things")
}

/**
 * Result of asking a [MonitoringProvider] for a device's latest telemetry. [NotConfigured] is
 * deliberately the outcome every real-manufacturer entry in [MonitoringProviderRegistry] returns
 * today — see [MonitoringProvider]'s own doc for why no live client exists yet.
 */
sealed class MonitoringResult {
    data class Connected(val telemetry: DeviceTelemetry) : MonitoringResult()
    data object NotConfigured : MonitoringResult()
    data class Error(val message: String) : MonitoringResult()
}

/**
 * A149 (Deye integration round): one entry in "the devices/plants I manage" — the piece
 * [MonitoringProvider.fetchLatest] always assumed the caller already had (it takes a [deviceId]
 * with no way to discover one). [id] is the value a subsequent [MonitoringProvider.fetchLatest]
 * call expects back; [plantName] is the station/site grouping a real account can have multiple
 * devices under (null when a provider has no such concept, e.g. the mock/simulated providers).
 */
data class DeviceSummary(
    val id: String,
    val label: String,
    val manufacturer: MonitoringManufacturer?,
    val plantName: String? = null
)

/** Result of asking a [MonitoringProvider] to enumerate the devices/plants it can see — the same three-way shape as [MonitoringResult], for the same reason (never a fabricated device list). */
sealed class DeviceListResult {
    data class Available(val devices: List<DeviceSummary>) : DeviceListResult()
    data object NotConfigured : DeviceListResult()
    data class Error(val message: String) : DeviceListResult()
}

/**
 * A83: the contract a real manufacturer integration would implement — translate that
 * manufacturer's own proprietary API/protocol into one [DeviceTelemetry] snapshot. Per the spec's
 * own "prepare the architecture... but do not make monitoring the priority until the design/
 * sizing/simulation engine is stable": no real Deye/LuxPower/Growatt/SOLARMAN/Solar of Things
 * client existed at first. No API credentials or documented endpoint contracts were provided to
 * build against, and writing plausible-looking HTTP calls to real commercial services without
 * either would mean inventing behavior this app has no way to verify — exactly what this
 * codebase's own "don't invent data" discipline (already applied to equipment specs, business
 * content, and electrical-code citations) forbids. [SimulatedMonitoringProvider] is the one
 * implementation that existed from the start, deriving a real snapshot from this app's own
 * already-computed simulation rather than a live device — always clearly labeled as such, never
 * presented as a real telemetry feed. A149 adds the first genuine live client
 * ([com.lumix.estimator.domain.monitoring.deye.RealDeyeProvider]) once DeyeCloud's own sample
 * code (not full docs — see that file's own doc for exactly what is and isn't confirmed) gave
 * something real to build against.
 */
interface MonitoringProvider {
    val manufacturer: MonitoringManufacturer?
    suspend fun fetchLatest(deviceId: String): MonitoringResult
    /** Enumerates the devices/plants this provider can currently see — see [DeviceSummary]'s own doc. */
    suspend fun listDevices(): DeviceListResult
}

/**
 * A85: which of the two states in [MonitoringProviderRegistry]'s own diagram ("Mock Provider OR
 * Real Provider") a manufacturer is currently in — read by the Settings "Device Monitoring"
 * section so it never has to duplicate the same [MonitoringCredentials.isConfigured] check.
 */
enum class MonitoringIntegrationStatus(val label: String) {
    MOCK_DATA("Mock data — ready for future activation"),
    CREDENTIALS_SET_NO_CLIENT("Credentials set — real client not yet implemented"),
    /** A149: Deye specifically now has a real client — this is the "simulated by default, live once reachable" state, not a settled "definitely working" claim (a real call can still fail — see [FallbackMonitoringProvider]'s own doc). */
    REAL_CLIENT_CONNECTED("Connected — live data when online, simulated otherwise")
}

/**
 * A83 (extended A85 — "BUILD NOW, ACTIVATE LATER... continue building the architecture so these
 * integrations can be connected later without redesigning the application"): one [MonitoringProvider]
 * per named manufacturer. Every entry returns a [MockMonitoringProvider] until [MonitoringConfig]
 * reports real credentials for that manufacturer — the literal "prepare the architecture for Deye/
 * LuxPower/Growatt/SOLARMAN/Solar of Things" the spec asks for, now with mock data flowing so the
 * monitoring UI/charts/alerts can be built and exercised today.
 *
 * "Do NOT rebuild the architecture" when real credentials eventually arrive: this is the ONE place
 * that changes. The `credentials.isConfigured` branch below is where a real per-manufacturer
 * network client gets constructed and returned once real API docs/credentials exist for that
 * manufacturer — nothing else in the app (the [MonitoringProvider] interface, [DeviceTelemetry],
 * any UI reading through this registry) needs to change when that happens.
 */
object MonitoringProviderRegistry {
    /**
     * [isOnline] defaults to always-true so every existing call site (Settings' status list,
     * which only ever needed [statusFor] anyway) keeps compiling unchanged — a caller that
     * actually wants the real "fall back to mock while offline" behavior (A149's new Devices
     * screen) passes a real connectivity check, typically backed by
     * [com.lumix.estimator.network.NetworkConnectivityObserver.isOnline].
     */
    fun providerFor(manufacturer: MonitoringManufacturer, isOnline: () -> Boolean = { true }): MonitoringProvider {
        val credentials = MonitoringConfig.credentialsFor(manufacturer)
        if (manufacturer == MonitoringManufacturer.DEYE) {
            // A149: Deye now has a real client — see FallbackMonitoringProvider's own doc for why
            // the mock/live decision happens per-call here, not once at construction.
            val deyeCredentials = credentials as MonitoringCredentials.Deye
            return FallbackMonitoringProvider(
                real = com.lumix.estimator.domain.monitoring.deye.RealDeyeProvider(deyeCredentials),
                mock = MockMonitoringProvider(manufacturer),
                isOnline = isOnline
            )
        }
        return if (!credentials.isConfigured) {
            MockMonitoringProvider(manufacturer)
        } else {
            // READY FOR FUTURE ACTIVATION: credentials exist, but no manufacturer network client
            // has been implemented yet (no documented endpoint contract to build against — same
            // "don't invent data" reasoning as A83's original doc, and the same reasoning that
            // applied to Deye before A149 gave it something real to build against). Replace this
            // branch with a real provider once one is implemented for `manufacturer`; every caller
            // of this registry is unaffected by that swap — exactly what happened for Deye.
            object : MonitoringProvider {
                override val manufacturer: MonitoringManufacturer = manufacturer
                override suspend fun fetchLatest(deviceId: String): MonitoringResult = MonitoringResult.NotConfigured
                override suspend fun listDevices(): DeviceListResult = DeviceListResult.NotConfigured
            }
        }
    }

    fun statusFor(manufacturer: MonitoringManufacturer): MonitoringIntegrationStatus {
        val configured = MonitoringConfig.credentialsFor(manufacturer).isConfigured
        return when {
            !configured -> MonitoringIntegrationStatus.MOCK_DATA
            manufacturer == MonitoringManufacturer.DEYE -> MonitoringIntegrationStatus.REAL_CLIENT_CONNECTED
            else -> MonitoringIntegrationStatus.CREDENTIALS_SET_NO_CLIENT
        }
    }
}
