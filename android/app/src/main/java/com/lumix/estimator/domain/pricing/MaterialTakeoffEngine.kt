package com.lumix.estimator.domain.pricing

import com.lumix.estimator.domain.EquipmentSelectionEngine
import com.lumix.estimator.domain.EquipmentSpecs
import com.lumix.estimator.domain.InverterOption
import com.lumix.estimator.domain.MaterialLine
import com.lumix.estimator.domain.PriceList
import com.lumix.estimator.domain.RailLayoutCalculator
import com.lumix.estimator.domain.RoofType
import com.lumix.estimator.domain.SystemMode
import com.lumix.estimator.domain.TransferSwitchMode
import kotlin.math.ceil

/**
 * A89/Ph21 — the master prompt's "QUOTATION PRICING ENGINE + MATERIAL TAKEOFF" formulas, in one
 * place: mounting hardware (rails/clamps/legs/weeb), wiring bundles, PV string DC protection,
 * changeover/transfer switch sizing, the AC breaker pair, distribution panel, battery DC
 * connection (breaker/busbars/box), surge arresters, enclosures, trunking/conduit, grounding, and
 * the misc/voltage-regulator lines. Deliberately narrow: this ONLY computes quantities and looks
 * up [PriceList] prices — it never chooses panels/inverters/batteries (that's still
 * [EquipmentSelectionEngine]/[com.lumix.estimator.domain.SystemCalculator]'s job) and never
 * invents a price PriceList doesn't have (every price read here is a real, named [PriceList]
 * field with a real spreadsheet-sourced default — see that file's own A89 doc).
 *
 * The always-2-rails-per-set model (replacing the old roof-type-dependent 2-or-3-rail count) and
 * the NEC-style changeover-switch/AC-breaker/DC-protection amperage sizing below were both
 * explicit, disclosed departures from this app's PRE-EXISTING engineering logic — confirmed with
 * the project owner via AskUserQuestion (2026-08-18: "Force always-2-rails per your literal
 * spec") rather than assumed.
 */
object MaterialTakeoffEngine {

    data class TakeoffInput(
        val panelW: Int,
        val panelCount: Int,
        val effectiveSystemMode: SystemMode,
        val roofType: RoofType,
        val inverter: InverterOption,
        /** No inverter-brand exception — confirmed with the project owner (AskUserQuestion, 2026-08-18): busbars/box are added purely from [batteryModuleCount], never from which battery/inverter model was picked. */
        val batteryModuleCount: Int,
        /** Already resolved to a single mode for THIS quote — see [com.lumix.estimator.domain.SystemCalculator]'s own resolution of [TransferSwitchMode] vs. the legacy MANUAL+OFFGRID [com.lumix.estimator.domain.QuoteInputs.manualOffgridUseAutoTransfer] toggle. */
        val transferSwitchMode: TransferSwitchMode,
        val useVoltageRegulator: Boolean,
        val use8WayDistributionPanel: Boolean
    )

    /** 2 x (panels in this one 2-rail set - 1), never negative — a lone panel needs zero mid clamps. */
    private fun midClampsForSet(panelsInSet: Int): Int = (2 * (panelsInSet - 1)).coerceAtLeast(0)

    private fun dcProtectionTierFor(requiredA: Double): Pair<Int, (PriceList) -> Double> = when {
        requiredA <= 15.0 -> 15 to { p: PriceList -> p.dcProtection15A }
        requiredA <= 20.0 -> 20 to { p: PriceList -> p.dcProtection20A }
        requiredA <= 25.0 -> 25 to { p: PriceList -> p.dcProtection25A }
        requiredA <= 30.0 -> 30 to { p: PriceList -> p.dcProtection30A }
        else -> 40 to { p: PriceList -> p.dcProtection40A }
    }

    /** NEC 690.8-style: breaker/fuse sized at 1.25x the string's short-circuit current (= the panel's own real Isc — a series string's current equals one module's Isc regardless of how many modules are in series). Falls back to a mid-tier default only when no Isc figure is on record for this wattage. */
    private fun requiredStringProtectionA(panelW: Int): Double {
        val iscA = EquipmentSpecs.panelSpecFor(panelW)?.iscA ?: 16.0
        return iscA * 1.25
    }

    /** Same 1.25x-continuous convention [com.lumix.estimator.domain.SystemCalculator.requiredInverterKw] already uses for inverter sizing, applied to the changeover switch and AC breaker pair — the real inverter AC output current when known, else derived from its kW rating at 240V split-phase. */
    private fun requiredAcCurrentA(inverter: InverterOption): Double {
        val invSpec = EquipmentSpecs.inverterSpecFor(inverter.kw, inverter.name)
        val baseA = invSpec?.acOutputA?.toDouble() ?: (inverter.kw * 1000.0 / 240.0)
        return baseA * 1.25
    }

    private fun changeoverTierFor(requiredA: Double): Pair<Int, (PriceList) -> Double> = when {
        requiredA <= 40.0 -> 40 to { p: PriceList -> p.changeoverSwitch40A }
        requiredA <= 50.0 -> 50 to { p: PriceList -> p.changeoverSwitch50A }
        requiredA <= 100.0 -> 100 to { p: PriceList -> p.changeoverSwitch100A }
        else -> 120 to { p: PriceList -> p.changeoverSwitch120A }
    }

    fun compute(input: TakeoffInput, prices: PriceList): List<MaterialLine> {
        val lines = mutableListOf<MaterialLine>()

        // ---- Mounting: always 2 rails per set (spec §"MOUNTING") ----
        if (input.panelCount > 0) {
            val panelWidthMm = EquipmentSpecs.panelSpecFor(input.panelW)?.dimensions?.widthMm ?: 1134.0
            val panelsPerSet = RailLayoutCalculator.layoutFor(panelWidthMm).maxPracticalModules.coerceAtLeast(1)
            val totalSets = ceil(input.panelCount / panelsPerSet.toDouble()).toInt()
            val fullSets = input.panelCount / panelsPerSet
            val remainder = input.panelCount % panelsPerSet

            val totalRails = totalSets * 2
            val totalEndClamps = totalSets * 4
            val totalMidClamps = fullSets * midClampsForSet(panelsPerSet) +
                (if (remainder > 0) midClampsForSet(remainder) else 0)
            val totalWeeb = totalSets

            lines += MaterialLine("Mounting rail (16ft)", totalRails.toDouble(), prices.mountingRail16ft, "RAIL_16FT")
            lines += MaterialLine("Mid clamp", totalMidClamps.toDouble(), prices.midClamp, "MID_CLAMP")
            lines += MaterialLine("End clamp", totalEndClamps.toDouble(), prices.endClamp, "END_CLAMP")
            lines += MaterialLine("WEEB grounding clip", totalWeeb.toDouble(), prices.weebClip, "WEEB_CLIP")

            when (input.roofType) {
                RoofType.SLAB -> {
                    lines += MaterialLine("Front leg", (totalSets * 4).toDouble(), prices.frontLeg, "FRONT_LEG")
                    lines += MaterialLine("Back leg", (totalSets * 4).toDouble(), prices.backLeg, "BACK_LEG")
                    // No spreadsheet row for anchor bolts (out of this round's scope) — a real
                    // necessary part for slab-mounted legs, same 2-bolts-per-leg ratio the code
                    // this replaces already used.
                    lines += MaterialLine("Expansion bolt M6/M8", (totalSets * 8 * 2).toDouble(), prices.m6m8Bolt)
                }
                // A89/Ph21 follow-up (2026-08-18 — "shingle roof and zinc roof carrys the same
                // rule"): SHINGLE now gets the identical 8-L-foot-per-set treatment as ZINC,
                // fixing a pre-existing gap (the code this replaces never priced SHINGLE mounting
                // at all).
                RoofType.ZINC, RoofType.SHINGLE -> {
                    lines += MaterialLine("L-foot bracket", (totalRails * 4).toDouble(), prices.lFoot, "L_FOOT")
                }
            }

            // ---- PV DC wire bundle: 20ft red + 20ft black, flat default, all systems with panels ----
            lines += MaterialLine("PV DC wire, red (#10 AWG, ft)", 20.0, prices.pvWirePerFt, "PV_RED")
            lines += MaterialLine("PV DC wire, black (#10 AWG, ft)", 20.0, prices.pvWirePerFt, "PV_BLACK")

            // ---- DC string protection: sized by real string count + real per-string Isc ----
            val compat = EquipmentSelectionEngine.checkPanelInverterCompatibility(
                input.panelW, input.panelCount, input.inverter.kw, inverterNameHint = input.inverter.name
            )
            val stringCount = compat.stringCounts.size.takeIf { it > 0 } ?: 1
            val (tierA, tierPrice) = dcProtectionTierFor(requiredStringProtectionA(input.panelW))
            when {
                stringCount <= 1 -> {
                    lines += MaterialLine("PV DIN-rail box (single string)", 1.0, prices.pvDinSingleString, "PV_DIN_SINGLE")
                    lines += MaterialLine("DC breaker/fuse ${tierA}A", 1.0, tierPrice(prices), "DC_PROTECTION_$tierA")
                }
                else -> {
                    // Greedy grouping into the spreadsheet's two combiner sizes (2-in/2-out,
                    // 3-in/3-out) — no larger combiner exists in the catalog, so >3 strings groups
                    // into multiple boxes rather than inventing a bigger one.
                    var remainingStrings = stringCount
                    var comb3 = 0
                    var comb2 = 0
                    var dinSingle = 0
                    while (remainingStrings > 0) {
                        when {
                            remainingStrings >= 3 -> { comb3++; remainingStrings -= 3 }
                            remainingStrings == 2 -> { comb2++; remainingStrings -= 2 }
                            else -> { dinSingle++; remainingStrings -= 1 }
                        }
                    }
                    if (comb3 > 0) lines += MaterialLine("DC combiner box (3 in / 3 out)", comb3.toDouble(), prices.dcCombiner3x3, "COMBINER_3X3")
                    if (comb2 > 0) lines += MaterialLine("DC combiner box (2 in / 2 out)", comb2.toDouble(), prices.dcCombiner2x2, "COMBINER_2X2")
                    if (dinSingle > 0) lines += MaterialLine("PV DIN-rail box (single string)", dinSingle.toDouble(), prices.pvDinSingleString, "PV_DIN_SINGLE")
                    lines += MaterialLine("DC breaker/fuse ${tierA}A", stringCount.toDouble(), tierPrice(prices), "DC_PROTECTION_$tierA")
                }
            }
        }

        // ---- AC wiring bundle + AC breaker pair — HYBRID/GRIDTIE only; OFFGRID keeps its own
        // pre-existing 6mm wiring path (untouched, computed in SystemCalculator directly — no
        // spreadsheet/master-prompt rule addresses off-grid AC wiring). ----
        if (input.effectiveSystemMode != SystemMode.OFFGRID) {
            // A89/Ph21 follow-up (2026-08-18): "if no Transfer switch use 30ft of the 10mm wire,
            // if manual or automatic use 80ft of the wire" — footage now keyed to the same
            // transfer-switch decision as the changeover switch/trunking below, replacing the old
            // flat 80ft-regardless default.
            val acWireFt = if (input.transferSwitchMode == TransferSwitchMode.NONE) 30.0 else 80.0
            lines += MaterialLine("AC wire, red (10mm, ft)", acWireFt, prices.ac10mmPerFt, "AC_RED")
            lines += MaterialLine("AC wire, black (10mm, ft)", acWireFt, prices.ac10mmPerFt, "AC_BLACK")
            lines += MaterialLine("AC ground wire (10mm, ft)", acWireFt, prices.ac10mmPerFt, "AC_GROUND")
            // A89/Ph21 follow-up: confirmed by the project owner — "that is hybrid logic where
            // jps send power to inverter, it does not export to the grid, so both breakers is
            // needed" (JPS feeds the inverter as an input/charging source, not a grid-export
            // relationship) — 2 breakers for HYBRID/GRIDTIE, both of which tie to JPS at the AC
            // side; GRIDTIE's own export-to-grid case wasn't explicitly addressed by that
            // confirmation, so it's left unchanged here pending a follow-up check.
            lines += MaterialLine("AC breaker (inverter output)", 1.0, prices.acBreaker, "AC_BREAKER")
            lines += MaterialLine("AC breaker (JPS input)", 1.0, prices.acBreaker, "AC_BREAKER")
        } else {
            // Pure off-grid has no JPS side to protect — one AC breaker (inverter output only).
            lines += MaterialLine("AC breaker (inverter output)", 1.0, prices.acBreaker, "AC_BREAKER")
        }

        // ---- Changeover/transfer switch — universal 3-way ask, sized by real AC load ----
        if (input.transferSwitchMode != TransferSwitchMode.NONE) {
            val (tierA, tierPrice) = changeoverTierFor(requiredAcCurrentA(input.inverter))
            val modeLabel = if (input.transferSwitchMode == TransferSwitchMode.AUTOMATIC) "automatic" else "manual"
            lines += MaterialLine("Changeover switch ${tierA}A ($modeLabel)", 1.0, tierPrice(prices), "CHANGEOVER_$tierA")
        }

        // ---- Trunking: 4in when a changeover switch was added, 3in otherwise ----
        if (input.transferSwitchMode != TransferSwitchMode.NONE) {
            lines += MaterialLine("Trunking, 4in", 1.0, prices.trunking4Inch, "TRUNK_4")
        } else {
            lines += MaterialLine("Trunking, 3in", 1.0, prices.trunking3Inch, "TRUNK_3")
        }

        // ---- Distribution panel: 4-way default, 8-way selectable ----
        if (input.use8WayDistributionPanel) {
            lines += MaterialLine("AC distribution panel (8-way)", 1.0, prices.distPanel8Way, "DIST_8WAY")
        } else {
            lines += MaterialLine("AC distribution panel (4-way)", 1.0, prices.distPanel4Way, "DIST_4WAY")
        }

        // ---- Battery DC connection: flat 250A breaker for every battery-equipped system,
        // regardless of battery brand/type; busbars + box only when more than one battery is
        // present (confirmed with the project owner — no inverter-brand exception). ----
        if (input.batteryModuleCount > 0) {
            lines += MaterialLine("Battery DC breaker (250A)", 1.0, prices.batteryBreaker250A, "BAT_BREAKER_250")
            if (input.batteryModuleCount > 1) {
                lines += MaterialLine("Battery busbar, red", 1.0, prices.batteryBusbarRed, "BAT_BUS_RED")
                lines += MaterialLine("Battery busbar, black", 1.0, prices.batteryBusbarBlack, "BAT_BUS_BLACK")
                lines += MaterialLine("Battery box, 12x12x6in", 1.0, prices.batteryBox12x12x6, "BAT_BOX_12X12X6")
            }
        }

        // ---- Surge arresters, enclosures, conduit, grounding, misc — flat defaults, all systems ----
        lines += MaterialLine("DC 1000V surge arrestor", 1.0, prices.dcSurge, "SPD_DC_1000")
        lines += MaterialLine("AC 1000V surge arrestor", 1.0, prices.acSurge, "SPD_AC_1000")
        lines += MaterialLine("PVC box, 6x4x3in", 1.0, prices.pvcBox6x4x3, "BOX_6X4X3")
        lines += MaterialLine("PVC box, 4x4x3in", 2.0, prices.pvcBox4x4x3, "BOX_4X4X3")
        lines += MaterialLine("PVC conduit, 1in", 6.0, prices.pvcConduit1Inch, "CONDUIT_1")
        lines += MaterialLine("Flex conduit, 1-1/2in (ft)", 6.0, prices.flexConduit1_5Inch, "FLEX_1_5")
        lines += MaterialLine("Earth rod, 4ft", 1.0, prices.earthRod, "EARTH_ROD_4FT")
        lines += MaterialLine("Grounding wire (ft)", 30.0, prices.groundWirePerFt, "GROUND_WIRE")
        lines += MaterialLine("Misc. allowance (screws, silicone)", 1.0, prices.miscAllowance, "MISC_6000")

        if (input.useVoltageRegulator) {
            lines += MaterialLine("Voltage regulator", 1.0, prices.voltageRegulator, "VOLT_REG")
        }

        return lines
    }
}
