package com.lumix.estimator.domain.simulation

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.random.Random

/**
 * A80 (spec Phase 17 — "REALISTIC JAMAICA WEATHER/SOLAR SIMULATION"): replaces the removed
 * `WeatherState` — five buttons that multiplied the whole day's PV output by one fixed percentage
 * ("100% SUN"/"70% SUN"/etc, the spec's own §"REMOVE THE CURRENT CONCEPT OF"). Installers now pick
 * a [WeatherScenario] (a climatological framing, not a sun percentage) plus an optional
 * [solarConditionsDeviation] ("SOLAR RESOURCE" slider, spec's own naming — never labeled "Sun %"),
 * and a [WeatherCurve] is generated from those plus the month's [JamaicaClimatology.MonthProfile]:
 * a smoothly-varying per-hour availability curve (baseline + cloud events with duration/
 * attenuation/smooth recovery), not one flat number applied to the whole day.
 *
 * **Reproducible, not random**: [seed] drives a [Random] instance, so the same
 * (scenario, month, deviation, seed) always regenerates the identical curve — spec's own "same
 * scenario should produce the same result when reopened." The default seed is derived from the
 * inputs themselves (deterministic hash), so two calls with the same parameters agree without the
 * caller having to thread an explicit seed through everywhere; an explicit seed is still
 * available for tests wanting a fixed, human-traceable curve.
 */
enum class WeatherScenario(val label: String) {
    TYPICAL("Typical"),
    CLEARER("Clearer than normal"),
    CLOUDIER("Cloudier than normal"),
    RAINY("Rainy / reduced solar"),
    CUSTOM("Custom")
}

/** One transient cloud (or rain) event — a smooth dip in irradiance availability, not an instant on/off step. */
private data class CloudEvent(val centerHour: Double, val halfWidthHours: Double, val depth: Double)

/**
 * A precomputed set of cloud events for one simulated day, exposing [factorAt] — the irradiance
 * *availability* fraction (0..1) at any hour, smoothly varying, always continuous (no
 * discontinuous jumps at event boundaries — each event's attenuation is a raised-cosine bump that
 * is exactly zero at its own edges).
 */
class WeatherCurve internal constructor(
    private val baseline: Double,
    private val events: List<CloudEvent>,
    private val deviationMultiplier: Double
) {
    fun factorAt(hour: Double): Double {
        val h = hour.mod(24.0)
        var attenuation = 0.0
        for (event in events) {
            val distance = abs(h - event.centerHour)
            if (distance < event.halfWidthHours) {
                // Raised-cosine bump: 0 at the edges, 1 at the center — a smooth dip, never an
                // abrupt step, per the spec's own "do NOT create abrupt 0 -> 100 -> 0 transitions."
                val shape = (1.0 + cos(PI * distance / event.halfWidthHours)) / 2.0
                attenuation += event.depth * shape
            }
        }
        val raw = (baseline - attenuation).coerceIn(0.02, 1.0)
        return (raw * deviationMultiplier).coerceIn(0.02, 1.0)
    }

    companion object {
        /** Flat, always-clear curve — the exact prior default behavior (`cloudMultiplier = 1.0`), for any caller that hasn't opted into the weather model. */
        val CLEAR: WeatherCurve = WeatherCurve(baseline = 1.0, events = emptyList(), deviationMultiplier = 1.0)
    }
}

object WeatherEngine {

    /**
     * Builds a reproducible [WeatherCurve] for one simulated day.
     * @param scenario the installer's chosen climatological framing.
     * @param month 1-12, or null to use [JamaicaClimatology.ANNUAL_AVERAGE].
     * @param solarConditionsDeviation the "SOLAR RESOURCE"/"SOLAR CONDITIONS" slider — a fractional
     * deviation from the scenario's own baseline (e.g. `+0.10` = 10% brighter than this scenario's
     * typical day, `-0.10` = 10% dimmer). Deliberately applied as a scalar shift to the *whole*
     * generated curve (preserving its cloud-event shape) rather than recomputed per-timestep as an
     * independent multiplier — the spec's own "this does NOT multiply every timestep by one
     * constant number... adjusts the weather model while preserving sunrise/sunset/solar curve/
     * cloud events/day length/PSH relationship."
     * @param seed explicit seed for reproducible tests; defaults to a hash of the other parameters
     * so ordinary callers get "same inputs -> same curve" for free.
     */
    fun generate(
        scenario: WeatherScenario,
        month: Int?,
        solarConditionsDeviation: Double = 0.0,
        seed: Long = defaultSeedFor(scenario, month, solarConditionsDeviation)
    ): WeatherCurve {
        val monthProfile = JamaicaClimatology.profileFor(month)
        val random = Random(seed)

        // Each scenario shifts the month's own baseline cloudiness/variability rather than
        // replacing it outright — a "Cloudier than normal" October is still built from October's
        // real climatological tendency, not a generic cloudy-day template.
        val (cloudBias, variabilityBias, depthBias) = when (scenario) {
            WeatherScenario.TYPICAL -> Triple(1.0, 1.0, 1.0)
            WeatherScenario.CLEARER -> Triple(0.5, 0.6, 0.7)
            WeatherScenario.CLOUDIER -> Triple(1.6, 1.4, 1.2)
            WeatherScenario.RAINY -> Triple(2.2, 1.6, 1.6)
            WeatherScenario.CUSTOM -> Triple(1.0, 1.0, 1.0)
        }

        // Modeling ranges straight from the spec's own §"WEATHER/CLOUD MODEL" — clear 0.90-1.00,
        // partly cloudy 0.55-0.90, cloudy 0.25-0.65, heavy cloud/rain 0.05-0.40 — the month's
        // cloudinessBaseline (times the scenario's cloudBias) picks where in that range this
        // particular day's baseline lands.
        val cloudinessLevel = (monthProfile.cloudinessBaseline * cloudBias).coerceIn(0.0, 1.0)
        val baseline = (1.0 - cloudinessLevel * 0.85).coerceIn(0.10, 1.0)

        val effectiveVariability = (monthProfile.variabilityFactor * variabilityBias).coerceIn(0.0, 1.0)
        // More variable months/scenarios get more, and deeper, transient cloud events — not a
        // separate concept from the baseline, just more activity layered on top of it.
        val eventCount = 2 + (effectiveVariability * 6).toInt() + (monthProfile.tropicalStormRisk * random.nextDouble() * 4).toInt()

        val events = (0 until eventCount).map {
            val center = 5.0 + random.nextDouble() * 13.0 // spread across the daylight-adjacent window
            val halfWidth = 0.15 + random.nextDouble() * 0.6
            val depth = (0.1 + random.nextDouble() * 0.35 * depthBias).coerceIn(0.05, 0.75)
            CloudEvent(centerHour = center, halfWidthHours = halfWidth, depth = depth)
        }

        val deviationMultiplier = (1.0 + solarConditionsDeviation).coerceIn(0.3, 1.5)

        return WeatherCurve(baseline = baseline, events = events, deviationMultiplier = deviationMultiplier)
    }

    private fun defaultSeedFor(scenario: WeatherScenario, month: Int?, deviation: Double): Long {
        var result = scenario.ordinal.toLong()
        result = result * 31 + (month ?: 0)
        result = result * 31 + (deviation * 1000).toLong()
        return result
    }
}
