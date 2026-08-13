package com.lumix.estimator.domain

/**
 * A41/A51: the equipment database — the panels, inverters, and batteries this app actually
 * searches, quotes, and simulates against.
 *
 * A51 replaced the panel/inverter list content wholesale with the verified equipment structure
 * supplied 2026-08-13 ("UPDATE THE EQUIPMENT DATABASE — VERIFIED SOLAR EQUIPMENT ONLY"), per its
 * own explicit rule: no invented specs, no generic "6kW Deye"/"620W panel" placeholders. Every
 * entry carries a [VerificationStatus] rather than being silently treated as production-ready.
 * [dataQualityNote] spells out exactly which fields came directly from that message (or an
 * earlier real, source-linked datasheet already in this file) versus a disclosed industry-typical
 * assumption used to fill a field the message didn't specify — never a fabricated precise figure
 * presented as if it were a manufacturer's own number.
 *
 * [EquipmentSelectionEngine] only ever auto-selects [VerificationStatus.VERIFIED] entries for
 * GUIDED/LOAD ("do not automatically place unverified equipment into Load-Based recommendations").
 * [Catalog] additionally offers [VerificationStatus.PARTIALLY_VERIFIED]/[VerificationStatus.NEEDS_VERIFICATION]
 * entries in MANUAL mode's own picker only, each labeled with its status — an installer's own
 * judgment call, never an automatic pick. [VerificationStatus.REGIONAL_MODEL_REQUIRES_CONFIRMATION]
 * and [VerificationStatus.DO_NOT_USE] entries never appear in any picker at all.
 */
enum class VerificationStatus(val label: String) {
    VERIFIED("Verified"),
    PARTIALLY_VERIFIED("Partially verified"),
    NEEDS_VERIFICATION("Needs verification"),
    REGIONAL_MODEL_REQUIRES_CONFIRMATION("Regional model — requires confirmation"),
    DO_NOT_USE("Do not use")
}

/** Millimeter dimensions with feet derived once here — never hard-coded separately per the spec's own instruction. */
data class PhysicalDimensions(val lengthMm: Double, val widthMm: Double, val thicknessMm: Double) {
    val lengthFt: Double get() = lengthMm / 304.8
    val widthFt: Double get() = widthMm / 304.8
    val thicknessFt: Double get() = thicknessMm / 304.8
}

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
    /** Typical crystalline-silicon coefficients unless the source datasheet gave a model-specific figure — see [dataQualityNote]. */
    val tempCoeffVocPctPerC: Double,
    val tempCoeffPmaxPctPerC: Double,
    val dimensions: PhysicalDimensions,
    val weightKg: Double,
    val maxSystemVoltageV: Int,
    val maxSeriesFuseA: Int,
    val bifacial: Boolean,
    val verificationStatus: VerificationStatus,
    val dataQualityNote: String,
    val status: String = verificationStatus.label,
    val sourceUrl: String
)

/** One real hybrid/off-grid inverter's datasheet characteristics. */
data class InverterSpec(
    val brand: String,
    val model: String,
    val series: String,
    val region: String,
    val ratingLabel: String,
    val ratedOutputW: Int,
    val acVoltage: String,
    /** Raw datasheet value — "50/60", "60", or "verify"/"Verify datasheet" when unconfirmed. */
    val frequencyHzRaw: String,
    val splitPhase: Boolean,
    val maxPvW: Int?,
    val maxPvV: Int?,
    val mpptVoltageMinV: Int?,
    val mpptVoltageMaxV: Int?,
    val startupVoltageV: Int?,
    val mpptCount: Int?,
    val stringsPerMppt: Int?,
    val maxInputCurrentPerMpptA: Double?,
    val maxShortCircuitCurrentPerMpptA: Double?,
    val batteryVoltageRange: String,
    val batteryVoltageMinV: Double?,
    val batteryVoltageMaxV: Double?,
    val maxBatteryA: Int?,
    val maxChargePowerKw: Double?,
    val maxDischargePowerKw: Double?,
    val acOutputA: Int?,
    val efficiencyPercent: Double?,
    val type: String,
    val verificationStatus: VerificationStatus,
    val dataQualityNote: String,
    val engineeringNote: String,
    val sourceUrl: String
) {
    /**
     * Whether this unit's own datasheet confirms 50Hz support — Jamaica's grid frequency.
     * A raw value of "verify"/"Verify datasheet" is UNKNOWN, not a pass, and is treated as
     * not-yet-confirmed here rather than silently assumed compatible.
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
    val minVoltageV: Double,
    val maxVoltageV: Double,
    val capacityAh: Int,
    val chemistry: String,
    val maxDodPercent: Double?,
    val maxChargeA: Int,
    val maxDischargeA: Int,
    val maxChargePowerKw: Double,
    val maxDischargePowerKw: Double,
    val recommendedContinuousPowerKw: Double,
    val maxParallelUnits: Int,
    val parallelSupported: Boolean,
    val bmsCommunication: String,
    val inverterCompatibility: List<String>,
    val cycleLife: Int,
    val dimensions: String,
    val weightKg: Double,
    val verificationStatus: VerificationStatus,
    val dataQualityNote: String,
    val sourceUrl: String
)

object EquipmentSpecs {
    /** Standard mono-PERC/TOPCon coefficients used only where the source didn't give a model-specific figure (see each panel's [PanelSpec.dataQualityNote]). */
    private const val TYPICAL_TEMP_COEFF_VOC = -0.29
    private const val TYPICAL_TEMP_COEFF_PMAX = -0.35
    private const val USER_PROVIDED = "Manufacturer/model/electrical data provided directly by the project owner, 2026-08-13 equipment spec message — no external datasheet URL supplied; verify against the manufacturer's published datasheet before finalizing a design."

    val panels = listOf(
        PanelSpec(
            brand = "JA Solar", model = "JAM72D40-GB", ratingLabel = "595W",
            pmaxW = 595, vmpV = 44.6, impA = 13.3, vocV = 52.6, iscA = 14.0, efficiencyPercent = null,
            tempCoeffVocPctPerC = TYPICAL_TEMP_COEFF_VOC, tempCoeffPmaxPctPerC = TYPICAL_TEMP_COEFF_PMAX,
            dimensions = PhysicalDimensions(2278.0, 1134.0, 30.0), weightKg = 27.5,
            maxSystemVoltageV = 1500, maxSeriesFuseA = 20, bifacial = false,
            verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = "$USER_PROVIDED Temperature coefficients are typical mono-PERC values, not this model's datasheet figures.",
            sourceUrl = ""
        ),
        PanelSpec(
            brand = "DAS Solar", model = "DH156NA", ratingLabel = "615W",
            pmaxW = 615, vmpV = 45.76, impA = 13.44, vocV = 55.46, iscA = 14.11, efficiencyPercent = null,
            tempCoeffVocPctPerC = TYPICAL_TEMP_COEFF_VOC, tempCoeffPmaxPctPerC = TYPICAL_TEMP_COEFF_PMAX,
            dimensions = PhysicalDimensions(2465.0, 1134.0, 35.0), weightKg = 28.5,
            maxSystemVoltageV = 1500, maxSeriesFuseA = 20, bifacial = false,
            verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = "$USER_PROVIDED Temperature coefficients are typical mono-PERC values, not this model's datasheet figures.",
            sourceUrl = ""
        ),
        PanelSpec(
            brand = "DAS Solar", model = "DH156NA", ratingLabel = "620W",
            pmaxW = 620, vmpV = 45.93, impA = 13.50, vocV = 55.60, iscA = 14.19, efficiencyPercent = null,
            tempCoeffVocPctPerC = TYPICAL_TEMP_COEFF_VOC, tempCoeffPmaxPctPerC = TYPICAL_TEMP_COEFF_PMAX,
            dimensions = PhysicalDimensions(2465.0, 1134.0, 35.0), weightKg = 28.5,
            maxSystemVoltageV = 1500, maxSeriesFuseA = 20, bifacial = false,
            verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = "$USER_PROVIDED Higher power bin of the same DH156NA family as the 615W entry. Temperature coefficients are typical mono-PERC values, not this model's datasheet figures.",
            sourceUrl = ""
        ),
        // 700W/720W: kept from the already real, source-linked JinkoSolar entries (predating this
        // round) rather than instantiating an unnamed "representative" module — their real
        // datasheet Voc/Vmp/Isc/Imp already sit within a few tenths of the figures given in this
        // round's own 700W/720W table, and a real, sourced, named product beats an unnamed one.
        PanelSpec(
            brand = "JinkoSolar", model = "JKM700N-66HL5-BDV", ratingLabel = "700W",
            pmaxW = 700, vmpV = 40.42, impA = 17.32, vocV = 48.40, iscA = 18.40, efficiencyPercent = 22.50,
            tempCoeffVocPctPerC = TYPICAL_TEMP_COEFF_VOC, tempCoeffPmaxPctPerC = TYPICAL_TEMP_COEFF_PMAX,
            dimensions = PhysicalDimensions(2384.0, 1303.0, 33.0), weightKg = 37.5,
            maxSystemVoltageV = 1500, maxSeriesFuseA = 25, bifacial = true,
            verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = "Real manufacturer datasheet (see sourceUrl). Temperature coefficients are typical mono-PERC values, not this model's own datasheet figures.",
            sourceUrl = "https://jinkosolar.com.au/wp-content/uploads/2025/05/Jinko-Tech-Team_Presentation_Module-Part.pdf"
        ),
        PanelSpec(
            brand = "JinkoSolar", model = "JKM720N-66HL5-BDV", ratingLabel = "720W",
            pmaxW = 720, vmpV = 40.89, impA = 17.61, vocV = 49.04, iscA = 18.67, efficiencyPercent = 23.20,
            tempCoeffVocPctPerC = TYPICAL_TEMP_COEFF_VOC, tempCoeffPmaxPctPerC = TYPICAL_TEMP_COEFF_PMAX,
            dimensions = PhysicalDimensions(2384.0, 1303.0, 33.0), weightKg = 37.5,
            maxSystemVoltageV = 1500, maxSeriesFuseA = 25, bifacial = true,
            verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = "Real manufacturer datasheet (see sourceUrl). Temperature coefficients are typical mono-PERC values, not this model's own datasheet figures.",
            sourceUrl = "https://jinkosolar.com.au/wp-content/uploads/2025/05/Jinko-Tech-Team_Presentation_Module-Part.pdf"
        )
    )

    /**
     * LuxPower/Deye/SRNE/Growatt split-phase hybrid families, per the 2026-08-13 "UPDATE THE
     * EQUIPMENT DATABASE" message. Entries the message itself said not to invent (Deye 10K/12K
     * generic, SRNE 13K, Growatt 12K/13K) are deliberately absent rather than filled with copied
     * or guessed numbers — see the object-level doc comment.
     */
    val inverters = listOf(
        // LuxPower GEN-LB-US family — voltage/MPPT envelope is confirmed shared across the whole
        // 5K-13K family by the source message; per-model max PV power is NOT ("varies by model"),
        // so 6K/8K/10K's maxPvW here is derived by scaling the 13K's own confirmed 18kW/13kW
        // ratio — a stated derivation, not a datasheet figure for those three specific models.
        InverterSpec(
            brand = "LuxPower", model = "GEN-LB-US 6K", series = "GEN-LB-US", region = "US",
            ratingLabel = "6kW split-phase", ratedOutputW = 6000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = 8300, maxPvV = 550, mpptVoltageMinV = 120, mpptVoltageMaxV = 440, startupVoltageV = 140,
            mpptCount = 2, stringsPerMppt = 2, maxInputCurrentPerMpptA = null, maxShortCircuitCurrentPerMpptA = null,
            batteryVoltageRange = "40-60 V", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = null, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = null, efficiencyPercent = null,
            type = "Hybrid", verificationStatus = VerificationStatus.PARTIALLY_VERIFIED,
            dataQualityNote = "Family-level AC/MPPT envelope (120/240V, 550V max PV, 120-440V MPPT range, 140V start, 2 MPPT) confirmed for the whole GEN-LB-US 5K-13K family. Max PV power derived by scaling the 13K's own confirmed 18kW/13kW ratio — not a per-model datasheet figure.",
            engineeringNote = "Per-model current/power ratings for 6K specifically were not provided — confirm against LuxPower's GEN-LB-US 6K datasheet before finalizing.",
            sourceUrl = "https://luxpowertek.com/gen-lb-us-13k/"
        ),
        InverterSpec(
            brand = "LuxPower", model = "GEN-LB-US 8K", series = "GEN-LB-US", region = "US",
            ratingLabel = "8kW split-phase", ratedOutputW = 8000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = 11100, maxPvV = 550, mpptVoltageMinV = 120, mpptVoltageMaxV = 440, startupVoltageV = 140,
            mpptCount = 2, stringsPerMppt = 2, maxInputCurrentPerMpptA = null, maxShortCircuitCurrentPerMpptA = null,
            batteryVoltageRange = "40-60 V", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = null, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = null, efficiencyPercent = null,
            type = "Hybrid", verificationStatus = VerificationStatus.PARTIALLY_VERIFIED,
            dataQualityNote = "Family-level AC/MPPT envelope confirmed for the whole GEN-LB-US family. Max PV power derived by scaling the 13K's own confirmed ratio — not a per-model datasheet figure.",
            engineeringNote = "Per-model current/power ratings for 8K specifically were not provided — confirm against LuxPower's GEN-LB-US 8K datasheet before finalizing.",
            sourceUrl = "https://luxpowertek.com/gen-lb-us-13k/"
        ),
        InverterSpec(
            brand = "LuxPower", model = "GEN-LB-US 10K", series = "GEN-LB-US", region = "US",
            ratingLabel = "10kW split-phase", ratedOutputW = 10000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = 13800, maxPvV = 550, mpptVoltageMinV = 120, mpptVoltageMaxV = 440, startupVoltageV = 140,
            mpptCount = 2, stringsPerMppt = 2, maxInputCurrentPerMpptA = null, maxShortCircuitCurrentPerMpptA = null,
            batteryVoltageRange = "40-60 V", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = null, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = null, efficiencyPercent = null,
            type = "Hybrid", verificationStatus = VerificationStatus.PARTIALLY_VERIFIED,
            dataQualityNote = "Family-level AC/MPPT envelope confirmed for the whole GEN-LB-US family. Max PV power derived by scaling the 13K's own confirmed ratio — not a per-model datasheet figure.",
            engineeringNote = "Per-model current/power ratings for 10K specifically were not provided — confirm against LuxPower's GEN-LB-US 10K datasheet before finalizing.",
            sourceUrl = "https://luxpowertek.com/gen-lb-us-13k/"
        ),
        InverterSpec(
            brand = "LuxPower", model = "GEN-LB-US 13K", series = "GEN-LB-US", region = "US",
            ratingLabel = "13kW split-phase", ratedOutputW = 13000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = 18000, maxPvV = 550, mpptVoltageMinV = 120, mpptVoltageMaxV = 440, startupVoltageV = 140,
            mpptCount = 2, stringsPerMppt = 2, maxInputCurrentPerMpptA = 26.0, maxShortCircuitCurrentPerMpptA = 31.0,
            batteryVoltageRange = "40-60 V (48/51.2 V class)", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = 208, maxChargePowerKw = 10.0, maxDischargePowerKw = 10.0, acOutputA = null, efficiencyPercent = null,
            type = "Hybrid", verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = USER_PROVIDED,
            engineeringNote = "13kW at 240V AC / 11.2kW at 208V AC — max PV array power (21kW) exceeds max PV input power (18kW); 18kW is the binding limit used for compatibility checks.",
            sourceUrl = "https://luxpowertek.com/gen-lb-us-13k/"
        ),
        InverterSpec(
            brand = "LuxPower", model = "LXP-LB-US 12K", series = "LXP-LB-US", region = "US",
            ratingLabel = "12kW split-phase", ratedOutputW = 12000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = 18000, maxPvV = 600, mpptVoltageMinV = 120, mpptVoltageMaxV = 500, startupVoltageV = 140,
            mpptCount = 3, stringsPerMppt = 2, maxInputCurrentPerMpptA = 25.0, maxShortCircuitCurrentPerMpptA = 31.0,
            batteryVoltageRange = "40-60 V (48 V class)", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = 250, maxChargePowerKw = 12.0, maxDischargePowerKw = 12.0, acOutputA = null, efficiencyPercent = null,
            type = "Hybrid", verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = "$USER_PROVIDED A distinct family from GEN-LB-US 13K — do not conflate the two.",
            engineeringNote = "3 independent MPPT inputs, 2:2:1 string arrangement; per-MPPT max current 25/15/15A, max short-circuit 31/19/19A.",
            sourceUrl = "https://luxpowertek.com/wp-content/uploads/2025/08/LXP-LB-US-12K-User-Manual-2025.4.14.pdf"
        ),
        // Deye SUN-*-SG01LP1-US family
        InverterSpec(
            brand = "Deye", model = "SUN-6K-SG01LP1-US", series = "SG01LP1-US", region = "US",
            ratingLabel = "6kW split-phase", ratedOutputW = 6000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = 7800, maxPvV = 500, mpptVoltageMinV = 150, mpptVoltageMaxV = 425, startupVoltageV = 125,
            mpptCount = 2, stringsPerMppt = 1, maxInputCurrentPerMpptA = 26.0, maxShortCircuitCurrentPerMpptA = 44.0,
            batteryVoltageRange = "40-60 V (48 V class)", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = null, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = null, efficiencyPercent = null,
            type = "Hybrid", verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = USER_PROVIDED,
            engineeringNote = "MPPT1/MPPT2 current ratings differ (26A + 13A operating, 44A + 22A short-circuit) — the second MPPT is a smaller tracker on this model.",
            sourceUrl = "https://www.deyeinverter.com/product/split-phase-hybrid-inverter-1/sun5-6-7-6-8-10-12ksg02lp2usam2-am3-512kw-2-mppt-hybrid-inverter-lv-battery-supported.html"
        ),
        InverterSpec(
            brand = "Deye", model = "SUN-8K-SG01LP1-US", series = "SG01LP1-US", region = "US",
            ratingLabel = "8kW split-phase", ratedOutputW = 8000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = 10400, maxPvV = 500, mpptVoltageMinV = 150, mpptVoltageMaxV = 425, startupVoltageV = 125,
            mpptCount = 2, stringsPerMppt = 1, maxInputCurrentPerMpptA = 26.0, maxShortCircuitCurrentPerMpptA = 44.0,
            batteryVoltageRange = "40-60 V (48 V class)", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = null, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = null, efficiencyPercent = null,
            type = "Hybrid", verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = USER_PROVIDED,
            engineeringNote = "Both MPPTs equal on this model: 26A + 26A operating, 44A + 44A short-circuit.",
            sourceUrl = "https://www.deyeinverter.com/product/split-phase-hybrid-inverter-1/sun5-6-7-6-8-10-12ksg02lp2usam2-am3-512kw-2-mppt-hybrid-inverter-lv-battery-supported.html"
        ),
        // Deye 10K/12K deliberately absent — the source message explicitly forbids inserting a
        // generic "Deye 10K"/"Deye 12K" until the exact regional model number is confirmed.
        //
        // SRNE HESP 4-6.5K-HUS — family confirmed (120/240V split-phase, 48V, 2 MPPT, 50/60Hz);
        // no per-model current/power figures were given.
        InverterSpec(
            brand = "SRNE", model = "HESP 4-6.5K-HUS", series = "HESP-HUS", region = "US",
            ratingLabel = "6kW-class split-phase", ratedOutputW = 6500, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = null, maxPvV = null, mpptVoltageMinV = null, mpptVoltageMaxV = null, startupVoltageV = null,
            mpptCount = 2, stringsPerMppt = null, maxInputCurrentPerMpptA = null, maxShortCircuitCurrentPerMpptA = null,
            batteryVoltageRange = "48 V class", batteryVoltageMinV = null, batteryVoltageMaxV = null,
            maxBatteryA = null, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = null, efficiencyPercent = null,
            type = "Hybrid", verificationStatus = VerificationStatus.NEEDS_VERIFICATION,
            dataQualityNote = "Family confirmed 120/240V split-phase, 48V, 2 MPPT, 50/60Hz. No PV voltage/current/power figures were provided for this specific family — excluded from automatic Load-Based selection until confirmed.",
            engineeringNote = "Official SRNE catalog confirms the 4-6.5kW-HUS split-phase family exists; exact model datasheet still required before treating as fully verified.",
            sourceUrl = "https://www.srnesolar.com/download/9/Download.html"
        ),
        InverterSpec(
            brand = "SRNE", model = "HESP 8K-US", series = "HESP 8-12K-US", region = "US",
            ratingLabel = "8kW split-phase", ratedOutputW = 8000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = null, maxPvV = null, mpptVoltageMinV = null, mpptVoltageMaxV = null, startupVoltageV = null,
            mpptCount = 2, stringsPerMppt = null, maxInputCurrentPerMpptA = null, maxShortCircuitCurrentPerMpptA = null,
            batteryVoltageRange = "48 V class", batteryVoltageMinV = null, batteryVoltageMaxV = null,
            maxBatteryA = null, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = null, efficiencyPercent = null,
            type = "Hybrid", verificationStatus = VerificationStatus.NEEDS_VERIFICATION,
            dataQualityNote = "Family confirmed 120/240V split-phase, 48V, 2 independent MPPTs, 50/60Hz. Per-model PV voltage/current/power for 8K specifically not provided — excluded from automatic Load-Based selection until confirmed.",
            engineeringNote = "Official SRNE catalog confirms the 8-12kW-US split-phase family exists; exact 8K model datasheet still required.",
            sourceUrl = "https://www.srnesolar.us/download/Download.html"
        ),
        InverterSpec(
            brand = "SRNE", model = "HESP 10K-US", series = "HESP 8-12K-US", region = "US",
            ratingLabel = "10kW split-phase", ratedOutputW = 10000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = null, maxPvV = null, mpptVoltageMinV = null, mpptVoltageMaxV = null, startupVoltageV = null,
            mpptCount = 2, stringsPerMppt = null, maxInputCurrentPerMpptA = null, maxShortCircuitCurrentPerMpptA = null,
            batteryVoltageRange = "48 V class", batteryVoltageMinV = null, batteryVoltageMaxV = null,
            maxBatteryA = null, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = null, efficiencyPercent = null,
            type = "Hybrid", verificationStatus = VerificationStatus.NEEDS_VERIFICATION,
            dataQualityNote = "Family confirmed 120/240V split-phase, 48V, 2 independent MPPTs, 50/60Hz. Per-model PV voltage/current/power for 10K specifically not provided — excluded from automatic Load-Based selection until confirmed.",
            engineeringNote = "Official SRNE catalog confirms the 8-12kW-US split-phase family exists; exact 10K model datasheet still required.",
            sourceUrl = "https://www.srnesolar.us/download/Download.html"
        ),
        InverterSpec(
            brand = "SRNE", model = "HESP 12K-US", series = "HESP 8-12K-US", region = "US",
            ratingLabel = "12kW split-phase", ratedOutputW = 12000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = null, maxPvV = null, mpptVoltageMinV = null, mpptVoltageMaxV = null, startupVoltageV = null,
            mpptCount = 2, stringsPerMppt = null, maxInputCurrentPerMpptA = null, maxShortCircuitCurrentPerMpptA = null,
            batteryVoltageRange = "48 V class", batteryVoltageMinV = null, batteryVoltageMaxV = null,
            maxBatteryA = null, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = null, efficiencyPercent = null,
            type = "Hybrid", verificationStatus = VerificationStatus.NEEDS_VERIFICATION,
            dataQualityNote = "Family confirmed 120/240V split-phase, 48V, 2 independent MPPTs, 50/60Hz. Per-model PV voltage/current/power for 12K specifically not provided — excluded from automatic Load-Based selection until confirmed.",
            engineeringNote = "Official SRNE catalog confirms the 8-12kW-US split-phase family exists; exact 12K model datasheet still required.",
            sourceUrl = "https://www.srnesolar.us/download/Download.html"
        ),
        // SRNE 13K deliberately absent — source message: "Do NOT invent a 13K SRNE split-phase
        // model... 13K = NOT AVAILABLE."
        //
        // Growatt SPH-HU family
        InverterSpec(
            brand = "Growatt", model = "SPH 8000TL-HU-US", series = "SPH-HU", region = "US",
            ratingLabel = "8kW split-phase", ratedOutputW = 8000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = null, maxPvV = null, mpptVoltageMinV = null, mpptVoltageMaxV = null, startupVoltageV = null,
            mpptCount = null, stringsPerMppt = null, maxInputCurrentPerMpptA = null, maxShortCircuitCurrentPerMpptA = null,
            batteryVoltageRange = "40-60 V (48 V class)", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = null, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = null, efficiencyPercent = null,
            type = "Hybrid", verificationStatus = VerificationStatus.NEEDS_VERIFICATION,
            dataQualityNote = "US SPH-HU split-phase family exists; exact 8K model PV/MPPT/current specs were not provided (message explicitly asks to verify the exact 8K model specs from the manufacturer datasheet) — excluded from automatic Load-Based selection until confirmed.",
            engineeringNote = "Verify against Growatt's own SPH 8000TL-HU-US datasheet before treating as fully verified.",
            sourceUrl = ""
        ),
        InverterSpec(
            brand = "Growatt", model = "SPH 10000TL-HU-US", series = "SPH-HU", region = "US",
            ratingLabel = "10kW split-phase", ratedOutputW = 10000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = 15000, maxPvV = 525, mpptVoltageMinV = 150, mpptVoltageMaxV = 450, startupVoltageV = 150,
            mpptCount = 3, stringsPerMppt = 2, maxInputCurrentPerMpptA = 22.0, maxShortCircuitCurrentPerMpptA = 27.0,
            batteryVoltageRange = "40-60 V (48 V class)", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = 200, maxChargePowerKw = 10.0, maxDischargePowerKw = 10.0, acOutputA = 50, efficiencyPercent = null,
            type = "Hybrid", verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = USER_PROVIDED,
            engineeringNote = "3 MPPT trackers, 2 PV strings per MPPT.",
            sourceUrl = ""
        )
        // Growatt 12K/13K deliberately absent — source message: do not invent these if an exact
        // US split-phase model cannot be verified.
    )

    val batteries = listOf(
        BatterySpecSheet(
            brand = "SRNE", model = "SR-EOS05B / SR-EOS05B-Pro", ratingLabel = "5kWh class",
            ratedEnergyKwh = 5.12, usableEnergyKwh = 4.92, voltageV = 51.2, minVoltageV = 44.8, maxVoltageV = 58.4,
            capacityAh = 100, chemistry = "LFP", maxDodPercent = 96.0,
            maxChargeA = 100, maxDischargeA = 100,
            maxChargePowerKw = 100 * 51.2 / 1000.0, maxDischargePowerKw = 100 * 51.2 / 1000.0,
            recommendedContinuousPowerKw = 100 * 51.2 / 1000.0 * 0.8,
            maxParallelUnits = 16, parallelSupported = true,
            bmsCommunication = "CAN/RS485 (typical for this battery class — exact protocol not in the provided datasheet excerpt)",
            inverterCompatibility = listOf("SRNE", "Deye", "LuxPower", "Growatt"),
            cycleLife = 6000, dimensions = "760 x 405 x 151 mm (Pro manual)", weightKg = 49.0,
            verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = "Rated/usable energy, voltage, current, cycle life from real SRNE datasheet (sourceUrl). Voltage window, charge/discharge power, parallel count, BMS protocol, and inverter compatibility are typical values for this LFP battery class, not line items confirmed in the excerpt on file.",
            sourceUrl = "https://www.srnesolar.com/userfiles/files/2025/04/23/User%20Manual_SR-EOS05B-Pro%26EOS10B%26EOS15B%20Energy%20Storage%20Battery_EN-V2.0.pdf"
        ),
        BatterySpecSheet(
            brand = "SRNE", model = "SR-EOS10B", ratingLabel = "10kWh class",
            ratedEnergyKwh = 10.24, usableEnergyKwh = 9.83, voltageV = 51.2, minVoltageV = 44.8, maxVoltageV = 58.4,
            capacityAh = 200, chemistry = "LFP", maxDodPercent = null,
            maxChargeA = 150, maxDischargeA = 200,
            maxChargePowerKw = 150 * 51.2 / 1000.0, maxDischargePowerKw = 200 * 51.2 / 1000.0,
            recommendedContinuousPowerKw = 200 * 51.2 / 1000.0 * 0.8,
            maxParallelUnits = 16, parallelSupported = true,
            bmsCommunication = "CAN/RS485 (typical for this battery class — exact protocol not in the provided datasheet excerpt)",
            inverterCompatibility = listOf("SRNE", "Deye", "LuxPower", "Growatt"),
            cycleLife = 6000, dimensions = "1014 x 620 x 205 mm", weightKg = 88.0,
            verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = "Rated/usable energy, voltage, current, cycle life from real SRNE datasheet (sourceUrl). Voltage window, charge/discharge power, parallel count, BMS protocol, and inverter compatibility are typical values for this LFP battery class, not line items confirmed in the excerpt on file.",
            sourceUrl = "https://www.srnesolar.com/userfiles/files/2025/04/23/User%20Manual_SR-EOS05B-Pro%26EOS10B%26EOS15B%20Energy%20Storage%20Battery_EN-V2.0.pdf"
        ),
        BatterySpecSheet(
            brand = "SRNE", model = "SR-EOS15B", ratingLabel = "16kWh class",
            ratedEnergyKwh = 16.07, usableEnergyKwh = 15.42, voltageV = 51.2, minVoltageV = 44.8, maxVoltageV = 58.4,
            capacityAh = 314, chemistry = "LFP", maxDodPercent = null,
            maxChargeA = 200, maxDischargeA = 200,
            maxChargePowerKw = 200 * 51.2 / 1000.0, maxDischargePowerKw = 200 * 51.2 / 1000.0,
            recommendedContinuousPowerKw = 200 * 51.2 / 1000.0 * 0.8,
            maxParallelUnits = 16, parallelSupported = true,
            bmsCommunication = "CAN/RS485 (typical for this battery class — exact protocol not in the provided datasheet excerpt)",
            inverterCompatibility = listOf("SRNE", "Deye", "LuxPower", "Growatt"),
            cycleLife = 8000, dimensions = "1075 x 460 x 271 mm", weightKg = 123.0,
            verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = "Rated/usable energy, voltage, current, cycle life from real SRNE datasheet (sourceUrl). This is the real product behind the catalog's \"15kWh\" tier (see Catalog.hybridBatteries) — SRNE's own class naming rounds it to 16kWh; there is no separate distinct ~15kWh SKU. Voltage window, charge/discharge power, parallel count, BMS protocol, and inverter compatibility are typical values for this LFP battery class, not line items confirmed in the excerpt on file.",
            sourceUrl = "https://www.srnesolar.com/productdetail/Energy-Storage-System-EOS-16kWh.html"
        )
    )

    /** Exact-wattage match — every wattage [Catalog.panelWattages] offers now has a real matched entry. */
    fun panelSpecFor(watts: Int): PanelSpec? = panels.firstOrNull { it.pmaxW == watts }

    /**
     * Exact-kW match, preferring the VERIFIED entry when more than one real inverter shares the
     * same wattage (e.g. Deye SUN-6K and LuxPower GEN-LB-US 6K are both 6000W) — [Catalog] only
     * ever puts a wattage into [Catalog.hybridInverters] when exactly one VERIFIED entry exists
     * for it, so this always resolves to the same specific product Catalog actually selected.
     */
    fun inverterSpecFor(kw: Double): InverterSpec? {
        val watts = (kw * 1000.0).toInt()
        return inverters.filter { it.ratedOutputW == watts }
            .minByOrNull { if (it.verificationStatus == VerificationStatus.VERIFIED) 0 else 1 }
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
