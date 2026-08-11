package com.lumix.estimator.site.geometry

import com.lumix.estimator.site.GeoPoint
import com.lumix.estimator.site.PanelLayout
import com.lumix.estimator.site.PanelOrientation
import com.lumix.estimator.site.PanelPlacement
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Answers "given this traced roof and these panel dimensions, how many actually fit?" by
 * placing real rectangles into the polygon — not `roofArea / panelArea`, which ignores
 * spacing, edge setback, and the fact a rectangle grid rarely tiles a roof shape perfectly.
 * Tries both [PanelOrientation]s and keeps whichever seats more panels, per spec.
 */
object PanelLayoutOptimizer {

    data class Input(
        val vertices: List<GeoPoint>,
        val panelWidthM: Double,
        val panelHeightM: Double,
        val panelWattage: Double,
        val spacingM: Double = 0.02,
        val setbackM: Double = 0.5,
        /** Grid row direction, in degrees from true north — typically the roof's confirmed azimuth. */
        val alignmentAzimuthDegrees: Double = 0.0,
        val exclusionZones: List<List<GeoPoint>> = emptyList()
    )

    /** Packs both orientations and returns whichever seats more panels. */
    fun optimize(input: Input): PanelLayout {
        val portrait = pack(input, PanelOrientation.PORTRAIT)
        val landscape = pack(input, PanelOrientation.LANDSCAPE)
        return if (landscape.panelCount > portrait.panelCount) landscape else portrait
    }

    /** Whether [desiredCount] panels of this size can physically fit, given the best-fit layout. */
    fun fitsPanelCount(desiredCount: Int, layout: PanelLayout): Boolean = desiredCount <= layout.panelCount

    private fun pack(input: Input, orientation: PanelOrientation): PanelLayout {
        if (input.vertices.size < 3) {
            return PanelLayout(input.panelWidthM, input.panelHeightM, input.panelWattage, 0, orientation)
        }
        val (panelW, panelH) = if (orientation == PanelOrientation.PORTRAIT) {
            input.panelWidthM to input.panelHeightM
        } else {
            input.panelHeightM to input.panelWidthM
        }

        val reference = input.vertices.first()
        val polygon = RoofGeometryEngine.toLocalMeters(input.vertices, reference)
        val exclusions = input.exclusionZones.map { RoofGeometryEngine.toLocalMeters(it, reference) }

        // Work in a frame rotated so grid rows run along the alignment azimuth, then rotate
        // placements back to the real local frame before converting to map coordinates.
        val theta = Math.toRadians(input.alignmentAzimuthDegrees)
        fun rotate(p: Pair<Double, Double>, angle: Double): Pair<Double, Double> {
            val (x, y) = p
            return (x * cos(angle) - y * sin(angle)) to (x * sin(angle) + y * cos(angle))
        }

        val rotatedPolygon = polygon.map { rotate(it, -theta) }
        val rotatedExclusions = exclusions.map { zone -> zone.map { rotate(it, -theta) } }

        val minX = rotatedPolygon.minOf { it.first }
        val maxX = rotatedPolygon.maxOf { it.first }
        val minY = rotatedPolygon.minOf { it.second }
        val maxY = rotatedPolygon.maxOf { it.second }

        // Start the grid at the setback-inset corner rather than the raw bounding box, so the
        // whole first row/column isn't wasted scanning positions the setback check would reject
        // anyway — each candidate is still individually validated below, so this is purely an
        // efficiency/packing-quality improvement, not a correctness relaxation.
        val placements = mutableListOf<PanelPlacement>()
        var y = minY + input.setbackM
        while (y + panelH <= maxY - input.setbackM) {
            var x = minX + input.setbackM
            while (x + panelW <= maxX - input.setbackM) {
                val corners = listOf(
                    x to y, x + panelW to y,
                    x + panelW to (y + panelH), x to (y + panelH)
                )
                if (corners.all { isValidPlacementPoint(it, rotatedPolygon, rotatedExclusions, input.setbackM) }) {
                    val centerRotated = (x + panelW / 2) to (y + panelH / 2)
                    val centerLocal = rotate(centerRotated, theta)
                    val centerGeo = RoofGeometryEngine.toGeoPoint(centerLocal.first, centerLocal.second, reference)
                    placements += PanelPlacement(center = centerGeo, rotationDegrees = input.alignmentAzimuthDegrees)
                }
                x += panelW + input.spacingM
            }
            y += panelH + input.spacingM
        }

        return PanelLayout(
            panelWidthM = input.panelWidthM,
            panelHeightM = input.panelHeightM,
            panelWattage = input.panelWattage,
            panelCount = placements.size,
            orientation = orientation,
            placements = placements
        )
    }

    private fun isValidPlacementPoint(
        point: Pair<Double, Double>,
        polygon: List<Pair<Double, Double>>,
        exclusions: List<List<Pair<Double, Double>>>,
        setbackM: Double
    ): Boolean {
        if (!pointInPolygon(point, polygon)) return false
        if (distanceToPolygonBoundary(point, polygon) < setbackM) return false
        return exclusions.none { pointInPolygon(point, it) }
    }

    private fun pointInPolygon(point: Pair<Double, Double>, polygon: List<Pair<Double, Double>>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val (xi, yi) = polygon[i]
            val (xj, yj) = polygon[j]
            if ((yi > point.second) != (yj > point.second) &&
                point.first < (xj - xi) * (point.second - yi) / (yj - yi) + xi
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    private fun distanceToPolygonBoundary(point: Pair<Double, Double>, polygon: List<Pair<Double, Double>>): Double {
        var minDistance = Double.MAX_VALUE
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            minDistance = minOf(minDistance, distancePointToSegment(point, a, b))
        }
        return minDistance
    }

    private fun distancePointToSegment(p: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dx = b.first - a.first
        val dy = b.second - a.second
        val lengthSq = dx * dx + dy * dy
        if (lengthSq < 1e-12) return hypot(p.first - a.first, p.second - a.second)
        val t = (((p.first - a.first) * dx + (p.second - a.second) * dy) / lengthSq).coerceIn(0.0, 1.0)
        return hypot(p.first - (a.first + t * dx), p.second - (a.second + t * dy))
    }
}
