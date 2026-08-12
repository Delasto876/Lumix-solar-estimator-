package com.lumix.estimator.domain.simulation

import kotlin.math.sin

/**
 * Typical/modeled electrical figures for "Technical" mode — not measured telemetry.
 * Voltage and frequency are nominal assumptions for a Jamaica-style 110V/60Hz grid and a
 * 48V LiFePO4 battery bus; current is simply power/voltage from those assumptions. Energy
 * today is a real integral of the precomputed timeline; energy this month is that day's
 * total simply extrapolated ×30, not a recorded history.
 */
data class TechnicalReadout(
    val pvVoltage: Double,
    val pvCurrent: Double,
    val pvPowerKw: Double,
    val batteryVoltage: Double,
    val batteryCurrent: Double,
    val batterySocPercent: Float,
    val inverterOutputKw: Double,
    val gridVoltage: Double,
    val gridCurrent: Double,
    val frequencyHz: Double,
    val energyTodayKwh: Double,
    val energyMonthEstKwh: Double
)

object TechnicalModel {
    private const val PV_NOMINAL_VOLTAGE = 380.0
    private const val BATTERY_MIN_VOLTAGE = 46.0
    private const val BATTERY_MAX_VOLTAGE = 53.0
    private const val GRID_NOMINAL_VOLTAGE = 110.0
    private const val GRID_NOMINAL_FREQUENCY = 60.0

    fun compute(frame: SimFrame, config: SimSystemConfig, timeline: List<SimFrame>): TechnicalReadout {
        val pvVoltage = if (frame.pvKw > 0.01) PV_NOMINAL_VOLTAGE else 0.0
        val pvCurrent = if (pvVoltage > 0) (frame.pvKw * 1000.0) / pvVoltage else 0.0

        val batteryVoltage = if (config.hasBattery) {
            val socFraction = (frame.batterySocPercent / 100f).coerceIn(0f, 1f)
            BATTERY_MIN_VOLTAGE + socFraction * (BATTERY_MAX_VOLTAGE - BATTERY_MIN_VOLTAGE)
        } else 0.0
        val batteryCurrent = if (batteryVoltage > 0) (frame.batteryPowerKw * 1000.0) / batteryVoltage else 0.0

        val inverterOutputKw = (frame.houseLoadKw - frame.unmetLoadKw).coerceAtLeast(0.0) + frame.solarToBatteryKw + frame.gridToBatteryKw

        val gridActive = config.gridConnectable
        val gridVoltage = if (gridActive) GRID_NOMINAL_VOLTAGE else 0.0
        val gridCurrent = if (gridActive) (frame.gridPowerKw * 1000.0) / GRID_NOMINAL_VOLTAGE else 0.0
        val frequencyHz = if (gridActive) GRID_NOMINAL_FREQUENCY + 0.02 * sin(frame.hour) else 0.0

        val dt = if (timeline.size > 1) timeline[1].hour - timeline[0].hour else 5.0 / 60.0
        val energyTodayKwh = timeline.filter { it.hour <= frame.hour }.sumOf { it.pvKw * dt }

        return TechnicalReadout(
            pvVoltage = pvVoltage,
            pvCurrent = pvCurrent,
            pvPowerKw = frame.pvKw,
            batteryVoltage = batteryVoltage,
            batteryCurrent = batteryCurrent,
            batterySocPercent = frame.batterySocPercent,
            inverterOutputKw = inverterOutputKw,
            gridVoltage = gridVoltage,
            gridCurrent = gridCurrent,
            frequencyHz = frequencyHz,
            energyTodayKwh = energyTodayKwh,
            energyMonthEstKwh = energyTodayKwh * 30
        )
    }
}
