package com.lumix.estimator.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.rememberReduceMotion
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * An ambient sun -> panels -> home illustration. [activity] (0..1) drives how "awake" the
 * scene looks — dim and mostly static with no quote yet, fully glowing with flowing energy
 * particles once a system is sized.
 *
 * "FRONT PAGE – SUN MOVEMENT ANIMATION" (2026-08-18): layered a continuous, self-contained
 * ~48s day/night cycle on top of the house illustration below — sky color, sun arc position/
 * color, house window/door lights, and ground shadow all animate through a full 24-hour cycle
 * (pre-dawn -> sunrise -> morning -> midday -> afternoon -> sunset -> dusk -> night -> loop),
 * per the spec's own explicit sequence. This is the front page's own clock, deliberately
 * decoupled from [com.lumix.estimator.ui.simulation]'s real irradiance-driven sun/darkness
 * overlays (`EnvironmentOverlays.kt`'s `SunIndicator`/`SceneAtmosphereOverlay`) — "Do NOT link
 * this animation to the simulator time" was explicit, so this reimplements the same visual
 * language (sun arc, dusk/night color grade) as a purely decorative, always-looping clock with
 * no dependency on that engine's state.
 *
 * A88 (spec Phase 26 §3/§4 — "sun positioned on the same side/direction as the solar panels...
 * modern residential house... realistic roof... Do not place the sun on the wrong side of the
 * house"): this is a Compose vector illustration, not a photorealistic photo — no image-
 * generation or raw-image-file access was available in this environment to use the reference
 * photo the spec's own mockups show (see the README's own disclosure for this round). Within
 * that constraint, the house stays the exact vector house this app has used since A88 (roof +
 * wall body + door, panels on the same slope the sun/light sweeps over) — "do not change the
 * house, roof, or surroundings" — only the sky/sun/lighting/shadow around it now animate.
 */
@Composable
fun SolarHeroVisual(
    activity: Float,
    modifier: Modifier = Modifier
) {
    val palette = LocalLumixPalette.current
    val reduceMotion = rememberReduceMotion()
    val clampedActivity = activity.coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "solarHero")
    val pulse = if (reduceMotion) 0f else infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "sunPulse"
    ).value
    val flow = if (reduceMotion) 0f else infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "energyFlow"
    ).value
    // A full 24h cycle compressed into ~48s (spec's own "40 to 60 seconds" range), looping
    // seamlessly (Restart, not Reverse — phase 1.0 and 0.0 are defined identically below, so
    // there's no visible jump). Reduced-motion freezes on a fixed, representative midday frame
    // instead of animating — the spec's own "offer subtle or static fallback."
    val dayPhase = if (reduceMotion) 0.5f else infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(48_000, easing = LinearEasing), RepeatMode.Restart),
        label = "dayCycle"
    ).value

    val sky = computeSkyState(dayPhase)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        val w = size.width
        val h = size.height

        drawRect(brush = Brush.verticalGradient(listOf(sky.skyTop, sky.skyBottom)))

        if (sky.starsAlpha > 0.01f) {
            for (i in STAR_SEEDS.indices) {
                val (sx, sy, sr) = STAR_SEEDS[i]
                drawCircle(
                    color = Color.White.copy(alpha = sky.starsAlpha * (0.5f + 0.5f * sr)),
                    radius = (0.6f + sr * 0.8f).dp.toPx(),
                    center = Offset(sx * w, sy * h * 0.55f)
                )
            }
        }

        // A88: off-center, above the panel (right) slope of the roof — not centered over a
        // symmetric roof, so "sun on the panel side" is an actual geometric fact, not ambiguous.
        // The sun's own screen position now additionally arcs east (left) -> west (right) with
        // [dayPhase] per the spec's "Sunrise in the East -> highest point around midday -> sunset
        // in the West" — [sky.sunNormX]/[sky.sunNormY] already encode that arc; when the sun is
        // below the horizon (night) sky.sunVisible is false and nothing is drawn here at all.
        val sunCenter = Offset(w * sky.sunNormX, h * sky.sunNormY)
        val sunRadius = 16.dp.toPx()

        if (sky.sunVisible) {
            val glowScale = 1f + pulse * 0.12f
            val brightness = sky.daylight * (0.55f + 0.45f * clampedActivity)
            drawCircle(
                color = sky.sunColor.copy(alpha = 0.10f * brightness),
                radius = sunRadius * 3.4f * glowScale,
                center = sunCenter
            )
            drawCircle(
                color = sky.sunColor.copy(alpha = 0.24f * brightness),
                radius = sunRadius * 2.0f * glowScale,
                center = sunCenter
            )
            drawCircle(
                color = sky.sunColor.copy(alpha = (0.5f + 0.5f * clampedActivity) * sky.daylight),
                radius = sunRadius,
                center = sunCenter
            )
        }

        // Roof (gable — two slopes) sitting on a simple house body, per A88's own doc above:
        // panels only go on the RIGHT slope (the same side the sun sits above).
        val roofTop = h * 0.48f
        val roofBottom = h * 0.72f
        val roofLeft = w * 0.14f
        val roofRight = w * 0.86f
        val roofPeakY = h * 0.34f
        val roofPeakX = w * 0.5f

        // Ground shadow — falls opposite the sun's horizontal position, per the spec's "Shadows
        // shift direction and length according to sun position." Longest/faintest near sunrise/
        // sunset (low sun angle), shortest/darkest near midday (high sun angle); absent at night
        // (nothing to cast it, and it would be invisible in the dark anyway).
        if (sky.daylight > 0.02f) {
            val shadowLean = (0.5f - sky.sunNormX) * 2f // -1 (sun far right) .. +1 (sun far left)
            val shadowLength = (0.22f - 0.13f * sky.sunHeightFactor) * w
            val wallBottomY = h * 0.92f
            val shadowPath = Path().apply {
                moveTo(roofLeft, wallBottomY)
                lineTo(roofRight, wallBottomY)
                lineTo(roofRight + shadowLength * shadowLean, wallBottomY + 6.dp.toPx())
                lineTo(roofLeft + shadowLength * shadowLean, wallBottomY + 6.dp.toPx())
                close()
            }
            drawPath(shadowPath, color = Color.Black.copy(alpha = 0.16f * sky.daylight))
        }

        val roofPath = Path().apply {
            moveTo(roofPeakX, roofPeakY)
            lineTo(roofRight, roofTop)
            lineTo(roofRight, roofBottom)
            lineTo(roofLeft, roofBottom)
            lineTo(roofLeft, roofTop)
            close()
        }
        drawPath(roofPath, color = palette.surfaceElevated)
        drawPath(roofPath, color = palette.outline, style = Stroke(width = 1.dp.toPx()))

        // House wall body, below the roof eaves — what turns "a roof" into "a home."
        val wallTop = roofBottom - 2.dp.toPx()
        val wallBottom = h * 0.92f
        val wallLeft = w * 0.22f
        val wallRight = w * 0.78f
        drawRect(
            color = palette.surfaceElevated.copy(alpha = 0.7f),
            topLeft = Offset(wallLeft, wallTop),
            size = Size(wallRight - wallLeft, wallBottom - wallTop)
        )
        drawRect(
            color = palette.outline,
            topLeft = Offset(wallLeft, wallTop),
            size = Size(wallRight - wallLeft, wallBottom - wallTop),
            style = Stroke(width = 1.dp.toPx())
        )
        // Windows, one per side of the door — dark by day, warm-lit by night/dusk/dawn, per the
        // spec's own "5:30 AM Pre-dawn ... house lights on" / "7:00 PM Dusk ... house lights on."
        val windowW = (wallRight - wallLeft) * 0.14f
        val windowH = (wallBottom - wallTop) * 0.22f
        val windowY = wallTop + (wallBottom - wallTop) * 0.16f
        listOf(wallLeft + (wallRight - wallLeft) * 0.16f, wallRight - (wallRight - wallLeft) * 0.16f - windowW).forEach { wx ->
            drawRoundRect(
                color = lerpColor(palette.outline.copy(alpha = 0.35f), WINDOW_LIT_COLOR, sky.lightsOn),
                topLeft = Offset(wx, windowY),
                size = Size(windowW, windowH),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
        // Door, centered on the wall — a small, unambiguous "this is a house" cue. Also warms
        // with the same house-lights signal as the windows.
        val doorW = (wallRight - wallLeft) * 0.16f
        val doorH = (wallBottom - wallTop) * 0.55f
        drawRoundRect(
            color = lerpColor(palette.outline.copy(alpha = 0.5f), WINDOW_LIT_COLOR.copy(alpha = 0.8f), sky.lightsOn * 0.7f),
            topLeft = Offset(roofPeakX - doorW / 2f, wallBottom - doorH),
            size = Size(doorW, doorH),
            cornerRadius = CornerRadius(2.dp.toPx())
        )

        // Panels on the RIGHT roof slope only — the same slope the sun sits above.
        val panelCount = 4
        val panelSlopeLeft = roofPeakX + 6.dp.toPx()
        val panelSlopeRight = roofRight - 6.dp.toPx()
        val panelW = (panelSlopeRight - panelSlopeLeft) / panelCount
        val panelTop = roofTop + 8.dp.toPx()
        val panelBottom = roofBottom - 10.dp.toPx()
        repeat(panelCount) { i ->
            val litFraction = (clampedActivity * panelCount - i).coerceIn(0f, 1f)
            if (litFraction <= 0f) return@repeat
            val left = panelSlopeLeft + i * panelW
            drawRoundRect(
                color = palette.technicalCyan.copy(alpha = 0.18f + 0.22f * litFraction),
                topLeft = Offset(left + 2.dp.toPx(), panelTop),
                size = Size(panelW - 4.dp.toPx(), panelBottom - panelTop),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
        }

        // Energy particles: sun -> the panel slope (not the roof ridge), for three staggered
        // dots — reinforces SUN -> PANELS -> HOME along the same side the panels actually sit on.
        // Only while the sun is actually up, same as real solar generation.
        if (!reduceMotion && clampedActivity > 0.05f && sky.sunVisible) {
            val target = Offset((panelSlopeLeft + panelSlopeRight) / 2f, (panelTop + panelBottom) / 2f)
            for (i in 0 until 3) {
                val phase = ((flow + i / 3f) % 1f)
                val pos = Offset(
                    x = sunCenter.x + (target.x - sunCenter.x) * phase,
                    y = sunCenter.y + (target.y - sunCenter.y) * phase
                )
                val alpha = (1f - phase) * 0.8f * clampedActivity * sky.daylight
                drawCircle(color = sky.sunColor.copy(alpha = alpha.coerceIn(0f, 1f)), radius = 3.dp.toPx(), center = pos)
            }
        }
    }
}

/** Fixed pseudo-random star field (position + size seed) so stars don't visibly jitter frame to frame. */
private val STAR_SEEDS: List<Triple<Float, Float, Float>> = buildList {
    var seed = 7L
    fun next(): Float {
        seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
        return (seed % 1000) / 1000f
    }
    repeat(28) { add(Triple(next(), next(), next())) }
}

private val WINDOW_LIT_COLOR = Color(0xFFFFCB6B)

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val c = t.coerceIn(0f, 1f)
    return Color(
        red = lerp(a.red, b.red, c),
        green = lerp(a.green, b.green, c),
        blue = lerp(a.blue, b.blue, c),
        alpha = lerp(a.alpha, b.alpha, c)
    )
}

/** One full day's worth of derived visual state for a given [dayPhase] (0f = midnight, wraps at 1f). */
private class SkyState(
    val skyTop: Color,
    val skyBottom: Color,
    val sunColor: Color,
    val sunVisible: Boolean,
    val sunNormX: Float,
    val sunNormY: Float,
    val sunHeightFactor: Float,
    /** 0 at night, ramps to ~1 through the middle of the day. */
    val daylight: Float,
    /** 0 = windows/door dark, 1 = fully warm-lit. */
    val lightsOn: Float,
    val starsAlpha: Float
)

// Real clock-time fractions of a 24h day (0f..1f), matching the spec's own named keyframes
// exactly: 5:30 pre-dawn, 6:30 sunrise, 9:00 morning, 12:00 midday, 15:00 afternoon,
// 17:30 sunset, 19:00 dusk, 22:00 night.
private const val T_MIDNIGHT = 0.000f
private const val T_PREDAWN = 5.5f / 24f
private const val T_SUNRISE = 6.5f / 24f
private const val T_MORNING = 9f / 24f
private const val T_MIDDAY = 12f / 24f
private const val T_AFTERNOON = 15f / 24f
private const val T_SUNSET = 17.5f / 24f
private const val T_DUSK = 19f / 24f
private const val T_NIGHT = 22f / 24f

private val SKY_TOP_STOPS = listOf(
    T_MIDNIGHT to Color(0xFF03060F),
    T_PREDAWN to Color(0xFF0A1026),
    T_SUNRISE to Color(0xFF5B6FA8),
    T_MORNING to Color(0xFF3E8FD6),
    T_MIDDAY to Color(0xFF2C86D8),
    T_AFTERNOON to Color(0xFF4C97D8),
    T_SUNSET to Color(0xFF7C5A8C),
    T_DUSK to Color(0xFF2C2350),
    T_NIGHT to Color(0xFF060A1C),
    1f to Color(0xFF03060F)
)
private val SKY_BOTTOM_STOPS = listOf(
    T_MIDNIGHT to Color(0xFF0B1224),
    T_PREDAWN to Color(0xFF1C2440),
    T_SUNRISE to Color(0xFFFFA766),
    T_MORNING to Color(0xFFAEDBFA),
    T_MIDDAY to Color(0xFFBFE4FF),
    T_AFTERNOON to Color(0xFFBFDDF6),
    T_SUNSET to Color(0xFFFF9E62),
    T_DUSK to Color(0xFF5B4172),
    T_NIGHT to Color(0xFF0B1224),
    1f to Color(0xFF0B1224)
)
private val SUN_COLOR_STOPS = listOf(
    T_SUNRISE to Color(0xFFFFA45C),
    T_MORNING to Color(0xFFFFE9A8),
    T_MIDDAY to Color(0xFFFFF7DE),
    T_AFTERNOON to Color(0xFFFFEBB0),
    T_SUNSET to Color(0xFFFF9D5C)
)

private fun sampleStops(stops: List<Pair<Float, Color>>, t: Float): Color {
    if (t <= stops.first().first) return stops.first().second
    if (t >= stops.last().first) return stops.last().second
    for (i in 0 until stops.size - 1) {
        val (t0, c0) = stops[i]
        val (t1, c1) = stops[i + 1]
        if (t in t0..t1) {
            val local = if (t1 > t0) (t - t0) / (t1 - t0) else 0f
            return lerpColor(c0, c1, local)
        }
    }
    return stops.last().second
}

private fun computeSkyState(dayPhase: Float): SkyState {
    val t = dayPhase.coerceIn(0f, 1f)
    val skyTop = sampleStops(SKY_TOP_STOPS, t)
    val skyBottom = sampleStops(SKY_BOTTOM_STOPS, t)

    val sunVisible = t in T_SUNRISE..T_SUNSET
    val daylightSpan = T_SUNSET - T_SUNRISE
    val sunProgress = if (sunVisible) ((t - T_SUNRISE) / daylightSpan).coerceIn(0f, 1f) else 0f
    val sunHeightFactor = sin(PI.toFloat() * sunProgress).coerceIn(0f, 1f)
    val sunNormX = lerp(0.06f, 0.94f, sunProgress)
    val sunNormY = 0.62f - 0.46f * sunHeightFactor
    val sunColor = if (sunVisible) sampleStops(SUN_COLOR_STOPS, t) else Color(0xFFFFE9A8)

    // Smooth 0 -> 1 -> 0 daylight envelope across sunrise..sunset, easing in/out near the edges
    // (so dawn/dusk aren't an abrupt on/off switch) rather than the raw sine's own sharper edges.
    val daylight = if (sunVisible) {
        val eased = min(1f, sunProgress / 0.12f) * min(1f, (1f - sunProgress) / 0.12f)
        max(sunHeightFactor, 0f) * max(eased, 0.15f)
    } else 0f

    val lightsOn = (1f - daylight * 1.35f).coerceIn(0f, 1f)
    val starsAlpha = (1f - daylight * 2.2f).coerceIn(0f, 1f) *
        (if (t in T_DUSK..1f || t <= T_PREDAWN) 1f else ((t - T_SUNSET) / (T_DUSK - T_SUNSET)).coerceIn(0f, 1f))

    return SkyState(
        skyTop = skyTop,
        skyBottom = skyBottom,
        sunColor = sunColor,
        sunVisible = sunVisible,
        sunNormX = sunNormX,
        sunNormY = sunNormY,
        sunHeightFactor = sunHeightFactor,
        daylight = daylight,
        lightsOn = lightsOn,
        starsAlpha = starsAlpha
    )
}
