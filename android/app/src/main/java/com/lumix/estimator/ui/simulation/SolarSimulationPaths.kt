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
 * visible white line. [points] traces that same line so the particle overlay renders on
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
 * Anchor coordinates for `bg_house_energy_routes.png` (A23 artwork, 1173x1341 — a utility
 * pole/ground run to the inverter, panels down to the inverter, and a short inverter-to-
 * battery hop), read directly off the printed line's own corners in the artwork (each bend
 * verified against a pixel-gridded crop, not an automated trace) — a per-column/row pixel
 * scan was tried first but over-sampled the line into a slightly wavy polyline instead of the
 * artwork's actual straight segments; a small set of precise corner points reproduces the real
 * line far more faithfully. See A23 in the README for the correction history.
 *
 * The image also prints four dashed "pointer" lines from each of its own baked labels (Grid/
 * Solar/Consumption/Battery, each showing a static "0 W" placeholder) down to the artwork —
 * those are deliberately NOT traced as [EnergyPath]s here, since they're annotation lines to
 * a number, not real electrical routes; [WattageOverlays] covers their numbers with live text
 * instead of animating particles along them.
 *
 * [inverterToHousePath] is not a separately traced line — this artwork only draws ONE physical
 * line between the inverter and the ground/street side (the same one [gridToInverterPath]
 * follows), which in real installations genuinely is shared: the meter/main-panel connection
 * carries both JPS's incoming supply and the house's outgoing load on the same conductor run.
 * Its points are [gridToInverterPath]'s own points, reversed, so "house" particles travel that
 * exact line from the inverter back toward the street junction, opposite grid's incoming ones.
 *
 * Still worth a visual sanity check in Android Studio (enable [SolarSimulationPaths.DEBUG_SHOW_PATHS])
 * if the artwork asset is ever swapped again.
 */
object SolarSimulationPaths {
    /** Development-only: draws the raw path polylines so misalignment is easy to spot. Off in prod. */
    const val DEBUG_SHOW_PATHS = false

    val solarToInverterPath = EnergyPath(
        id = "solar_inverter",
        source = EnergyNode.SOLAR,
        destination = EnergyNode.INVERTER,
        points = listOf(
            NormalizedPoint(0.5090f, 0.3788f),
            NormalizedPoint(0.4970f, 0.4191f),
            NormalizedPoint(0.5013f, 0.5071f)
        ),
        bidirectional = false
    )

    val gridToInverterPath = EnergyPath(
        id = "grid_inverter",
        source = EnergyNode.GRID,
        destination = EnergyNode.INVERTER,
        points = listOf(
            NormalizedPoint(0.0870f, 0.4855f),
            NormalizedPoint(0.0853f, 0.6674f),
            NormalizedPoint(0.1833f, 0.7233f),
            NormalizedPoint(0.3069f, 0.7755f),
            NormalizedPoint(0.5158f, 0.7457f),
            NormalizedPoint(0.5183f, 0.5817f)
        ),
        // Import-only: the grid connection never carries power the other way (no export).
        bidirectional = false
    )

    val inverterToBatteryPath = EnergyPath(
        id = "inverter_battery",
        source = EnergyNode.INVERTER,
        destination = EnergyNode.BATTERY,
        points = listOf(
            NormalizedPoint(0.5294f, 0.5779f),
            NormalizedPoint(0.5584f, 0.5966f),
            NormalizedPoint(0.5814f, 0.6055f)
        ),
        bidirectional = true
    )

    val inverterToHousePath = EnergyPath(
        id = "inverter_house",
        source = EnergyNode.INVERTER,
        destination = EnergyNode.HOUSE,
        points = gridToInverterPath.points.reversed(),
        bidirectional = false
    )

    val allPaths = listOf(solarToInverterPath, gridToInverterPath, inverterToBatteryPath, inverterToHousePath)

    /** Bounding boxes for overlays that aren't particles (battery fill, panel/sun glow). */
    val panelArrayBounds = NormalizedRect(left = 0.3027f, top = 0.1454f, right = 0.6821f, bottom = 0.3841f)
    val batteryBounds = NormalizedRect(left = 0.5926f, top = 0.5295f, right = 0.6974f, bottom = 0.6674f)

    /**
     * Bounding boxes over the artwork's own baked "0 W" (and, for battery, "100%")
     * placeholder text — [WattageOverlays] draws a live value directly on top of each,
     * fully covering the static placeholder rather than editing image pixels.
     */
    val gridLabelBounds = NormalizedRect(left = 0.0980f, top = 0.1283f, right = 0.2345f, bottom = 0.1566f)
    val solarLabelBounds = NormalizedRect(left = 0.3240f, top = 0.1253f, right = 0.4305f, bottom = 0.1543f)
    val consumptionLabelBounds = NormalizedRect(left = 0.3155f, top = 0.8151f, right = 0.4178f, bottom = 0.8412f)
    val batteryLabelBounds = NormalizedRect(left = 0.6053f, top = 0.8151f, right = 0.7588f, bottom = 0.8412f)
}
