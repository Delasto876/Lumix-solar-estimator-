package com.lumix.estimator.site.geometry

/**
 * A nearby obstruction the installer has visually confirmed, used only to *suggest* a starting
 * shading estimate — never computed from imagery alone. Satellite imagery has no elevation
 * data, so real ray-traced shading is out of reach here; this is a documented flat-percentage
 * heuristic per obstruction type, always labeled "estimated" and always overridable by hand.
 */
enum class ShadeObstructionType(val label: String, val exposureLossPercent: Double) {
    TREES("Trees", 8.0),
    NEARBY_BUILDING("Nearby building", 10.0),
    UTILITY_POLE("Utility pole", 4.0),
    OTHER("Other obstruction", 5.0)
}

object ShadeEstimator {
    private const val MAX_LOSS_PERCENT = 45.0

    /** Suggested exposure fraction (0f..1f) from the obstruction types marked nearby. A starting point, not a measurement. */
    fun suggestExposureFraction(selectedTypes: Set<ShadeObstructionType>): Double {
        val totalLossPercent = selectedTypes.sumOf { it.exposureLossPercent }.coerceAtMost(MAX_LOSS_PERCENT)
        return (100.0 - totalLossPercent) / 100.0
    }
}
