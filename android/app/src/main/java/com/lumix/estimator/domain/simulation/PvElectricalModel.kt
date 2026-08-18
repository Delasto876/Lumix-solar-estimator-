package com.lumix.estimator.domain.simulation

import com.lumix.estimator.domain.EquipmentSelectionEngine
import com.lumix.estimator.domain.EquipmentSpecs
import com.lumix.estimator.domain.MpptStringPlanner
import kotlin.math.pow

/**
 * A53/A62: real per-string PV electrical behavior, replacing a flat hardcoded PV voltage. Panel
 * count is split across the selected inverter's own real MPPT-tracker count using the exact same
 * [MpptStringPlanner] rule [com.lumix.estimator.domain.EquipmentSelectionEngine] already validates
 * a design's string topology against — one shared function, not two separately-maintained copies —
 * so what the simulation shows is always the same topology the sizing engine actually chose,
 * including the A62 case where fewer than every available tracker gets used. Each tracker's
 * series-string Vmp/Voc is temperature-corrected from the panel's own (real or disclosed-typical)
 * datasheet coefficients using the cell temperature [SystemLosses] already derives for this
 * instant — no new temperature model needed.
 *
 * [vmpV]/[vocV] are the string's maximum-power and open-circuit voltages, set by panel count, MPPT
 * topology, and cell temperature only — reference points on the I-V curve, independent of how much
 * power is actually being harvested. [operatingVoltageV] is where on that curve the MPPT is actually
 * sitting this instant: normally at [vmpV] (tracking maximum power), but when the array is throttled
 * back because the battery is full and there's nowhere for the surplus to go, a real MPPT walks the
 * operating point UP toward [vocV] (voltage rises, current falls) rather than holding Vmp — see
 * [PvElectricalModel.mpptReadouts]'s own doc for the curve model. The array is still electrically
 * live (nonzero voltage) whenever there's irradiance, even under full curtailment — see
 * [MpptReadout.isActive].
 */
data class MpptReadout(
    val index: Int,
    val panelCount: Int,
    val vmpV: Double,
    val vocV: Double,
    /**
     * 2026-08-18 charging-physics fix: the string's actual operating-point voltage this instant.
     * Equals [vmpV] under normal (untracked) operation and slides toward [vocV] as the MPPT
     * throttles the array back off its maximum-power point when downstream can't absorb the power.
     */
    val operatingVoltageV: Double,
    val impA: Double,
    val iscA: Double,
    val powerKw: Double,
    val isActive: Boolean
)

object PvElectricalModel {
    /** Cold-morning/hot-noon correction baseline — Standard Test Conditions. */
    private const val STC_TEMP_C = 25.0

    /**
     * 2026-08-18 charging-physics fix: shape of the operating-voltage slide from Vmp toward Voc as
     * the MPPT throttles the array back. Physically motivated: the I-V curve's power is parabolic-
     * flat at the maximum-power point (dP/dV = 0 there), so `Pmp - P ≈ a·(V - Vmp)²` near the knee
     * — inverting gives `(V - Vmp) ∝ √(Pmp - P)`, i.e. the fraction of the Vmp→Voc gap consumed goes
     * as the square-root of the fraction of power backed off. Exponent 0.5 reproduces that and hits
     * both endpoints exactly (no throttle → Vmp; fully backed off → Voc). Still a smooth engineering
     * approximation of the real right-branch curve, not a diode-model solve — one constant to swap
     * if a measured per-panel I-V curve is ever added.
     */
    private const val THROTTLE_VOLTAGE_SHAPE = 0.5

    /**
     * One instant's per-MPPT electrical state for [config]'s actual selected panel/inverter.
     * [cellTempC] should be the same value [SimFrame.cellTempC] already carries for this instant.
     * [potentialPvKw]/[realizedPvKw] are [SimFrame.potentialPvKw]/[SimFrame.pvKw] — [potentialPvKw]
     * only decides whether the string is electrically live at all (see [MpptReadout.isActive]) and
     * [realizedPvKw] apportions each tracker's *share* of harvested power. [harvestablePvKw] is
     * [SimFrame.harvestablePvKw] — the post-loss ceiling the array could deliver if downstream could
     * absorb it; the ratio `realized / harvestable` is how far the MPPT has throttled the array back,
     * which drives [MpptReadout.operatingVoltageV] up toward Voc. Defaults to [realizedPvKw] (no
     * throttling → operating voltage sits at Vmp) so callers that don't model curtailment are
     * unaffected.
     */
    fun mpptReadouts(
        panelWatts: Int,
        panelCount: Int,
        inverterKw: Double,
        inverterNameHint: String?,
        cellTempC: Double,
        potentialPvKw: Double,
        realizedPvKw: Double,
        harvestablePvKw: Double = realizedPvKw
    ): List<MpptReadout> {
        if (panelCount <= 0) return emptyList()
        val panelSpec = EquipmentSpecs.panelSpecFor(panelWatts)
        val invSpec = EquipmentSpecs.inverterSpecFor(inverterKw, inverterNameHint)
        val mpptTrackers = invSpec?.mpptCount?.coerceAtLeast(1) ?: 2
        // A71: the real per-model MPPT floor when confirmed — shares EquipmentSelectionEngine's
        // own resolution function (not a separately re-guessed constant), including its "higher of
        // the tracking floor and the startup threshold" logic — see that function's own doc for
        // why this file's old flat-constant approach was already wrong for it, not just for the
        // continuous-tracking floor this file's own doc still explains.
        val mpptTrackingMinV = EquipmentSelectionEngine.effectiveMpptFloorV(invSpec)

        val vmpPanel = panelSpec?.vmpV ?: (0.0163 * panelWatts) // ~40V/615W typical family ratio, disclosed fallback
        val vocPanel = panelSpec?.vocV ?: (0.0192 * panelWatts)
        val impPanel = panelSpec?.impA ?: (panelWatts / (vmpPanel.takeIf { it > 0 } ?: 40.0))
        val iscPanel = panelSpec?.iscA ?: (impPanel * 1.06)
        val tempCoeffVoc = panelSpec?.tempCoeffVocPctPerC ?: -0.29

        // A62: MpptStringPlanner may deliberately use fewer than mpptTrackers strings (see its own
        // doc) — padded back out to the inverter's real physical tracker count so an unused MPPT
        // still shows up as its own inactive/zero readout, instead of silently vanishing from the
        // per-tracker breakdown.
        val plannedCounts = MpptStringPlanner.planStrings(panelCount, mpptTrackers, vmpPanel, mpptTrackingMinV)
        val counts = plannedCounts + List((mpptTrackers - plannedCounts.size).coerceAtLeast(0)) { 0 }
        // Array is electrically "live" (MPPT holding a real operating voltage) whenever there's
        // any potential production this instant — independent of how much of it is actually being
        // used downstream. This is what keeps voltage from collapsing to zero during curtailment
        // (full sun, battery full, low load) while still correctly reading zero at night.
        val isActive = potentialPvKw > 0.01
        // 2026-08-18 charging-physics fix: how much of the array's harvestable power is actually
        // being taken. 1.0 = MPP tracking normally; below 1.0 = the MPPT is throttling the array
        // back (battery full, nowhere for the surplus to go), which walks the operating voltage up
        // toward Voc. Guarded against a zero/absent ceiling so it never divides by zero.
        val harvestFraction = if (harvestablePvKw > 0.01) (realizedPvKw / harvestablePvKw).coerceIn(0.0, 1.0) else 1.0
        val voltageRiseFraction = (1.0 - harvestFraction).pow(THROTTLE_VOLTAGE_SHAPE)

        return counts.mapIndexed { i, panelsInTracker ->
            if (panelsInTracker <= 0) {
                MpptReadout(i + 1, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false)
            } else {
                val tempFactor = 1.0 + (tempCoeffVoc / 100.0) * (cellTempC - STC_TEMP_C)
                val vmpStc = vmpPanel * panelsInTracker
                val vocStc = vocPanel * panelsInTracker
                val vmp = if (isActive) (vmpStc * tempFactor).coerceAtLeast(0.0) else 0.0
                val voc = if (isActive) (vocStc * tempFactor).coerceAtLeast(0.0) else 0.0
                // Operating point slides from Vmp (full tracking) toward Voc (fully throttled off).
                val operatingV = if (isActive) (vmp + voltageRiseFraction * (voc - vmp)).coerceIn(vmp, voc) else 0.0
                val share = panelsInTracker.toDouble() / panelCount
                val powerKw = realizedPvKw * share
                MpptReadout(
                    index = i + 1,
                    panelCount = panelsInTracker,
                    vmpV = vmp,
                    vocV = voc,
                    operatingVoltageV = operatingV,
                    impA = if (isActive) impPanel else 0.0,
                    iscA = if (isActive) iscPanel else 0.0,
                    powerKw = powerKw,
                    isActive = isActive && vmp >= mpptTrackingMinV
                )
            }
        }
    }

    /**
     * A single "whole system" figure for UIs that don't (yet) show the per-MPPT breakdown — the
     * panel-count-weighted average *operating* voltage across all active trackers. Uses
     * [MpptReadout.operatingVoltageV] (not [MpptReadout.vmpV]), so a headline "PV Voltage" reading
     * correctly rises toward Voc when the array is throttled back — matching the real operating
     * point, and staying consistent with the [SimFrame.pvKw]/this-voltage current the technical
     * readout derives from it.
     */
    fun blendedVoltage(readouts: List<MpptReadout>): Double {
        val active = readouts.filter { it.isActive }
        if (active.isEmpty()) return 0.0
        val totalPanels = active.sumOf { it.panelCount }
        if (totalPanels == 0) return 0.0
        return active.sumOf { it.operatingVoltageV * it.panelCount } / totalPanels
    }
}
