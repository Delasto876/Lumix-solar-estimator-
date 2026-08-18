package com.lumix.estimator.domain

import kotlinx.serialization.Serializable

/**
 * A51: prices for the new real, named equipment models ([Catalog], backed by [EquipmentSpecs])
 * started out as placeholder values — "just put some random price, I will update the prices in
 * settings when created" (2026-08-13). 2026-08-18 ("for any inverter or component that doesn't
 * have a price leave blank with option to edit and enter price") replaced every one of those
 * still-unpriced placeholders with a real `Double? = null` — the exact rule the A89 master prompt
 * itself already stated twice ("NEVER INVENT A PRICE. A BLANK PRICE MUST ALWAYS REMAIN BLANK
 * UNTIL THE INSTALLER ENTERS IT. ESPECIALLY FOR INVERTERS") but had only actually been applied to
 * [deliveryTollJmd] before this round. See [PriceFields.nullableAll] for the Settings-editable
 * blank/enter-a-price UI these now render through.
 */
@Serializable
data class PriceList(
    // A89/Ph21: Deye 6k, LuxPower 12k/13k and SRNE 6k/8k/10k below carry the price-list
    // spreadsheet's real JMD figures (Solar_Installer_Price_List_Template_2.xlsx) as their
    // defaults — see the matching EquipmentSpecs.kt model-string reconciliation ("USE THE LATEST
    // ONE WITH THE LATEST PRICE"). Every other inverter/battery/misc field below is nullable —
    // no real price has ever been given for these — and starts blank, per this file's own top doc.
    val inverterDeye6k: Double = 230000.0,
    val inverterDeye8k: Double? = null,
    val inverterGrowatt10k: Double? = null,
    val inverterLuxpowerLxpLb12k: Double = 300000.0,
    val inverterLuxpowerGenLb13k: Double = 340000.0,

    // MANUAL-only (partially verified / needs verification) inverters — no real price given yet.
    val inverterLuxpowerGenLb6k: Double? = null,
    val inverterLuxpowerGenLb8k: Double? = null,
    val inverterLuxpowerGenLb10k: Double? = null,
    val inverterSrneHesp4to6_5k: Double = 190000.0,
    val inverterSrneHesp8k: Double = 275000.0,
    val inverterSrneHesp10k: Double = 300000.0,
    val inverterSrneHesp12k: Double? = null,
    val inverterGrowattSph8k: Double? = null,

    val inverterGridTie15k: Double? = null,

    val inverterOffgrid3k72: Double? = null,
    val inverterOffgrid3k78: Double? = null,
    val inverterOffgrid3_2kPowmr: Double? = null,
    val chargeController80A: Double? = null,

    // A89/Ph21: 5/10/15/16 kWh prices match the price-list spreadsheet's BAT-5/10/15/16 rows
    // (JMD 155,000/290,000/310,000/325,000). No spreadsheet row exists for 20 kWh, a custom-per-kWh
    // rate, or the AGM class below — no real price has been given for any of those three, so they
    // stay blank rather than carry a guessed figure.
    val batteryLFP5k: Double = 155000.0,
    val batteryLFP10k: Double = 290000.0,
    val batteryLFP15k: Double = 310000.0,
    // A54: MANUAL mode's battery bank step previously only offered 5/10/15 kWh — added per
    // installer request ("make it 5, 10, 15, 16, 20 and a custom field"). 16k/20k are additional
    // MANUAL-only nominal-capacity classes (same "class label, not a matched real product" costing
    // pattern the existing 5/10/15 fields already use — see Catalog.kt's own doc on this).
    val batteryLFP16k: Double = 325000.0,
    val batteryLFP20k: Double? = null,
    /** $/kWh rate for MANUAL mode's custom battery-capacity field — installer enters any kWh figure and a count; cost = kWh x count x this rate. No real rate given yet. */
    val batteryLFPCustomPerKwh: Double? = null,

    val batteryAGM12V: Double? = null,

    // A89/Ph21: panel prices now match the price-list spreadsheet's Panels sheet exactly
    // (P595/P615/P620/P700/P720 rows).
    val panel595W: Double = 18500.0,
    val panel615W: Double = 19000.0,
    val panel620W: Double = 21000.0,
    val panel700W: Double = 24000.0,
    val panel720W: Double = 26000.0,

    // A89/Ph21: mounting-hardware prices below now match the price-list spreadsheet's Materials &
    // Pricing sheet exactly (RAIL_16FT/MID_CLAMP/END_CLAMP/FRONT_LEG/BACK_LEG/L_FOOT/WEEB_CLIP).
    val mountingRail16ft: Double = 4500.0,
    val midClamp: Double = 200.0,
    val endClamp: Double = 200.0,
    val lFoot: Double = 450.0,
    val backLeg: Double = 1500.0,
    val frontLeg: Double = 1350.0,
    val m6m8Bolt: Double = 100.0,
    /** A89/Ph21 follow-up (2026-08-18): real price from the project owner — J$450/pair, 4 pairs on every system, replacing the old placeholder and the old off-grid-vs-other-mode count split. */
    val mc4Pair: Double = 450.0,
    val weebClip: Double = 300.0,

    // A89/Ph21: PV wire (red/black, #10 AWG) and AC wire (red/black/ground, 10mm) both price
    // identically per color/conductor in the spreadsheet (PV_RED=PV_BLACK=100/ft;
    // AC_RED=AC_BLACK=AC_GROUND=100/ft) — one field each, [MaterialTakeoffEngine] emits separate
    // red/black(/ground) material lines at the same per-ft rate rather than duplicating fields.
    val pvWirePerFt: Double = 100.0,
    val ac10mmPerFt: Double = 100.0,
    /** Off-grid-only AC wiring — no spreadsheet counterpart, and no real rate given yet, so this stays blank rather than carry a guessed figure; [MaterialTakeoffEngine] keeps off-grid's own AC wire path separate from the spreadsheet-driven hybrid/grid-tie bundle above. */
    val ac6mmPerFt: Double? = null,

    // A89/Ph21: DC string protection (breaker/fuse by amperage tier) and PV combiner/DIN
    // selection — new, from the spreadsheet's Protection rows. Selected by
    // [MaterialTakeoffEngine] from real short-circuit current (string count -> 1 = single DIN
    // box, 2 = 2x2 combiner, 3 = 3x3 combiner) rather than a flat fixed line.
    val dcProtection15A: Double = 2200.0,
    val dcProtection20A: Double = 2500.0,
    val dcProtection25A: Double = 2500.0,
    val dcProtection30A: Double = 2500.0,
    val dcProtection40A: Double = 3000.0,
    val dcCombiner2x2: Double = 19000.0,
    val dcCombiner3x3: Double = 28000.0,
    val pvDinSingleString: Double = 5500.0,

    /** AC breaker (inverter output / JPS input pair) — replaces the old flat [pvDisconnect32A]/[dcBatteryBreaker100A] pattern; [MaterialTakeoffEngine] always uses 2 of these (one per side), per the spreadsheet's own AC_BREAKER row and the master prompt's "1 for inverter output and one for jps input." */
    val acBreaker: Double = 98000.0,

    // A89/Ph21: changeover/transfer switch, now 4 real amperage tiers (spreadsheet
    // CHANGEOVER_40/50/100/120) selected by [MaterialTakeoffEngine] from the system's real AC
    // load per NEC-style breaker-sizing logic — replaces the old single flat [transferSwitch]/
    // [changeOverSwitchOffgrid] pair, which couldn't represent "the right size for this load."
    val changeoverSwitch40A: Double = 15000.0,
    val changeoverSwitch50A: Double = 17000.0,
    val changeoverSwitch100A: Double = 25000.0,
    val changeoverSwitch120A: Double = 28000.0,

    val distPanel4Way: Double = 2500.0,
    val distPanel8Way: Double = 3500.0,

    // A89/Ph21: battery DC connection — a flat 250A breaker for every system regardless of
    // battery type/brand (spreadsheet's own "Updated from 200 A" note), plus busbars/box only
    // when more than one battery is present (2+ in parallel) — see MaterialTakeoffEngine's own
    // doc for the exact rule (confirmed with the project owner: no inverter-brand exception).
    val batteryBreaker250A: Double = 17000.0,
    val batteryBusbarRed: Double = 2500.0,
    val batteryBusbarBlack: Double = 2500.0,
    val batteryBox12x12x6: Double = 10000.0,

    val dcSurge: Double = 15000.0,
    val acSurge: Double = 15000.0,
    val pvcBox6x4x3: Double = 2000.0,
    val pvcBox4x4x3: Double = 1500.0,
    /** 4in when a changeover switch (manual or automatic) is selected, 3in when none — see [MaterialTakeoffEngine]. */
    val trunking4Inch: Double = 6000.0,
    val trunking3Inch: Double = 5000.0,
    val pvcConduit1Inch: Double = 4000.0,
    val flexConduit1_5Inch: Double = 2000.0,
    val earthRod: Double = 2500.0,
    /** A89/Ph21 follow-up (2026-08-18): the project owner confirmed the spreadsheet's J$3,000/ft was not correct and gave the real rate — J$100/ft, in line with the PV/AC wire rows above. */
    val groundWirePerFt: Double = 100.0,
    val miscAllowance: Double = 5000.0,
    /** Ask-only — added to a quote only when [QuoteInputs.useVoltageRegulator] is explicitly set. */
    val voltageRegulator: Double = 18000.0,

    /**
     * A89/Ph21: the Junction (St. Elizabeth) -> Santa Cruz baseline [DeliveryCalculator] scales
     * every other non-toll route's delivery charge from — spreadsheet's own DEL-BASE row.
     */
    val deliveryBaseChargeJmd: Double = 18000.0,
    val deliveryBaselineDistanceKm: Double = 28.0,
    /**
     * The first genuinely nullable price in this file, and the pattern every other nullable field
     * below now follows (spreadsheet's own DEL-TOLL row is blank — "use current official rate").
     * Null means exactly what the master prompt requires: any quote whose route crosses a toll
     * cannot be finalized until the installer enters this — see
     * [QuoteResult.missingPriceItems]/[QuoteResult.canFinalize]. Never defaulted to 0 or any
     * invented figure.
     */
    val deliveryTollJmd: Double? = null,

    /**
     * A79 (spec Phase 16 — "improve settings/materials", §40's own "Labour rates"): previously
     * hard-coded as a literal `* 0.15` inside [SystemCalculator], not editable anywhere despite
     * being exactly the kind of business rate the spec's Settings checklist asks for. Now the
     * single source [SystemCalculator.calculate] reads, editable from the same Materials & Pricing
     * settings section every other price already is.
     */
    val serviceRatePercent: Double = 15.0,
    /**
     * A79 (spec Phase 16, §40's own "Tax settings"): always effectively 0 before this round (see
     * [QuoteResult.taxAmount]'s A78 doc) since no rate existed anywhere to set. Applied to the
     * post-discount subtotal in [SystemCalculator.calculate], matching standard practice and the
     * spec's own display order ("Original subtotal, Discount, Final subtotal, Tax/fees, Grand
     * total" — tax follows discount, not the other way around). Defaults to 0.0 — enabling a real
     * rate (e.g. Jamaica's GCT) is the installer's own business decision to make here, not
     * something to assume.
     */
    val taxRatePercent: Double = 0.0
) {
    fun panelPrice(watts: Int): Double = when (watts) {
        595 -> panel595W
        615 -> panel615W
        620 -> panel620W
        700 -> panel700W
        720 -> panel720W
        else -> panel595W
    }

    companion object {
        val DEFAULT = PriceList()
    }
}

data class PriceFieldSpec(
    val key: String,
    val label: String,
    val group: String,
    val get: (PriceList) -> Double,
    val set: (PriceList, Double) -> PriceList,
    /** A79 (spec Phase 16): almost every field here is a J$ amount, but [PriceList.serviceRatePercent]/[PriceList.taxRatePercent] are percentages — this lets the shared Settings field loop render the correct unit instead of always assuming currency. */
    val suffix: String = "J$"
)

/**
 * 2026-08-18 ("leave blank with option to edit and enter price"): the nullable counterpart of
 * [PriceFieldSpec], for every price this file has no real figure for yet. [get] returns null when
 * blank; the Settings UI ([com.lumix.estimator.ui.settings.SettingsScreen]) renders that as an
 * empty, editable field rather than a guessed "0" — and [SystemCalculator]'s existing
 * [QuoteResult.missingPriceItems]/[MaterialLine.hasPrice] machinery (built in A89 for
 * [PriceList.deliveryTollJmd], the first field this pattern was used for) already treats a null
 * price as "flag this quote, don't invent a cost" with no further wiring needed here.
 */
data class NullablePriceFieldSpec(
    val key: String,
    val label: String,
    val group: String,
    val get: (PriceList) -> Double?,
    val set: (PriceList, Double?) -> PriceList,
    val suffix: String = "J$"
)

object PriceFields {
    val all: List<PriceFieldSpec> = listOf(
        PriceFieldSpec("inverterDeye6k", "Deye SUN-6K-SG02LP2-US (needs verification)", "Hybrid Inverters (Verified)", { it.inverterDeye6k }, { p, v -> p.copy(inverterDeye6k = v) }),
        PriceFieldSpec("inverterLuxpowerLxpLb12k", "LuxPower SNA-US 12K (needs verification)", "Hybrid Inverters (Verified)", { it.inverterLuxpowerLxpLb12k }, { p, v -> p.copy(inverterLuxpowerLxpLb12k = v) }),
        PriceFieldSpec("inverterLuxpowerGenLb13k", "LuxPower LXP-LB-US 12K/13K (needs verification)", "Hybrid Inverters (Verified)", { it.inverterLuxpowerGenLb13k }, { p, v -> p.copy(inverterLuxpowerGenLb13k = v) }),

        PriceFieldSpec("inverterSrneHesp4to6_5k", "SRNE ASF4860U80-H (needs verification)", "Hybrid Inverters (Manual only)", { it.inverterSrneHesp4to6_5k }, { p, v -> p.copy(inverterSrneHesp4to6_5k = v) }),
        PriceFieldSpec("inverterSrneHesp8k", "SRNE ASF4880S180-H (needs verification)", "Hybrid Inverters (Manual only)", { it.inverterSrneHesp8k }, { p, v -> p.copy(inverterSrneHesp8k = v) }),
        PriceFieldSpec("inverterSrneHesp10k", "SRNE HES48100U200-H (needs verification)", "Hybrid Inverters (Manual only)", { it.inverterSrneHesp10k }, { p, v -> p.copy(inverterSrneHesp10k = v) }),

        PriceFieldSpec("batteryLFP5k", "5 kWh LiFePO4 (SRNE SR-EOS05B)", "Batteries", { it.batteryLFP5k }, { p, v -> p.copy(batteryLFP5k = v) }),
        PriceFieldSpec("batteryLFP10k", "10 kWh LiFePO4 (SRNE SR-EOS10B)", "Batteries", { it.batteryLFP10k }, { p, v -> p.copy(batteryLFP10k = v) }),
        PriceFieldSpec("batteryLFP15k", "15 kWh LiFePO4 (SRNE SR-EOS15B)", "Batteries", { it.batteryLFP15k }, { p, v -> p.copy(batteryLFP15k = v) }),
        PriceFieldSpec("batteryLFP16k", "16 kWh LiFePO4 (Manual only)", "Batteries", { it.batteryLFP16k }, { p, v -> p.copy(batteryLFP16k = v) }),

        PriceFieldSpec("panel595W", "595W PV panel (JA Solar JAM72D40-GB)", "Panels", { it.panel595W }, { p, v -> p.copy(panel595W = v) }),
        PriceFieldSpec("panel615W", "615W PV panel (DAS DH156NA)", "Panels", { it.panel615W }, { p, v -> p.copy(panel615W = v) }),
        PriceFieldSpec("panel620W", "620W PV panel (DAS DH156NA)", "Panels", { it.panel620W }, { p, v -> p.copy(panel620W = v) }),
        PriceFieldSpec("panel700W", "700W PV panel (JinkoSolar JKM700N)", "Panels", { it.panel700W }, { p, v -> p.copy(panel700W = v) }),
        PriceFieldSpec("panel720W", "720W PV panel (JinkoSolar JKM720N)", "Panels", { it.panel720W }, { p, v -> p.copy(panel720W = v) }),

        PriceFieldSpec("mountingRail16ft", "Mounting rail (16ft)", "Mounting Hardware", { it.mountingRail16ft }, { p, v -> p.copy(mountingRail16ft = v) }),
        PriceFieldSpec("midClamp", "Mid clamp", "Mounting Hardware", { it.midClamp }, { p, v -> p.copy(midClamp = v) }),
        PriceFieldSpec("endClamp", "End clamp", "Mounting Hardware", { it.endClamp }, { p, v -> p.copy(endClamp = v) }),
        PriceFieldSpec("lFoot", "L-FOOT bracket", "Mounting Hardware", { it.lFoot }, { p, v -> p.copy(lFoot = v) }),
        PriceFieldSpec("backLeg", "Adjustable back leg", "Mounting Hardware", { it.backLeg }, { p, v -> p.copy(backLeg = v) }),
        PriceFieldSpec("frontLeg", "Front leg", "Mounting Hardware", { it.frontLeg }, { p, v -> p.copy(frontLeg = v) }),
        PriceFieldSpec("m6m8Bolt", "Expansion bolt M6/M8", "Mounting Hardware", { it.m6m8Bolt }, { p, v -> p.copy(m6m8Bolt = v) }),
        PriceFieldSpec("mc4Pair", "MC4 connector pair", "Mounting Hardware", { it.mc4Pair }, { p, v -> p.copy(mc4Pair = v) }),
        PriceFieldSpec("weebClip", "WEEB clip", "Mounting Hardware", { it.weebClip }, { p, v -> p.copy(weebClip = v) }),

        PriceFieldSpec("pvWirePerFt", "PV DC wire, red or black (per ft, #10 AWG)", "Wiring", { it.pvWirePerFt }, { p, v -> p.copy(pvWirePerFt = v) }),
        PriceFieldSpec("ac10mmPerFt", "AC wire, red/black/ground (per ft, 10mm)", "Wiring", { it.ac10mmPerFt }, { p, v -> p.copy(ac10mmPerFt = v) }),
        PriceFieldSpec("groundWirePerFt", "Grounding wire (per ft)", "Wiring", { it.groundWirePerFt }, { p, v -> p.copy(groundWirePerFt = v) }),

        PriceFieldSpec("dcProtection15A", "DC breaker/fuse 15A", "DC & AC Protection", { it.dcProtection15A }, { p, v -> p.copy(dcProtection15A = v) }),
        PriceFieldSpec("dcProtection20A", "DC breaker/fuse 20A", "DC & AC Protection", { it.dcProtection20A }, { p, v -> p.copy(dcProtection20A = v) }),
        PriceFieldSpec("dcProtection25A", "DC breaker/fuse 25A", "DC & AC Protection", { it.dcProtection25A }, { p, v -> p.copy(dcProtection25A = v) }),
        PriceFieldSpec("dcProtection30A", "DC breaker/fuse 30A", "DC & AC Protection", { it.dcProtection30A }, { p, v -> p.copy(dcProtection30A = v) }),
        PriceFieldSpec("dcProtection40A", "DC breaker/fuse 40A", "DC & AC Protection", { it.dcProtection40A }, { p, v -> p.copy(dcProtection40A = v) }),
        PriceFieldSpec("dcCombiner2x2", "DC combiner box (2 in / 2 out)", "DC & AC Protection", { it.dcCombiner2x2 }, { p, v -> p.copy(dcCombiner2x2 = v) }),
        PriceFieldSpec("dcCombiner3x3", "DC combiner box (3 in / 3 out)", "DC & AC Protection", { it.dcCombiner3x3 }, { p, v -> p.copy(dcCombiner3x3 = v) }),
        PriceFieldSpec("pvDinSingleString", "PV DIN-rail box (single string)", "DC & AC Protection", { it.pvDinSingleString }, { p, v -> p.copy(pvDinSingleString = v) }),
        PriceFieldSpec("acBreaker", "AC breaker (inverter output / JPS input)", "DC & AC Protection", { it.acBreaker }, { p, v -> p.copy(acBreaker = v) }),
        PriceFieldSpec("dcSurge", "DC 1000V surge arrestor", "DC & AC Protection", { it.dcSurge }, { p, v -> p.copy(dcSurge = v) }),
        PriceFieldSpec("acSurge", "AC 1000V surge arrestor", "DC & AC Protection", { it.acSurge }, { p, v -> p.copy(acSurge = v) }),

        PriceFieldSpec("changeoverSwitch40A", "Changeover switch 40A", "Transfer & Distribution", { it.changeoverSwitch40A }, { p, v -> p.copy(changeoverSwitch40A = v) }),
        PriceFieldSpec("changeoverSwitch50A", "Changeover switch 50A", "Transfer & Distribution", { it.changeoverSwitch50A }, { p, v -> p.copy(changeoverSwitch50A = v) }),
        PriceFieldSpec("changeoverSwitch100A", "Changeover switch 100A", "Transfer & Distribution", { it.changeoverSwitch100A }, { p, v -> p.copy(changeoverSwitch100A = v) }),
        PriceFieldSpec("changeoverSwitch120A", "Changeover switch 120A", "Transfer & Distribution", { it.changeoverSwitch120A }, { p, v -> p.copy(changeoverSwitch120A = v) }),
        PriceFieldSpec("distPanel4Way", "AC distribution panel (4-way)", "Transfer & Distribution", { it.distPanel4Way }, { p, v -> p.copy(distPanel4Way = v) }),
        PriceFieldSpec("distPanel8Way", "AC distribution panel (8-way)", "Transfer & Distribution", { it.distPanel8Way }, { p, v -> p.copy(distPanel8Way = v) }),
        PriceFieldSpec("voltageRegulator", "Voltage regulator", "Transfer & Distribution", { it.voltageRegulator }, { p, v -> p.copy(voltageRegulator = v) }),

        PriceFieldSpec("batteryBreaker250A", "Battery DC breaker (250A)", "Battery Connection", { it.batteryBreaker250A }, { p, v -> p.copy(batteryBreaker250A = v) }),
        PriceFieldSpec("batteryBusbarRed", "Battery busbar, red (>1 battery)", "Battery Connection", { it.batteryBusbarRed }, { p, v -> p.copy(batteryBusbarRed = v) }),
        PriceFieldSpec("batteryBusbarBlack", "Battery busbar, black (>1 battery)", "Battery Connection", { it.batteryBusbarBlack }, { p, v -> p.copy(batteryBusbarBlack = v) }),
        PriceFieldSpec("batteryBox12x12x6", "Battery box, 12x12x6in (>1 battery)", "Battery Connection", { it.batteryBox12x12x6 }, { p, v -> p.copy(batteryBox12x12x6 = v) }),

        PriceFieldSpec("pvcBox6x4x3", "PVC box, 6x4x3in", "Enclosures & Conduit", { it.pvcBox6x4x3 }, { p, v -> p.copy(pvcBox6x4x3 = v) }),
        PriceFieldSpec("pvcBox4x4x3", "PVC box, 4x4x3in", "Enclosures & Conduit", { it.pvcBox4x4x3 }, { p, v -> p.copy(pvcBox4x4x3 = v) }),
        PriceFieldSpec("trunking4Inch", "Trunking, 4in (with changeover switch)", "Enclosures & Conduit", { it.trunking4Inch }, { p, v -> p.copy(trunking4Inch = v) }),
        PriceFieldSpec("trunking3Inch", "Trunking, 3in (no changeover switch)", "Enclosures & Conduit", { it.trunking3Inch }, { p, v -> p.copy(trunking3Inch = v) }),
        PriceFieldSpec("pvcConduit1Inch", "PVC conduit, 1in", "Enclosures & Conduit", { it.pvcConduit1Inch }, { p, v -> p.copy(pvcConduit1Inch = v) }),
        PriceFieldSpec("flexConduit1_5Inch", "Flex conduit, 1-1/2in (per ft)", "Enclosures & Conduit", { it.flexConduit1_5Inch }, { p, v -> p.copy(flexConduit1_5Inch = v) }),
        PriceFieldSpec("earthRod", "Earth rod, 4ft, with clamp", "Enclosures & Conduit", { it.earthRod }, { p, v -> p.copy(earthRod = v) }),
        PriceFieldSpec("miscAllowance", "Misc. allowance (screws, silicone)", "Enclosures & Conduit", { it.miscAllowance }, { p, v -> p.copy(miscAllowance = v) }),

        // A89/Ph21: delivery pricing infrastructure — see DeliveryCalculator.kt. deliveryTollJmd
        // is NOT listed here — it's nullable, see nullableAll below.
        PriceFieldSpec("deliveryBaseChargeJmd", "Delivery baseline (Junction -> Santa Cruz, 28km)", "Delivery", { it.deliveryBaseChargeJmd }, { p, v -> p.copy(deliveryBaseChargeJmd = v) }),

        // A79 (spec Phase 16, §40 "Labour rates" / "Tax settings"): the only two percentage-typed
        // fields in this list — see PriceFieldSpec.suffix's own doc for why the "%" is explicit.
        PriceFieldSpec("serviceRatePercent", "Service/labour rate (% of materials)", "Business Rates", { it.serviceRatePercent }, { p, v -> p.copy(serviceRatePercent = v) }, suffix = "%"),
        PriceFieldSpec("taxRatePercent", "Tax/fees rate (% of post-discount subtotal)", "Business Rates", { it.taxRatePercent }, { p, v -> p.copy(taxRatePercent = v) }, suffix = "%")
    )

    /**
     * 2026-08-18 ("for any inverter or component that doesn't have a price leave blank with option
     * to edit and enter price"): every price this file has no real figure for, rendered by the
     * Settings screen as its own blank-until-entered group, separate from [all] above so the two
     * lists' differing get/set signatures (`Double` vs `Double?`) never have to unify into one.
     */
    val nullableAll: List<NullablePriceFieldSpec> = listOf(
        NullablePriceFieldSpec("inverterDeye8k", "Deye SUN-8K-SG01LP1-US", "Hybrid Inverters (Verified)", { it.inverterDeye8k }, { p, v -> p.copy(inverterDeye8k = v) }),
        NullablePriceFieldSpec("inverterGrowatt10k", "Growatt SPH 10000TL-HU-US", "Hybrid Inverters (Verified)", { it.inverterGrowatt10k }, { p, v -> p.copy(inverterGrowatt10k = v) }),

        NullablePriceFieldSpec("inverterLuxpowerGenLb6k", "LuxPower GEN-LB-US 6K (partially verified)", "Hybrid Inverters (Manual only)", { it.inverterLuxpowerGenLb6k }, { p, v -> p.copy(inverterLuxpowerGenLb6k = v) }),
        NullablePriceFieldSpec("inverterLuxpowerGenLb8k", "LuxPower GEN-LB-US 8K (partially verified)", "Hybrid Inverters (Manual only)", { it.inverterLuxpowerGenLb8k }, { p, v -> p.copy(inverterLuxpowerGenLb8k = v) }),
        NullablePriceFieldSpec("inverterLuxpowerGenLb10k", "LuxPower GEN-LB-US 10K (partially verified)", "Hybrid Inverters (Manual only)", { it.inverterLuxpowerGenLb10k }, { p, v -> p.copy(inverterLuxpowerGenLb10k = v) }),
        NullablePriceFieldSpec("inverterSrneHesp12k", "SRNE HESP 12K-US (needs verification)", "Hybrid Inverters (Manual only)", { it.inverterSrneHesp12k }, { p, v -> p.copy(inverterSrneHesp12k = v) }),
        NullablePriceFieldSpec("inverterGrowattSph8k", "Growatt SPH 8000TL-HU-US (needs verification)", "Hybrid Inverters (Manual only)", { it.inverterGrowattSph8k }, { p, v -> p.copy(inverterGrowattSph8k = v) }),

        NullablePriceFieldSpec("inverterGridTie15k", "15000W Grid-tie 3-phase", "Grid-tie Inverters", { it.inverterGridTie15k }, { p, v -> p.copy(inverterGridTie15k = v) }),

        NullablePriceFieldSpec("inverterOffgrid3k72", "3000W Off-grid 12V (72k)", "Off-grid Inverters", { it.inverterOffgrid3k72 }, { p, v -> p.copy(inverterOffgrid3k72 = v) }),
        NullablePriceFieldSpec("inverterOffgrid3k78", "3000W Off-grid 12V (78k)", "Off-grid Inverters", { it.inverterOffgrid3k78 }, { p, v -> p.copy(inverterOffgrid3k78 = v) }),
        NullablePriceFieldSpec("inverterOffgrid3_2kPowmr", "3200W Off-grid PowMr 24V", "Off-grid Inverters", { it.inverterOffgrid3_2kPowmr }, { p, v -> p.copy(inverterOffgrid3_2kPowmr = v) }),
        NullablePriceFieldSpec("chargeController80A", "80A MPPT charge controller", "Off-grid Inverters", { it.chargeController80A }, { p, v -> p.copy(chargeController80A = v) }),

        NullablePriceFieldSpec("batteryLFP20k", "20 kWh LiFePO4 (Manual only)", "Batteries", { it.batteryLFP20k }, { p, v -> p.copy(batteryLFP20k = v) }),
        NullablePriceFieldSpec("batteryLFPCustomPerKwh", "Custom-capacity LiFePO4 (per kWh)", "Batteries", { it.batteryLFPCustomPerKwh }, { p, v -> p.copy(batteryLFPCustomPerKwh = v) }),
        NullablePriceFieldSpec("batteryAGM12V", "12V AGM battery (~2.4kWh)", "Batteries", { it.batteryAGM12V }, { p, v -> p.copy(batteryAGM12V = v) }),

        NullablePriceFieldSpec("ac6mmPerFt", "6mm single wire, off-grid only (per ft)", "Wiring", { it.ac6mmPerFt }, { p, v -> p.copy(ac6mmPerFt = v) }),

        NullablePriceFieldSpec("deliveryTollJmd", "Toll charge (route crosses a toll)", "Delivery", { it.deliveryTollJmd }, { p, v -> p.copy(deliveryTollJmd = v) })
    )

    val groups: List<String> = all.map { it.group }.distinct()
    val nullableGroups: List<String> = nullableAll.map { it.group }.distinct()
}
