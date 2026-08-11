package com.lumix.estimator.solar

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.sin

/**
 * A geometry-only estimate of Peak Sun Hours, built entirely from sun position — no real
 * irradiance, weather, or aerosol data. Real resource data stays the job of
 * [SolarResourceProvider], which honestly reports "unavailable" until a live dataset (NASA
 * POWER, PVGIS, Solcast, ...) is wired in; this exists only to give installers a rough seasonal
 * *shape* (how much shorter December days are than June's at this latitude) and must never be
 * confused with, or silently substituted for, real measured PSH.
 *
 * Method: for a representative day of each month, integrate sin(elevation) — the standard
 * clear-sky horizontal-irradiance proxy (cos of the zenith angle, Duffie & Beckman) — over every
 * daylight sample, then apply a fixed 0.75 clearness index (a commonly cited rule-of-thumb
 * atmospheric transmittance for a clear sky at sea level) so the numbers land in a believable
 * range instead of the un-attenuated geometric maximum. Every caller-facing surface must label
 * this as an estimate.
 */
object ClearSkyPshEstimator {
    private const val CLEARNESS_INDEX = 0.75
    private val calculator = SolarPositionCalculator()

    /** 12 values, January first, hours/day. */
    fun estimateMonthlyPsh(
        latitude: Double,
        longitude: Double,
        zone: ZoneOffset = ZoneOffset.of("-05:00"),
        year: Int = LocalDate.now().year
    ): List<Double> = (1..12).map { month ->
        estimateDailyPsh(latitude, longitude, LocalDate.of(year, month, 15), zone)
    }

    fun estimateDailyPsh(
        latitude: Double,
        longitude: Double,
        date: LocalDate,
        zone: ZoneOffset,
        stepMinutes: Int = 10
    ): Double {
        val dtHours = stepMinutes / 60.0
        val steps = (24 * 60) / stepMinutes
        var total = 0.0
        for (i in 0..steps) {
            val minutesOfDay = (i * stepMinutes) % (24 * 60)
            val time = LocalTime.of(minutesOfDay / 60, minutesOfDay % 60)
            val position = calculator.calculate(latitude, longitude, ZonedDateTime.of(date, time, zone))
            if (position.elevationDegrees > 0.0) {
                total += sin(Math.toRadians(position.elevationDegrees)) * dtHours
            }
        }
        return total * CLEARNESS_INDEX
    }
}
