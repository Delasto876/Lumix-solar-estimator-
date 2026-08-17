package com.lumix.estimator.solar

import java.time.LocalDate
import java.time.LocalTime

/**
 * Location-specific solar resource data — Peak Sun Hours, irradiance, and the monthly PSH
 * table. Any field the active [SolarResourceProvider] can't actually supply is null; the UI
 * shows "Solar resource data unavailable" and offers manual PSH entry rather than ever
 * inventing a plausible-looking number for a field with no real source behind it.
 */
data class SolarResource(
    val dailyPsh: Double?,
    val irradianceWm2: Double?,
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
    val solarNoon: LocalTime?,
    /** 12 values, January first, hours/day. Null when no dataset is configured. */
    val monthlyPsh: List<Double>?
)

/**
 * Abstraction over wherever Peak Sun Hours / irradiance data comes from, so a real dataset or
 * API (NASA POWER, PVGIS, Solcast, ...) can be dropped in later without touching any caller.
 * [UnavailableSolarResourceProvider] is the only implementation today — this module ships with
 * no live resource data source configured, per the spec's "do not fake accuracy" requirement.
 */
interface SolarResourceProvider {
    suspend fun getSolarResource(latitude: Double, longitude: Double, date: LocalDate): SolarResource
}

/** Always reports "unavailable" — the honest default until a real data source is wired in. */
class UnavailableSolarResourceProvider : SolarResourceProvider {
    override suspend fun getSolarResource(latitude: Double, longitude: Double, date: LocalDate): SolarResource =
        SolarResource(
            dailyPsh = null,
            irradianceWm2 = null,
            sunrise = null,
            sunset = null,
            solarNoon = null,
            monthlyPsh = null
        )
}
