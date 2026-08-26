package com.lumix.estimator.site.geometry

import com.lumix.estimator.site.GeoPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Turns a hand-traced roof polygon (real WGS84 vertices) into area, perimeter, centroid, and
 * an azimuth hint. Everything here is a *preliminary estimate* from a flat satellite trace —
 * see the module README for the accuracy disclaimer that applies to every value this produces.
 *
 * Geometry is done in a local equirectangular projection (meters, relative to the polygon's
 * first vertex): accurate to a small fraction of a percent at roof scale (tens of meters),
 * which is well within the uncertainty already inherent in hand-tracing a roof from satellite
 * imagery — a full geodesic (ellipsoidal) area calculation would add complexity without adding
 * real accuracy at this scale.
 */
object RoofGeometryEngine {
    private const val METERS_PER_DEGREE_LAT = 111_320.0

    private fun metersPerDegreeLon(referenceLatitude: Double): Double =
        METERS_PER_DEGREE_LAT * cos(Math.toRadians(referenceLatitude))

    /** Projects [points] into local meters (east, north) relative to [reference]. */
    fun toLocalMeters(points: List<GeoPoint>, reference: GeoPoint): List<Pair<Double, Double>> {
        val metersPerDegreeLon = metersPerDegreeLon(reference.latitude)
        return points.map { p ->
            val x = (p.longitude - reference.longitude) * metersPerDegreeLon
            val y = (p.latitude - reference.latitude) * METERS_PER_DEGREE_LAT
            x to y
        }
    }

    /** Inverse of [toLocalMeters] for a single point. */
    fun toGeoPoint(xMeters: Double, yMeters: Double, reference: GeoPoint): GeoPoint {
        val metersPerDegreeLon = metersPerDegreeLon(reference.latitude)
        return GeoPoint(
            latitude = reference.latitude + yMeters / METERS_PER_DEGREE_LAT,
            longitude = reference.longitude + xMeters / metersPerDegreeLon
        )
    }

    /** Horizontal (satellite-projected) polygon area in m² via the shoelace formula. */
    fun horizontalAreaM2(vertices: List<GeoPoint>): Double {
        if (vertices.size < 3) return 0.0
        val local = toLocalMeters(vertices, vertices.first())
        return shoelaceArea(local)
    }

    private fun shoelaceArea(points: List<Pair<Double, Double>>): Double {
        var sum = 0.0
        for (i in points.indices) {
            val (x1, y1) = points[i]
            val (x2, y2) = points[(i + 1) % points.size]
            sum += x1 * y2 - x2 * y1
        }
        return abs(sum) / 2.0
    }

    /**
     * True roof-surface area from the horizontal projection, per spec: roofArea =
     * horizontalArea / cos(pitch). Falls back to the horizontal area (labeled a preliminary
     * estimate by the caller) when pitch hasn't been supplied.
     */
    fun roofAreaM2(horizontalAreaM2: Double, pitchDegrees: Double?): Double {
        if (pitchDegrees == null) return horizontalAreaM2
        val pitchRad = Math.toRadians(pitchDegrees.coerceIn(0.0, 89.0))
        return horizontalAreaM2 / cos(pitchRad)
    }

    fun perimeterMeters(vertices: List<GeoPoint>): Double {
        if (vertices.size < 2) return 0.0
        val local = toLocalMeters(vertices, vertices.first())
        var perimeter = 0.0
        for (i in local.indices) {
            val (x1, y1) = local[i]
            val (x2, y2) = local[(i + 1) % local.size]
            perimeter += hypot(x2 - x1, y2 - y1)
        }
        return perimeter
    }

    /** Area-weighted centroid, back-projected to a real map coordinate. */
    fun centroid(vertices: List<GeoPoint>): GeoPoint {
        require(vertices.isNotEmpty()) { "Cannot compute centroid of an empty polygon" }
        if (vertices.size < 3) {
            return GeoPoint(vertices.map { it.latitude }.average(), vertices.map { it.longitude }.average())
        }
        val reference = vertices.first()
        val local = toLocalMeters(vertices, reference)
        var signedArea = 0.0
        var cx = 0.0
        var cy = 0.0
        for (i in local.indices) {
            val (x1, y1) = local[i]
            val (x2, y2) = local[(i + 1) % local.size]
            val cross = x1 * y2 - x2 * y1
            signedArea += cross
            cx += (x1 + x2) * cross
            cy += (y1 + y2) * cross
        }
        signedArea *= 0.5
        if (abs(signedArea) < 1e-9) {
            return GeoPoint(vertices.map { it.latitude }.average(), vertices.map { it.longitude }.average())
        }
        return toGeoPoint(cx / (6 * signedArea), cy / (6 * signedArea), reference)
    }

    /**
     * Two candidate facing directions (perpendicular to the polygon's longest edge, which is
     * typically a ridge or eave line), 180° apart. A flat traced polygon alone cannot say which
     * of the two is actually downslope — that ambiguity is why this is a *suggestion* the user
     * confirms, never [RoofPlane.azimuthDegrees][com.lumix.estimator.site.RoofPlane.azimuthDegrees]
     * directly.
     */
    fun suggestAzimuthCandidates(vertices: List<GeoPoint>): Pair<Double, Double>? {
        if (vertices.size < 2) return null
        val local = toLocalMeters(vertices, vertices.first())
        var longestIndex = 0
        var longestLength = -1.0
        for (i in local.indices) {
            val (x1, y1) = local[i]
            val (x2, y2) = local[(i + 1) % local.size]
            val length = hypot(x2 - x1, y2 - y1)
            if (length > longestLength) {
                longestLength = length
                longestIndex = i
            }
        }
        val (x1, y1) = local[longestIndex]
        val (x2, y2) = local[(longestIndex + 1) % local.size]
        // Bearing from true north (clockwise) of the edge vector itself.
        val edgeBearing = normalizeDegrees(Math.toDegrees(atan2(x2 - x1, y2 - y1)))
        return normalizeDegrees(edgeBearing + 90.0) to normalizeDegrees(edgeBearing - 90.0)
    }

    fun normalizeDegrees(degrees: Double): Double {
        val d = degrees % 360.0
        return if (d < 0) d + 360.0 else d
    }

    private val compassLabels = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    /** The nearest 8-point compass label for an azimuth in degrees (0 = N, 90 = E, ...). */
    fun compassLabel(azimuthDegrees: Double): String {
        val index = (normalizeDegrees(azimuthDegrees) / 45.0).roundToInt() % 8
        return compassLabels[index]
    }

    /**
     * Roof-surface usable area after setback and drawn exclusion zones (chimneys, tanks,
     * vents, ...). Setback loss is approximated as `perimeter × setback` — exact for a
     * rectangle's edges, a slight overestimate at concave corners — rather than computing a
     * full polygon offset/erosion, which is unnecessary precision for a preliminary estimate.
     * Both losses are scaled by the same horizontal→roof-surface ratio as [roofAreaM2] so a
     * pitched roof's usable area accounts for pitch consistently with its total area.
     */
    fun usableAreaM2(
        vertices: List<GeoPoint>,
        horizontalAreaM2: Double,
        roofAreaM2: Double,
        setbackMeters: Double,
        /** Site Survey / Solar Mapping round: real, individually-typed obstruction polygons — see [RoofExclusionZone]'s own doc. */
        exclusionZones: List<RoofExclusionZone>,
        /** A lump-sum obstruction area (chimney, tank, vents, ...) already in roof-surface m², for when tracing individual exclusion polygons isn't practical (e.g. manual entry with no map). */
        additionalExclusionAreaM2: Double = 0.0
    ): Double {
        val pitchScale = if (horizontalAreaM2 > 0) roofAreaM2 / horizontalAreaM2 else 1.0
        val setbackLossM2 = perimeterMeters(vertices) * setbackMeters * pitchScale
        val exclusionAreaM2 = exclusionZones.sumOf { it.areaM2 } * pitchScale
        return (roofAreaM2 - setbackLossM2 - exclusionAreaM2 - additionalExclusionAreaM2).coerceAtLeast(0.0)
    }
}
