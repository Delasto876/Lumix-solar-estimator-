package com.lumix.estimator.domain.simulation

import com.lumix.estimator.domain.ApplianceType
import com.lumix.estimator.domain.QuoteInputs
import kotlinx.serialization.Serializable

@Serializable
enum class SimApplianceType(val label: String, val watts: Int) {
    LIGHTS("Lights", 120),
    TV("TV", 120),
    REFRIGERATOR("Refrigerator", 150),
    FANS("Fans", 60),
    AIR_CONDITIONER("Air Conditioner", 1500),
    MICROWAVE("Microwave", 1200),
    WASHING_MACHINE("Washing Machine", 500),
    DRYER("Dryer", 1500),
    IRON("Iron", 1200),
    WATER_HEATER("Water Heater", 3000),
    OVEN("Oven", 2200),
    PUMP("Water Pump", 750),
    COMPUTER("Computer", 200),
    EV_CHARGER("EV Charger", 7000)
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
