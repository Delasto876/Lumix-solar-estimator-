package com.lumix.estimator.domain

import kotlinx.serialization.Serializable

/**
 * A51: prices for the new real, named equipment models ([Catalog], backed by [EquipmentSpecs])
 * are placeholder values — "just put some random price, I will update the prices in settings
 * when created" (2026-08-13). Every field below is editable in Settings ([PriceFields]); nothing
 * here should be read as an actual quoted JMD figure until the project owner fills it in.
 */
@Serializable
data class PriceList(
    val inverterDeye6k: Double = 260000.0,
    val inverterDeye8k: Double = 340000.0,
    val inverterGrowatt10k: Double = 420000.0,
    val inverterLuxpowerLxpLb12k: Double = 500000.0,
    val inverterLuxpowerGenLb13k: Double = 560000.0,

    // MANUAL-only (partially verified / needs verification) inverters — same placeholder pattern.
    val inverterLuxpowerGenLb6k: Double = 260000.0,
    val inverterLuxpowerGenLb8k: Double = 340000.0,
    val inverterLuxpowerGenLb10k: Double = 420000.0,
    val inverterSrneHesp4to6_5k: Double = 280000.0,
    val inverterSrneHesp8k: Double = 340000.0,
    val inverterSrneHesp10k: Double = 420000.0,
    val inverterSrneHesp12k: Double = 500000.0,
    val inverterGrowattSph8k: Double = 340000.0,

    val inverterGridTie15k: Double = 450000.0,

    val inverterOffgrid3k72: Double = 72000.0,
    val inverterOffgrid3k78: Double = 78000.0,
    val inverterOffgrid3_2kPowmr: Double = 100000.0,
    val chargeController80A: Double = 35000.0,

    val batteryLFP5k: Double = 215000.0,
    val batteryLFP10k: Double = 420000.0,
    val batteryLFP15k: Double = 520000.0,
    // A54: MANUAL mode's battery bank step previously only offered 5/10/15 kWh — added per
    // installer request ("make it 5, 10, 15, 16, 20 and a custom field"). 16k/20k are additional
    // MANUAL-only nominal-capacity classes (same "class label, not a matched real product" costing
    // pattern the existing 5/10/15 fields already use — see Catalog.kt's own doc on this).
    val batteryLFP16k: Double = 555000.0,
    val batteryLFP20k: Double = 680000.0,
    /** Placeholder $/kWh rate for MANUAL mode's custom battery-capacity field — installer enters any kWh figure and a count; cost = kWh x count x this rate. */
    val batteryLFPCustomPerKwh: Double = 34000.0,

    val batteryAGM12V: Double = 45000.0,

    val panel595W: Double = 20500.0,
    val panel615W: Double = 21000.0,
    val panel620W: Double = 21200.0,
    val panel700W: Double = 24000.0,
    val panel720W: Double = 24500.0,

    val mountingRail16ft: Double = 4500.0,
    val midClamp: Double = 200.0,
    val endClamp: Double = 200.0,
    val lFoot: Double = 500.0,
    val backLeg: Double = 2000.0,
    val frontLeg: Double = 800.0,
    val m6m8Bolt: Double = 100.0,
    val mc4Pair: Double = 500.0,
    val weebClip: Double = 400.0,

    val pvWirePerFt: Double = 120.0,
    val ac10mmPerFt: Double = 150.0,
    val ac6mmPerFt: Double = 100.0,
    val battCablePerFt: Double = 1500.0,
    val battLug: Double = 250.0,

    val dinRailBox5Way: Double = 4000.0,
    val dinRailBox8Way: Double = 8000.0,
    val dcSurge: Double = 18000.0,
    val acSurge: Double = 18000.0,
    val pvDisconnect32A: Double = 12000.0,
    val dcBatteryBreaker100A: Double = 16000.0,
    val trunking: Double = 12000.0,
    val transferSwitch: Double = 27000.0,
    val changeOverSwitchOffgrid: Double = 18000.0,
    val earthRod: Double = 1500.0,
    val db8Way: Double = 5000.0,
    val drawBox: Double = 2000.0,
    val pvcConduitHalfBundle: Double = 4800.0,
    val pvcConduitOneBundle: Double = 10000.0
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
    val set: (PriceList, Double) -> PriceList
)

object PriceFields {
    val all: List<PriceFieldSpec> = listOf(
        PriceFieldSpec("inverterDeye6k", "Deye SUN-6K-SG01LP1-US", "Hybrid Inverters (Verified)", { it.inverterDeye6k }, { p, v -> p.copy(inverterDeye6k = v) }),
        PriceFieldSpec("inverterDeye8k", "Deye SUN-8K-SG01LP1-US", "Hybrid Inverters (Verified)", { it.inverterDeye8k }, { p, v -> p.copy(inverterDeye8k = v) }),
        PriceFieldSpec("inverterGrowatt10k", "Growatt SPH 10000TL-HU-US", "Hybrid Inverters (Verified)", { it.inverterGrowatt10k }, { p, v -> p.copy(inverterGrowatt10k = v) }),
        PriceFieldSpec("inverterLuxpowerLxpLb12k", "LuxPower LXP-LB-US 12K", "Hybrid Inverters (Verified)", { it.inverterLuxpowerLxpLb12k }, { p, v -> p.copy(inverterLuxpowerLxpLb12k = v) }),
        PriceFieldSpec("inverterLuxpowerGenLb13k", "LuxPower GEN-LB-US 13K", "Hybrid Inverters (Verified)", { it.inverterLuxpowerGenLb13k }, { p, v -> p.copy(inverterLuxpowerGenLb13k = v) }),

        PriceFieldSpec("inverterLuxpowerGenLb6k", "LuxPower GEN-LB-US 6K (partially verified)", "Hybrid Inverters (Manual only)", { it.inverterLuxpowerGenLb6k }, { p, v -> p.copy(inverterLuxpowerGenLb6k = v) }),
        PriceFieldSpec("inverterLuxpowerGenLb8k", "LuxPower GEN-LB-US 8K (partially verified)", "Hybrid Inverters (Manual only)", { it.inverterLuxpowerGenLb8k }, { p, v -> p.copy(inverterLuxpowerGenLb8k = v) }),
        PriceFieldSpec("inverterLuxpowerGenLb10k", "LuxPower GEN-LB-US 10K (partially verified)", "Hybrid Inverters (Manual only)", { it.inverterLuxpowerGenLb10k }, { p, v -> p.copy(inverterLuxpowerGenLb10k = v) }),
        PriceFieldSpec("inverterSrneHesp4to6_5k", "SRNE HESP 4-6.5K-HUS (needs verification)", "Hybrid Inverters (Manual only)", { it.inverterSrneHesp4to6_5k }, { p, v -> p.copy(inverterSrneHesp4to6_5k = v) }),
        PriceFieldSpec("inverterSrneHesp8k", "SRNE HESP 8K-US (needs verification)", "Hybrid Inverters (Manual only)", { it.inverterSrneHesp8k }, { p, v -> p.copy(inverterSrneHesp8k = v) }),
        PriceFieldSpec("inverterSrneHesp10k", "SRNE HESP 10K-US (needs verification)", "Hybrid Inverters (Manual only)", { it.inverterSrneHesp10k }, { p, v -> p.copy(inverterSrneHesp10k = v) }),
        PriceFieldSpec("inverterSrneHesp12k", "SRNE HESP 12K-US (needs verification)", "Hybrid Inverters (Manual only)", { it.inverterSrneHesp12k }, { p, v -> p.copy(inverterSrneHesp12k = v) }),
        PriceFieldSpec("inverterGrowattSph8k", "Growatt SPH 8000TL-HU-US (needs verification)", "Hybrid Inverters (Manual only)", { it.inverterGrowattSph8k }, { p, v -> p.copy(inverterGrowattSph8k = v) }),

        PriceFieldSpec("inverterGridTie15k", "15000W Grid-tie 3-phase", "Grid-tie Inverters", { it.inverterGridTie15k }, { p, v -> p.copy(inverterGridTie15k = v) }),

        PriceFieldSpec("inverterOffgrid3k72", "3000W Off-grid 12V (72k)", "Off-grid Inverters", { it.inverterOffgrid3k72 }, { p, v -> p.copy(inverterOffgrid3k72 = v) }),
        PriceFieldSpec("inverterOffgrid3k78", "3000W Off-grid 12V (78k)", "Off-grid Inverters", { it.inverterOffgrid3k78 }, { p, v -> p.copy(inverterOffgrid3k78 = v) }),
        PriceFieldSpec("inverterOffgrid3_2kPowmr", "3200W Off-grid PowMr 24V", "Off-grid Inverters", { it.inverterOffgrid3_2kPowmr }, { p, v -> p.copy(inverterOffgrid3_2kPowmr = v) }),
        PriceFieldSpec("chargeController80A", "80A MPPT charge controller", "Off-grid Inverters", { it.chargeController80A }, { p, v -> p.copy(chargeController80A = v) }),

        PriceFieldSpec("batteryLFP5k", "5 kWh LiFePO4 (SRNE SR-EOS05B)", "Batteries", { it.batteryLFP5k }, { p, v -> p.copy(batteryLFP5k = v) }),
        PriceFieldSpec("batteryLFP10k", "10 kWh LiFePO4 (SRNE SR-EOS10B)", "Batteries", { it.batteryLFP10k }, { p, v -> p.copy(batteryLFP10k = v) }),
        PriceFieldSpec("batteryLFP15k", "15 kWh LiFePO4 (SRNE SR-EOS15B)", "Batteries", { it.batteryLFP15k }, { p, v -> p.copy(batteryLFP15k = v) }),
        PriceFieldSpec("batteryLFP16k", "16 kWh LiFePO4 (Manual only)", "Batteries", { it.batteryLFP16k }, { p, v -> p.copy(batteryLFP16k = v) }),
        PriceFieldSpec("batteryLFP20k", "20 kWh LiFePO4 (Manual only)", "Batteries", { it.batteryLFP20k }, { p, v -> p.copy(batteryLFP20k = v) }),
        PriceFieldSpec("batteryLFPCustomPerKwh", "Custom-capacity LiFePO4 (per kWh)", "Batteries", { it.batteryLFPCustomPerKwh }, { p, v -> p.copy(batteryLFPCustomPerKwh = v) }),
        PriceFieldSpec("batteryAGM12V", "12V AGM battery (~2.4kWh)", "Batteries", { it.batteryAGM12V }, { p, v -> p.copy(batteryAGM12V = v) }),

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

        PriceFieldSpec("pvWirePerFt", "AWG10 PV wire (per ft)", "Wiring", { it.pvWirePerFt }, { p, v -> p.copy(pvWirePerFt = v) }),
        PriceFieldSpec("ac10mmPerFt", "10mm single wire (per ft)", "Wiring", { it.ac10mmPerFt }, { p, v -> p.copy(ac10mmPerFt = v) }),
        PriceFieldSpec("ac6mmPerFt", "6mm single wire (per ft)", "Wiring", { it.ac6mmPerFt }, { p, v -> p.copy(ac6mmPerFt = v) }),
        PriceFieldSpec("battCablePerFt", "#2/0 battery cable (per ft)", "Wiring", { it.battCablePerFt }, { p, v -> p.copy(battCablePerFt = v) }),
        PriceFieldSpec("battLug", "Battery lug", "Wiring", { it.battLug }, { p, v -> p.copy(battLug = v) }),

        PriceFieldSpec("dinRailBox5Way", "5-way DIN-rail box", "Boxes & Protection", { it.dinRailBox5Way }, { p, v -> p.copy(dinRailBox5Way = v) }),
        PriceFieldSpec("dinRailBox8Way", "8-way DIN-rail box", "Boxes & Protection", { it.dinRailBox8Way }, { p, v -> p.copy(dinRailBox8Way = v) }),
        PriceFieldSpec("dcSurge", "DC 1000V surge arrestor", "Boxes & Protection", { it.dcSurge }, { p, v -> p.copy(dcSurge = v) }),
        PriceFieldSpec("acSurge", "AC 1000V surge arrestor", "Boxes & Protection", { it.acSurge }, { p, v -> p.copy(acSurge = v) }),
        PriceFieldSpec("pvDisconnect32A", "PV disconnect 32A", "Boxes & Protection", { it.pvDisconnect32A }, { p, v -> p.copy(pvDisconnect32A = v) }),
        PriceFieldSpec("dcBatteryBreaker100A", "100A DC battery breaker", "Boxes & Protection", { it.dcBatteryBreaker100A }, { p, v -> p.copy(dcBatteryBreaker100A = v) }),
        PriceFieldSpec("trunking", "Trunking", "Boxes & Protection", { it.trunking }, { p, v -> p.copy(trunking = v) }),
        PriceFieldSpec("transferSwitch", "Transfer switch (63-125A)", "Boxes & Protection", { it.transferSwitch }, { p, v -> p.copy(transferSwitch = v) }),
        PriceFieldSpec("changeOverSwitchOffgrid", "Off-grid changeover switch", "Boxes & Protection", { it.changeOverSwitchOffgrid }, { p, v -> p.copy(changeOverSwitchOffgrid = v) }),
        PriceFieldSpec("earthRod", "Earth rod with clamp", "Boxes & Protection", { it.earthRod }, { p, v -> p.copy(earthRod = v) }),
        PriceFieldSpec("db8Way", "8-way distribution panel", "Boxes & Protection", { it.db8Way }, { p, v -> p.copy(db8Way = v) }),
        PriceFieldSpec("drawBox", "Draw box", "Boxes & Protection", { it.drawBox }, { p, v -> p.copy(drawBox = v) }),
        PriceFieldSpec("pvcConduitHalfBundle", "PVC conduit 1/2\" bundle", "Boxes & Protection", { it.pvcConduitHalfBundle }, { p, v -> p.copy(pvcConduitHalfBundle = v) }),
        PriceFieldSpec("pvcConduitOneBundle", "PVC conduit 1\" bundle", "Boxes & Protection", { it.pvcConduitOneBundle }, { p, v -> p.copy(pvcConduitOneBundle = v) })
    )

    val groups: List<String> = all.map { it.group }.distinct()
}
