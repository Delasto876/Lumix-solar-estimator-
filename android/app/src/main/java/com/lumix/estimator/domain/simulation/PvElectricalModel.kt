package com.lumix.estimator.domain.simulation

import com.lumix.estimator.domain.EquipmentSpecs

/**
 * A53: real per-string PV electrical behavior, replacing a flat hardcoded PV voltage. Panel count
 * is split across the selected inverter's own real MPPT-tracker count (the exact same "split as
 * evenly as possible" rule [EquipmentSelectionEngine] already validates a design's string topology
 * against — so what the simulation shows is the same topology the sizing engine actually checked,
 * not a second, disconnected assumption). Each tracker's series-string Vmp/Voc is temperature-
 * corrected from the panel's own (real or disclosed-typical) datasheet coefficients using the cell
 * temperature [SystemLosses] already derives for this instant — no new temperature model needed.
 *
 * Voltage moves with panel count, MPPT topology, and cell temperature; it is NOT simply
 * proportional to delivered power. An MPPT keeps its string near operating Vmp whenever there's
 * irradiance, even while downstream curtailment means little of that power is actually being used
 * — see [MpptReadout.isActive]'s own doc for why this reads off potential, not delivered, power.
 */
data class MpptReadout(
    val index: Int,
    val panelCount: Int,
    val vmpV: Double,
    val vocV: Double,
    val impA: Double,
    val iscA: Double,
    val powerKw: Double,
    val isActive: Boolean
)

object PvElectricalModel {
    /** Cold-morning/hot-noon correction baseline — Standard Test Conditions. */
    private const val STC_TEMP_C = 25.0

    /** Typical low-end MPPT start voltage for this inverter class — matches [com.lumix.estimator.domain.EquipmentSelectionEngine]'s own disclosed assumption, kept consistent here rather than re-guessed. */
    private const val MIN_MPPT_OPERATING_VOLTAGE = 90.0

    /**
     * Splits [totalPanelCount] across [mpptTrackers] as evenly as possible (first trackers get the
     * remainder), mirroring [com.lumix.estimator.domain.EquipmentSelectionEngine]'s own distribution
     * so a design's validated string topology and the simulation's displayed topology never disagree.
     */
    private fun panelsPerTracker(totalPanelCount: Int, mpptTrackers: Int): List<Int> {
        if (mpptTrackers <= 0 || totalPanelCount <= 0) return emptyList()
        val base = totalPanelCount / mpptTrackers
        val remainder = totalPanelCount % mpptTrackers
        return (0 until mpptTrackers).map { i -> if (i < remainder) base + 1 else base }
    }

    /**
     * One instant's per-MPPT electrical state for [config]'s actual selected panel/inverter.
     * [cellTempC] should be the same value [SimFrame.cellTempC] already carries for this instant.
     * [potentialPvKw]/[realizedPvKw] are [SimFrame.potentialPvKw]/[SimFrame.pvKw] — used only to
     * apportion each tracker's *share* of total array power, not to decide whether it's producing
     * a voltage at all (see [MpptReadout.isActive]).
     */
    fun mpptReadouts(
        panelWatts: Int,
        panelCount: Int,
        inverterKw: Double,
        inverterNameHint: String?,
        cellTempC: Double,
        potentialPvKw: Double,
        realizedPvKw: Double
    ): List<MpptReadout> {
        if (panelCount <= 0) return emptyList()
        val panelSpec = EquipmentSpecs.panelSpecFor(panelWatts)
        val invSpec = EquipmentSpecs.inverterSpecFor(inverterKw, inverterNameHint)
        val mpptTrackers = invSpec?.mpptCount?.coerceAtLeast(1) ?: 2

        val vmpPanel = panelSpec?.vmpV ?: (0.0163 * panelWatts) // ~40V/615W typical family ratio, disclosed fallback
        val vocPanel = panelSpec?.vocV ?: (0.0192 * panelWatts)
        val impPanel = panelSpec?.impA ?: (panelWatts / (vmpPanel.takeIf { it > 0 } ?: 40.0))
        val iscPanel = panelSpec?.iscA ?: (impPanel * 1.06)
        val tempCoeffVoc = panelSpec?.tempCoeffVocPctPerC ?: -0.29

        val counts = panelsPerTracker(panelCount, mpptTrackers)
        // Array is electrically "live" (MPPT holding a real operating voltage) whenever there's
        // any potential production this instant — independent of how much of it is actually being
        // used downstream. This is what keeps voltage from collapsing to zero during curtailment
        // (full sun, battery full, low load) while still correctly reading zero at night.
        val isActive = potentialPvKw > 0.01

        return counts.mapIndexed { i, panelsInTracker ->
            if (panelsInTracker <= 0) {
                MpptReadout(i + 1, 0, 0.0, 0.0, 0.0, 0.0, 0.0, false)
            } else {
                val tempFactor = 1.0 + (tempCoeffVoc / 100.0) * (cellTempC - STC_TEMP_C)
                val vmpStc = vmpPanel * panelsInTracker
                val vocStc = vocPanel * panelsInTracker
                val vmp = if (isActive) (vmpStc * tempFactor).coerceAtLeast(0.0) else 0.0
                val voc = if (isActive) (vocStc * tempFactor).coerceAtLeast(0.0) else 0.0
                val share = panelsInTracker.toDouble() / panelCount
                val powerKw = realizedPvKw * share
                MpptReadout(
                    index = i + 1,
                    panelCount = panelsInTracker,
                    vmpV = vmp,
                    vocV = voc,
                    impA = if (isActive) impPanel else 0.0,
                    iscA = if (isActive) iscPanel else 0.0,
                    powerKw = powerKw,
                    isActive = isActive && vmp >= MIN_MPPT_OPERATING_VOLTAGE
                )
            }
        }
    }

    /** A single "whole system" figure for UIs that don't (yet) show the per-MPPT breakdown — the panel-count-weighted average operating Vmp across all active trackers. */
    fun blendedVoltage(readouts: List<MpptReadout>): Double {
        val active = readouts.filter { it.isActive }
        if (active.isEmpty()) return 0.0
        val totalPanels = active.sumOf { it.panelCount }
        if (totalPanels == 0) return 0.0
        return active.sumOf { it.vmpV * it.panelCount } / totalPanels
    }
}
