package com.lumix.estimator.domain.simulation

/**
 * Hybrid inverter operating mode. The grid connection modeled by this app is strictly
 * import-only — none of these modes ever export solar (or anything else) back to JPS.
 */
enum class InverterMode(val label: String, val description: String) {
    SOL(
        "SOL",
        "Solar first: the home runs on solar, surplus charges the battery, and JPS only " +
            "steps in once the battery is drawn down."
    ),
    SBU(
        "SBU",
        "Solar → Battery → Utility: functionally the same priority as SOL — solar first, " +
            "battery next, JPS only once the battery is drawn down."
    ),
    UTI(
        "UTI",
        "Utility first: JPS powers the home whenever it's connected (and can top off the " +
            "battery too). The battery is reserved as backup for outages."
    )
}

enum class SystemStatus(val label: String) {
    IDLE("Idle"),
    SOLAR_POWERING_HOME("Solar powering home"),
    SOLAR_PLUS_BATTERY("Solar + battery powering home"),
    BATTERY_POWERING_HOME("Battery powering home"),
    GRID_POWERING_HOME("JPS powering home"),
    BATTERY_PLUS_GRID("Battery + JPS powering home"),
    GRID_CHARGING_BATTERY("JPS charging battery"),
    POWER_LIMITED("Power limited — demand exceeds supply")
}

/**
 * A fully-resolved instant of the simulation: every flow, in kW, plus battery state.
 *
 * The grid is strictly import-only in this app — there is no `solarToGridKw`/export field,
 * and none ever will be. [gridToBatteryKw] is JPS charging the battery (UTI mode only, when
 * enabled); it is a separate import path from [gridToHouseKw], and both can be active in the
 * same frame. [curtailedSolarKw] is solar production that had nowhere to go this instant
 * (battery full or absent, and no export outlet) — it's simply unused, not sent anywhere.
 *
 * [pvKw] is realized (loss-adjusted) production; [potentialPvKw] is the same instant with no
 * real-world losses applied — the gap between them is [SystemLosses]' itemized inverter/wiring/
 * soiling factors plus [temperatureDerateFraction], not something wasted or curtailed.
 */
data class SimFrame(
    val hour: Double,
    val pvKw: Double,
    val potentialPvKw: Double,
    val cellTempC: Double,
    val temperatureDerateFraction: Double,
    val houseLoadKw: Double,
    val solarToHouseKw: Double,
    val solarToBatteryKw: Double,
    val batteryToHouseKw: Double,
    val gridToHouseKw: Double,
    val gridToBatteryKw: Double,
    val batterySocKwh: Double,
    val batterySocPercent: Float,
    val batteryPowerKw: Double,
    val gridPowerKw: Double,
    val unmetLoadKw: Double,
    val curtailedSolarKw: Double,
    val status: SystemStatus
)
