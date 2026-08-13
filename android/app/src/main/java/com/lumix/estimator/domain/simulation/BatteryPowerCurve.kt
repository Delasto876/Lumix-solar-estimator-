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
    /** Fraction (0f..1f) of the rated max CHARGE power actually available at this SOC (0f..1f). */
    fun chargeTaperFraction(socFraction: Double): Double = when {
        socFraction < 0.80 -> 1.0
        socFraction < 0.90 -> 0.6
        socFraction < 0.95 -> 0.3
        else -> 0.1
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
