package com.lumix.estimator.domain

import kotlinx.serialization.Serializable

@Serializable
enum class SystemMode { HYBRID, OFFGRID, GRIDTIE }

data class InverterOption(
    val id: String,
    val name: String,
    val kw: Double,
    val mode: SystemMode,
    val price: (PriceList) -> Double
)

data class BatteryOption(
    val name: String,
    val kwh: Double,
    val price: (PriceList) -> Double
)

/**
 * A51: [hybridInverters]/[manualInverters]/[hybridBatteries]/[panelWattages] are now derived
 * from [EquipmentSpecs] — the verified equipment database is the single source of truth, per the
 * 2026-08-13 "UPDATE THE EQUIPMENT DATABASE" message's own explicit requirement, rather than a
 * separately hand-maintained generic list.
 *
 * [hybridInverters] (GUIDED/LOAD's auto-selectable pool, via [EquipmentSelectionEngine]) is
 * filtered to [VerificationStatus.VERIFIED] only ("do not automatically place unverified
 * equipment into Load-Based recommendations"). [manualInverters] (MANUAL mode's own picker,
 * where an installer makes their own judgment call) additionally includes
 * [VerificationStatus.PARTIALLY_VERIFIED]/[VerificationStatus.NEEDS_VERIFICATION] entries, each
 * labeled with its status in the display name — [VerificationStatus.REGIONAL_MODEL_REQUIRES_CONFIRMATION]/
 * [VerificationStatus.DO_NOT_USE] entries don't exist in [EquipmentSpecs.inverters] at all (the
 * message explicitly said not to invent placeholder specs for those — Deye 10K/12K, SRNE 13K,
 * Growatt 12K/13K — so there's nothing to filter out here).
 *
 * Panel prices are still keyed by wattage ([PriceList.panelPrice]) since every verified wattage is
 * currently a single distinct real model; inverters use the same per-option price lambda pattern
 * as before, now pointing at named per-model [PriceList] fields instead of generic "Hybrid Nk"
 * ones. All prices default to a flat placeholder (the project owner will fill in real JMD figures
 * via Settings — see [PriceList]'s own doc note).
 */
object Catalog {
    private fun spec(brand: String, model: String) =
        EquipmentSpecs.inverters.first { it.brand == brand && it.model == model }

    private fun displayName(s: InverterSpec): String {
        val statusSuffix = if (s.verificationStatus == VerificationStatus.VERIFIED) "" else " (${s.verificationStatus.label.lowercase()})"
        return "${s.brand} ${s.model}$statusSuffix"
    }

    // A89/Ph21: model strings below updated to match this round's EquipmentSpecs.kt renames
    // (spreadsheet reconciliation, "USE THE LATEST ONE WITH THE LATEST PRICE") — spec()'s
    // `.first{...}` throws if a model string here doesn't exist in EquipmentSpecs.inverters, so
    // these MUST stay in lockstep with that file's own model strings.
    val hybridInverters: List<InverterOption> = listOf(
        InverterOption("deye6k", displayName(spec("Deye", "SUN-6K-SG02LP2-US")), 6.0, SystemMode.HYBRID) { it.inverterDeye6k },
        InverterOption("deye8k", displayName(spec("Deye", "SUN-8K-SG01LP1-US")), 8.0, SystemMode.HYBRID) { it.inverterDeye8k },
        InverterOption("growatt10k", displayName(spec("Growatt", "SPH 10000TL-HU-US")), 10.0, SystemMode.HYBRID) { it.inverterGrowatt10k },
        InverterOption("luxLxpLb12k", displayName(spec("LuxPower", "SNA-US 12K")), 12.0, SystemMode.HYBRID) { it.inverterLuxpowerLxpLb12k },
        InverterOption("luxGenLb13k", displayName(spec("LuxPower", "LXP-LB-US 12K/13K")), 13.0, SystemMode.HYBRID) { it.inverterLuxpowerGenLb13k }
    )

    // No verified off-grid/grid-tie replacements were supplied in the 2026-08-13 equipment-database
    // message (it was entirely split-phase hybrid + panels + batteries) — these stay on their prior
    // generic entries, a deliberate scope note, not an oversight.
    val offgridInverters = listOf(
        InverterOption("off3k78", "3000W Off-grid (78k)", 3.0, SystemMode.OFFGRID) { it.inverterOffgrid3k78 },
        InverterOption("off3k72", "3000W Off-grid (72k)", 3.0, SystemMode.OFFGRID) { it.inverterOffgrid3k72 },
        InverterOption("off3_2k", "3200W Off-grid PowMr", 3.2, SystemMode.OFFGRID) { it.inverterOffgrid3_2kPowmr }
    )

    val gridtieInverters = listOf(
        InverterOption("grid15k", "15000W Grid-tie 3-phase", 15.0, SystemMode.GRIDTIE) { it.inverterGridTie15k }
    )

    /** MANUAL mode's picker — every real inverter EquipmentSpecs carries (never REGIONAL_MODEL_REQUIRES_CONFIRMATION/DO_NOT_USE, since none exist), plus off-grid/grid-tie. */
    val manualInverters: List<InverterOption> = hybridInverters + listOf(
        InverterOption("luxGenLb6k", displayName(spec("LuxPower", "GEN-LB-US 6K")), 6.0, SystemMode.HYBRID) { it.inverterLuxpowerGenLb6k },
        InverterOption("luxGenLb8k", displayName(spec("LuxPower", "GEN-LB-US 8K")), 8.0, SystemMode.HYBRID) { it.inverterLuxpowerGenLb8k },
        InverterOption("luxGenLb10k", displayName(spec("LuxPower", "GEN-LB-US 10K")), 10.0, SystemMode.HYBRID) { it.inverterLuxpowerGenLb10k },
        InverterOption("srneHesp4to6_5k", displayName(spec("SRNE", "ASF4860U80-H")), 6.0, SystemMode.HYBRID) { it.inverterSrneHesp4to6_5k },
        InverterOption("srneHesp8k", displayName(spec("SRNE", "ASF4880S180-H")), 8.0, SystemMode.HYBRID) { it.inverterSrneHesp8k },
        InverterOption("srneHesp10k", displayName(spec("SRNE", "HES48100U200-H")), 10.0, SystemMode.HYBRID) { it.inverterSrneHesp10k },
        InverterOption("srneHesp12k", displayName(spec("SRNE", "HESP 12K-US")), 12.0, SystemMode.HYBRID) { it.inverterSrneHesp12k },
        InverterOption("growattSph8k", displayName(spec("Growatt", "SPH 8000TL-HU-US")), 8.0, SystemMode.HYBRID) { it.inverterGrowattSph8k }
    ) + offgridInverters + gridtieInverters

    fun findManual(id: String): InverterOption? = manualInverters.find { it.id == id }

    fun poolFor(mode: SystemMode): List<InverterOption> = when (mode) {
        SystemMode.HYBRID -> hybridInverters
        SystemMode.OFFGRID -> offgridInverters
        SystemMode.GRIDTIE -> gridtieInverters
    }

    // Names keep the "N kWh" substring EquipmentSpecs.batterySpecFor/EquipmentSelectionEngine
    // already match on; "kwh" itself stays the class label (5/10/15) SystemCalculator's own
    // material-cost switch keys off, not the real product's exact rated capacity (5.12/10.24/16.07)
    // — see EquipmentSpecs.batteries' own dataQualityNote for why 15kWh maps to the real 16kWh SKU.
    val hybridBatteries = listOf(
        BatteryOption("5 kWh LiFePO4 (SRNE SR-EOS05B)", 5.0) { it.batteryLFP5k },
        BatteryOption("10 kWh LiFePO4 (SRNE SR-EOS10B)", 10.0) { it.batteryLFP10k },
        BatteryOption("15 kWh LiFePO4 (SRNE SR-EOS15B)", 15.0) { it.batteryLFP15k }
    )

    const val offgridModuleKwh = 2.4

    val panelWattages: List<Int> = EquipmentSpecs.panels
        .filter { it.verificationStatus == VerificationStatus.VERIFIED }
        .map { it.pmaxW }
        .distinct()
        .sorted()

    val parishTowns: Map<String, List<String>> = linkedMapOf(
        "Kingston" to listOf("Kingston", "Port Royal", "Harbour View"),
        "St. Andrew" to listOf("Half Way Tree", "Constant Spring", "Stony Hill", "Bull Bay", "Mona", "Liguanea"),
        "St. Catherine" to listOf("Spanish Town", "Portmore", "Old Harbour", "Linstead", "Bog Walk", "Ewarton"),
        "Clarendon" to listOf("May Pen", "Chapelton", "Lionel Town", "Rocky Point", "Four Paths", "Milk River"),
        "Manchester" to listOf("Mandeville", "Christiana", "Porus", "Spalding", "Mile Gully", "Newport"),
        "St. Elizabeth" to listOf("Santa Cruz", "Black River", "Lacovia", "Balaclava", "Junction", "Southfield", "Siloah", "Maggotty"),
        "Westmoreland" to listOf("Savanna-la-Mar", "Negril", "Little London", "Frome", "Petersfield"),
        "Hanover" to listOf("Lucea", "Hopewell", "Green Island", "Sandy Bay"),
        "St. James" to listOf("Montego Bay", "Anchovy", "Cambridge"),
        "Trelawny" to listOf("Falmouth", "Duncans", "Wakefield", "Albert Town", "Clark's Town"),
        "St. Ann" to listOf("Ocho Rios", "St Ann's Bay", "Runaway Bay", "Brown's Town", "Alexandria"),
        "St. Mary" to listOf("Port Maria", "Annotto Bay", "Highgate", "Oracabessa", "Gayle"),
        "Portland" to listOf("Port Antonio", "Buff Bay", "Hope Bay", "Manchioneal"),
        "St. Thomas" to listOf("Morant Bay", "Yallahs", "Seaforth", "Port Morant", "Bath")
    )

    val parishes: List<String> = parishTowns.keys.toList()
}
