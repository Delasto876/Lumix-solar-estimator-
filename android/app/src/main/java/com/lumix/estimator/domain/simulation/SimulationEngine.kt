package com.lumix.estimator.domain.simulation

import com.lumix.estimator.solar.SolarIrradianceModel
import com.lumix.estimator.solar.SolarPosition
import com.lumix.estimator.solar.SolarPositionCalculator
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
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
    const val SUNRISE_HOUR = 5.5
    const val SUNSET_HOUR = 18.5
    // SOL/SBU reserve the battery down to this floor before ever importing from JPS —
    // a 20% DOD cutoff, per the real hybrid-inverter behavior this models. Public so the UI
    // (battery runtime estimates, cutoff display) stays in sync with the engine's own value.
    const val BATTERY_MIN_SOC_FRACTION = 0.20
    private const val BATTERY_MAX_SOC_FRACTION = 1.00
    private const val BATTERY_CHARGE_EFFICIENCY = 0.95
    private const val FLOW_EPSILON = 0.01
    private const val BACKGROUND_LOAD_FRACTION = 0.4
    private const val BACKGROUND_LOAD_FLOOR_KW = 0.15
    // Jamaican residential service is rated in amps against the 220V split-phase feed (both
    // legs) — this is the same convention TechnicalReadout uses for its own service-limit math,
    // so the two stay consistent.
    const val GRID_SERVICE_VOLTAGE = 220.0
    const val DEFAULT_GRID_SERVICE_AMPS = 30.0

    // Jamaica has no DST, so a fixed UTC offset stands in for a full timezone lookup —
    // consistent with the rest of this Jamaica-focused app (see solar/SolarPositionCalculator
    // verification, which uses the same offset).
    private val JAMAICA_ZONE = ZoneOffset.of("-05:00")
    private val solarPositionCalculator = SolarPositionCalculator()

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

    /** 0f at sunrise, 1f at sunset; null outside daylight hours. For positioning a sun marker. */
    fun daylightProgress(hour: Double): Float? {
        val h = hour.mod(24.0)
        if (h <= SUNRISE_HOUR || h >= SUNSET_HOUR) return null
        return ((h - SUNRISE_HOUR) / (SUNSET_HOUR - SUNRISE_HOUR)).toFloat()
    }

    fun loadFactor(hour: Double): Double {
        val h = hour.mod(24.0)
        val i0 = h.toInt().mod(24)
        val i1 = (i0 + 1).mod(24)
        val frac = h - h.toInt()
        val v = loadShape[i0] * (1 - frac) + loadShape[i1] * frac
        return v / loadShapeMean
    }

    /**
     * Multiplicative correction on top of [irradianceFactor]'s generic bell curve, based on the
     * real sun position for today's date at this hour vs. the roof's actual azimuth/pitch. A
     * well-oriented (south-facing, latitude-tilt) roof peaks near 1.0 at solar noon — matching
     * the generic model's own peak — so it isn't penalized relative to a site-unaware quote,
     * while a poorly-oriented roof shows genuinely reduced/asymmetric output.
     *
     * Uses [LocalDate.now] rather than a fixed reference date, since this is a live digital twin
     * meant to reflect the actual current day. [JAMAICA_ZONE] stands in for a real timezone
     * lookup, consistent with the rest of this Jamaica-focused app.
     */
    fun sitePlaneOfArrayFactor(
        hour: Double,
        latitude: Double,
        longitude: Double,
        roofAzimuthDegrees: Double,
        roofPitchDegrees: Double
    ): Double {
        val position = sitePosition(hour, latitude, longitude)
        return SolarIrradianceModel.planeOfArrayFactor(
            sunAzimuthDegrees = position.azimuthDegrees,
            sunElevationDegrees = position.elevationDegrees,
            roofAzimuthDegrees = roofAzimuthDegrees,
            roofPitchDegrees = roofPitchDegrees
        )
    }

    /** The real sun position, for today's date, at the given simulated hour and location. */
    fun sitePosition(hour: Double, latitude: Double, longitude: Double): SolarPosition {
        val h = hour.mod(24.0)
        val wholeHour = h.toInt().coerceIn(0, 23)
        val minute = ((h - wholeHour) * 60).toInt().coerceIn(0, 59)
        val dateTime = ZonedDateTime.of(LocalDate.now(), LocalTime.of(wholeHour, minute), JAMAICA_ZONE)
        return solarPositionCalculator.calculate(latitude, longitude, dateTime)
    }

    fun buildDayTimeline(
        config: SimSystemConfig,
        cloudMultiplier: Double = 1.0,
        gridConnected: Boolean = true,
        startSocFraction: Double = 0.6,
        resolutionMinutes: Int = 5,
        applianceLoadKw: Double = 0.0,
        inverterMode: InverterMode = InverterMode.SBU,
        gridChargeEnabled: Boolean = true,
        gridServiceAmps: Double = DEFAULT_GRID_SERVICE_AMPS
    ): List<SimFrame> {
        val maxSocKwh = config.batteryCapacityKwh * BATTERY_MAX_SOC_FRACTION
        val minSocKwh = config.batteryCapacityKwh * BATTERY_MIN_SOC_FRACTION
        var batterySocKwh = (config.batteryCapacityKwh * startSocFraction).coerceIn(minSocKwh, maxSocKwh)
        val maxBatteryRateKw = if (config.hasBattery) min(config.batteryCapacityKwh * 0.5, config.inverterKw.coerceAtLeast(0.1)) else 0.0
        // The day-shaped curve represents ambient/background load (standby draw, misc
        // cycling) not covered by the explicit appliance checklist; the checklist's total
        // is added on top, flat, so toggling an appliance has an immediate, visible effect
        // rather than being smoothed into an average.
        val backgroundPerHourKw = (config.avgDailyLoadKwh / 24.0 * BACKGROUND_LOAD_FRACTION).coerceAtLeast(BACKGROUND_LOAD_FLOOR_KW)
        val dt = resolutionMinutes / 60.0

        val steps = (24 * 60) / resolutionMinutes
        val frames = ArrayList<SimFrame>(steps + 1)

        for (i in 0..steps) {
            val hour = (i * resolutionMinutes) / 60.0

            val siteFactor = if (config.isSiteAware) {
                sitePlaneOfArrayFactor(
                    hour = hour,
                    latitude = config.siteLatitude!!,
                    longitude = config.siteLongitude!!,
                    roofAzimuthDegrees = config.roofAzimuthDegrees!!,
                    roofPitchDegrees = config.roofPitchDegrees!!
                )
            } else 1.0
            val pv = (irradianceFactor(hour) * config.pvCapacityKw * cloudMultiplier * siteFactor)
                .coerceIn(0.0, config.inverterKw)
            val load = (loadFactor(hour) * backgroundPerHourKw + applianceLoadKw).coerceAtLeast(0.0)

            // The grid connection is strictly import-only in every mode — solar never exports;
            // any surplus that can't be used or stored is simply curtailed.
            //
            // SOL/SBU: solar → house first, surplus solar → battery, deficit → battery down to
            // its reserve floor, only then → JPS. Grid never charges the battery.
            //
            // UTI: JPS is the primary house supply whenever connected (battery is pure outage
            // backup — it does not discharge to serve the house while grid is up), and JPS can
            // simultaneously top off the battery when [gridChargeEnabled]. Solar still serves
            // the house and charges the battery for free first, ahead of the grid either way.
            val gridConnectedNow = config.gridConnectable && gridConnected
            val utiServesHouseFromGrid = inverterMode == InverterMode.UTI && gridConnectedNow
            val allowGridChargeBattery = inverterMode == InverterMode.UTI && gridChargeEnabled && gridConnectedNow

            var solarToHouse = min(pv, load)
            var remainingPv = pv - solarToHouse
            var remainingLoad = load - solarToHouse
            var solarToBattery = 0.0
            var gridToBattery = 0.0
            var batteryToHouse = 0.0
            var gridToHouse = 0.0
            var curtailedSolar = 0.0
            var unmet = 0.0

            if (utiServesHouseFromGrid && remainingLoad > FLOW_EPSILON) {
                gridToHouse = remainingLoad
                remainingLoad = 0.0
            }

            if (config.hasBattery) {
                val roomKwh = (maxSocKwh - batterySocKwh).coerceAtLeast(0.0)
                val maxChargeThisStep = min(maxBatteryRateKw, roomKwh / dt)
                solarToBattery = min(remainingPv, maxChargeThisStep)
                remainingPv -= solarToBattery
                if (allowGridChargeBattery) {
                    gridToBattery = (maxChargeThisStep - solarToBattery).coerceAtLeast(0.0)
                }
            }
            curtailedSolar = remainingPv

            if (remainingLoad > FLOW_EPSILON) {
                if (config.hasBattery) {
                    val availableKwh = (batterySocKwh - minSocKwh).coerceAtLeast(0.0)
                    val maxDischargeThisStep = min(maxBatteryRateKw, availableKwh / dt)
                    batteryToHouse = min(remainingLoad, maxDischargeThisStep)
                    remainingLoad -= batteryToHouse
                }
                if (remainingLoad > FLOW_EPSILON) {
                    if (!utiServesHouseFromGrid && gridConnectedNow) {
                        gridToHouse = remainingLoad
                    } else {
                        unmet = remainingLoad
                    }
                }
            }

            // The utility connection itself is current-limited (a real main-breaker/service
            // rating, e.g. 30A) — this is separate from and on top of everything above. If the
            // combined grid draw would exceed it, back off battery charging first (it's the
            // lower-priority use of grid import), then throttle house import as a last resort,
            // turning any remainder into unmet load exactly like a genuine outage would.
            if (gridConnectedNow) {
                val maxGridServiceKw = gridServiceAmps * GRID_SERVICE_VOLTAGE / 1000.0
                val totalGridDraw = gridToHouse + gridToBattery
                if (totalGridDraw > maxGridServiceKw + FLOW_EPSILON) {
                    var overage = totalGridDraw - maxGridServiceKw
                    val batteryCut = min(gridToBattery, overage)
                    gridToBattery -= batteryCut
                    overage -= batteryCut
                    if (overage > FLOW_EPSILON) {
                        val houseCut = min(gridToHouse, overage)
                        gridToHouse -= houseCut
                        unmet += houseCut
                    }
                }
            }

            val chargeEnergyKwh = (solarToBattery + gridToBattery) * dt * BATTERY_CHARGE_EFFICIENCY
            val dischargeEnergyKwh = batteryToHouse * dt
            batterySocKwh = (batterySocKwh + chargeEnergyKwh - dischargeEnergyKwh).coerceIn(minSocKwh, maxSocKwh)

            val batteryPowerKw = (solarToBattery + gridToBattery) - batteryToHouse
            val gridPowerKw = gridToHouse + gridToBattery
            val socPercent = if (config.batteryCapacityKwh > 0) {
                (batterySocKwh / config.batteryCapacityKwh * 100.0).toFloat()
            } else 0f

            val status = when {
                unmet > FLOW_EPSILON -> SystemStatus.POWER_LIMITED
                gridToHouse > FLOW_EPSILON && batteryToHouse > FLOW_EPSILON -> SystemStatus.BATTERY_PLUS_GRID
                gridToHouse > FLOW_EPSILON -> SystemStatus.GRID_POWERING_HOME
                gridToBattery > FLOW_EPSILON -> SystemStatus.GRID_CHARGING_BATTERY
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
                batteryToHouseKw = batteryToHouse,
                gridToHouseKw = gridToHouse,
                gridToBatteryKw = gridToBattery,
                batterySocKwh = batterySocKwh,
                batterySocPercent = socPercent,
                batteryPowerKw = batteryPowerKw,
                gridPowerKw = gridPowerKw,
                unmetLoadKw = unmet,
                curtailedSolarKw = curtailedSolar,
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
        batteryToHouseKw = lerp(a.batteryToHouseKw, b.batteryToHouseKw, t),
        gridToHouseKw = lerp(a.gridToHouseKw, b.gridToHouseKw, t),
        gridToBatteryKw = lerp(a.gridToBatteryKw, b.gridToBatteryKw, t),
        batterySocKwh = lerp(a.batterySocKwh, b.batterySocKwh, t),
        batterySocPercent = lerp(a.batterySocPercent.toDouble(), b.batterySocPercent.toDouble(), t).toFloat(),
        batteryPowerKw = lerp(a.batteryPowerKw, b.batteryPowerKw, t),
        gridPowerKw = lerp(a.gridPowerKw, b.gridPowerKw, t),
        unmetLoadKw = lerp(a.unmetLoadKw, b.unmetLoadKw, t),
        curtailedSolarKw = lerp(a.curtailedSolarKw, b.curtailedSolarKw, t),
        status = if (t < 0.5) a.status else b.status
    )

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    /**
     * Scans forward from [fromHour] (wrapping once past midnight) for the next frame at or
     * above ~full charge. Returns null if the battery never reaches full within the day, or
     * if it isn't currently charging (turning the marker off rather than showing a stale
     * estimate from an earlier charge cycle).
     */
    fun nextBatteryFullHour(timeline: List<SimFrame>, fromHour: Double): Double? {
        if (timeline.isEmpty()) return null
        val current = frameAt(timeline, fromHour)
        if (current.batteryPowerKw <= FLOW_EPSILON || current.batterySocPercent >= 99.5f) return null

        val startIdx = timeline.indexOfFirst { it.hour >= fromHour }.let { if (it < 0) 0 else it }
        for (i in 0 until timeline.size) {
            val frame = timeline[(startIdx + i) % timeline.size]
            if (frame.batterySocPercent >= 99.5f) return frame.hour
        }
        return null
    }
}
