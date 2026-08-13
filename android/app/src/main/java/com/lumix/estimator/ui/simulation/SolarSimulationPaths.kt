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
 * Unlike A23-A28, this trace used real file access: the image came back out of git history
 * (`git show <Phase A commit>:...bg_house_energy_routes.png`), not a chat-pasted photo, so every
 * point below was read directly off pixel-gridded crops of the actual artwork — the same
 * render-a-grid-overlay-and-read-it method used successfully on the A23 image, just applied to
 * the correct photo this time.
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
            NormalizedPoint(0.4850f, 0.3650f),
            NormalizedPoint(0.4700f, 0.3830f),
            NormalizedPoint(0.4980f, 0.4000f),
            NormalizedPoint(0.4920f, 0.4600f)
        ),
        bidirectional = false
    )

    val gridToInverterPath = EnergyPath(
        id = "grid_inverter",
        source = EnergyNode.GRID,
        destination = EnergyNode.INVERTER,
        points = listOf(
            NormalizedPoint(0.1050f, 0.3350f),
            NormalizedPoint(0.1050f, 0.4350f),
            NormalizedPoint(0.2000f, 0.4630f),
            NormalizedPoint(0.3000f, 0.5050f),
            NormalizedPoint(0.4000f, 0.5650f),
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
            NormalizedPoint(0.4970f, 0.5830f),
            NormalizedPoint(0.4970f, 0.6850f),
            NormalizedPoint(0.5380f, 0.6850f)
        ),
        bidirectional = true
    )

    val inverterToHousePath = EnergyPath(
        id = "inverter_house",
        source = EnergyNode.INVERTER,
        destination = EnergyNode.HOUSE,
        // The long terminal: exits the inverter's bottom-right, dips down, then runs along the
        // wall past the battery and the window to a small junction/breaker box — the artwork's
        // actual load/consumption connection point.
        points = listOf(
            NormalizedPoint(0.5080f, 0.5830f),
            NormalizedPoint(0.5080f, 0.6150f),
            NormalizedPoint(0.5350f, 0.6220f),
            NormalizedPoint(0.6000f, 0.5850f),
            NormalizedPoint(0.6500f, 0.5650f),
            NormalizedPoint(0.6950f, 0.5350f),
            NormalizedPoint(0.7020f, 0.5150f)
        ),
        bidirectional = false
    )

    val allPaths = listOf(solarToInverterPath, gridToInverterPath, inverterToBatteryPath, inverterToHousePath)

    /** Bounding boxes for overlays that aren't particles (battery fill, panel/sun glow). */
    val panelArrayBounds = NormalizedRect(left = 0.3100f, top = 0.1000f, right = 0.7200f, bottom = 0.3600f)
    val batteryBounds = NormalizedRect(left = 0.5380f, top = 0.5850f, right = 0.5980f, bottom = 0.7550f)
}
