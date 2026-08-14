package com.lumix.estimator.domain

/**
 * A60 (spec §13–15 — "DO NOT hard-code PSH = 5.5 for every location... use site location...
 * If detailed solar resource data is unavailable, default planning PSH: 5.5, but clearly mark it
 * as an estimate"): a parish-level starting point for [QuoteInputs.peakSunHours] instead of one
 * flat national figure, without adding a location/maps dependency this app deliberately doesn't
 * carry (Google Maps/geolocation were removed entirely in A17/A18).
 *
 * **What this is, honestly**: a rough regional split within the exact range the spec's own cited
 * sources give for Jamaica as a whole — Global Solar Atlas GHI 4.18–5.90 kWh/m²/day, Jamaica
 * Ministry of Energy ~5 kWh/m²/day, Jamaica Integrated Resource Plan ~5.5–6.0 kWh/m²/day for "much
 * of Jamaica" — positioned using well-established, genuinely public knowledge about each parish's
 * general climate (St. Elizabeth is widely known as Jamaica's driest parish and agricultural
 * "breadbasket"; Portland is widely known as its wettest, in the Blue Mountains' rain shadow).
 * **This is NOT measured per-parish satellite irradiance data** — no such dataset is available to
 * this app — and every place it's surfaced says so. It is a real improvement over "always 5.5
 * regardless of where the site actually is," not a claim of engineering-grade site data.
 */
object SolarResource {

    /** The one prior flat default — still the fallback for a blank/unrecognized parish. */
    const val NATIONAL_DEFAULT_PSH = 5.5

    private val parishPshEstimate: Map<String, Double> = mapOf(
        "St. Elizabeth" to 5.8, // Jamaica's driest parish, "breadbasket" — least cloud cover
        "Kingston" to 5.7,
        "St. Catherine" to 5.7,
        "Clarendon" to 5.7,
        "Westmoreland" to 5.6,
        "St. Andrew" to 5.6,
        "Hanover" to 5.5,
        "St. James" to 5.5,
        "Manchester" to 5.4, // interior highlands — cooler, more mist/cloud
        "St. Ann" to 5.3,
        "Trelawny" to 5.3, // Cockpit Country karst terrain — more rainfall
        "St. Thomas" to 5.2,
        "St. Mary" to 5.1,
        "Portland" to 5.0 // Jamaica's wettest parish — Blue/John Crow Mountains rainfall
    )

    /** [NATIONAL_DEFAULT_PSH] for a blank or unrecognized parish — never throws, never guesses beyond the disclosed table. */
    fun estimatedPshFor(parish: String): Double = parishPshEstimate[parish] ?: NATIONAL_DEFAULT_PSH
}
