package com.lumix.estimator.ui.simulation

import com.lumix.estimator.domain.simulation.EnergyNode

/**
 * A point in 0f..1f space relative to the background image's own width/height, so paths
 * stay correctly anchored to the artwork regardless of how the image is scaled/letterboxed
 * on a given device. `screenX = normalizedX * displayedImageWidth`, same for Y — this is what
 * lets the same route geometry work unmodified on a Samsung A15 or any other device/density.
 */
data class NormalizedPoint(val x: Float, val y: Float)

/** A normalized rectangle used to size an overlay (battery fill, panel glow) against the image. */
data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * A single electrical route baked into `bg_house_energy_routes.png` as a static, always-
 * visible colored glow line. [points] traces that same line, in order, as a deterministic
 * polyline — particles walk P0→P1→P2→...→Pn along the arc length, never a shortest-path or
 * straight jump from source to destination. The route itself never moves or disappears — only
 * whether particles animate along it (driven by [com.lumix.estimator.domain.simulation.EnergyFlow.active])
 * and which direction they travel changes; power level changes particle count/speed/intensity,
 * never the geometry.
 */
data class EnergyPath(
    val id: String,
    val source: EnergyNode,
    val destination: EnergyNode,
    val points: List<NormalizedPoint>,
    val bidirectional: Boolean
)

/**
 * Anchor coordinates for `bg_house_energy_routes.png` — A34 swapped in a new reference photo
 * (1181x1331, a "LUXIN"-branded inverter/battery, supplied as a genuine file upload this round —
 * the first time a *replacement* background image landed as an actual attachment rather than a
 * chat paste, so this trace used real pixel measurement from the start instead of working around
 * a missing file). All four routes are printed in flat, distinct, non-crossing colors with mostly
 * axis-aligned bends, so hand-reading vertices off a fine coordinate grid (render the grid onto
 * the real image, crop tightly around each line, read intersections) was reliable — the same
 * method used successfully for the A29/A32 artwork, just against an image built specifically for
 * this kind of tracing.
 *
 * Explicit per this artwork's own legend and confirmed by tracing where each line actually
 * terminates: **pink** = grid→inverter, **yellow** = solar→inverter, **green** = inverter→house
 * (ends at a point near the front door), **blue** = inverter↔battery (ends at the battery
 * casing). This is the *opposite* of the A29/A32 artwork's own green/blue pairing — that was a
 * different photo with its own printed routing; this one's colors and endpoints are unrelated to
 * it and were re-verified from scratch, not assumed to carry over.
 *
 * Verified before writing any of this by rendering all four traced polylines, in these exact
 * colors, back onto the real photo and reading the composite — full-scene and a tight zoom on
 * the inverter/battery junction where all four lines converge.
 */
object SolarSimulationPaths {
    /** Development-only: draws the raw path polylines (and, when true, numbers each point) so misalignment is easy to spot. Off in prod. */
    const val DEBUG_SHOW_PATHS = false

    val solarToInverterPath = EnergyPath(
        id = "solar_inverter",
        source = EnergyNode.SOLAR,
        destination = EnergyNode.INVERTER,
        // Straight down from the panel array, a single left-then-down jog, then into the
        // inverter's PV input on its top-left.
        points = listOf(
            NormalizedPoint(0.5080f, 0.3280f),
            NormalizedPoint(0.5080f, 0.4600f),
            NormalizedPoint(0.4480f, 0.4600f),
            NormalizedPoint(0.4480f, 0.5150f),
            NormalizedPoint(0.5080f, 0.5200f)
        ),
        bidirectional = false
    )

    val gridToInverterPath = EnergyPath(
        id = "grid_inverter",
        source = EnergyNode.GRID,
        destination = EnergyNode.INVERTER,
        // Down the utility pole, a diagonal drop to ground level, right along the lawn/patio
        // edge, then straight up into the inverter's grid connection on its lower-left.
        points = listOf(
            NormalizedPoint(0.0860f, 0.3350f),
            NormalizedPoint(0.0860f, 0.6850f),
            NormalizedPoint(0.2450f, 0.8280f),
            NormalizedPoint(0.3500f, 0.8150f),
            NormalizedPoint(0.5100f, 0.7900f),
            NormalizedPoint(0.5350f, 0.7750f),
            NormalizedPoint(0.5350f, 0.5720f)
        ),
        // Import-only: the grid connection never carries power the other way (no export).
        bidirectional = false
    )

    val inverterToHousePath = EnergyPath(
        id = "inverter_house",
        source = EnergyNode.INVERTER,
        destination = EnergyNode.HOUSE,
        // Left out of the inverter's load output, straight down along the wall, then across
        // the patio to a point at the front door — the house's load connection.
        points = listOf(
            NormalizedPoint(0.5080f, 0.5520f),
            NormalizedPoint(0.4400f, 0.5520f),
            NormalizedPoint(0.4400f, 0.7600f),
            NormalizedPoint(0.2450f, 0.7650f),
            NormalizedPoint(0.3180f, 0.7350f)
        ),
        bidirectional = false
    )

    val inverterToBatteryPath = EnergyPath(
        id = "inverter_battery",
        source = EnergyNode.INVERTER,
        destination = EnergyNode.BATTERY,
        // A short hop off the inverter's lower-right, down then right into the battery casing.
        // Bidirectional: charging animates inverter→battery, discharging reverses the same
        // physical line rather than drawing a second hidden route.
        points = listOf(
            NormalizedPoint(0.5620f, 0.5720f),
            NormalizedPoint(0.5650f, 0.6050f),
            NormalizedPoint(0.6220f, 0.6100f)
        ),
        bidirectional = true
    )

    val allPaths = listOf(solarToInverterPath, gridToInverterPath, inverterToHousePath, inverterToBatteryPath)

    // Named component anchors, per spec — each is just the relevant path's own first/last point,
    // so they can never drift out of sync with the route geometry itself.
    val gridAnchor: NormalizedPoint get() = gridToInverterPath.points.first()
    val solarAnchor: NormalizedPoint get() = solarToInverterPath.points.first()
    val inverterGridAnchor: NormalizedPoint get() = gridToInverterPath.points.last()
    val inverterPvAnchor: NormalizedPoint get() = solarToInverterPath.points.last()
    val inverterLoadAnchor: NormalizedPoint get() = inverterToHousePath.points.first()
    val inverterBatteryAnchor: NormalizedPoint get() = inverterToBatteryPath.points.first()
    val batteryAnchor: NormalizedPoint get() = inverterToBatteryPath.points.last()
    val houseLoadAnchor: NormalizedPoint get() = inverterToHousePath.points.last()

    /** Bounding boxes for overlays that aren't particles (battery fill, panel/sun glow). */
    val panelArrayBounds = NormalizedRect(left = 0.3750f, top = 0.1950f, right = 0.8000f, bottom = 0.3350f)
    val batteryBounds = NormalizedRect(left = 0.6180f, top = 0.5670f, right = 0.7000f, bottom = 0.6650f)
}
