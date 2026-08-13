package com.lumix.estimator.domain.simulation

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
 * [pvPowerKw] is realized (loss-adjusted) solar output; [potentialPvKw] is the same instant with
 * no real-world losses at all. [temperatureLossPercent] and [fixedSystemLossPercent] are the two
 * itemized causes of that gap — see [SystemLosses] for the individual inverter/wiring/soiling
 * factors [fixedSystemLossPercent] combines.
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
    val gridServiceAmps: Double,
    val gridServiceUtilization: Float,
    val frequencyHz: Double,
    val energyTodayKwh: Double,
    val energyMonthEstKwh: Double
)

object TechnicalModel {
    private const val PV_NOMINAL_VOLTAGE = 380.0
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
        val pvVoltage = if (frame.pvKw > 0.01) PV_NOMINAL_VOLTAGE else 0.0
        val pvCurrent = if (pvVoltage > 0) (frame.pvKw * 1000.0) / pvVoltage else 0.0

        val batteryVoltage = if (config.hasBattery) {
            val socFraction = (frame.batterySocPercent / 100f).coerceIn(0f, 1f)
            BATTERY_MIN_VOLTAGE + socFraction * (BATTERY_MAX_VOLTAGE - BATTERY_MIN_VOLTAGE)
        } else 0.0
        val batteryCurrent = if (batteryVoltage > 0) (frame.batteryPowerKw * 1000.0) / batteryVoltage else 0.0

        val inverterOutputKw = (frame.houseLoadKw - frame.unmetLoadKw).coerceAtLeast(0.0) + frame.solarToBatteryKw + frame.gridToBatteryKw

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

        val maxGridServiceKw = gridServiceAmps * GRID_HIGH_VOLTAGE / 1000.0
        val gridServiceUtilization = if (gridActive && maxGridServiceKw > 0) (gridTotalKw / maxGridServiceKw).toFloat() else 0f

        val frequencyHz = if (gridActive) GRID_FREQUENCY_HZ + 0.02 * sin(frame.hour) else 0.0

        val dt = if (timeline.size > 1) timeline[1].hour - timeline[0].hour else 5.0 / 60.0
        val energyTodayKwh = timeline.filter { it.hour <= frame.hour }.sumOf { it.pvKw * dt }

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
            gridServiceAmps = gridServiceAmps,
            gridServiceUtilization = gridServiceUtilization,
            frequencyHz = frequencyHz,
            energyTodayKwh = energyTodayKwh,
            energyMonthEstKwh = energyTodayKwh * 30
        )
    }
}
