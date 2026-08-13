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
 * battery hop). The grid route is a genuine "W": pole down to a first dip, up to a peak that
 * passes directly under the artwork's "Consumption" dashed pointer, down into a second
 * (deeper) dip, then a long gentle rise to the inverter. That self-crossing shape — the up-
 * and down-strokes visually cross near the peak — defeated three earlier extraction attempts
 * before this one: a per-column centroid scan over-sampled noise into a wavy line; a hand-read
 * set of corners simplified the W into a plain V and lost the peak; a graph-shortest-path trace
 * (skeletonize + shortest path between endpoints) correctly avoided the wavy-noise problem but
 * *also* cut straight across the crossing instead of following the true up-then-down route,
 * skipping the peak. Final points were read directly off pixel-gridded crops at each of the
 * W's five real vertices, confirmed against the raw pixel data at each one (not shortest-path,
 * which will always defeat itself on a self-crossing curve). See A23/A25 in the README.
 *
 * The image also prints four dashed "pointer" lines from each of its own baked labels (Grid/
 * Solar/Consumption/Battery, each showing a static "0 W" placeholder) down to the artwork —
 * those are deliberately NOT traced as [EnergyPath]s here, since they're annotation lines to
 * a number, not real electrical routes; the flow label chips in `EnergyFlowCanvas.kt` cover
 * their numbers with live icon+label+value text instead of animating particles along them.
 *
 * [inverterToHousePath] is not a separately traced line — it's the second half of
 * [gridToInverterPath] (from the T-junction at the door threshold onward, reversed), not the
 * whole thing. That junction — where the "Consumption" dashed pointer meets the ground, the
 * grid route's own peak — is where the artwork treats the meter/consumption point as living:
 * a "house" particle travels from the inverter and ends its trip there, at the door, rather
 * than continuing all the way back up to the utility pole the way a naive full-reversal would.
 * Grid's own particles continue past that same junction toward the pole, in the other
 * direction — so the two flows genuinely share one physical line, they just don't share the
 * same *span* of it: grid runs pole↔inverter, house runs door↔inverter, both riding the
 * conductor between the door junction and the inverter, in opposite directions, when both
 * are active at once.
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
            NormalizedPoint(0.4962f, 0.3818f),
            NormalizedPoint(0.5192f, 0.4176f),
            NormalizedPoint(0.5192f, 0.4996f)
        ),
        bidirectional = false
    )

    val gridToInverterPath = EnergyPath(
        id = "grid_inverter",
        source = EnergyNode.GRID,
        destination = EnergyNode.INVERTER,
        points = listOf(
            NormalizedPoint(0.0870f, 0.4855f),
            NormalizedPoint(0.0870f, 0.6659f),
            NormalizedPoint(0.2020f, 0.7256f),
            // The route's peak — passes directly under the artwork's "Consumption" dashed
            // pointer line (which meets the ground here) before dropping into its second dip.
            // Skipping this peak (an earlier attempt's shortest-path trace cut straight across
            // the W's self-crossing instead of following it) left the Consumption label looking
            // visually disconnected from the animated route.
            NormalizedPoint(0.3146f, 0.6957f),
            NormalizedPoint(0.3223f, 0.7718f),
            NormalizedPoint(0.5158f, 0.7271f),
            NormalizedPoint(0.5175f, 0.5817f)
        ),
        // Import-only: the grid connection never carries power the other way (no export).
        bidirectional = false
    )

    val inverterToBatteryPath = EnergyPath(
        id = "inverter_battery",
        source = EnergyNode.INVERTER,
        destination = EnergyNode.BATTERY,
        points = listOf(
            NormalizedPoint(0.5303f, 0.5787f),
            NormalizedPoint(0.5303f, 0.6130f),
            NormalizedPoint(0.5797f, 0.6055f)
        ),
        bidirectional = true
    )

    val inverterToHousePath = EnergyPath(
        id = "inverter_house",
        source = EnergyNode.INVERTER,
        destination = EnergyNode.HOUSE,
        // Not the whole grid line reversed — just its second half, from the inverter back to
        // the T-junction at the door threshold (index 3 in gridToInverterPath.points, the same
        // point the "Consumption" dashed pointer meets). That junction is where the artwork
        // treats the meter/consumption point as living, not the utility pole — a "house"
        // particle should end its trip at the door, not travel all the way back up the pole.
        points = gridToInverterPath.points.drop(3).reversed(),
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
