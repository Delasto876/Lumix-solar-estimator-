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
 * Starting on/off state for each appliance, derived from what the user actually told
 * the estimator they have — not arbitrary defaults. Appliances the wizard never asks
 * about (water heater, oven, pump, computer, EV charger) default off so the simulator
 * starts matching the quoted load, and turning them on is an explicit "what if."
 */
fun defaultApplianceStates(inputs: QuoteInputs): Map<SimApplianceType, Boolean> {
    fun qty(type: ApplianceType) = inputs.appliances[type]?.qty ?: 0
    return linkedMapOf(
        SimApplianceType.LIGHTS to true,
        SimApplianceType.REFRIGERATOR to (qty(ApplianceType.FRIDGE) > 0),
        SimApplianceType.FANS to (qty(ApplianceType.FAN) > 0),
        SimApplianceType.TV to (qty(ApplianceType.TV) > 0),
        SimApplianceType.AIR_CONDITIONER to inputs.ac.hasAc,
        SimApplianceType.MICROWAVE to (qty(ApplianceType.MICROWAVE) > 0),
        SimApplianceType.WASHING_MACHINE to (qty(ApplianceType.WASHER) > 0),
        SimApplianceType.DRYER to (qty(ApplianceType.DRYER) > 0),
        SimApplianceType.IRON to (qty(ApplianceType.IRON) > 0),
        SimApplianceType.WATER_HEATER to false,
        SimApplianceType.OVEN to false,
        SimApplianceType.PUMP to false,
        SimApplianceType.COMPUTER to false,
        SimApplianceType.EV_CHARGER to false
    )
}

fun totalApplianceLoadKw(states: Map<SimApplianceType, Boolean>): Double =
    states.filterValues { it }.keys.sumOf { it.watts } / 1000.0

/** Splits the currently-on appliances' load by [ElectricalTier], for per-circuit current readings. */
fun applianceLoadKwByTier(states: Map<SimApplianceType, Boolean>): Map<ElectricalTier, Double> =
    states.filterValues { it }.keys
        .groupBy { it.tier }
        .mapValues { (_, types) -> types.sumOf { it.watts } / 1000.0 }
