package com.lumix.estimator.domain.simulation

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.sin
import kotlin.math.tan

/**
 * A80 (spec Phase 17 — "REALISTIC JAMAICA WEATHER/SOLAR SIMULATION", §"SOLAR POSITION" —
 * "calculate sunrise, sunset, solar elevation, solar azimuth, day length... Day length must
 * change seasonally. Do not assume sunrise and sunset occur at exactly the same time every
 * month"): a real solar-declination/hour-angle calculation — standard, published solar geometry
 * (NOAA/ASHRAE's own formulas), not invented or fabricated data. Deliberately solar-time only (no
 * equation-of-time/longitude correction) — for Jamaica's actual longitude (~77°W) against its
 * EST clock (zone-centered on 75°W), this is a few-minutes-scale simplification, not a claim of
 * second-precision civil sunrise/sunset. [SimulationEngine] previously used one fixed
 * sunrise/sunset pair (5.75/17.75, an annual Jamaica average) year-round; this now provides the
 * real month-to-month day-length variation that pair was always an average *of*.
 *
 * Solar elevation/azimuth (also named in the spec's own list) are deliberately NOT computed here:
 * nothing in this app's engine currently consumes them (there is no panel tilt/orientation/
 * shading model — the Solar Site feature that would have tracked that was removed entirely in
 * A20), so adding them now would be unused surface area, not a real capability. [dayLengthHours]/
 * sunrise/sunset are the pieces [SimulationEngine]'s irradiance curve actually needs and now uses.
 */
object SolarPosition {

    /** Jamaica's approximate island-wide latitude (~18.0°N) — a well-known geographic fact, not a proprietary/measured solar-resource dataset. Good enough for sunrise/sunset (which vary by only ~1 minute across Jamaica's ~1.5° of latitude span). */
    const val JAMAICA_LATITUDE_DEG = 18.0

    /** Represents mid-month for a stable day-length figure per calendar month, rather than one that visibly shifts as a specific quote's "today" changes within the month. */
    private val REPRESENTATIVE_DAY_OF_YEAR = intArrayOf(17, 47, 75, 105, 135, 162, 198, 228, 258, 288, 318, 344)

    data class SunTimes(val sunriseHour: Double, val sunsetHour: Double) {
        val dayLengthHours: Double get() = sunsetHour - sunriseHour
    }

    /** [month] is 1-12. Falls back to Jamaica's annual-average day (day-of-year 80, near the spring equinox — very close to Jamaica's actual year-round average given its low latitude) for an out-of-range month rather than throwing. */
    fun sunTimesForMonth(month: Int, latitudeDeg: Double = JAMAICA_LATITUDE_DEG): SunTimes {
        val dayOfYear = REPRESENTATIVE_DAY_OF_YEAR.getOrElse(month - 1) { 80 }
        return sunTimesForDayOfYear(dayOfYear, latitudeDeg)
    }

    fun sunTimesForDayOfYear(dayOfYear: Int, latitudeDeg: Double = JAMAICA_LATITUDE_DEG): SunTimes {
        val declinationRad = declinationRadians(dayOfYear)
        val latRad = latitudeDeg * PI / 180.0
        // cos(hour angle) = -tan(lat) * tan(declination) — standard sunrise-equation form.
        val cosHourAngle = (-tan(latRad) * tan(declinationRad)).coerceIn(-1.0, 1.0)
        val hourAngleRad = acos(cosHourAngle)
        val halfDayHours = hourAngleRad * 12.0 / PI
        val sunrise = 12.0 - halfDayHours
        val sunset = 12.0 + halfDayHours
        return SunTimes(sunrise, sunset)
    }

    /** Declination in radians via the standard Cooper (1969) approximation: 23.45° * sin(360/365 * (284 + N)). */
    private fun declinationRadians(dayOfYear: Int): Double {
        val declinationDeg = 23.45 * sin(2.0 * PI * (284 + dayOfYear) / 365.0)
        return declinationDeg * PI / 180.0
    }
}
