package com.lumix.estimator.ui.simulation

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure geometry/particle math for animating current flow along [EnergyPath]s. Kept free of
 * any Compose/Android types so it can be reasoned about (and unit-verified) independently of
 * the rendering layer — [EnergyFlowCanvas] is the only place that turns these into pixels.
 */
object EnergyFlowPathManager {
    private const val MAX_PARTICLES = 16
    private const val BASE_SPEED = 0.18f // normalized path-fraction per second at ~0 kW
    private const val MAX_ADDITIONAL_SPEED = 0.55f
    private const val SPEED_REFERENCE_KW = 8.0

    // (powerKw, particleCount) breakpoints from the spec; interpolated + rounded between them.
    private val particleTable = listOf(
        0.0 to 0,
        0.5 to 2,
        1.0 to 3,
        2.0 to 5,
        4.0 to 8,
        6.0 to 12,
        8.0 to 16
    )

    /** Power-dependent particle count, capped at [MAX_PARTICLES] for performance. */
    fun particleCountFor(powerKw: Double): Int {
        if (powerKw <= 0.0) return 0
        if (powerKw >= particleTable.last().first) return MAX_PARTICLES
        for (i in 0 until particleTable.size - 1) {
            val (p0, c0) = particleTable[i]
            val (p1, c1) = particleTable[i + 1]
            if (powerKw in p0..p1) {
                val t = (powerKw - p0) / (p1 - p0)
                return (c0 + (c1 - c0) * t).roundToInt().coerceIn(0, MAX_PARTICLES)
            }
        }
        return MAX_PARTICLES
    }

    /** Power-dependent particle speed, in normalized path-fraction per second. */
    fun particleSpeedFor(powerKw: Double): Float {
        val normalized = (powerKw / SPEED_REFERENCE_KW).toFloat().coerceIn(0f, 1f)
        return BASE_SPEED + normalized * MAX_ADDITIONAL_SPEED
    }

    /** Total polyline length in normalized units (sum of segment lengths). */
    fun pathLength(path: EnergyPath): Float {
        var length = 0f
        for (i in 0 until path.points.size - 1) {
            length += segmentLength(path.points[i], path.points[i + 1])
        }
        return length
    }

    /**
     * Arc-length-based position along [path] at parametric [t] in 0f..1f, so a particle
     * moves at a visually uniform speed across bends rather than snapping through short
     * segments. [t] outside 0f..1f is coerced.
     */
    fun pointAt(path: EnergyPath, t: Float): NormalizedPoint {
        val points = path.points
        if (points.size == 1) return points[0]
        val clamped = t.coerceIn(0f, 1f)
        val totalLength = pathLength(path)
        if (totalLength <= 0f) return points[0]
        val target = clamped * totalLength

        var traveled = 0f
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val segLength = segmentLength(a, b)
            if (segLength <= 0f) continue
            if (traveled + segLength >= target || i == points.size - 2) {
                val segT = ((target - traveled) / segLength).coerceIn(0f, 1f)
                return NormalizedPoint(
                    x = a.x + (b.x - a.x) * segT,
                    y = a.y + (b.y - a.y) * segT
                )
            }
            traveled += segLength
        }
        return points.last()
    }

    /**
     * [t] spawn offsets, evenly spaced, for a given particle count. Travel direction is not
     * baked in here — the renderer advances (or retreats) these offsets over time depending
     * on the flow's [com.lumix.estimator.domain.simulation.FlowDirection].
     */
    fun particleOffsets(count: Int): List<Float> {
        if (count <= 0) return emptyList()
        return (0 until count).map { i -> i.toFloat() / count }
    }

    /** Wraps [value] into 0f..1f, e.g. for animated phase accumulation. */
    fun wrapUnit(value: Float): Float {
        val wrapped = value % 1f
        return if (wrapped < 0f) wrapped + 1f else wrapped
    }

    private fun segmentLength(a: NormalizedPoint, b: NormalizedPoint): Float =
        hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()

    fun clampProgress(value: Float): Float = min(1f, max(0f, value))
}
