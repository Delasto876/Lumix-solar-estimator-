package com.lumix.estimator.solar

import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sun position from latitude/longitude/date-time, using the NOAA Solar Calculator's published
 * closed-form equations (themselves derived from Jean Meeus' *Astronomical Algorithms*) —
 * accurate to roughly ±0.01° for any date within a few centuries of today, which is far
 * tighter than every other uncertainty in this module (a hand-traced roof polygon, panel
 * placement, shading). No live ephemeris lookup is needed; time-of-day drives it directly, so
 * dragging the simulation's time dial can call this every frame cheaply.
 */
class SolarPositionCalculator {

    fun calculate(latitude: Double, longitude: Double, dateTime: ZonedDateTime): SolarPosition {
        val utc = dateTime.withZoneSameInstant(ZoneOffset.UTC)
        val jd = julianDay(utc)
        val t = julianCentury(jd)

        val l0 = geomMeanLongitudeSunDeg(t)
        val m = geomMeanAnomalySunDeg(t)
        val e = eccentricityEarthOrbit(t)
        val c = sunEqOfCenterDeg(m, t)
        val trueLongDeg = l0 + c
        val apparentLongDeg = sunApparentLongDeg(trueLongDeg, t)
        val obliquityCorrDeg = obliquityCorrectedDeg(t)
        val declinationDeg = sunDeclinationDeg(obliquityCorrDeg, apparentLongDeg)
        val eqTimeMinutes = equationOfTimeMinutes(t, l0, e, m, obliquityCorrDeg)

        val tzOffsetHours = dateTime.offset.totalSeconds / 3600.0
        val minutesOfDay = dateTime.hour * 60.0 + dateTime.minute + dateTime.second / 60.0
        val trueSolarTimeMinutes = (minutesOfDay + eqTimeMinutes + 4.0 * longitude - 60.0 * tzOffsetHours).mod(1440.0)

        var hourAngleDeg = trueSolarTimeMinutes / 4.0 - 180.0
        if (hourAngleDeg < -180.0) hourAngleDeg += 360.0

        val latRad = Math.toRadians(latitude)
        val decRad = Math.toRadians(declinationDeg)
        val haRad = Math.toRadians(hourAngleDeg)

        val cosZenith = (sin(latRad) * sin(decRad) + cos(latRad) * cos(decRad) * cos(haRad)).coerceIn(-1.0, 1.0)
        val zenithDeg = Math.toDegrees(acos(cosZenith))
        val elevationDeg = 90.0 - zenithDeg
        val azimuthDeg = solarAzimuthDeg(latRad, decRad, Math.toRadians(zenithDeg), hourAngleDeg)

        val haSunriseDeg = sunriseHourAngleDeg(latitude, declinationDeg)
        val solarNoonMinutes = 720.0 - 4.0 * longitude - eqTimeMinutes + tzOffsetHours * 60.0
        val solarNoon = minutesToLocalTime(solarNoonMinutes)
        val sunrise = haSunriseDeg?.let { minutesToLocalTime(solarNoonMinutes - it * 4.0) }
        val sunset = haSunriseDeg?.let { minutesToLocalTime(solarNoonMinutes + it * 4.0) }

        return SolarPosition(
            azimuthDegrees = azimuthDeg,
            elevationDegrees = elevationDeg,
            zenithDegrees = zenithDeg,
            sunrise = sunrise,
            sunset = sunset,
            solarNoon = solarNoon
        )
    }

    private fun julianDay(utc: ZonedDateTime): Double {
        var year = utc.year
        var month = utc.monthValue
        val day = utc.dayOfMonth
        val hourFraction = utc.hour + utc.minute / 60.0 + utc.second / 3600.0
        if (month <= 2) {
            year -= 1
            month += 12
        }
        val a = floor(year / 100.0)
        val b = 2 - a + floor(a / 4.0)
        val jd0 = floor(365.25 * (year + 4716)) + floor(30.6001 * (month + 1)) + day + b - 1524.5
        return jd0 + hourFraction / 24.0
    }

    private fun julianCentury(jd: Double) = (jd - 2451545.0) / 36525.0

    private fun geomMeanLongitudeSunDeg(t: Double): Double =
        (280.46646 + t * (36000.76983 + t * 0.0003032)).mod(360.0)

    private fun geomMeanAnomalySunDeg(t: Double) = 357.52911 + t * (35999.05029 - 0.0001537 * t)

    private fun eccentricityEarthOrbit(t: Double) = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)

    private fun sunEqOfCenterDeg(mDeg: Double, t: Double): Double {
        val mRad = Math.toRadians(mDeg)
        return sin(mRad) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * mRad) * (0.019993 - 0.000101 * t) +
            sin(3 * mRad) * 0.000289
    }

    private fun sunApparentLongDeg(trueLongDeg: Double, t: Double): Double {
        val omega = 125.04 - 1934.136 * t
        return trueLongDeg - 0.00569 - 0.00478 * sin(Math.toRadians(omega))
    }

    private fun meanObliquityOfEclipticDeg(t: Double): Double {
        val arcSeconds = 21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))
        return 23.0 + (26.0 + arcSeconds / 60.0) / 60.0
    }

    private fun obliquityCorrectedDeg(t: Double): Double {
        val e0 = meanObliquityOfEclipticDeg(t)
        val omega = 125.04 - 1934.136 * t
        return e0 + 0.00256 * cos(Math.toRadians(omega))
    }

    private fun sunDeclinationDeg(obliquityCorrDeg: Double, apparentLongDeg: Double): Double {
        val sinDec = sin(Math.toRadians(obliquityCorrDeg)) * sin(Math.toRadians(apparentLongDeg))
        return Math.toDegrees(asin(sinDec.coerceIn(-1.0, 1.0)))
    }

    private fun equationOfTimeMinutes(t: Double, l0Deg: Double, e: Double, mDeg: Double, obliquityCorrDeg: Double): Double {
        val y = tan(Math.toRadians(obliquityCorrDeg) / 2.0).let { it * it }
        val l0Rad = Math.toRadians(l0Deg)
        val mRad = Math.toRadians(mDeg)
        val eqTimeRad = y * sin(2 * l0Rad) -
            2 * e * sin(mRad) +
            4 * e * y * sin(mRad) * cos(2 * l0Rad) -
            0.5 * y * y * sin(4 * l0Rad) -
            1.25 * e * e * sin(2 * mRad)
        return 4.0 * Math.toDegrees(eqTimeRad)
    }

    /** Standard tilted/horizontal-surface azimuth formula (Meeus/NOAA), 0..360 clockwise from north. */
    private fun solarAzimuthDeg(latRad: Double, decRad: Double, zenithRad: Double, hourAngleDeg: Double): Double {
        val denom = cos(latRad) * sin(zenithRad)
        if (abs(denom) < 1e-6) return 0.0
        val cosAz = ((sin(latRad) * cos(zenithRad)) - sin(decRad)) / denom
        val azBaseDeg = Math.toDegrees(acos(cosAz.coerceIn(-1.0, 1.0)))
        return if (hourAngleDeg > 0.0) (azBaseDeg + 180.0).mod(360.0) else (540.0 - azBaseDeg).mod(360.0)
    }

    /** Hour angle (deg) at sunrise/sunset, using the standard 90.833° horizon (atmospheric refraction + solar radius). Null in polar day/night. */
    private fun sunriseHourAngleDeg(latitudeDeg: Double, declinationDeg: Double): Double? {
        val latRad = Math.toRadians(latitudeDeg)
        val decRad = Math.toRadians(declinationDeg)
        val cosH = cos(Math.toRadians(90.833)) / (cos(latRad) * cos(decRad)) - tan(latRad) * tan(decRad)
        if (cosH < -1.0 || cosH > 1.0) return null
        return Math.toDegrees(acos(cosH))
    }

    private fun minutesToLocalTime(minutes: Double): LocalTime {
        val wrapped = minutes.mod(1440.0)
        val totalSeconds = (wrapped * 60.0).roundToLong().coerceIn(0L, 86399L)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return LocalTime.of(h.toInt(), m.toInt(), s.toInt())
    }
}
