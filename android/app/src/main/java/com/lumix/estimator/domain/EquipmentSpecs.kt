package com.lumix.estimator.domain

/**
 * Real manufacturer-datasheet specs for the panel/inverter/battery families this catalog's own
 * generic wattage/kW/kWh tiers ([Catalog.panelWattages], [Catalog.hybridInverters],
 * [Catalog.hybridBatteries]) are meant to represent — supplied 2026-08-13 as
 * `Lumix_Solar_Equipment_Spec_Library_2026` (CSV/XLSX/JSON, all three carrying the same data;
 * this file transcribes the XLSX's own column headers, the most complete of the three).
 *
 * This is reference data, not a live product database: several entries are themselves marked
 * "REFERENCE - verify exact label" or "verify" in the source library, because wattage/kW alone
 * doesn't uniquely identify a real product. Treat every match here the same way the source
 * library's own README instructs — a starting point for engineering checks, not a substitute for
 * reading the exact model's real datasheet before finalizing a design.
 */

/** One real PV module's datasheet electrical characteristics (STC). */
data class PanelSpec(
    val brand: String,
    val model: String,
    val ratingLabel: String,
    val pmaxW: Int,
    val vmpV: Double?,
    val impA: Double?,
    val vocV: Double?,
    val iscA: Double?,
    val efficiencyPercent: Double?,
    val dimensions: String,
    val weightKg: Double,
    val bifacial: Boolean,
    val status: String,
    val sourceUrl: String
)

/** One real hybrid/off-grid inverter's datasheet characteristics. */
data class InverterSpec(
    val brand: String,
    val model: String,
    val ratingLabel: String,
    val ratedOutputW: Int,
    val acVoltage: String,
    /** Raw datasheet value — "60", "50/60", or "verify"/"Verify datasheet" when unconfirmed. */
    val frequencyHzRaw: String,
    val maxPvW: Int?,
    val maxPvV: Int?,
    val mpptCount: Int?,
    val batteryVoltageRange: String,
    val maxBatteryA: Int?,
    val acOutputA: Int?,
    val efficiencyPercent: Double?,
    val type: String,
    val engineeringNote: String,
    val sourceUrl: String
) {
    /**
     * Whether this unit's own datasheet confirms 50Hz support — Jamaica's grid frequency.
     * Per the source library's own explicit rule: never assume 50Hz is supported just because a
     * unit is otherwise a good fit; a raw value of "verify"/"Verify datasheet" is UNKNOWN, not a
     * pass, and is treated as not-yet-confirmed here rather than silently assumed compatible.
     */
    val jamaicaFrequencyConfirmed: Boolean get() = frequencyHzRaw.contains("50")

    val frequencyUnverified: Boolean get() = frequencyHzRaw.contains("verify", ignoreCase = true)
}

/** One real LiFePO4 battery module's datasheet characteristics. */
data class BatterySpecSheet(
    val brand: String,
    val model: String,
    val ratingLabel: String,
    val ratedEnergyKwh: Double,
    val usableEnergyKwh: Double,
    val voltageV: Double,
    val capacityAh: Int,
    val chemistry: String,
    val maxDodPercent: Double?,
    val maxChargeA: Int,
    val maxDischargeA: Int,
    val cycleLife: Int,
    val dimensions: String,
    val weightKg: Double,
    val sourceUrl: String
)

object EquipmentSpecs {
    val panels = listOf(
        PanelSpec("Trina Solar", "Vertex NEG19RC.20 family", "595W", 595, 40.30, 14.91, 48.40, 15.69, 21.99, "2384 x 1134 x 30 mm", 33.7, true, "REFERENCE - verify exact label", "https://static.trinasolar.com/sites/default/files/DS_NEG19RC.20_JP_2023_A.pdf"),
        PanelSpec("JinkoSolar", "JKM615N-66HL4M-BDV family", "615W", 615, 40.60, 15.15, 48.88, 16.02, 22.77, "2382 x 1134 x 30 mm", 32.4, true, "REFERENCE - verify exact label", "https://jinkojsolar.com/d.asp?id=367"),
        PanelSpec("JinkoSolar", "JKM620N-66HL4M-BDV family", "620W", 620, 40.74, 15.22, 49.08, 16.08, 22.95, "2382 x 1134 x 30 mm", 32.4, true, "REFERENCE - verify exact label", "https://jinkojsolar.com/d.asp?id=367"),
        PanelSpec("JinkoSolar", "JKM700N-66HL5-BDV", "700W", 700, 40.42, 17.32, 48.40, 18.40, 22.50, "2384 x 1303 x 33 mm", 37.5, true, "REFERENCE - verify exact label", "https://jinkosolar.com.au/wp-content/uploads/2025/05/Jinko-Tech-Team_Presentation_Module-Part.pdf"),
        PanelSpec("JinkoSolar", "JKM720N-66HL5-BDV", "720W", 720, 40.89, 17.61, 49.04, 18.67, 23.20, "2384 x 1303 x 33 mm", 37.5, true, "REFERENCE - verify exact label", "https://jinkosolar.com.au/wp-content/uploads/2025/05/Jinko-Tech-Team_Presentation_Module-Part.pdf"),
        PanelSpec("JinkoSolar", "JKM585-595N-72HL4-BDV-U family", "595W alternate", 595, null, null, null, null, null, "2278 x 1134 mm", 31.0, true, "ALTERNATE - verify exact label", "https://jinkosolar.com.au/wp-content/uploads/2025/05/Jinko-Tech-Team_Presentation_Module-Part.pdf")
    )

    val inverters = listOf(
        InverterSpec("Growatt", "SPE 6000 US", "6kW split-phase", 6000, "120/240 V", "60", 9000, 550, 2, "46-60 V lithium", 140, 25, 96.5, "Off-grid/storage", "60 Hz US model; verify Jamaica 50 Hz compatibility", "https://growattportable.com/products/growatt-spe-6000-us"),
        InverterSpec("Growatt", "SPF 3500 US", "3.5kW single-phase", 3500, "230 V", "50/60", 4500, 450, 1, "48 V", null, null, 93.0, "Off-grid", "230V single-phase is not Jamaica 110/220 split-phase", "https://us.growatt.com/upload/file/20221110/9a632fa752fd3fdce38b4a2c746d0559.pdf"),
        InverterSpec("Deye", "SUN-6K-SG02LP2-US-AM2", "6kW split-phase", 6000, "120/240 V", "60", 9000, 500, 2, "40-60 V", 135, 25, 97.6, "Hybrid", "US 60 Hz model; verify Jamaica 50 Hz and exact suffix", "https://www.deyeinverter.com/product/split-phase-hybrid-inverter-1/sun5-6-7-6-8-10-12ksg02lp2usam2-am3-512kw-2-mppt-hybrid-inverter-lv-battery-supported.html"),
        InverterSpec("Deye", "SUN-12K-SG02LP2-US-AM3", "12kW split-phase", 12000, "120/240 V", "60", 18000, 500, 3, "40-60 V", 250, 50, 97.6, "Hybrid", "US 60 Hz model; verify Jamaica 50 Hz and exact suffix", "https://www.deyeinverter.com/deyeinverter/2024/07/16/datasheet_sun-5-12k-sg02lp2-us-am2_240716_en.pdf"),
        InverterSpec("LuxPower", "SNA US 6K", "6kW split-phase", 6000, "110/220 or 120/240 V", "50/60", 8000, 500, 2, "38.4-60 V", 140, 28, 93.0, "Off-grid/storage", "Split-phase; 50/60 Hz; not the same class as LXP-LB hybrid", "https://luxpowertek.com/wp-content/uploads/2026/04/SNA-US-6K-Datasheet20260123.pdf"),
        InverterSpec("LuxPower", "LXP-LB-US 10K", "10kW split-phase", 10000, "120/240 or 120/208 V", "50/60", 15000, 600, 2, "40-60 V", 210, 42, 97.5, "Hybrid", "Verify final market configuration", "https://luxpowertek.com/wp-content/uploads/2025/08/LXP-LB-US-5-10K-Datasheet-2.pdf"),
        InverterSpec("LuxPower", "LXP-LB-US 12K", "12kW split-phase", 12000, "120/240 or 120/208 V", "60", 18000, 600, 3, "40-60 V", 250, 50, 97.5, "Hybrid", "Verify exact frequency behavior for Jamaica", "https://luxpowertek.com/wp-content/uploads/2025/08/LXP-LB-US-12K-User-Manual-2025.4.14.pdf"),
        InverterSpec("LuxPower", "GEN-LB-US 13KPRO", "13kW split-phase", 13000, "120/240 or 120/208 V", "Verify datasheet", 21000, 550, 3, "48/51.2 V", 250, 54, null, "Hybrid", "Official page lists 13KPRO; detailed frequency/current data must be verified from final datasheet", "https://luxpowertek.com/gen-lb-us-13k/"),
        InverterSpec("SRNE", "HESP4865U140-HUS family", "6kW-class split-phase", 6500, "120/240 V class", "verify", null, null, null, "48 V class", 140, null, null, "Hybrid", "Official catalog confirms 4-6.5kW-HUS split-phase; exact 6kW model datasheet required", "https://www.srnesolar.com/download/9/Download.html"),
        InverterSpec("SRNE", "HESP 8-12kW-US family", "8kW-class split-phase", 8000, "120/240 V class", "verify", null, null, null, "48 V class", null, null, null, "Hybrid", "Official catalog confirms 8-12kW-US split-phase; exact model datasheet required", "https://www.srnesolar.us/download/Download.html"),
        InverterSpec("SRNE", "HESP 8-12kW-US family", "12kW-class split-phase", 12000, "120/240 V class", "verify", null, null, null, "48 V class", null, null, null, "Hybrid", "Official catalog confirms 8-12kW-US split-phase; exact model datasheet required", "https://www.srnesolar.us/download/Download.html")
    )

    val batteries = listOf(
        BatterySpecSheet("SRNE", "SR-EOS05B / SR-EOS05B-Pro", "5kWh class", 5.12, 4.92, 51.2, 100, "LFP", 96.0, 100, 100, 6000, "760 x 405 x 151 mm (Pro manual)", 49.0, "https://www.srnesolar.com/userfiles/files/2025/04/23/User%20Manual_SR-EOS05B-Pro%26EOS10B%26EOS15B%20Energy%20Storage%20Battery_EN-V2.0.pdf"),
        BatterySpecSheet("SRNE", "SR-EOS10B", "10kWh class", 10.24, 9.83, 51.2, 200, "LFP", null, 150, 200, 6000, "1014 x 620 x 205 mm", 88.0, "https://www.srnesolar.com/userfiles/files/2025/04/23/User%20Manual_SR-EOS05B-Pro%26EOS10B%26EOS15B%20Energy%20Storage%20Battery_EN-V2.0.pdf"),
        BatterySpecSheet("SRNE", "SR-EOS15B", "16kWh class", 16.07, 15.42, 51.2, 314, "LFP", null, 200, 200, 8000, "1075 x 460 x 271 mm", 123.0, "https://www.srnesolar.com/productdetail/Energy-Storage-System-EOS-16kWh.html")
    )

    /** Exact-wattage match only — this library doesn't cover every wattage this catalog quotes. */
    fun panelSpecFor(watts: Int): PanelSpec? = panels.firstOrNull { it.pmaxW == watts }

    /**
     * Closest inverter by rated kW within a small tolerance — this catalog's own tiers (3/6/8/10/
     * 12kW) don't all have a confirmed real match in the library (no 3kW hybrid is covered at
     * all, and the 8kW SRNE entry has mostly unverified fields), so this can return null.
     */
    fun inverterSpecFor(kw: Double): InverterSpec? {
        val watts = kw * 1000.0
        return inverters
            .filter { it.type == "Hybrid" || it.type == "Off-grid/storage" }
            .filter { kotlin.math.abs(it.ratedOutputW - watts) <= 750 }
            // Prefer a real "Hybrid"-typed match over an "Off-grid/storage" one at equal or
            // closer wattage — this catalog's own tiers are explicitly labeled Hybrid
            // (SystemMode.HYBRID), so that's the more representative real-world match.
            .minWithOrNull(compareBy({ if (it.type == "Hybrid") 0 else 1 }, { kotlin.math.abs(it.ratedOutputW - watts) }))
    }

    /** Matches on the nominal per-unit size embedded in the catalog's own battery name ("5 kWh", "10 kWh", "15 kWh"). */
    fun batterySpecFor(name: String?): BatterySpecSheet? {
        if (name == null) return null
        return when {
            name.contains("5 kWh") -> batteries.firstOrNull { it.ratingLabel == "5kWh class" }
            name.contains("10 kWh") -> batteries.firstOrNull { it.ratingLabel == "10kWh class" }
            name.contains("15 kWh") -> batteries.firstOrNull { it.ratingLabel == "16kWh class" }
            else -> null
        }
    }
}
