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
 * visible colored glow line. [points] traces that same line so the particle overlay renders on
 * top of it; the route itself never moves or disappears — only whether particles animate along
 * it (driven by [com.lumix.estimator.domain.simulation.EnergyFlow.active]) changes.
 */
data class EnergyPath(
    val id: String,
    val source: EnergyNode,
    val destination: EnergyNode,
    val points: List<NormalizedPoint>,
    val bidirectional: Boolean
)

/**
 * Anchor coordinates for `bg_house_energy_routes.png` — A29 restored the original Phase A
 * artwork (1536x1024) after several rounds (A23-A28) spent on a different photo that only had
 * plain white lines and had to have four colors reconstructed onto it from descriptions. This
 * original image already prints its own four genuinely-colored glow lines — no reconstruction
 * needed, only tracing what's actually there.
 *
 * A32 replaced A29's hand-read points with a pixel-measured trace: per-color HSV thresholding
 * (each line's hue/saturation is distinct enough from the house/foliage/sky to isolate cleanly)
 * → connected-component isolation → `skimage.morphology.skeletonize` → shortest-path walk between
 * the component's two endpoints → Ramer–Douglas–Peucker simplification. None of these four lines
 * self-cross (unlike the old A23-A26 artwork), so skeleton shortest-path is reliable here — no
 * risk of cutting across a self-crossing curve the way it was for that other image. Two of the
 * four lines (grid and house) have real gaps in the printed artwork — a dim, shadowed stretch and
 * a stretch that dips out of the color-detection range — where the mask breaks into two or three
 * disconnected pieces; those pieces were each traced independently and stitched together, with a
 * couple of interpolated points across the genuinely gap regions to keep the animated line
 * visually continuous. The result hugs the actual printed conduit at every bend (the roofline,
 * the wall corners, the small kinks where it clips to a downpipe) instead of the coarser 6-9
 * point straight-segment approximations from A29, which visibly drifted off the printed line
 * partway along the two longest runs.
 *
 * One finding worth flagging: at the inverter's two bottom terminals, the *green* line is the
 * short one that terminates directly against the battery casing, and the *blue* line is the long
 * one that continues past the battery, along the wall, to a small junction/breaker box near the
 * window. That means, in this specific artwork, green = inverter→battery and blue =
 * inverter→house — the reverse of the green=load/blue=battery mapping stated earlier for a
 * different (hand-drawn) reference. Since the artwork's own printed endpoints are the ground
 * truth here (an actual line terminating at an actual component beats a remembered verbal
 * description), the code below follows what the pixels show. Flag this if it's wrong — it's a
 * one-line fix to swap [inverterToBatteryPath] and [inverterToHousePath]'s point lists.
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
            NormalizedPoint(0.4733f, 0.3711f),
            NormalizedPoint(0.4785f, 0.3701f),
            NormalizedPoint(0.4831f, 0.3848f),
            NormalizedPoint(0.4883f, 0.3848f),
            NormalizedPoint(0.4922f, 0.3936f),
            NormalizedPoint(0.4987f, 0.3926f),
            NormalizedPoint(0.5072f, 0.4082f),
            NormalizedPoint(0.5065f, 0.4561f)
        ),
        bidirectional = false
    )

    val gridToInverterPath = EnergyPath(
        id = "grid_inverter",
        source = EnergyNode.GRID,
        destination = EnergyNode.INVERTER,
        // Pole down, along the roof/awning edge, a shadowed dip near the downpipe clip, then
        // into the inverter's left side — every bend is a real vertex read off the printed
        // conduit, not a straight-segment shortcut across it.
        points = listOf(
            NormalizedPoint(0.0931f, 0.3105f),
            NormalizedPoint(0.0990f, 0.3555f),
            NormalizedPoint(0.1126f, 0.3564f),
            NormalizedPoint(0.1133f, 0.4346f),
            NormalizedPoint(0.1204f, 0.4521f),
            NormalizedPoint(0.1595f, 0.4834f),
            NormalizedPoint(0.2150f, 0.5050f),
            NormalizedPoint(0.3250f, 0.5240f),
            NormalizedPoint(0.3320f, 0.5684f),
            NormalizedPoint(0.3581f, 0.5596f),
            NormalizedPoint(0.3893f, 0.5723f),
            NormalizedPoint(0.4062f, 0.5713f),
            NormalizedPoint(0.4186f, 0.5439f),
            NormalizedPoint(0.4284f, 0.5645f),
            NormalizedPoint(0.4707f, 0.5537f),
            NormalizedPoint(0.4766f, 0.5469f),
            NormalizedPoint(0.4727f, 0.5273f),
            NormalizedPoint(0.4950f, 0.5480f)
        ),
        // Import-only: the grid connection never carries power the other way (no export).
        bidirectional = false
    )

    val inverterToBatteryPath = EnergyPath(
        id = "inverter_battery",
        source = EnergyNode.INVERTER,
        destination = EnergyNode.BATTERY,
        // The short terminal: exits the inverter's bottom-left, drops straight down, then a
        // short jog right into the battery casing — a genuine printed endpoint (a bright glow
        // dot flush against the case), not a mid-route bend.
        points = listOf(
            NormalizedPoint(0.4948f, 0.5889f),
            NormalizedPoint(0.4987f, 0.6006f),
            NormalizedPoint(0.5007f, 0.6904f),
            NormalizedPoint(0.5195f, 0.6924f),
            NormalizedPoint(0.5319f, 0.6846f),
            NormalizedPoint(0.5404f, 0.6914f),
            NormalizedPoint(0.5397f, 0.7002f)
        ),
        bidirectional = true
    )

    val inverterToHousePath = EnergyPath(
        id = "inverter_house",
        source = EnergyNode.INVERTER,
        destination = EnergyNode.HOUSE,
        // The long terminal: exits the inverter's bottom-right, dips down through a small S-bend
        // near the battery's corner, then runs along the wall past the window to a small
        // junction/breaker box — the artwork's actual load/consumption connection point.
        points = listOf(
            NormalizedPoint(0.5124f, 0.5889f),
            NormalizedPoint(0.5117f, 0.6123f),
            NormalizedPoint(0.5189f, 0.6221f),
            NormalizedPoint(0.5443f, 0.6182f),
            NormalizedPoint(0.5957f, 0.5898f),
            NormalizedPoint(0.6126f, 0.5928f),
            NormalizedPoint(0.6302f, 0.5879f),
            NormalizedPoint(0.6445f, 0.5732f),
            NormalizedPoint(0.6719f, 0.5645f),
            NormalizedPoint(0.6771f, 0.5645f),
            NormalizedPoint(0.6823f, 0.5723f),
            NormalizedPoint(0.6979f, 0.5693f),
            NormalizedPoint(0.7025f, 0.5645f),
            NormalizedPoint(0.7070f, 0.5293f)
        ),
        bidirectional = false
    )

    val allPaths = listOf(solarToInverterPath, gridToInverterPath, inverterToBatteryPath, inverterToHousePath)

    /** Bounding boxes for overlays that aren't particles (battery fill, panel/sun glow). */
    val panelArrayBounds = NormalizedRect(left = 0.3100f, top = 0.1000f, right = 0.7200f, bottom = 0.3600f)
    val batteryBounds = NormalizedRect(left = 0.5380f, top = 0.5850f, right = 0.5980f, bottom = 0.7550f)
}
