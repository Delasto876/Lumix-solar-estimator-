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
 * A83: the contract a real manufacturer integration would implement — translate that
 * manufacturer's own proprietary API/protocol into one [DeviceTelemetry] snapshot. Per the spec's
 * own "prepare the architecture... but do not make monitoring the priority until the design/
 * sizing/simulation engine is stable": no real Deye/LuxPower/Growatt/SOLARMAN/Solar of Things
 * client exists yet. No API credentials or documented endpoint contracts were provided to build
 * against, and writing plausible-looking HTTP calls to real commercial services without either
 * would mean inventing behavior this app has no way to verify — exactly what this codebase's own
 * "don't invent data" discipline (already applied to equipment specs, business content, and
 * electrical-code citations) forbids. [SimulatedMonitoringProvider] is the one implementation that
 * exists today, deriving a real snapshot from this app's own already-computed simulation rather
 * than a live device — always clearly labeled as such, never presented as a real telemetry feed.
 */
interface MonitoringProvider {
    val manufacturer: MonitoringManufacturer?
    suspend fun fetchLatest(deviceId: String): MonitoringResult
}

/**
 * A83: one [MonitoringProvider] per named manufacturer, standing in until a real API integration
 * exists — every entry unconditionally returns [MonitoringResult.NotConfigured], never a
 * fabricated reading. This is the literal "prepare the architecture for Deye/LuxPower/Growatt/
 * SOLARMAN/Solar of Things" the spec asks for: the registry/interface shape is real and ready:
 * what remains, for each manufacturer, is the actual network client once real API access exists.
 */
object MonitoringProviderRegistry {
    fun providerFor(manufacturer: MonitoringManufacturer): MonitoringProvider = object : MonitoringProvider {
        override val manufacturer: MonitoringManufacturer = manufacturer
        override suspend fun fetchLatest(deviceId: String): MonitoringResult = MonitoringResult.NotConfigured
    }
}
