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
 * Anchor coordinates for `bg_house_energy_routes.png`, sampled directly off the 1536x1024
 * reference image's own printed neon wires (thresholded per-color pixel scan of the actual
 * PNG, then hand-smoothed into a polyline) rather than eyeballed — see A19 in the README.
 * Still worth a visual sanity check in Android Studio (enable [SolarSimulationPaths.DEBUG_SHOW_PATHS])
 * if the artwork asset is ever swapped for a different render.
 */
object SolarSimulationPaths {
    /** Development-only: draws the raw path polylines so misalignment is easy to spot. Off in prod. */
    const val DEBUG_SHOW_PATHS = false

    val solarToInverterPath = EnergyPath(
        id = "solar_inverter",
        source = EnergyNode.SOLAR,
        destination = EnergyNode.INVERTER,
        points = listOf(
            NormalizedPoint(0.485f, 0.375f),
            NormalizedPoint(0.492f, 0.391f),
            NormalizedPoint(0.506f, 0.410f),
            NormalizedPoint(0.507f, 0.461f)
        ),
        bidirectional = false
    )

    val gridToInverterPath = EnergyPath(
        id = "grid_inverter",
        source = EnergyNode.GRID,
        destination = EnergyNode.INVERTER,
        points = listOf(
            NormalizedPoint(0.101f, 0.230f),
            NormalizedPoint(0.111f, 0.391f),
            NormalizedPoint(0.116f, 0.449f),
            NormalizedPoint(0.163f, 0.474f),
            NormalizedPoint(0.195f, 0.501f),
            NormalizedPoint(0.214f, 0.516f),
            NormalizedPoint(0.238f, 0.572f),
            NormalizedPoint(0.260f, 0.585f),
            NormalizedPoint(0.329f, 0.580f),
            NormalizedPoint(0.365f, 0.555f),
            NormalizedPoint(0.391f, 0.570f),
            NormalizedPoint(0.423f, 0.561f),
            NormalizedPoint(0.456f, 0.553f),
            NormalizedPoint(0.479f, 0.543f)
        ),
        // Import-only: the grid connection never carries power the other way (no export).
        bidirectional = false
    )

    val inverterToBatteryPath = EnergyPath(
        id = "inverter_battery",
        source = EnergyNode.INVERTER,
        destination = EnergyNode.BATTERY,
        points = listOf(
            NormalizedPoint(0.499f, 0.588f),
            NormalizedPoint(0.497f, 0.679f),
            NormalizedPoint(0.518f, 0.689f)
        ),
        bidirectional = true
    )

    val inverterToHousePath = EnergyPath(
        id = "inverter_house",
        source = EnergyNode.INVERTER,
        destination = EnergyNode.HOUSE,
        points = listOf(
            NormalizedPoint(0.540f, 0.620f),
            NormalizedPoint(0.596f, 0.587f),
            NormalizedPoint(0.651f, 0.586f),
            NormalizedPoint(0.684f, 0.575f),
            NormalizedPoint(0.710f, 0.547f),
            NormalizedPoint(0.752f, 0.553f),
            NormalizedPoint(0.781f, 0.586f),
            NormalizedPoint(0.814f, 0.605f),
            NormalizedPoint(0.830f, 0.611f)
        ),
        bidirectional = false
    )

    val allPaths = listOf(solarToInverterPath, gridToInverterPath, inverterToBatteryPath, inverterToHousePath)

    /** Bounding boxes for overlays that aren't particles (battery fill, panel/sun glow). */
    val panelArrayBounds = NormalizedRect(left = 0.3125f, top = 0.146f, right = 0.703f, bottom = 0.371f)
    val batteryBounds = NormalizedRect(left = 0.540f, top = 0.576f, right = 0.605f, bottom = 0.752f)
}
