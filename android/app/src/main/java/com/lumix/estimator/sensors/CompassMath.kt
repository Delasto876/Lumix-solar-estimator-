package com.lumix.estimator.sensors

import kotlin.math.roundToInt

/**
 * Pure heading math — no Android sensor dependency, so it's independently verifiable (the
 * sensor plumbing in [CompassManager] itself is not, since this sandbox has no real device).
 */
object CompassMath {
    private const val SMOOTHING_ALPHA = 0.15
    private val compassLabels = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    fun normalizeDegrees(degrees: Double): Double {
        val d = degrees % 360.0
        return if (d < 0) d + 360.0 else d
    }

    /** Shortest signed angular delta from [from] to [to] (degrees), correctly handling the 0/360 wraparound. */
    fun shortestAngleDelta(from: Double, to: Double): Double {
        var delta = (to - from) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        return delta
    }

    /** Exponential-moving-average smoothing of a new raw heading against the previous smoothed value. */
    fun smooth(previousSmoothed: Double?, newHeading: Double, alpha: Double = SMOOTHING_ALPHA): Double {
        val previous = previousSmoothed ?: return normalizeDegrees(newHeading)
        val delta = shortestAngleDelta(previous, newHeading)
        return normalizeDegrees(previous + alpha * delta)
    }

    fun compassLabel(headingDegrees: Double): String {
        val index = Math.round(normalizeDegrees(headingDegrees) / 45.0).toInt() % 8
        return compassLabels[index]
    }
}
