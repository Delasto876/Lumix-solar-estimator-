package com.lumix.estimator.domain.simulation

/**
 * Simplified charge/discharge power tapering, applied on top of a battery's flat rated
 * max charge/discharge kW ([SimSystemConfig.batteryMaxChargeKw]/[SimSystemConfig.batteryMaxDischargeKw]).
 * Real lithium packs don't accept or deliver full rated power right up to the very top or
 * bottom of their usable range — this is a generic taper (this catalog carries one battery
 * chemistry, LiFePO4, with no manufacturer-specific charge-curve data to read instead), not
 * measured cell data. If the catalog ever gains a real per-model curve, that would override
 * this rather than feed into it.
 */
object BatteryPowerCurve {
    /**
     * A87 (spec Phase 24 §5) + 2026-08-18 trickle-charge fix: a CV-style absorption taper — full
     * rated power up to [TAPER_START_FRACTION] (85%), then a linear taper toward zero as SOC
     * approaches 100%, but held at a nonzero [TRICKLE_FLOOR_FRACTION] so the last couple of percent
     * actually *complete* rather than asymptoting and stalling.
     *
     * The earlier quadratic-to-zero curve had the trickle current collapse toward zero near the
     * top (~0.4% of rated at 99% SOC), so the final 1% took hours of vanishing current and the
     * battery would sit at 99% into the evening and never read 100% — exactly the "trickle charge
     * doesn't mean stop at 99%" bug an installer flagged. A real absorption/float stage keeps a
     * small but genuine trickle current flowing until the pack is actually full. Holding the taper
     * at a 10% floor gives that: from ~97% the pack tops off over roughly the next half hour (for a
     * typical battery/charger ratio) instead of stalling, and the hard room-based clamp in
     * [SimulationEngine.buildDayTimeline] (`roomKwh / dt`) finishes the exact top-off to 100% and
     * holds it there. Still a generic engineering curve, not measured cell data — two constants to
     * replace if the catalog ever gains a real per-model CV-tail.
     */
    private const val TAPER_START_FRACTION = 0.85
    private const val TRICKLE_FLOOR_FRACTION = 0.10

    /** Fraction (0f..1f) of the rated max CHARGE power actually available at this SOC (0f..1f). */
    fun chargeTaperFraction(socFraction: Double): Double {
        if (socFraction <= TAPER_START_FRACTION) return 1.0
        if (socFraction >= 1.0) return 0.0
        // Linear from full rated at TAPER_START toward zero at 100%, floored so the trickle never
        // collapses to a near-zero, never-completing current below 100%.
        val linear = (1.0 - socFraction) / (1.0 - TAPER_START_FRACTION)
        return linear.coerceIn(TRICKLE_FLOOR_FRACTION, 1.0)
    }

    /**
     * Fraction (0f..1f) of the rated max DISCHARGE power actually available at this SOC,
     * measured as headroom above [minSocFraction] (the configured reserve floor) rather than
     * headroom above absolute zero — the taper narrows as the battery nears its own cutoff,
     * not the physical bottom of the cell.
     */
    fun dischargeTaperFraction(socFraction: Double, minSocFraction: Double): Double {
        val headroom = socFraction - minSocFraction
        return when {
            headroom > 0.10 -> 1.0
            headroom > 0.05 -> 0.6
            headroom > 0.0 -> 0.3
            else -> 0.0
        }
    }
}
