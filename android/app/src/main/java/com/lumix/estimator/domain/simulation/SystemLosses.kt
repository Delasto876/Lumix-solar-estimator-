package com.lumix.estimator.domain.simulation

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

/**
 * Real-world PV production losses, modeled as separate, named factors rather than one blended
 * derate — so the Technical panel can show which real-world cause is actually costing the most,
 * the same way PVWatts-style solar design tools break production down. Typical figures for a
 * well-installed residential hybrid system; not a substitute for a real irradiance/thermal model,
 * but a meaningfully closer approximation of realized output than an ideal, loss-free panel.
 */
object SystemLosses {
    /** DC → AC conversion loss at the inverter. */
    const val INVERTER_EFFICIENCY = 0.97

    /** Resistive loss in the DC wiring between panels and inverter. */
    const val DC_WIRING_EFFICIENCY = 0.98

    /** Dust/soiling, connector loss, panel mismatch, and system availability, combined. */
    const val SOILING_AVAILABILITY_EFFICIENCY = 0.97

    /** Combined non-temperature system losses — inverter × wiring × soiling/availability. */
    val fixedSystemEfficiency: Double
        get() = INVERTER_EFFICIENCY * DC_WIRING_EFFICIENCY * SOILING_AVAILABILITY_EFFICIENCY

    // Crystalline-silicon panels lose output as they heat above Standard Test Conditions (25°C) —
    // a typical datasheet temperature coefficient is about -0.4%/°C.
    private const val TEMP_COEFFICIENT_PER_C = -0.004
    private const val STC_TEMP_C = 25.0

    // Nominal Operating Cell Temperature (NOCT): a typical panel datasheet figure — the cell
    // temperature reached at 800 W/m² irradiance, 20°C ambient, light wind.
    private const val NOCT_C = 45.0

    /**
     * Jamaica's fairly flat, warm ambient temperature curve — a mild coastal diurnal swing
     * (mean ~27.5°C, peaking mid-afternoon, dipping before dawn), not a live weather feed.
     */
    fun ambientTemperatureC(hour: Double): Double {
        val h = hour.mod(24.0)
        val meanTempC = 27.5
        val diurnalSwingC = 3.5
        val peakHour = 15.0 // mid-afternoon
        val phase = (h - peakHour) / 24.0 * 2 * PI
        return meanTempC + diurnalSwingC * cos(phase)
    }

    /** Panel cell temperature from ambient + irradiance, via the standard NOCT approximation. */
    fun cellTemperatureC(ambientC: Double, irradianceFraction: Double): Double {
        val irradianceWm2 = irradianceFraction.coerceIn(0.0, 1.0) * 1000.0 // 1000 W/m² = full-sun reference
        return ambientC + (NOCT_C - 20.0) / 800.0 * irradianceWm2
    }

    /** Multiplicative derate from panel temperature — 1.0 at/below STC, less above it. */
    fun temperatureDerate(cellTempC: Double): Double =
        max(0.0, 1.0 + TEMP_COEFFICIENT_PER_C * (cellTempC - STC_TEMP_C))
}
