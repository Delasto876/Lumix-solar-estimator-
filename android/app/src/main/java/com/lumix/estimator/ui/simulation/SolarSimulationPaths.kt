package com.lumix.estimator.ui.simulation

import com.lumix.estimator.domain.simulation.EnergyNode

/**
 * A point in 0f..1f space relative to the background image's own width/height, so paths
 * stay correctly anchored to the artwork regardless of how the image is scaled/letterboxed
 * on a given device.
 */
data class NormalizedPoint(val x: Float, val y: Float)

/** A normalized rectangle used to size an overlay (battery fill, panel glow) against the image. */
data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * A single electrical route baked into `bg_house_energy_routes.png` as a static, always-
 * visible colored line. [points] traces that same line so the particle overlay renders on
 * top of it; the route itself never moves or disappears — only whether particles animate
 * along it (driven by [com.lumix.estimator.domain.simulation.EnergyFlow.active]) changes.
 */
data class EnergyPath(
    val id: String,
    val source: EnergyNode,
    val destination: EnergyNode,
    val points: List<NormalizedPoint>,
    val bidirectional: Boolean
)

/**
 * Anchor coordinates for `bg_house_energy_routes.png`, hand-picked against the supplied
 * 1536x1024 reference image. These are approximate — recalibrate visually in Android Studio
 * (enable [SolarSimulationPaths.DEBUG_SHOW_PATHS]) if a particle drifts off the printed line.
 */
object SolarSimulationPaths {
    /** Development-only: draws the raw path polylines so misalignment is easy to spot. Off in prod. */
    const val DEBUG_SHOW_PATHS = false

    val solarToInverterPath = EnergyPath(
        id = "solar_inverter",
        source = EnergyNode.SOLAR,
        destination = EnergyNode.INVERTER,
        points = listOf(
            NormalizedPoint(0.485f, 0.332f),
            NormalizedPoint(0.485f, 0.381f),
            NormalizedPoint(0.503f, 0.449f)
        ),
        bidirectional = false
    )

    val gridToInverterPath = EnergyPath(
        id = "grid_inverter",
        source = EnergyNode.GRID,
        destination = EnergyNode.INVERTER,
        points = listOf(
            NormalizedPoint(0.114f, 0.288f),
            NormalizedPoint(0.114f, 0.552f),
            NormalizedPoint(0.475f, 0.552f),
            NormalizedPoint(0.479f, 0.571f)
        ),
        // Import-only: the grid connection never carries power the other way (no export).
        bidirectional = false
    )

    val inverterToBatteryPath = EnergyPath(
        id = "inverter_battery",
        source = EnergyNode.INVERTER,
        destination = EnergyNode.BATTERY,
        points = listOf(
            NormalizedPoint(0.501f, 0.581f),
            NormalizedPoint(0.514f, 0.601f),
            NormalizedPoint(0.540f, 0.625f)
        ),
        bidirectional = true
    )

    val inverterToHousePath = EnergyPath(
        id = "inverter_house",
        source = EnergyNode.INVERTER,
        destination = EnergyNode.HOUSE,
        points = listOf(
            NormalizedPoint(0.540f, 0.552f),
            NormalizedPoint(0.700f, 0.552f),
            NormalizedPoint(0.840f, 0.552f),
            NormalizedPoint(0.840f, 0.520f)
        ),
        bidirectional = false
    )

    val allPaths = listOf(solarToInverterPath, gridToInverterPath, inverterToBatteryPath, inverterToHousePath)

    /** Bounding boxes for overlays that aren't particles (battery fill, panel/sun glow). */
    val panelArrayBounds = NormalizedRect(left = 0.3125f, top = 0.146f, right = 0.703f, bottom = 0.371f)
    val batteryBounds = NormalizedRect(left = 0.540f, top = 0.576f, right = 0.605f, bottom = 0.752f)
}
