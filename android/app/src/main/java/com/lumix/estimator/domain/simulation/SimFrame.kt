package com.lumix.estimator.domain.simulation

/**
 * Hybrid inverter operating mode. The grid connection modeled by this app is strictly
 * import-only — none of these modes ever export solar (or anything else) back to JPS.
 */
enum class InverterMode(val label: String, val description: String) {
    SOL(
        "SOL",
        "Solar only: the home runs on solar and battery alone. JPS is never used, even if " +
            "it's connected — once the battery is drawn down, any remaining load simply goes " +
            "unmet, the same as a true off-grid system."
    ),
    SBU(
        "SBU",
        "Solar → Battery → Utility: solar first, battery next, and JPS steps in as a last " +
            "resort once the battery is drawn down to its reserve floor."
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
 * same frame.
 *
 * 2026-08-18 charging-physics fix: three tiers of PV figure, matching how a real MPPT hybrid
 * inverter actually behaves rather than a "produce everything, dump the surplus" accounting:
 *   - [potentialPvKw] — pre-loss ceiling (irradiance × array capacity), no real-world losses.
 *   - [harvestablePvKw] — post-loss, what the array *could* deliver this instant if the house +
 *     battery could absorb all of it. The gap down from [potentialPvKw] is [SystemLosses]'
 *     itemized inverter/wiring/soiling factors plus [temperatureDerateFraction] — real losses,
 *     not throttling.
 *   - [pvKw] — what the array *actually* produces: a real inverter walks the array back off its
 *     maximum-power point when the battery is full and there's nowhere for the surplus to go, so
 *     harvested production drops to exactly `house + charging`. This is that harvested figure
 *     ([solarToHouseKw] + [solarToBatteryKw]), the number a real PV monitor would show — it drops
 *     to match the load once the battery tops off, instead of staying pinned at the ceiling.
 * [curtailedSolarKw] is the throttled-off remainder ([harvestablePvKw] − [pvKw]) — energy the
 * array was backed off from making because the battery is full/absent and there's no export
 * outlet. It's foregone at the source, not produced and dumped anywhere.
 *
 * [inverterLoadKw] is the power actually passing through the inverter's inverting stage this
 * instant: solar/battery serving the house, plus whatever's charging the battery. It deliberately
 * excludes [gridToHouseKw] — a real hybrid inverter routes grid-sourced house power through an
 * internal bypass relay, not the inverter bridge itself, so it doesn't count against the
 * inverter's own continuous kW rating the way inverted solar/battery power does.
 *
 * A73: [batteryToHouseKw] is the AC-side figure — the power actually delivered to the house
 * (what [houseLoadKw] is measured against, and what the energy-balance invariant in
 * [SimulationEngine.energyImbalanceKw] checks). The real DC energy drawn from the battery's own
 * SOC to produce it is *larger* by the same inverter DC→AC conversion loss
 * ([SystemLosses.INVERTER_EFFICIENCY]) the PV→house path already pays — the same physical
 * conversion stage handles both. [batteryPowerKw] reflects that real DC-side draw (not
 * [batteryToHouseKw] directly), so it's directly comparable to [solarToBatteryKw]/[gridToBatteryKw]
 * (also DC-side battery-terminal quantities) rather than mixing AC- and DC-side power in one field.
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
    val inverterLoadKw: Double,
    val status: SystemStatus,
    /**
     * A87 (spec Phase 24 §3 — "INVERTER SELF-CONSUMPTION"): the inverter's own housekeeping power
     * draw this instant — see [SimSystemConfig.inverterSelfConsumptionKw]'s own doc for how it's
     * resolved. NOT included in [houseLoadKw] (which stays pure appliance/background load), but IS
     * part of what [solarToHouseKw]/[batteryToHouseKw]/[gridToHouseKw] actually serve — see
     * [SimulationEngine.energyImbalanceKw], which adds this in on the demand side of its own
     * conservation check for exactly that reason. Defaults to 0.0 only for direct test-fixture
     * construction that doesn't care about it; every real frame [SimulationEngine.buildDayTimeline]
     * produces sets this from the config it was built against.
     */
    val inverterSelfConsumptionKw: Double = 0.0
) {
    /**
     * 2026-08-18 charging-physics fix: post-loss production the array *could* have delivered this
     * instant if the house + battery could absorb all of it — i.e. before the inverter throttled
     * the array back off its maximum-power point. Equals [pvKw] (actually harvested) plus
     * [curtailedSolarKw] (throttled off). Useful for showing "how much was left on the table
     * because the battery was full" without re-deriving it at every call site.
     */
    val harvestablePvKw: Double get() = pvKw + curtailedSolarKw
}
