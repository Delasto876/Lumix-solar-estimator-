package com.lumix.estimator.domain.simulation

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Builds a full 24-hour timeline of [SimFrame]s for a given system configuration.
 * The timeline is precomputed once (not integrated live frame-by-frame), so dragging
 * the time dial is a cheap lookup + interpolation rather than a re-simulation.
 */
object SimulationEngine {
    private const val SUNRISE_HOUR = 5.5
    private const val SUNSET_HOUR = 18.5
    private const val BATTERY_MIN_SOC_FRACTION = 0.10
    private const val BATTERY_MAX_SOC_FRACTION = 1.00
    private const val BATTERY_CHARGE_EFFICIENCY = 0.95
    private const val FLOW_EPSILON = 0.01

    // Typical residential demand shape (higher morning + evening, lower overnight/midday).
    // Normalized by its own mean, so it only shapes the curve — total daily energy still
    // comes from the quote's actual calculated average load.
    private val loadShape = doubleArrayOf(
        0.42, 0.36, 0.32, 0.30, 0.32, 0.40,
        0.62, 0.80, 0.68, 0.58, 0.52, 0.50,
        0.52, 0.54, 0.52, 0.50, 0.55, 0.72,
        0.92, 1.00, 0.94, 0.80, 0.62, 0.48
    )
    private val loadShapeMean = loadShape.average()

    fun irradianceFactor(hour: Double): Double {
        val h = hour.mod(24.0)
        if (h <= SUNRISE_HOUR || h >= SUNSET_HOUR) return 0.0
        val span = SUNSET_HOUR - SUNRISE_HOUR
        val x = (h - SUNRISE_HOUR) / span
        return sin(PI * x).pow(1.2)
    }

    fun loadFactor(hour: Double): Double {
        val h = hour.mod(24.0)
        val i0 = h.toInt().mod(24)
        val i1 = (i0 + 1).mod(24)
        val frac = h - h.toInt()
        val v = loadShape[i0] * (1 - frac) + loadShape[i1] * frac
        return v / loadShapeMean
    }

    fun buildDayTimeline(
        config: SimSystemConfig,
        cloudMultiplier: Double = 1.0,
        gridConnected: Boolean = true,
        startSocFraction: Double = 0.6,
        resolutionMinutes: Int = 5
    ): List<SimFrame> {
        val maxSocKwh = config.batteryCapacityKwh * BATTERY_MAX_SOC_FRACTION
        val minSocKwh = config.batteryCapacityKwh * BATTERY_MIN_SOC_FRACTION
        var batterySocKwh = (config.batteryCapacityKwh * startSocFraction).coerceIn(minSocKwh, maxSocKwh)
        val maxBatteryRateKw = if (config.hasBattery) min(config.batteryCapacityKwh * 0.5, config.inverterKw.coerceAtLeast(0.1)) else 0.0
        val avgLoadPerHourKw = config.avgDailyLoadKwh / 24.0
        val dt = resolutionMinutes / 60.0

        val steps = (24 * 60) / resolutionMinutes
        val frames = ArrayList<SimFrame>(steps + 1)

        for (i in 0..steps) {
            val hour = (i * resolutionMinutes) / 60.0

            val pv = (irradianceFactor(hour) * config.pvCapacityKw * cloudMultiplier)
                .coerceIn(0.0, config.inverterKw)
            val load = (loadFactor(hour) * avgLoadPerHourKw).coerceIn(0.0, config.peakLoadKw)

            var solarToHouse = min(pv, load)
            var remainingPv = pv - solarToHouse
            var remainingLoad = load - solarToHouse
            var solarToBattery = 0.0
            var solarToGrid = 0.0
            var batteryToHouse = 0.0
            var gridToHouse = 0.0
            var unmet = 0.0

            if (remainingPv > FLOW_EPSILON) {
                if (config.hasBattery) {
                    val roomKwh = (maxSocKwh - batterySocKwh).coerceAtLeast(0.0)
                    val maxChargeThisStep = min(maxBatteryRateKw, roomKwh / dt)
                    solarToBattery = min(remainingPv, maxChargeThisStep)
                    remainingPv -= solarToBattery
                }
                if (remainingPv > FLOW_EPSILON && config.gridConnectable && gridConnected) {
                    solarToGrid = remainingPv
                }
            } else if (remainingLoad > FLOW_EPSILON) {
                if (config.hasBattery) {
                    val availableKwh = (batterySocKwh - minSocKwh).coerceAtLeast(0.0)
                    val maxDischargeThisStep = min(maxBatteryRateKw, availableKwh / dt)
                    batteryToHouse = min(remainingLoad, maxDischargeThisStep)
                    remainingLoad -= batteryToHouse
                }
                if (remainingLoad > FLOW_EPSILON) {
                    if (config.gridConnectable && gridConnected) {
                        gridToHouse = remainingLoad
                    } else {
                        unmet = remainingLoad
                    }
                }
            }

            val chargeEnergyKwh = solarToBattery * dt * BATTERY_CHARGE_EFFICIENCY
            val dischargeEnergyKwh = batteryToHouse * dt
            batterySocKwh = (batterySocKwh + chargeEnergyKwh - dischargeEnergyKwh).coerceIn(minSocKwh, maxSocKwh)

            val batteryPowerKw = solarToBattery - batteryToHouse
            val gridPowerKw = gridToHouse - solarToGrid
            val socPercent = if (config.batteryCapacityKwh > 0) {
                (batterySocKwh / config.batteryCapacityKwh * 100.0).toFloat()
            } else 0f

            val status = when {
                unmet > FLOW_EPSILON -> SystemStatus.POWER_LIMITED
                solarToGrid > FLOW_EPSILON -> SystemStatus.EXPORTING_TO_GRID
                gridToHouse > FLOW_EPSILON && batteryToHouse > FLOW_EPSILON -> SystemStatus.BATTERY_PLUS_GRID
                gridToHouse > FLOW_EPSILON -> SystemStatus.GRID_POWERING_HOME
                batteryToHouse > FLOW_EPSILON && solarToHouse > FLOW_EPSILON -> SystemStatus.SOLAR_PLUS_BATTERY
                batteryToHouse > FLOW_EPSILON -> SystemStatus.BATTERY_POWERING_HOME
                solarToHouse > FLOW_EPSILON -> SystemStatus.SOLAR_POWERING_HOME
                else -> SystemStatus.IDLE
            }

            frames += SimFrame(
                hour = hour,
                pvKw = pv,
                houseLoadKw = load,
                solarToHouseKw = solarToHouse,
                solarToBatteryKw = solarToBattery,
                solarToGridKw = solarToGrid,
                batteryToHouseKw = batteryToHouse,
                gridToHouseKw = gridToHouse,
                batterySocKwh = batterySocKwh,
                batterySocPercent = socPercent,
                batteryPowerKw = batteryPowerKw,
                gridPowerKw = gridPowerKw,
                unmetLoadKw = unmet,
                status = status
            )
        }
        return frames
    }

    /** Linearly interpolates the two nearest precomputed frames for an arbitrary hour (0..24). */
    fun frameAt(timeline: List<SimFrame>, hour: Double): SimFrame {
        if (timeline.isEmpty()) error("Timeline is empty")
        val h = hour.mod(24.0)
        val stepHours = timeline[1].hour - timeline[0].hour
        val idx = (h / stepHours).toInt().coerceIn(0, timeline.size - 2)
        val a = timeline[idx]
        val b = timeline[idx + 1]
        val span = (b.hour - a.hour).takeIf { it > 0.0 } ?: return a
        val frac = ((h - a.hour) / span).coerceIn(0.0, 1.0)
        return lerpFrame(a, b, frac)
    }

    private fun lerpFrame(a: SimFrame, b: SimFrame, t: Double): SimFrame = SimFrame(
        hour = a.hour + (b.hour - a.hour) * t,
        pvKw = lerp(a.pvKw, b.pvKw, t),
        houseLoadKw = lerp(a.houseLoadKw, b.houseLoadKw, t),
        solarToHouseKw = lerp(a.solarToHouseKw, b.solarToHouseKw, t),
        solarToBatteryKw = lerp(a.solarToBatteryKw, b.solarToBatteryKw, t),
        solarToGridKw = lerp(a.solarToGridKw, b.solarToGridKw, t),
        batteryToHouseKw = lerp(a.batteryToHouseKw, b.batteryToHouseKw, t),
        gridToHouseKw = lerp(a.gridToHouseKw, b.gridToHouseKw, t),
        batterySocKwh = lerp(a.batterySocKwh, b.batterySocKwh, t),
        batterySocPercent = lerp(a.batterySocPercent.toDouble(), b.batterySocPercent.toDouble(), t).toFloat(),
        batteryPowerKw = lerp(a.batteryPowerKw, b.batteryPowerKw, t),
        gridPowerKw = lerp(a.gridPowerKw, b.gridPowerKw, t),
        unmetLoadKw = lerp(a.unmetLoadKw, b.unmetLoadKw, t),
        status = if (t < 0.5) a.status else b.status
    )

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
}
