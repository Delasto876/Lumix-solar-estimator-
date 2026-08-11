package com.lumix.estimator.site.geometry

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

data class RoofScoreBreakdown(
    val areaScore: Int,
    val orientationScore: Int,
    val pitchScore: Int,
    val usableSpaceScore: Int,
    val shadingScore: Int
) {
    val total: Int get() = areaScore + orientationScore + pitchScore + usableSpaceScore + shadingScore
}

/**
 * A 100-point "at a glance" preliminary roof quality estimate — explicitly an estimate, not a
 * certified engineering assessment, per spec. Five independent 0-20 factors rather than the
 * spec's own illustrative "Area / Orientation / Solar exposure / Usable space / Shading" (which
 * has Solar exposure and Shading measuring roughly the same thing) — Pitch replaces the
 * duplicate category here, since it's an independently meaningful factor with its own optimum.
 */
object RoofScoreCalculator {
    private const val REFERENCE_AREA_M2 = 50.0
    private const val PITCH_TOLERANCE_DEGREES = 45.0
    private const val UNCONFIRMED_ORIENTATION_SCORE = 10.0
    private const val UNCONFIRMED_PITCH_SCORE = 10.0

    fun score(
        usableAreaM2: Double,
        roofAreaM2: Double,
        azimuthDegrees: Double?,
        latitude: Double,
        pitchDegrees: Double?,
        shadingFactor: Double
    ): RoofScoreBreakdown {
        val areaScore = (usableAreaM2 / REFERENCE_AREA_M2 * 20.0).coerceIn(0.0, 20.0)

        // Ideal facing is toward the equator: south in the northern hemisphere, north in the southern.
        val idealAzimuth = if (latitude >= 0.0) 180.0 else 0.0
        val orientationScore = if (azimuthDegrees == null) {
            UNCONFIRMED_ORIENTATION_SCORE
        } else {
            val diff = angularDistanceDegrees(azimuthDegrees, idealAzimuth)
            (20.0 * cos(Math.toRadians(diff))).coerceIn(0.0, 20.0)
        }

        // Rule-of-thumb optimal fixed-panel tilt ~= site latitude.
        val idealPitch = abs(latitude)
        val pitchScore = if (pitchDegrees == null) {
            UNCONFIRMED_PITCH_SCORE
        } else {
            val diff = abs(pitchDegrees - idealPitch)
            (20.0 * (1.0 - diff / PITCH_TOLERANCE_DEGREES)).coerceIn(0.0, 20.0)
        }

        val usableSpaceScore = if (roofAreaM2 > 0.0) (usableAreaM2 / roofAreaM2 * 20.0).coerceIn(0.0, 20.0) else 0.0
        val shadingScore = (shadingFactor * 20.0).coerceIn(0.0, 20.0)

        return RoofScoreBreakdown(
            areaScore = areaScore.roundToInt(),
            orientationScore = orientationScore.roundToInt(),
            pitchScore = pitchScore.roundToInt(),
            usableSpaceScore = usableSpaceScore.roundToInt(),
            shadingScore = shadingScore.roundToInt()
        )
    }

    /** Shortest distance between two compass bearings, 0..180. */
    private fun angularDistanceDegrees(a: Double, b: Double): Double {
        val diff = abs(a - b) % 360.0
        return if (diff > 180.0) 360.0 - diff else diff
    }
}
