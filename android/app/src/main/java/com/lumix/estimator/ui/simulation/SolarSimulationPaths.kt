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
 * (1181x1331, a "LUXIN"-branded inverter/battery, supplied as a genuine file upload). A35
 * replaced A34's hand-read-off-a-grid points with a real pixel measurement pass: per-color HSV
 * threshold → connected-component isolation → `skimage.morphology.skeletonize` → shortest-path
 * walk between the component's two farthest-apart endpoints. Each of the four lines came back as
 * a single clean connected component (no gaps to bridge, unlike the A32 artwork), so this was
 * reliable end to end. The hand-read version had genuinely cut corners — confirmed by overlaying
 * both against the real photo — most visibly on the green route, which turned out to have a full
 * multi-bend loop down at ground level (down the wall, along it, down again, then a zigzag
 * across the patio) that the hand-read version had shortcut into a plain rectangle. Every point
 * below is real pixel data, not a simplification; nothing here is an approximation of the
 * printed line, it *is* the printed line's own centerline.
 *
 * Explicit per this artwork's own legend and confirmed by tracing where each line actually
 * terminates: **pink** = grid→inverter, **yellow** = solar→inverter, **green** = inverter→house
 * (ends at a point near the front door), **blue** = inverter↔battery (ends at the battery
 * casing). This is the *opposite* of the A29/A32 artwork's own green/blue pairing — that was a
 * different photo with its own printed routing; this one's colors and endpoints are unrelated to
 * it and were re-verified from scratch, not assumed to carry over.
 *
 * Verified before writing any of this by rendering all four traced polylines, with every
 * waypoint marked, back onto the real photo and reading the composite at full scene and at a
 * tight zoom on both the inverter/battery junction and the green route's ground-level loop —
 * the exact two places the previous hand-read pass had drifted.
 */
object SolarSimulationPaths {
    /** Development-only: draws the raw path polylines (and, when true, numbers each point) so misalignment is easy to spot. Off in prod. */
    const val DEBUG_SHOW_PATHS = false

    val solarToInverterPath = EnergyPath(
        id = "solar_inverter",
        source = EnergyNode.SOLAR,
        destination = EnergyNode.INVERTER,
        points = listOf(
            NormalizedPoint(0.5140f, 0.3351f),
            NormalizedPoint(0.5123f, 0.4493f),
            NormalizedPoint(0.5064f, 0.4530f),
            NormalizedPoint(0.4564f, 0.4568f),
            NormalizedPoint(0.4488f, 0.4613f),
            NormalizedPoint(0.4471f, 0.4658f),
            NormalizedPoint(0.4462f, 0.5131f),
            NormalizedPoint(0.4488f, 0.5199f),
            NormalizedPoint(0.4539f, 0.5222f),
            NormalizedPoint(0.4886f, 0.5199f),
            NormalizedPoint(0.4903f, 0.5147f)
        ),
        bidirectional = false
    )

    val gridToInverterPath = EnergyPath(
        id = "grid_inverter",
        source = EnergyNode.GRID,
        destination = EnergyNode.INVERTER,
        points = listOf(
            NormalizedPoint(0.0838f, 0.3434f),
            NormalizedPoint(0.0838f, 0.6987f),
            NormalizedPoint(0.0898f, 0.7062f),
            NormalizedPoint(0.2549f, 0.8332f),
            NormalizedPoint(0.2608f, 0.8355f),
            NormalizedPoint(0.2752f, 0.8340f),
            NormalizedPoint(0.5080f, 0.7791f),
            NormalizedPoint(0.5191f, 0.7739f),
            NormalizedPoint(0.5207f, 0.7686f),
            NormalizedPoint(0.5216f, 0.5838f)
        ),
        // Import-only: the grid connection never carries power the other way (no export).
        bidirectional = false
    )

    val inverterToHousePath = EnergyPath(
        id = "inverter_house",
        source = EnergyNode.INVERTER,
        destination = EnergyNode.HOUSE,
        // The full ground-level loop the hand-read A34 pass had shortcut: down the wall, along
        // it, down again to the patio, then a genuine zigzag (not a straight run) across the
        // patio before reaching the door. Every one of these points is load-bearing — the
        // printed line really does bend this many times.
        points = listOf(
            NormalizedPoint(0.4903f, 0.5485f),
            NormalizedPoint(0.4843f, 0.5530f),
            NormalizedPoint(0.4479f, 0.5575f),
            NormalizedPoint(0.4437f, 0.5650f),
            NormalizedPoint(0.4437f, 0.7498f),
            NormalizedPoint(0.4420f, 0.7588f),
            NormalizedPoint(0.4318f, 0.7641f),
            NormalizedPoint(0.3116f, 0.7911f),
            NormalizedPoint(0.3040f, 0.7911f),
            NormalizedPoint(0.2667f, 0.7678f),
            NormalizedPoint(0.2625f, 0.7633f),
            NormalizedPoint(0.2625f, 0.7596f),
            NormalizedPoint(0.2718f, 0.7543f),
            NormalizedPoint(0.3192f, 0.7400f),
            NormalizedPoint(0.3260f, 0.7408f)
        ),
        bidirectional = false
    )

    val inverterToBatteryPath = EnergyPath(
        id = "inverter_battery",
        source = EnergyNode.INVERTER,
        destination = EnergyNode.BATTERY,
        // Bidirectional: charging animates inverter→battery, discharging reverses the same
        // physical line rather than drawing a second hidden route.
        points = listOf(
            NormalizedPoint(0.5411f, 0.5800f),
            NormalizedPoint(0.5402f, 0.6078f),
            NormalizedPoint(0.5436f, 0.6146f),
            NormalizedPoint(0.5563f, 0.6153f),
            NormalizedPoint(0.6173f, 0.6071f)
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
