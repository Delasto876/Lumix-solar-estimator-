package com.lumix.estimator.domain

/**
 * A51: the 16ft rail-layout calculator the 2026-08-13 equipment-database message asked for — how
 * many modules a single 16ft (192in) rail actually fits, modeling real module spacing (mid-clamp
 * gaps, end-clamp/rail-end margins) rather than `floor(16 / panelWidth)`.
 *
 * The clamp-gap and end-margin figures below are typical rail-hardware allowances, not one
 * specific rail manufacturer's certified spacing — this is an installation-layout estimate, per
 * the message's own framing, not a structural-engineering approval. They reproduce the message's
 * own worked expectations exactly (595/615/620W panels: ~4 modules fit a 16ft rail; 700/720W:
 * 4 modules exceed it), which is what validates the chosen allowances rather than an arbitrary
 * pick.
 */
data class RailLayoutResult(
    val maxPracticalModules: Int,
    val remainingRailIn: Double,
    val exceedsRailAt: (moduleCount: Int) -> Boolean
)

object RailLayoutCalculator {
    const val RAIL_LENGTH_IN = 192.0 // 16ft
    private const val MID_CLAMP_GAP_IN = 1.0
    private const val END_MARGIN_IN = 1.5 // rail-end margin + end-clamp engagement, each end

    /** [panelWidthMm] is the module's short (portrait) edge — how [Catalog]'s own panel [PhysicalDimensions.widthMm] is defined. */
    fun layoutFor(panelWidthMm: Double): RailLayoutResult {
        val widthIn = panelWidthMm / 25.4
        val usableIn = RAIL_LENGTH_IN - (END_MARGIN_IN * 2)

        var count = 0
        var usedIn = 0.0
        while (true) {
            val next = if (count == 0) widthIn else widthIn + MID_CLAMP_GAP_IN
            if (usedIn + next > usableIn) break
            usedIn += next
            count++
        }

        return RailLayoutResult(
            maxPracticalModules = count,
            remainingRailIn = usableIn - usedIn,
            exceedsRailAt = { moduleCount ->
                val needed = if (moduleCount <= 0) 0.0 else widthIn * moduleCount + MID_CLAMP_GAP_IN * (moduleCount - 1)
                needed > usableIn
            }
        )
    }
}
