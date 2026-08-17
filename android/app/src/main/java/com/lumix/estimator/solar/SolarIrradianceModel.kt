package com.lumix.estimator.solar

import kotlin.math.cos
import kotlin.math.sin

/**
 * The purely geometric piece of "how does roof orientation affect production": the angle of
 * incidence between the sun's rays and a tilted roof plane's own normal vector, using the
 * standard tilted-surface formula (Duffie & Beckman, *Solar Engineering of Thermal
 * Processes*). This says nothing about atmospheric attenuation or absolute irradiance in
 * W/m² — combining this geometric factor with real resource/cloud data happens where PV
 * output is actually calculated (the simulation engine), not here.
 */
object SolarIrradianceModel {

    /**
     * Angle (degrees) between the sun vector and the roof plane's normal. 0° = sun directly
     * perpendicular to the roof (best case); 90°+ = sun behind the roof plane (no direct sun).
     */
    fun angleOfIncidenceDegrees(
        sunAzimuthDegrees: Double,
        sunElevationDegrees: Double,
        roofAzimuthDegrees: Double,
        roofPitchDegrees: Double
    ): Double {
        val elevationRad = Math.toRadians(sunElevationDegrees)
        val pitchRad = Math.toRadians(roofPitchDegrees)
        val azimuthDiffRad = Math.toRadians(sunAzimuthDegrees - roofAzimuthDegrees)

        val cosIncidence = (cos(elevationRad) * sin(pitchRad) * cos(azimuthDiffRad) + sin(elevationRad) * cos(pitchRad))
            .coerceIn(-1.0, 1.0)
        return Math.toDegrees(kotlin.math.acos(cosIncidence))
    }

    /**
     * Plane-of-array geometric factor, 0f..1f: the fraction of "sun straight at the panel"
     * output this roof's actual orientation/pitch/sun-position combination gets, from geometry
     * alone. 0 once the sun is behind the roof plane or below the horizon.
     */
    fun planeOfArrayFactor(
        sunAzimuthDegrees: Double,
        sunElevationDegrees: Double,
        roofAzimuthDegrees: Double,
        roofPitchDegrees: Double
    ): Double {
        if (sunElevationDegrees <= 0.0) return 0.0
        val aoi = angleOfIncidenceDegrees(sunAzimuthDegrees, sunElevationDegrees, roofAzimuthDegrees, roofPitchDegrees)
        return cos(Math.toRadians(aoi)).coerceIn(0.0, 1.0)
    }
}
