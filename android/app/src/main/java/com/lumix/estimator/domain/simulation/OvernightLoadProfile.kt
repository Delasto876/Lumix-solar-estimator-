package com.lumix.estimator.domain.simulation

/**
 * A64 (2026-08-14 "FIX 12-HOUR OVERNIGHT BACKUP SIZING" — §1-6: "The battery should NOT be sized
 * from an arbitrary average load. It must be sized from the actual selected appliances and their
 * time-of-day behavior... Do NOT use peak load x 12 hours... Do NOT use average daily load / 24 x
 * 12"): integrates the exact same appliance-duty-cycle load model
 * [SimulationEngine.buildDayTimeline] already computes for every other screen in this app, instead
 * of [com.lumix.estimator.domain.SystemCalculator]'s old flat
 * `criticalDailyKwh * (backupHours / 24)` formula.
 *
 * [SimFrame.houseLoadKw] is computed purely from the appliance/background-load model, before any
 * PV/battery/grid routing happens for that frame (see `buildDayTimeline`'s own source — the load
 * figure is resolved first, routing decisions second) — so this needs no real PV/battery/inverter
 * config, just the load inputs. A placeholder [SimSystemConfig] (no PV, no battery) is safe here
 * for exactly that reason.
 */
object OvernightLoadProfile {

    data class Result(
        /** Total energy the selected appliances actually draw across the window, per their real schedules — not an average, not a flat peak. */
        val energyKwh: Double,
        /** The highest instantaneous load seen anywhere in the window — what the battery's own discharge power must cover, not the whole day's worst case (spec §7). */
        val peakKw: Double
    )

    /**
     * [startHour] anchors the window the same way [BackupEstimator]'s own real backup-hours
     * simulation does — dusk, [SimulationEngine.SUNSET_HOUR] — by construction, not by
     * coincidence: the REQUIREMENT this computes and the SIMULATION that later verifies a battery
     * choice against it always describe the identical window, so the two can never silently
     * diverge on what "the backup period" means (the exact class of bug spec §6's "create ONE
     * centralized battery-energy calculation" is asking to prevent).
     */
    fun evaluate(
        avgDailyLoadKwh: Double,
        applianceStates: Map<SimApplianceType, ApplianceState>,
        windowHours: Double,
        loadMultiplier: Double,
        dayType: DayType = DayType.WEEKDAY,
        startHour: Double = SimulationEngine.SUNSET_HOUR,
        resolutionMinutes: Int = 5
    ): Result {
        if (windowHours <= 0.0) return Result(0.0, 0.0)

        val placeholderConfig = SimSystemConfig(
            pvCapacityKw = 0.0, panelCount = 0, panelWatts = 0,
            inverterKw = 100.0, inverterName = "", batteryCapacityKwh = 0.0, batteryName = null,
            hasBattery = false, gridConnectable = false, avgDailyLoadKwh = avgDailyLoadKwh,
            peakLoadKw = avgDailyLoadKwh, batteryMaxChargeKw = 0.0, batteryMaxDischargeKw = 0.0,
            batteryChargeEfficiency = 0.95, batteryDepthOfDischargeFraction = SimulationEngine.BATTERY_MIN_SOC_FRACTION
        )
        val timeline = SimulationEngine.buildDayTimeline(
            config = placeholderConfig,
            gridConnected = false,
            startSocFraction = 0.0,
            resolutionMinutes = resolutionMinutes,
            applianceStates = applianceStates,
            dayType = dayType,
            startHour = startHour,
            durationHours = windowHours,
            loadMultiplier = loadMultiplier
        )

        val dt = resolutionMinutes / 60.0
        // Left-Riemann sum over fixed-width steps — same per-step integration convention
        // BackupEstimator's own elapsed-hours math already uses. The last frame is the window's
        // closing instant, not the start of a further step, so it contributes no interval of its
        // own (steps+1 frames describe steps intervals).
        val energyKwh = timeline.dropLast(1).sumOf { it.houseLoadKw * dt }
        val peakKw = timeline.maxOfOrNull { it.houseLoadKw } ?: 0.0
        return Result(energyKwh, peakKw)
    }
}
