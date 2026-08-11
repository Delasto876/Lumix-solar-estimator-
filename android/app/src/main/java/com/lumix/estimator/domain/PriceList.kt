package com.lumix.estimator.domain

import kotlinx.serialization.Serializable

@Serializable
data class PriceList(
    val inverterHybrid3k: Double = 135000.0,
    val inverterHybrid6k: Double = 300000.0,
    val inverterHybrid8k: Double = 400000.0,
    val inverterHybrid10k: Double = 450000.0,
    val inverterHybrid12k: Double = 540000.0,

    val inverterGridTie15k: Double = 450000.0,

    val inverterOffgrid3k72: Double = 72000.0,
    val inverterOffgrid3k78: Double = 78000.0,
    val inverterOffgrid3_2kPowmr: Double = 100000.0,
    val chargeController80A: Double = 35000.0,

    val batteryLFP5k: Double = 215000.0,
    val batteryLFP10k: Double = 420000.0,
    val batteryLFP15k: Double = 520000.0,

    val batteryAGM12V: Double = 45000.0,

    val panel415W: Double = 17500.0,
    val panel550W: Double = 19500.0,
    val panel595W: Double = 20500.0,
    val panel600W: Double = 23000.0,

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
        415 -> panel415W
        550 -> panel550W
        600 -> panel600W
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
        PriceFieldSpec("inverterHybrid3k", "3000W Hybrid 110V (no AC)", "Hybrid Inverters", { it.inverterHybrid3k }, { p, v -> p.copy(inverterHybrid3k = v) }),
        PriceFieldSpec("inverterHybrid6k", "6000W Hybrid 110/220V", "Hybrid Inverters", { it.inverterHybrid6k }, { p, v -> p.copy(inverterHybrid6k = v) }),
        PriceFieldSpec("inverterHybrid8k", "8000W Hybrid 110/220V", "Hybrid Inverters", { it.inverterHybrid8k }, { p, v -> p.copy(inverterHybrid8k = v) }),
        PriceFieldSpec("inverterHybrid10k", "10000W Hybrid DEYE", "Hybrid Inverters", { it.inverterHybrid10k }, { p, v -> p.copy(inverterHybrid10k = v) }),
        PriceFieldSpec("inverterHybrid12k", "12000W Hybrid DEYE", "Hybrid Inverters", { it.inverterHybrid12k }, { p, v -> p.copy(inverterHybrid12k = v) }),

        PriceFieldSpec("inverterGridTie15k", "15000W Grid-tie 3-phase", "Grid-tie Inverters", { it.inverterGridTie15k }, { p, v -> p.copy(inverterGridTie15k = v) }),

        PriceFieldSpec("inverterOffgrid3k72", "3000W Off-grid 12V (72k)", "Off-grid Inverters", { it.inverterOffgrid3k72 }, { p, v -> p.copy(inverterOffgrid3k72 = v) }),
        PriceFieldSpec("inverterOffgrid3k78", "3000W Off-grid 12V (78k)", "Off-grid Inverters", { it.inverterOffgrid3k78 }, { p, v -> p.copy(inverterOffgrid3k78 = v) }),
        PriceFieldSpec("inverterOffgrid3_2kPowmr", "3200W Off-grid PowMr 24V", "Off-grid Inverters", { it.inverterOffgrid3_2kPowmr }, { p, v -> p.copy(inverterOffgrid3_2kPowmr = v) }),
        PriceFieldSpec("chargeController80A", "80A MPPT charge controller", "Off-grid Inverters", { it.chargeController80A }, { p, v -> p.copy(chargeController80A = v) }),

        PriceFieldSpec("batteryLFP5k", "5 kWh LiFePO4 battery", "Batteries", { it.batteryLFP5k }, { p, v -> p.copy(batteryLFP5k = v) }),
        PriceFieldSpec("batteryLFP10k", "10 kWh LiFePO4 battery", "Batteries", { it.batteryLFP10k }, { p, v -> p.copy(batteryLFP10k = v) }),
        PriceFieldSpec("batteryLFP15k", "15 kWh LiFePO4 battery", "Batteries", { it.batteryLFP15k }, { p, v -> p.copy(batteryLFP15k = v) }),
        PriceFieldSpec("batteryAGM12V", "12V AGM battery (~2.4kWh)", "Batteries", { it.batteryAGM12V }, { p, v -> p.copy(batteryAGM12V = v) }),

        PriceFieldSpec("panel415W", "415W PV panel", "Panels", { it.panel415W }, { p, v -> p.copy(panel415W = v) }),
        PriceFieldSpec("panel550W", "550W PV panel", "Panels", { it.panel550W }, { p, v -> p.copy(panel550W = v) }),
        PriceFieldSpec("panel595W", "595W PV panel", "Panels", { it.panel595W }, { p, v -> p.copy(panel595W = v) }),
        PriceFieldSpec("panel600W", "600W PV panel", "Panels", { it.panel600W }, { p, v -> p.copy(panel600W = v) }),

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
