package com.lumix.estimator.solar

import java.time.LocalTime

/** Where the sun actually is, for a given location and instant — degrees clockwise from true north. */
data class SolarPosition(
    val azimuthDegrees: Double,
    val elevationDegrees: Double,
    val zenithDegrees: Double,
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
    val solarNoon: LocalTime?
)
