package com.lumix.estimator.domain.simulation

import com.lumix.estimator.domain.ApplianceType
import com.lumix.estimator.domain.QuoteInputs
import kotlinx.serialization.Serializable

/**
 * Which residential circuit an appliance is modeled as living on. Jamaican homes (like the
 * US-style split-phase service this app assumes) wire general lighting/outlets at 110V and
 * heavy draws (AC, water heater, ovens, dryers, EV chargers) at 220V across both legs.
 */
enum class ElectricalTier(val nominalVoltage: Double) {
    LOW(110.0),
    HIGH(220.0)
}

@Serializable
enum class SimApplianceType(val label: String, val watts: Int, val tier: ElectricalTier) {
    LIGHTS("Lights", 120, ElectricalTier.LOW),
    TV("TV", 120, ElectricalTier.LOW),
    REFRIGERATOR("Refrigerator", 150, ElectricalTier.LOW),
    FANS("Fans", 60, ElectricalTier.LOW),
    AIR_CONDITIONER("Air Conditioner", 1500, ElectricalTier.HIGH),
    MICROWAVE("Microwave", 1200, ElectricalTier.LOW),
    WASHING_MACHINE("Washing Machine", 500, ElectricalTier.LOW),
    DRYER("Dryer", 1500, ElectricalTier.HIGH),
    IRON("Iron", 1200, ElectricalTier.LOW),
    WATER_HEATER("Water Heater", 3000, ElectricalTier.HIGH),
    OVEN("Oven", 2200, ElectricalTier.HIGH),
    PUMP("Water Pump", 750, ElectricalTier.HIGH),
    COMPUTER("Computer", 200, ElectricalTier.LOW),
    EV_CHARGER("EV Charger", 7000, ElectricalTier.HIGH)
}

/**
 * One scheduled window a quantity of an appliance runs — e.g. "3 units, from 6am for 3h".
 * An appliance can have several of these (a "+" control adds more), so a household's 6 fans
 * can be modeled as 3 running a daytime window and 3 running a separate nighttime window,
 * each contributing load only while its own window is active.
 */
data class ApplianceRun(
    val quantity: Int = 1,
    val startHour: Double = 0.0,
    val durationHours: Double = 24.0
) {
    /** Whether this run is contributing load at [hour] (0..24), handling wraparound past midnight. */
    fun isActiveAt(hour: Double): Boolean {
        val h = hour.mod(24.0)
        val endHour = startHour + durationHours
        return if (endHour <= 24.0) {
            h >= startHour && h < endHour
        } else {
            h >= startHour || h < (endHour - 24.0)
        }
    }
}

/**
 * An appliance's full runtime configuration: whether it's in play at all, and the one or more
 * scheduled [runs] making it up. A fresh, un-scheduled appliance defaults to a single run
 * covering the whole day, so simply toggling one on behaves exactly like the old flat on/off
 * model until someone actually customizes the schedule.
 */
data class ApplianceState(
    val enabled: Boolean = false,
    val runs: List<ApplianceRun> = listOf(ApplianceRun())
) {
    val totalQuantity: Int get() = runs.sumOf { it.quantity }
}

/**
 * A reasonable starting run schedule for a typical Jamaican household — a DEFAULT ASSUMPTION,
 * not a claim about how any specific household actually behaves. Always fully editable
 * afterward through the appliance picker's own quantity/hours/period controls. Quantities are
 * filled in separately by the caller; only the timing shape lives here. Short "event" appliances
 * (microwave, iron, pump) default to minutes, not hours, since that's how they're actually used.
 */
fun defaultScheduleFor(type: SimApplianceType): List<ApplianceRun> = when (type) {
    // Brief pre-dawn use, then the real usage window from dusk through bedtime.
    SimApplianceType.LIGHTS -> listOf(
        ApplianceRun(startHour = 5.0, durationHours = 1.0),
        ApplianceRun(startHour = 18.0, durationHours = 5.0)
    )
    // A compressor cycles on/off all day rather than truly running continuously, but its
    // average draw over a day is close enough to constant that a single all-day run is the
    // right shape for this — it isn't a "morning/evening" appliance like the others.
    SimApplianceType.REFRIGERATOR -> listOf(ApplianceRun(startHour = 0.0, durationHours = 24.0))
    // Midday heat through the night for sleeping.
    SimApplianceType.FANS -> listOf(ApplianceRun(startHour = 12.0, durationHours = 11.0))
    SimApplianceType.TV -> listOf(ApplianceRun(startHour = 17.0, durationHours = 4.0))
    SimApplianceType.AIR_CONDITIONER -> listOf(ApplianceRun(startHour = 19.0, durationHours = 8.0))
    // Two ~10-minute events — lunch and dinner.
    SimApplianceType.MICROWAVE -> listOf(
        ApplianceRun(startHour = 12.0, durationHours = 10.0 / 60.0),
        ApplianceRun(startHour = 18.5, durationHours = 10.0 / 60.0)
    )
    SimApplianceType.WASHING_MACHINE -> listOf(ApplianceRun(startHour = 9.0, durationHours = 1.0))
    SimApplianceType.DRYER -> listOf(ApplianceRun(startHour = 10.0, durationHours = 1.0))
    // A ~15-minute event.
    SimApplianceType.IRON -> listOf(ApplianceRun(startHour = 7.0, durationHours = 15.0 / 60.0))
    SimApplianceType.WATER_HEATER -> listOf(
        ApplianceRun(startHour = 6.0, durationHours = 1.0),
        ApplianceRun(startHour = 18.0, durationHours = 1.0)
    )
    SimApplianceType.OVEN -> listOf(ApplianceRun(startHour = 18.0, durationHours = 1.0))
    // A ~15-minute cycle.
    SimApplianceType.PUMP -> listOf(ApplianceRun(startHour = 7.0, durationHours = 15.0 / 60.0))
    SimApplianceType.COMPUTER -> listOf(ApplianceRun(startHour = 18.0, durationHours = 3.0))
    // No typical pattern exists for this one — entirely user-configurable — so this default
    // (overnight, off-peak) is just a starting point, not a behavioral claim.
    SimApplianceType.EV_CHARGER -> listOf(ApplianceRun(startHour = 22.0, durationHours = 6.0))
}

/**
 * Starting configuration for each appliance, derived from what the user actually told
 * the estimator they have — not arbitrary quantities. Appliances the wizard never asks
 * about (water heater, oven, pump, computer, EV charger) default off so the simulator
 * starts matching the quoted load, and turning them on is an explicit "what if." Every
 * appliance — on or off — carries [defaultScheduleFor]'s realistic timing, so switching one
 * on for the first time starts from a sensible schedule rather than an arbitrary all-day block.
 */
fun defaultApplianceStates(inputs: QuoteInputs): Map<SimApplianceType, ApplianceState> {
    fun qty(type: ApplianceType) = inputs.appliances[type]?.qty ?: 0
    fun stateFor(type: SimApplianceType, quantity: Int, enabled: Boolean = quantity > 0): ApplianceState {
        val q = quantity.coerceAtLeast(1)
        return ApplianceState(enabled = enabled, runs = defaultScheduleFor(type).map { it.copy(quantity = q) })
    }

    return linkedMapOf(
        SimApplianceType.LIGHTS to stateFor(SimApplianceType.LIGHTS, quantity = 1, enabled = true),
        SimApplianceType.REFRIGERATOR to stateFor(SimApplianceType.REFRIGERATOR, qty(ApplianceType.FRIDGE)),
        SimApplianceType.FANS to stateFor(SimApplianceType.FANS, qty(ApplianceType.FAN)),
        SimApplianceType.TV to stateFor(SimApplianceType.TV, qty(ApplianceType.TV)),
        SimApplianceType.AIR_CONDITIONER to stateFor(SimApplianceType.AIR_CONDITIONER, inputs.ac.counts.values.sum(), enabled = inputs.ac.hasAc),
        SimApplianceType.MICROWAVE to stateFor(SimApplianceType.MICROWAVE, qty(ApplianceType.MICROWAVE)),
        SimApplianceType.WASHING_MACHINE to stateFor(SimApplianceType.WASHING_MACHINE, qty(ApplianceType.WASHER)),
        SimApplianceType.DRYER to stateFor(SimApplianceType.DRYER, qty(ApplianceType.DRYER)),
        SimApplianceType.IRON to stateFor(SimApplianceType.IRON, qty(ApplianceType.IRON)),
        SimApplianceType.WATER_HEATER to stateFor(SimApplianceType.WATER_HEATER, 1, enabled = false),
        SimApplianceType.OVEN to stateFor(SimApplianceType.OVEN, 1, enabled = false),
        SimApplianceType.PUMP to stateFor(SimApplianceType.PUMP, 1, enabled = false),
        SimApplianceType.COMPUTER to stateFor(SimApplianceType.COMPUTER, 1, enabled = false),
        SimApplianceType.EV_CHARGER to stateFor(SimApplianceType.EV_CHARGER, 1, enabled = false)
    )
}

/** Total appliance load at a given hour (0..24) — only runs whose window is active contribute. */
fun totalApplianceLoadKwAt(states: Map<SimApplianceType, ApplianceState>, hour: Double): Double =
    states.entries.filter { it.value.enabled }.sumOf { (type, state) ->
        val activeQty = state.runs.filter { it.isActiveAt(hour) }.sumOf { it.quantity }
        activeQty * type.watts / 1000.0
    }

/** Splits the appliance load active at [hour] by [ElectricalTier], for per-circuit current readings. */
fun applianceLoadKwByTierAt(states: Map<SimApplianceType, ApplianceState>, hour: Double): Map<ElectricalTier, Double> =
    states.entries.filter { it.value.enabled }
        .associate { (type, state) -> type to state.runs.filter { it.isActiveAt(hour) }.sumOf { it.quantity } }
        .filterValues { it > 0 }
        .entries.groupBy({ it.key.tier }, { it.key to it.value })
        .mapValues { (_, list) -> list.sumOf { (type, qty) -> qty * type.watts } / 1000.0 }
