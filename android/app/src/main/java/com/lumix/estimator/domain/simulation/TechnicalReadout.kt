package com.lumix.estimator.domain.simulation

import kotlin.math.abs
import kotlin.math.sin

/**
 * Typical/modeled electrical figures for "Technical" mode — not measured telemetry.
 * Voltage and frequency are nominal assumptions for a Jamaica-style split-phase 110V/220V,
 * 50Hz grid and a 48V LiFePO4 battery bus; current is simply power/voltage (P=V×I) from those
 * assumptions. Energy today is a real integral of the precomputed timeline; energy this month
 * is that day's total simply extrapolated ×30, not a recorded history.
 *
 * The grid side is reported as two circuits, matching real Jamaican residential wiring: general
 * lighting/outlets at 110V ([gridLowCurrent]) and heavy appliances (AC, water heater, etc.) at
 * 220V ([gridHighCurrent]). [gridServiceUtilization] compares total grid draw against the
 * configured utility service rating ([gridServiceAmps]) — the same rating the simulation engine
 * itself enforces as a hard import cap, so this is always consistent with what the engine did.
 *
 * [pvPowerKw] is the *harvested* solar output — what the inverter actually pulls from the array
 * (house + charging), which a real MPPT walks back off the array's max-power point once the
 * battery tops off. [harvestablePvKw] is what it could have pulled if the battery could absorb
 * everything ([pvPowerKw] + [pvCurtailedKw]); [potentialPvKw] is that same instant with no
 * real-world losses at all. [temperatureLossPercent] and [fixedSystemLossPercent] are the two
 * itemized causes of the loss gap down from [potentialPvKw] — see [SystemLosses] for the
 * individual inverter/wiring/soiling factors [fixedSystemLossPercent] combines.
 *
 * [gridNeutralCurrent] is the imbalance between the two 110V legs ([applianceLoadKwByLegAt]) —
 * a balanced split-phase panel carries near-zero neutral current; the more one leg's load
 * outweighs the other's, the more the shared neutral conductor actually carries.
 * [startupSurgeKw] is a worst-case instantaneous figure, not something the timestep timeline
 * ever sustains — see [worstCaseStartupSurgeKw]'s own doc for why it's kept separate.
 * [energyBalanceErrorKw] is [SimulationEngine.energyImbalanceKw] surfaced for this instant — it
 * should read effectively zero always; a nonzero value here would mean the engine invented or
 * lost energy somewhere, and this is the one place that would actually be visible.
 */
data class TechnicalReadout(
    val pvVoltage: Double,
    val pvCurrent: Double,
    val pvPowerKw: Double,
    val potentialPvKw: Double,
    val cellTempC: Double,
    val temperatureLossPercent: Float,
    val fixedSystemLossPercent: Float,
    val batteryVoltage: Double,
    val batteryCurrent: Double,
    val batterySocPercent: Float,
    val inverterOutputKw: Double,
    val gridLowVoltage: Double,
    val gridLowCurrent: Double,
    val gridHighVoltage: Double,
    val gridHighCurrent: Double,
    val gridNeutralCurrent: Double,
    val gridServiceAmps: Double,
    val gridServiceUtilization: Float,
    val frequencyHz: Double,
    val energyTodayKwh: Double,
    /**
     * 2026-08-18 charging-physics fix: the day-so-far integral of [SimFrame.harvestablePvKw] — what
     * the array *could* have produced if the battery could absorb everything, i.e. [energyTodayKwh]
     * (actually harvested) plus everything throttled off while the battery was full. Shown next to
     * [energyTodayKwh] so the throttled headroom is visible rather than silently missing.
     */
    val energyTodayAvailableKwh: Double,
    val energyMonthEstKwh: Double,
    val startupSurgeKw: Double,
    val energyBalanceErrorKw: Double,
    /** A53: real per-MPPT-tracker electrical state — see [PvElectricalModel]. Empty only when there's no PV configured at all. */
    val mpptStrings: List<MpptReadout>,
    /** [SimFrame.curtailedSolarKw] surfaced here — the PV the inverter throttled off this instant because the battery is full/absent and there's no export outlet. */
    val pvCurtailedKw: Double,
    /** [SimFrame.harvestablePvKw] — post-loss production the array *could* have delivered if the house + battery could absorb all of it ([pvPowerKw] harvested + [pvCurtailedKw] throttled off). The ceiling [pvPowerKw] would reach with no full-battery throttling; still below [potentialPvKw], which is before real-world losses too. */
    val harvestablePvKw: Double
)

object TechnicalModel {
    private const val BATTERY_MIN_VOLTAGE = 46.0
    private const val BATTERY_MAX_VOLTAGE = 53.0
    private const val GRID_LOW_VOLTAGE = 110.0
    private const val GRID_HIGH_VOLTAGE = SimulationEngine.GRID_SERVICE_VOLTAGE // 220.0
    private const val GRID_FREQUENCY_HZ = 50.0 // Jamaica mains

    fun compute(
        frame: SimFrame,
        config: SimSystemConfig,
        timeline: List<SimFrame>,
        appliances: Map<SimApplianceType, ApplianceState> = emptyMap(),
        gridServiceAmps: Double = SimulationEngine.DEFAULT_GRID_SERVICE_AMPS,
        dayType: DayType = DayType.WEEKDAY
    ): TechnicalReadout {
        // A53: real per-string Vmp/Voc (temperature-corrected, split across the inverter's actual
        // MPPT trackers) replaces a flat hardcoded PV voltage — see PvElectricalModel's own doc for
        // why voltage is gated on *potential* production, not delivered/curtailed power.
        val mpptStrings = PvElectricalModel.mpptReadouts(
            panelWatts = config.panelWatts,
            panelCount = config.panelCount,
            inverterKw = config.inverterKw,
            inverterNameHint = config.inverterName,
            cellTempC = frame.cellTempC,
            potentialPvKw = frame.potentialPvKw,
            realizedPvKw = frame.pvKw
        )
        val pvVoltage = PvElectricalModel.blendedVoltage(mpptStrings)
        val pvCurrent = if (pvVoltage > 0) (frame.pvKw * 1000.0) / pvVoltage else 0.0

        val batteryVoltage = if (config.hasBattery) {
            val socFraction = (frame.batterySocPercent / 100f).coerceIn(0f, 1f)
            BATTERY_MIN_VOLTAGE + socFraction * (BATTERY_MAX_VOLTAGE - BATTERY_MIN_VOLTAGE)
        } else 0.0
        val batteryCurrent = if (batteryVoltage > 0) (frame.batteryPowerKw * 1000.0) / batteryVoltage else 0.0

        // 2026-08-18 audit fix: this used to re-derive "inverter output" from houseLoadKw minus
        // unmet load, which (in UTI mode, with the grid serving the house directly) double-counts
        // power that never actually passes through the inverter. [SimFrame.inverterLoadKw] is
        // already the one authoritative AC-side-throughput figure — the same value
        // SimulationWarnings compares against inverterKw for the 80/90/100% overload alerts — so
        // reading it directly here means the Technical panel and the overload warning can never
        // disagree about what "inverter output" means.
        val inverterOutputKw = frame.inverterLoadKw

        val gridActive = config.gridConnectable
        val gridTotalKw = frame.gridToHouseKw + frame.gridToBatteryKw

        // The frame only tracks one blended houseLoadKw; apportion the grid's actual delivered
        // power across the two circuits by the appliance mix actually scheduled to be running
        // at this instant, so the reading reflects what's really running right now.
        val loadByTier = applianceLoadKwByTierAt(appliances, frame.hour, dayType)
        val lowTierKw = loadByTier[ElectricalTier.LOW] ?: 0.0
        val highTierKw = loadByTier[ElectricalTier.HIGH] ?: 0.0
        val tieredTotalKw = lowTierKw + highTierKw
        val (gridLowKw, gridHighKw) = if (tieredTotalKw > 0.01) {
            (gridTotalKw * (lowTierKw / tieredTotalKw)) to (gridTotalKw * (highTierKw / tieredTotalKw))
        } else {
            gridTotalKw to 0.0 // no tier info (background load only) — assume general 110V circuits
        }

        val gridLowVoltage = if (gridActive) GRID_LOW_VOLTAGE else 0.0
        val gridHighVoltage = if (gridActive) GRID_HIGH_VOLTAGE else 0.0
        val gridLowCurrent = if (gridActive) (gridLowKw * 1000.0) / GRID_LOW_VOLTAGE else 0.0
        val gridHighCurrent = if (gridActive) (gridHighKw * 1000.0) / GRID_HIGH_VOLTAGE else 0.0

        // Neutral current is a property of the household wiring itself — it flows regardless of
        // whether the 110V legs are ultimately sourced from JPS or the inverter — so this is
        // computed from the appliances' own leg split, not apportioned by gridActive like the
        // two currents above.
        val (l1Kw, l2Kw) = applianceLoadKwByLegAt(appliances, frame.hour, dayType)
        val l1Current = (l1Kw * 1000.0) / GRID_LOW_VOLTAGE
        val l2Current = (l2Kw * 1000.0) / GRID_LOW_VOLTAGE
        val gridNeutralCurrent = abs(l1Current - l2Current)
        val startupSurgeKw = worstCaseStartupSurgeKw(appliances, frame.hour, dayType)

        val maxGridServiceKw = gridServiceAmps * GRID_HIGH_VOLTAGE / 1000.0
        val gridServiceUtilization = if (gridActive && maxGridServiceKw > 0) (gridTotalKw / maxGridServiceKw).toFloat() else 0f

        val frequencyHz = if (gridActive) GRID_FREQUENCY_HZ + 0.02 * sin(frame.hour) else 0.0

        val dt = if (timeline.size > 1) timeline[1].hour - timeline[0].hour else 5.0 / 60.0
        val framesSoFar = timeline.filter { it.hour <= frame.hour }
        val energyTodayKwh = framesSoFar.sumOf { it.pvKw * dt }
        // 2026-08-18 charging-physics fix: harvested (above) vs. harvestable — the day's throttled
        // headroom is the gap between them, shown side by side so a full-battery afternoon reads as
        // "produced less because there was nowhere to put it," not as a silent shortfall.
        val energyTodayAvailableKwh = framesSoFar.sumOf { it.harvestablePvKw * dt }

        val temperatureLossPercent = ((1.0 - frame.temperatureDerateFraction) * 100.0).toFloat().coerceAtLeast(0f)
        val fixedSystemLossPercent = ((1.0 - SystemLosses.fixedSystemEfficiency) * 100.0).toFloat()

        return TechnicalReadout(
            pvVoltage = pvVoltage,
            pvCurrent = pvCurrent,
            pvPowerKw = frame.pvKw,
            potentialPvKw = frame.potentialPvKw,
            cellTempC = frame.cellTempC,
            temperatureLossPercent = temperatureLossPercent,
            fixedSystemLossPercent = fixedSystemLossPercent,
            batteryVoltage = batteryVoltage,
            batteryCurrent = batteryCurrent,
            batterySocPercent = frame.batterySocPercent,
            inverterOutputKw = inverterOutputKw,
            gridLowVoltage = gridLowVoltage,
            gridLowCurrent = gridLowCurrent,
            gridHighVoltage = gridHighVoltage,
            gridHighCurrent = gridHighCurrent,
            gridNeutralCurrent = gridNeutralCurrent,
            gridServiceAmps = gridServiceAmps,
            gridServiceUtilization = gridServiceUtilization,
            frequencyHz = frequencyHz,
            energyTodayKwh = energyTodayKwh,
            energyTodayAvailableKwh = energyTodayAvailableKwh,
            energyMonthEstKwh = energyTodayKwh * 30,
            startupSurgeKw = startupSurgeKw,
            energyBalanceErrorKw = SimulationEngine.energyImbalanceKw(frame),
            mpptStrings = mpptStrings,
            pvCurtailedKw = frame.curtailedSolarKw,
            harvestablePvKw = frame.harvestablePvKw
        )
    }
}
