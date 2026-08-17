package com.lumix.estimator.solar

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Samples the sun's position across a full day for an explicit date/location — the data behind
 * a sun-path diagram. Unlike [com.lumix.estimator.domain.simulation.SimulationEngine], which
 * deliberately always uses today's real date for the live digital twin, this takes the date as a
 * parameter so it can plot any reference day (today, a solstice, whatever a diagram needs) and
 * so it stays independently testable without depending on "now".
 */
object SolarPathSampler {
    private val calculator = SolarPositionCalculator()

    /** One [SolarPosition] every [stepMinutes] minutes across the full 24h day, in order. */
    fun sampleDay(
        latitude: Double,
        longitude: Double,
        date: LocalDate,
        zone: ZoneOffset,
        stepMinutes: Int = 20
    ): List<SolarPosition> {
        val steps = (24 * 60) / stepMinutes
        return (0..steps).map { i ->
            val minutesOfDay = (i * stepMinutes) % (24 * 60)
            val time = LocalTime.of(minutesOfDay / 60, minutesOfDay % 60)
            calculator.calculate(latitude, longitude, ZonedDateTime.of(date, time, zone))
        }
    }

    /** Only the samples where the sun is above the horizon — what a sun-path diagram actually plots. */
    fun sampleDaylightPath(
        latitude: Double,
        longitude: Double,
        date: LocalDate,
        zone: ZoneOffset,
        stepMinutes: Int = 15
    ): List<SolarPosition> = sampleDay(latitude, longitude, date, zone, stepMinutes).filter { it.elevationDegrees > 0.0 }
}
