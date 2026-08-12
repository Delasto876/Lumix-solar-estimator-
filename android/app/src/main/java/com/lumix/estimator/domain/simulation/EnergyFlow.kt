package com.lumix.estimator.domain.simulation

enum class EnergyNode { SOLAR, INVERTER, BATTERY, HOUSE, GRID }

enum class FlowDirection { FORWARD, REVERSE }

/** One visual energy path's instantaneous state, derived from a [SimFrame]. */
data class EnergyFlow(
    val id: String,
    val source: EnergyNode,
    val destination: EnergyNode,
    val powerKw: Double,
    val direction: FlowDirection,
    val active: Boolean
)

/**
 * Maps a [SimFrame]'s already-resolved sub-flows onto the four visual energy paths
 * baked into the house illustration (solar→inverter, inverter↔battery, grid↔inverter,
 * inverter→house). The frame is the single source of truth — this is a relabeling, not
 * a re-simulation, so it stays consistent with [SimulationEngine]'s priority routing and
 * its `POWER_LIMITED` handling for free (grid fields are already zeroed by the engine
 * when disconnected).
 */
object EnergyFlowResolver {
    private const val EPSILON = 0.01

    fun resolve(frame: SimFrame): List<EnergyFlow> {
        val solarToInverter = EnergyFlow(
            id = "solar_inverter",
            source = EnergyNode.SOLAR,
            destination = EnergyNode.INVERTER,
            powerKw = frame.pvKw,
            direction = FlowDirection.FORWARD,
            active = frame.pvKw > EPSILON
        )

        val houseDeliveredKw = frame.solarToHouseKw + frame.batteryToHouseKw + frame.gridToHouseKw
        val inverterToHouse = EnergyFlow(
            id = "inverter_house",
            source = EnergyNode.INVERTER,
            destination = EnergyNode.HOUSE,
            powerKw = houseDeliveredKw,
            direction = FlowDirection.FORWARD,
            active = houseDeliveredKw > EPSILON
        )

        // Battery charging can come from solar and/or grid (UTI mode) at once — both share
        // the one physical inverter↔battery path, so their magnitudes combine here.
        val batteryChargeKw = frame.solarToBatteryKw + frame.gridToBatteryKw
        val batteryFlow = when {
            batteryChargeKw > EPSILON -> EnergyFlow(
                id = "inverter_battery",
                source = EnergyNode.INVERTER,
                destination = EnergyNode.BATTERY,
                powerKw = batteryChargeKw,
                direction = FlowDirection.FORWARD,
                active = true
            )
            frame.batteryToHouseKw > EPSILON -> EnergyFlow(
                id = "inverter_battery",
                source = EnergyNode.BATTERY,
                destination = EnergyNode.INVERTER,
                powerKw = frame.batteryToHouseKw,
                direction = FlowDirection.REVERSE,
                active = true
            )
            else -> EnergyFlow(
                id = "inverter_battery",
                source = EnergyNode.INVERTER,
                destination = EnergyNode.BATTERY,
                powerKw = 0.0,
                direction = FlowDirection.FORWARD,
                active = false
            )
        }

        // Import-only: the grid never receives power in this app, so this path is always
        // GRID → INVERTER, carrying whatever JPS is currently supplying to the house and/or
        // charging the battery (both can be nonzero in the same frame, in UTI mode).
        val gridDrawKw = frame.gridToHouseKw + frame.gridToBatteryKw
        val gridFlow = EnergyFlow(
            id = "grid_inverter",
            source = EnergyNode.GRID,
            destination = EnergyNode.INVERTER,
            powerKw = gridDrawKw,
            direction = FlowDirection.FORWARD,
            active = gridDrawKw > EPSILON
        )

        return listOf(solarToInverter, inverterToHouse, batteryFlow, gridFlow)
    }
}
