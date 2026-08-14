package com.lumix.estimator.domain

/**
 * A62 (spec §24–28 — flexible MPPT string allocation, replacing "always split evenly across every
 * tracker"): the single, shared rule for how a PV array's panel count is distributed across an
 * inverter's independent MPPT string inputs. Used by both [EquipmentSelectionEngine] (deciding and
 * validating a design's topology) and [com.lumix.estimator.domain.simulation.PvElectricalModel]
 * (displaying the live per-string electrical readout for whatever topology was decided) — one
 * source of truth, so a design's validated string topology and the simulation's displayed topology
 * can never silently disagree with each other.
 *
 * Panels are always split as evenly as possible across however many trackers actually get used
 * (the remainder, if any, goes to the first strings — a difference of at most one panel between
 * the longest and shortest string, e.g. 13 panels / 2 trackers -> [7, 6], never a lopsided split
 * like [10, 3]). Every available tracker is used by default: more, shorter strings run at lower
 * voltage, which is generally the safer choice. The one case this deliberately falls back to
 * fewer, longer strings is when splitting across every tracker would leave the *shortest* string's
 * Vmp below [minVmpPerString] (undervoltage) — and using FEWER trackers actually fixes that,
 * because fewer/longer strings run at HIGHER voltage per string, not lower.
 *
 * Using fewer trackers never helps the opposite problem (a string voltage too close to or over the
 * inverter's ceiling) — a longer string is always a higher-voltage string — so that case is
 * intentionally not handled here. A panel count that overvolts even the fullest possible split is
 * simply too large for this inverter; the caller's own panel-COUNT search (trying smaller counts)
 * is what's meant to resolve that, not the string-topology choice for one fixed count.
 */
object MpptStringPlanner {

    /**
     * @param panelCount total panels in the array.
     * @param maxTrackers the inverter's real number of independent MPPT string inputs.
     * @param vmpPerPanel one panel's operating voltage (STC, uncorrected) — used only to decide
     *   whether a candidate split's shortest string would undervolt, not to size the array.
     * @param minVmpPerString the inverter class's minimum MPPT start/operating voltage.
     * @return panel counts per string, longest-first, summing to [panelCount]. Empty if either
     *   input is non-positive.
     */
    fun planStrings(panelCount: Int, maxTrackers: Int, vmpPerPanel: Double, minVmpPerString: Double): List<Int> {
        if (panelCount <= 0 || maxTrackers <= 0) return emptyList()
        for (trackersUsed in maxTrackers downTo 1) {
            val base = panelCount / trackersUsed
            if (base <= 0) continue // fewer panels than trackers-used — not a meaningful split at this count
            val remainder = panelCount % trackersUsed
            val counts = (0 until trackersUsed).map { i -> if (i < remainder) base + 1 else base }
            val shortestVmp = counts.min() * vmpPerPanel
            if (shortestVmp >= minVmpPerString || trackersUsed == 1) {
                return counts
            }
        }
        return listOf(panelCount)
    }
}
