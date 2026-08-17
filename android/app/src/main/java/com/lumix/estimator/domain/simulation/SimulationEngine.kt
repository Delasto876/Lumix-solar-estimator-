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
    // A representative Jamaica daylight window, not a date/location-specific astronomical
    // calculation — sunrise ~5:30-6:00am, sunset ~5:30-6:00pm, so the midpoint of each
    // (5:45am/5:45pm) gives a clean ~12h day, matching Jamaica's real near-equatorial average.
    // This is the solar *curve's* window (irradianceFactor's own shape) — kept fixed regardless
    // of site-specific PSH (A70 doesn't widen/narrow daylight hours, only the curve's amplitude —
    // see REFERENCE_CURVE_PSH_HOURS below).
    const val SUNRISE_HOUR = 5.75
    const val SUNSET_HOUR = 17.75
    /**
     * A70 (spec §13/§66 — the simulation's daily energy yield must actually vary with the
     * installer's entered site-specific PSH, not just the resulting array size): the curve's own
     * implied "effective full-sun hours" at its native amplitude (peak = 1.0 at solar noon) —
     * hand-integrated numerically as `(SUNSET_HOUR - SUNRISE_HOUR) * integral(sin(pi*x)^1.2, 0, 1)`
     * = 12.0 * 0.600708 = 7.2085. Before this round, the curve always produced this many effective
     * sun-hours' worth of energy regardless of what PSH the installer entered or which parish was
     * selected — decoupled from [SystemCalculator]'s own sizing formula (`pvKw = dailyKwh / psh`),
     * which DOES vary by entered PSH. [SimSystemConfig.pshHours] now scales the curve's amplitude
     * by `pshHours / REFERENCE_CURVE_PSH_HOURS`, so a design sized against a weaker PSH (a bigger
     * array, to compensate for a worse solar resource) now also *simulates* weaker per-panel
     * output, matching how it was actually sized — and, as a direct consequence, the simulated
     * daily PV energy (before temperature/system-loss derates) now works out to approximately
     * `pvKw * pshHours` = the exact `designDailyKwh` the array was sized to cover, instead of an
     * unrelated fixed ~7.2h/day regardless of site. This deliberately scales curve *amplitude*,
     * not [SUNRISE_HOUR]/[SUNSET_HOUR] (a real longer/shorter day) — a simplification, not a claim
     * that low-PSH parishes have shorter days; see the A69 README section for the alternative
     * reading this round's install-time decision ruled out.
     */
    const val REFERENCE_CURVE_PSH_HOURS = 7.2085
    // SOL/SBU reserve the battery down to this floor before ever importing from JPS —
    // a 20% DOD cutoff, per the real hybrid-inverter behavior this models. Public so the UI
    // (battery runtime estimates, cutoff display) stays in sync with the engine's own value.
    const val BATTERY_MIN_SOC_FRACTION = 0.20
    private const val BATTERY_MAX_SOC_FRACTION = 1.00
    private const val FLOW_EPSILON = 0.01
    private const val BACKGROUND_LOAD_FRACTION = 0.4
    private const val BACKGROUND_LOAD_FLOOR_KW = 0.15
    // Jamaican residential service is rated in amps against the 220V split-phase feed (both
    // legs) — this is the same convention TechnicalReadout uses for its own service-limit math,
    // so the two stay consistent.
    const val GRID_SERVICE_VOLTAGE = 220.0
    const val DEFAULT_GRID_SERVICE_AMPS = 30.0

    // Typical WEEKDAY residential demand shape (higher morning + evening, lower overnight/
    // midday — the working household is mostly out of the house 8am-5pm, per section 18).
    // Normalized by its own mean, so it only shapes the curve — total daily energy still
    // comes from the quote's actual calculated average load.
    private val weekdayLoadShape = doubleArrayOf(
        0.42, 0.36, 0.32, 0.30, 0.32, 0.40,
        0.62, 0.80, 0.68, 0.58, 0.52, 0.50,
        0.52, 0.54, 0.52, 0.50, 0.55, 0.72,
        0.92, 1.00, 0.94, 0.80, 0.62, 0.48
    )
    // WEEKEND shape (Saturday/Sunday, section 27): the same morning/evening peaks, but no
    // 8am-5pm occupancy dip — daytime hours stay meaningfully higher because the household
    // is actually home (laundry, cleaning, cooking, kids), not away at work/school.
    private val weekendLoadShape = doubleArrayOf(
        0.46, 0.38, 0.34, 0.32, 0.34, 0.42,
        0.60, 0.72, 0.70, 0.68, 0.66, 0.66,
        0.68, 0.68, 0.66, 0.64, 0.66, 0.78,
        0.90, 0.96, 0.90, 0.78, 0.64, 0.52
    )
    private val weekdayLoadShapeMean = weekdayLoadShape.average()
    private val weekendLoadShapeMean = weekendLoadShape.average()

    private fun loadShapeFor(dayType: DayType) = if (dayType == DayType.WEEKDAY) weekdayLoadShape else weekendLoadShape
    private fun loadShapeMeanFor(dayType: DayType) = if (dayType == DayType.WEEKDAY) weekdayLoadShapeMean else weekendLoadShapeMean

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

    fun loadFactor(hour: Double, dayType: DayType = DayType.WEEKDAY): Double {
        val shape = loadShapeFor(dayType)
        val h = hour.mod(24.0)
        val i0 = h.toInt().mod(24)
        val i1 = (i0 + 1).mod(24)
        val frac = h - h.toInt()
        val v = shape[i0] * (1 - frac) + shape[i1] * frac
        return v / loadShapeMeanFor(dayType)
    }

    fun buildDayTimeline(
        config: SimSystemConfig,
        cloudMultiplier: Double = 1.0,
        gridConnected: Boolean = true,
        startSocFraction: Double = 0.6,
        resolutionMinutes: Int = 5,
        applianceLoadKw: Double = 0.0,
        applianceStates: Map<SimApplianceType, ApplianceState> = emptyMap(),
        inverterMode: InverterMode = InverterMode.SBU,
        gridChargeEnabled: Boolean = true,
        gridServiceAmps: Double = DEFAULT_GRID_SERVICE_AMPS,
        dayType: DayType = DayType.WEEKDAY,
        /** A54: first frame's clock hour — 0.0 for a normal midnight-to-midnight day (every existing
         * caller). [BackupEstimator] starts this at dusk instead, to run an outage timeline forward
         * from the same engine rather than a separate closed-form estimate. Uncapped (not mod 24) so
         * SOC integrates continuously across a run that spans past midnight; every hour-of-day lookup
         * inside this loop (irradiance, ambient temp, load shape) already wraps via `.mod(24.0)` on
         * its own, so this stays correct past the first 24 hours. */
        startHour: Double = 0.0,
        /** A54: total simulated span in hours — 24.0 for every existing caller (one calendar day).
         * [BackupEstimator] runs this out to a multi-day search window to find when an outage would
         * first go unmet. */
        durationHours: Double = 24.0,
        /** A54: scales the computed house load only (not PV/battery physics) — how [BackupEstimator]
         * represents "Critical Loads" / "Most Load" / a custom backup-coverage fraction without a
         * second load model: the same blanket fraction [SystemCalculator]'s own sizing already
         * applies to `criticalDailyKwh`, now shared by the simulation-driven estimate too. */
        loadMultiplier: Double = 1.0
    ): List<SimFrame> {
        val maxSocKwh = config.batteryCapacityKwh * BATTERY_MAX_SOC_FRACTION
        val minSocKwh = config.batteryCapacityKwh * config.batteryDepthOfDischargeFraction
        var batterySocKwh = (config.batteryCapacityKwh * startSocFraction).coerceIn(minSocKwh, maxSocKwh)
        // The day-shaped curve represents ambient/background load (standby draw, misc
        // cycling) not covered by the explicit appliance checklist; the checklist's total
        // is added on top so toggling an appliance has an immediate, visible effect rather
        // than being smoothed into an average. [applianceLoadKw] is a flat legacy contribution
        // (still used by simple, unscheduled call sites); [applianceStates] layers each
        // appliance's own scheduled run windows on top, hour by hour, for real precision.
        val backgroundPerHourKw = (config.avgDailyLoadKwh / 24.0 * BACKGROUND_LOAD_FRACTION).coerceAtLeast(BACKGROUND_LOAD_FLOOR_KW)
        val dt = resolutionMinutes / 60.0
        // A70: see REFERENCE_CURVE_PSH_HOURS's own doc — scales the curve's amplitude so this
        // site's simulated daily yield tracks its own entered PSH, not the same fixed ~7.2h/day
        // every site got before this round.
        val pshScale = config.pshHours / REFERENCE_CURVE_PSH_HOURS

        val steps = ((durationHours * 60) / resolutionMinutes).toInt()
        val frames = ArrayList<SimFrame>(steps + 1)

        for (i in 0..steps) {
            val hour = startHour + (i * resolutionMinutes) / 60.0

            val irradianceFraction = irradianceFactor(hour) * cloudMultiplier * pshScale
            // A69: capped at the inverter's real PV DC input ceiling (config.maxPvInputKw), NOT
            // its AC output rating (config.inverterKw) — a real hybrid inverter's DC/MPPT stage
            // typically accepts meaningfully more than its own AC rating (see
            // SimSystemConfig.maxPvInputKw's own doc). The inverter's overall AC-side throughput
            // is a separate, already-modeled concern (SimFrame.inverterLoadKw, warned on by
            // SimulationWarnings when it exceeds inverterKw — A36 — rather than silently clamped).
            val potentialPv = (irradianceFraction * config.pvCapacityKw).coerceIn(0.0, config.maxPvInputKw)

            // Real-world losses, modeled as separate itemized factors rather than one blended
            // derate: panel temperature (rises with irradiance/ambient heat, cutting output —
            // meaningful in Jamaica's climate) plus fixed inverter/wiring/soiling losses.
            val ambientTempC = SystemLosses.ambientTemperatureC(hour)
            val cellTempC = SystemLosses.cellTemperatureC(ambientTempC, irradianceFraction)
            val temperatureDerate = SystemLosses.temperatureDerate(cellTempC)
            val pv = (irradianceFraction * config.pvCapacityKw * temperatureDerate * SystemLosses.fixedSystemEfficiency)
                .coerceIn(0.0, config.maxPvInputKw)

            val load = ((loadFactor(hour, dayType) * backgroundPerHourKw + applianceLoadKw + totalApplianceLoadKwAt(applianceStates, hour, dayType)) * loadMultiplier)
                .coerceAtLeast(0.0)

            // The grid connection is strictly import-only in every mode — solar never exports;
            // any surplus that can't be used or stored is simply curtailed.
            //
            // SOL: solar → house first, surplus solar → battery, deficit → battery down to its
            // reserve floor — and that's the whole story. JPS is never touched, even if it's
            // connected and the battery is exhausted; any remaining deficit goes unmet, exactly
            // like a genuine off-grid system. This is the one mode where "gridConnected" doesn't
            // guarantee JPS ever actually supplies anything.
            //
            // SBU: Solar → Battery → Utility — the same priority order as SOL, but JPS is kept
            // as the last-resort fallback once the battery hits its reserve floor, instead of
            // leaving the load unmet.
            //
            // UTI: JPS is the primary house supply whenever connected (battery is pure outage
            // backup — it does not discharge to serve the house while grid is up), and JPS can
            // simultaneously top off the battery when [gridChargeEnabled]. Solar still serves
            // the house and charges the battery for free first, ahead of the grid either way.
            val gridConnectedNow = config.gridConnectable && gridConnected
            val utiServesHouseFromGrid = inverterMode == InverterMode.UTI && gridConnectedNow
            val allowGridChargeBattery = inverterMode == InverterMode.UTI && gridChargeEnabled && gridConnectedNow
            val solarOnlyMode = inverterMode == InverterMode.SOL

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

            // Both the charge and discharge caps taper with the battery's own current SOC
            // (see BatteryPowerCurve) rather than staying flat at the rated max right up to
            // 0%/100% — a real pack accepts/delivers less current near either end.
            val socFraction = if (config.batteryCapacityKwh > 0) batterySocKwh / config.batteryCapacityKwh else 0.0

            if (config.hasBattery) {
                val maxChargeRateKw = config.batteryMaxChargeKw * BatteryPowerCurve.chargeTaperFraction(socFraction)
                val roomKwh = (maxSocKwh - batterySocKwh).coerceAtLeast(0.0)
                val maxChargeThisStep = min(maxChargeRateKw, roomKwh / dt)
                solarToBattery = min(remainingPv, maxChargeThisStep)
                remainingPv -= solarToBattery
                if (allowGridChargeBattery) {
                    gridToBattery = (maxChargeThisStep - solarToBattery).coerceAtLeast(0.0)
                }
            }
            curtailedSolar = remainingPv

            if (remainingLoad > FLOW_EPSILON) {
                if (config.hasBattery) {
                    val maxDischargeRateKw = config.batteryMaxDischargeKw *
                        BatteryPowerCurve.dischargeTaperFraction(socFraction, config.batteryDepthOfDischargeFraction)
                    val availableKwh = (batterySocKwh - minSocKwh).coerceAtLeast(0.0)
                    val maxDischargeThisStep = min(maxDischargeRateKw, availableKwh / dt)
                    batteryToHouse = min(remainingLoad, maxDischargeThisStep)
                    remainingLoad -= batteryToHouse
                }
                if (remainingLoad > FLOW_EPSILON) {
                    if (!utiServesHouseFromGrid && gridConnectedNow && !solarOnlyMode) {
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

            val chargeEnergyKwh = (solarToBattery + gridToBattery) * dt * config.batteryChargeEfficiency
            val dischargeEnergyKwh = batteryToHouse * dt
            batterySocKwh = (batterySocKwh + chargeEnergyKwh - dischargeEnergyKwh).coerceIn(minSocKwh, maxSocKwh)

            val batteryPowerKw = (solarToBattery + gridToBattery) - batteryToHouse
            val gridPowerKw = gridToHouse + gridToBattery
            // What the inverter's own inverting stage is actually carrying — house power sourced
            // from solar/battery, plus whatever's charging the battery. Grid-to-house power
            // bypasses this (see SimFrame's own doc comment), so it's excluded here.
            val inverterLoadKw = solarToHouse + batteryToHouse + solarToBattery + gridToBattery
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
                potentialPvKw = potentialPv,
                cellTempC = cellTempC,
                temperatureDerateFraction = temperatureDerate,
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
                inverterLoadKw = inverterLoadKw,
                status = status
            )
        }
        return frames
    }

    /**
     * A47: the two conservation laws every [SimFrame] must satisfy by construction — every watt
     * of realized PV either serves the house, charges the battery, or is curtailed; every watt
     * of house load is met by solar, battery, or grid, or goes unmet. [buildDayTimeline] builds
     * each frame via sequential allocation from those same shared pools rather than an
     * independent solve, so this should always come back at (or within float rounding of) zero —
     * this function exists to actually verify that, not just assume it, and to give a concrete
     * number rather than silently trusting the animation looks right. Surfaced in the Technical
     * panel (`TechnicalDetailsCard.kt`) rather than logged, since this module has no Android
     * framework dependency to log through and stays that way deliberately.
     */
    fun energyImbalanceKw(frame: SimFrame): Double {
        val pvBalance = frame.solarToHouseKw + frame.solarToBatteryKw + frame.curtailedSolarKw - frame.pvKw
        val loadBalance = frame.solarToHouseKw + frame.batteryToHouseKw + frame.gridToHouseKw + frame.unmetLoadKw - frame.houseLoadKw
        return kotlin.math.abs(pvBalance) + kotlin.math.abs(loadBalance)
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
        potentialPvKw = lerp(a.potentialPvKw, b.potentialPvKw, t),
        cellTempC = lerp(a.cellTempC, b.cellTempC, t),
        temperatureDerateFraction = lerp(a.temperatureDerateFraction, b.temperatureDerateFraction, t),
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
        inverterLoadKw = lerp(a.inverterLoadKw, b.inverterLoadKw, t),
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

    /**
     * A54 (spec §31): plain-language "why" for a frame whose [SimFrame.status] involves the grid
     * or an unmet load — the message's own request that a battery→utility switch (or any moment
     * the grid has to step in) be explained rather than silently shown as a status label. Null for
     * every other status, where there's nothing surprising to explain (solar/battery covering the
     * load normally). Uses the same reserve-floor framing as [BackupEstimator]'s own shortfall
     * reason, so the two never describe the same physical event differently.
     */
    fun statusReason(frame: SimFrame, config: SimSystemConfig): String? {
        val reserveFloorPercent = config.batteryDepthOfDischargeFraction * 100.0
        return when (frame.status) {
            SystemStatus.GRID_POWERING_HOME, SystemStatus.BATTERY_PLUS_GRID -> when {
                !config.hasBattery -> "No battery in this system — JPS covers the load directly."
                frame.batterySocPercent <= reserveFloorPercent + 1.5f ->
                    "Battery reached its %.0f%% reserve floor.".format(reserveFloorPercent)
                else -> "Battery discharge power limit reached for this load."
            }
            SystemStatus.GRID_CHARGING_BATTERY -> "JPS is topping off the battery (Utility-first mode)."
            SystemStatus.POWER_LIMITED -> "Demand exceeds what solar, battery, and the grid connection can currently supply."
            else -> null
        }
    }
}
