package com.lumix.estimator.domain.simulation

enum class SystemStatus(val label: String) {
    IDLE("Idle"),
    SOLAR_POWERING_HOME("Solar powering home"),
    SOLAR_PLUS_BATTERY("Solar + battery powering home"),
    BATTERY_POWERING_HOME("Battery powering home"),
    GRID_POWERING_HOME("JPS powering home"),
    BATTERY_PLUS_GRID("Battery + JPS powering home"),
    EXPORTING_TO_GRID("Exporting to JPS"),
    POWER_LIMITED("Power limited — demand exceeds supply")
}

/** A fully-resolved instant of the simulation: every flow, in kW, plus battery state. */
data class SimFrame(
    val hour: Double,
    val pvKw: Double,
    val houseLoadKw: Double,
    val solarToHouseKw: Double,
    val solarToBatteryKw: Double,
    val solarToGridKw: Double,
    val batteryToHouseKw: Double,
    val gridToHouseKw: Double,
    val batterySocKwh: Double,
    val batterySocPercent: Float,
    val batteryPowerKw: Double,
    val gridPowerKw: Double,
    val unmetLoadKw: Double,
    val status: SystemStatus
)
