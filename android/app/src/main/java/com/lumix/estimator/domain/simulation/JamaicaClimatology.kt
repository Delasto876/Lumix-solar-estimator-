package com.lumix.estimator.domain.simulation

/**
 * A80 (spec Phase 17 — "REALISTIC JAMAICA WEATHER/SOLAR SIMULATION", §"MONTHLY WEATHER MODEL"):
 * Jamaica-wide, month-by-month climatological *tendencies* — not per-parish, not measured
 * satellite/ground-station data, and explicitly not a claim that any given day in a given month
 * will behave this way ("These are climatological tendencies, NOT deterministic weather rules...
 * do not assume October = rainy every day or January = sunny every day").
 *
 * **What this is, honestly**: a directional model built from the exact seasonal pattern the
 * installer's own spec described in this same message — December-March generally drier, May a
 * secondary rainfall peak, October the primary rainfall peak, July relatively dry, and rising
 * tropical-storm risk across August-October — turned into four relative factors per month. These
 * are modeled numbers expressing that documented direction, not figures sourced from a specific
 * dataset (Global Solar Atlas, Meteorological Service of Jamaica, etc. are all genuine sources for
 * a future, more precise version of this table — see [SOURCE_NOTE]).
 *
 * Combines multiplicatively with [com.lumix.estimator.domain.SolarResource]'s per-parish annual
 * PSH estimate (already disclosed the same way) — this table supplies the *seasonal* shape, that
 * one supplies the *regional* shape; neither claims to be the other.
 */
object JamaicaClimatology {

    const val SOURCE_NOTE = "Modeled seasonal tendency, not measured historical weather data. " +
        "Directional only — see this object's own doc for what it is and isn't."

    /**
     * One calendar month's climatological tendencies.
     * @param solarResourceFactor Relative multiplier on the site's annual-average PSH (1.0 = average; >1.0 = typically sunnier than the annual average; <1.0 = typically less sunny).
     * @param cloudinessBaseline 0..1 typical baseline cloud attenuation on an otherwise-clear day this month (higher = hazier/more baseline cloud cover even on a "typical" day).
     * @param variabilityFactor 0..1 how much day-to-day irradiance is expected to swing this month (higher = less predictable — more/bigger cloud events).
     * @param tropicalStormRisk 0..1 relative likelihood of a tropical-system-driven low-solar day this month (0 outside the June-November Atlantic hurricane season's practical Jamaica-relevant window).
     */
    data class MonthProfile(
        val solarResourceFactor: Double,
        val cloudinessBaseline: Double,
        val variabilityFactor: Double,
        val tropicalStormRisk: Double
    )

    private val profiles: Map<Int, MonthProfile> = mapOf(
        1 to MonthProfile(1.08, 0.20, 0.15, 0.00),  // January — generally drier, stronger sunshine potential
        2 to MonthProfile(1.08, 0.20, 0.15, 0.00),  // February — same drier-season tendency
        3 to MonthProfile(1.05, 0.22, 0.18, 0.00),  // March — still in the drier season, easing toward spring
        4 to MonthProfile(1.00, 0.28, 0.22, 0.00),  // April — transition toward the wetter half of the year
        5 to MonthProfile(0.90, 0.38, 0.32, 0.05),  // May — secondary rainfall peak, increased variability
        6 to MonthProfile(0.92, 0.35, 0.30, 0.15),  // June — variable, start of hurricane season
        7 to MonthProfile(1.02, 0.25, 0.20, 0.15),  // July — relatively drier tendency mid-season
        8 to MonthProfile(0.95, 0.32, 0.30, 0.30),  // August — variable, tropical weather risk rising
        9 to MonthProfile(0.90, 0.36, 0.32, 0.35),  // September — increasing tropical weather influence
        10 to MonthProfile(0.82, 0.42, 0.35, 0.30), // October — primary rainfall peak
        11 to MonthProfile(0.92, 0.32, 0.25, 0.10), // November — transition back toward the drier season
        12 to MonthProfile(1.05, 0.22, 0.16, 0.02)  // December — generally drier
    )

    /** Jamaica-wide annual average — used when no month has been selected, matching [com.lumix.estimator.domain.SolarResource]'s own "no site-specific data yet" fallback philosophy (a real 1.0/typical-baseline profile, not an arbitrarily different one). */
    val ANNUAL_AVERAGE = MonthProfile(solarResourceFactor = 1.0, cloudinessBaseline = 0.28, variabilityFactor = 0.23, tropicalStormRisk = 0.12)

    /** [month] is 1-12; any other value returns [ANNUAL_AVERAGE] rather than throwing. */
    fun profileFor(month: Int?): MonthProfile = month?.let { profiles[it] } ?: ANNUAL_AVERAGE
}
