package com.lumix.estimator.domain.simulation

import kotlin.math.pow

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
     * A87 (spec Phase 24 §5 — "BATTERY NEAR FULL — CRITICAL... charging should transition into a
     * tapering/CV-style region near full SOC... 90%: substantial charging. 95%: charging begins
     * reducing. 97%: charging noticeably reduced. 98-99%: charging increasingly limited. 100%:
     * charging = 0W... DO NOT hard-code 97%=X, 98%=X, 99%=X... unless those values come from an
     * actual battery model/specification. Use a configurable physically reasonable taper"): a
     * continuous CV-style curve — full rated power up to [TAPER_START_FRACTION], then a quadratic
     * decay (stays closer to full just past the start, falls away increasingly steeply approaching
     * 100%) down to exactly 0.0 at 100% SOC. Replaces a coarser 4-band step function that held a
     * flat 0.1 fraction across the ENTIRE 95-100% range — not "increasingly limited" through
     * 96/97/98/99% the way this worked example describes. At the constants below: ~90% SOC → ~44%
     * of rated power, ~95% → ~11%, ~97% → ~4%, ~99% → <1%, 100% → 0% — the right qualitative shape,
     * still a generic engineering placeholder rather than measured cell data (see this object's own
     * class doc), and still just two constants to replace if the catalog ever gains a real
     * per-model CV-tail curve.
     */
    private const val TAPER_START_FRACTION = 0.85
    private const val TAPER_EXPONENT = 2.0

    /** Fraction (0f..1f) of the rated max CHARGE power actually available at this SOC (0f..1f). */
    fun chargeTaperFraction(socFraction: Double): Double {
        if (socFraction <= TAPER_START_FRACTION) return 1.0
        if (socFraction >= 1.0) return 0.0
        val x = (1.0 - socFraction) / (1.0 - TAPER_START_FRACTION)
        return x.pow(TAPER_EXPONENT).coerceIn(0.0, 1.0)
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
