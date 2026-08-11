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

object Catalog {
    val hybridInverters = listOf(
        InverterOption("hyb3k", "3000W Hybrid", 3.0, SystemMode.HYBRID) { it.inverterHybrid3k },
        InverterOption("hyb6k", "6000W Hybrid", 6.0, SystemMode.HYBRID) { it.inverterHybrid6k },
        InverterOption("hyb8k", "8000W Hybrid", 8.0, SystemMode.HYBRID) { it.inverterHybrid8k },
        InverterOption("hyb10k", "10000W Hybrid DEYE", 10.0, SystemMode.HYBRID) { it.inverterHybrid10k },
        InverterOption("hyb12k", "12000W Hybrid DEYE", 12.0, SystemMode.HYBRID) { it.inverterHybrid12k }
    )

    val offgridInverters = listOf(
        InverterOption("off3k78", "3000W Off-grid (78k)", 3.0, SystemMode.OFFGRID) { it.inverterOffgrid3k78 },
        InverterOption("off3k72", "3000W Off-grid (72k)", 3.0, SystemMode.OFFGRID) { it.inverterOffgrid3k72 },
        InverterOption("off3_2k", "3200W Off-grid PowMr", 3.2, SystemMode.OFFGRID) { it.inverterOffgrid3_2kPowmr }
    )

    val gridtieInverters = listOf(
        InverterOption("grid15k", "15000W Grid-tie 3-phase", 15.0, SystemMode.GRIDTIE) { it.inverterGridTie15k }
    )

    // Distinct manual catalog: includes the "no AC" 110V hybrid label distinction from the wizard's manual dropdown.
    val manualInverters: List<InverterOption> = listOf(
        InverterOption("hyb3k", "3000W Hybrid 110V (no AC)", 3.0, SystemMode.HYBRID) { it.inverterHybrid3k },
        InverterOption("hyb6k", "6000W Hybrid 110/220V", 6.0, SystemMode.HYBRID) { it.inverterHybrid6k },
        InverterOption("hyb8k", "8000W Hybrid 110/220V", 8.0, SystemMode.HYBRID) { it.inverterHybrid8k },
        InverterOption("hyb10k", "10000W Hybrid 110/220V DEYE", 10.0, SystemMode.HYBRID) { it.inverterHybrid10k },
        InverterOption("hyb12k", "12000W Hybrid 110/220V DEYE", 12.0, SystemMode.HYBRID) { it.inverterHybrid12k },
        InverterOption("off3k72", "3000W Off-grid 12V (72k)", 3.0, SystemMode.OFFGRID) { it.inverterOffgrid3k72 },
        InverterOption("off3k78", "3000W Off-grid 12V (78k)", 3.0, SystemMode.OFFGRID) { it.inverterOffgrid3k78 },
        InverterOption("off3_2k", "3200W Off-grid PowMr 24V", 3.2, SystemMode.OFFGRID) { it.inverterOffgrid3_2kPowmr },
        InverterOption("grid15k", "15000W Grid-tie 3-phase", 15.0, SystemMode.GRIDTIE) { it.inverterGridTie15k }
    )

    fun findManual(id: String): InverterOption? = manualInverters.find { it.id == id }

    fun poolFor(mode: SystemMode): List<InverterOption> = when (mode) {
        SystemMode.HYBRID -> hybridInverters
        SystemMode.OFFGRID -> offgridInverters
        SystemMode.GRIDTIE -> gridtieInverters
    }

    val hybridBatteries = listOf(
        BatteryOption("5 kWh LiFePO4", 5.0) { it.batteryLFP5k },
        BatteryOption("10 kWh LiFePO4", 10.0) { it.batteryLFP10k },
        BatteryOption("15 kWh LiFePO4", 15.0) { it.batteryLFP15k }
    )

    const val offgridModuleKwh = 2.4

    val panelWattages = listOf(415, 550, 595, 600)

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
