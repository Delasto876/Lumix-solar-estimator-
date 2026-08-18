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
    /**
     * A77 (spec Phase 14 — "improve equipment database"): the spec's own required-fields list for
     * this database includes "surge power," previously only ever present as freeform prose inside
     * [engineeringNote] for the one or two entries that happened to mention it — not a queryable
     * field, and easy to miss reading the raw list. [surgePowerRatio] is the multiple of
     * [ratedOutputW] the unit can briefly deliver (e.g. `2.0` = 2x rated), [surgeDurationSeconds]
     * how long. Both null wherever the source datasheet excerpt on file for that model never gave a
     * surge figure — left unset rather than assumed identical to a same-brand sibling, since surge
     * rating is not guaranteed consistent across a family's capacity tiers.
     */
    val surgePowerRatio: Double?,
    val surgeDurationSeconds: Double?,
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
        // LuxPower GEN-LB-US family — A52 upgraded 6K/8K/10K from A51's derived-ratio estimate to
        // real per-model datasheet figures (2026-08-13 "FIX PV INPUT VALIDATION BUG" message's
        // supplied JSON, itself citing luxpowertek.com's GEN-LB-US 5-13K manual/catalogue).
        InverterSpec(
            brand = "LuxPower", model = "GEN-LB-US 6K", series = "GEN-LB-US", region = "US",
            ratingLabel = "6kW split-phase", ratedOutputW = 6000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = 12000, maxPvV = 550, mpptVoltageMinV = 120, mpptVoltageMaxV = 440, startupVoltageV = 140,
            mpptCount = 2, stringsPerMppt = 2, maxInputCurrentPerMpptA = 26.0, maxShortCircuitCurrentPerMpptA = 31.0,
            batteryVoltageRange = "40-60 V", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = null, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = null, efficiencyPercent = null,
            surgePowerRatio = null, surgeDurationSeconds = null,
            type = "Hybrid", verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = "PV/MPPT specs from LuxPower's own GEN-LB-US 5-13K manual/catalogue (sourceUrl). Battery charge/discharge current is model-dependent and not given per-model — do not inherit the 13K's battery-current figure for this unit.",
            engineeringNote = "4 PV inputs total (2 MPPT x 2 strings each).",
            sourceUrl = "https://luxpowertek.com/wp-content/uploads/2026/06/GEN-LB-US-5-13K-User-Manual-2026.06.08.pdf"
        ),
        InverterSpec(
            brand = "LuxPower", model = "GEN-LB-US 8K", series = "GEN-LB-US", region = "US",
            ratingLabel = "8kW split-phase", ratedOutputW = 8000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = 16000, maxPvV = 550, mpptVoltageMinV = 120, mpptVoltageMaxV = 440, startupVoltageV = 140,
            mpptCount = 2, stringsPerMppt = 2, maxInputCurrentPerMpptA = 26.0, maxShortCircuitCurrentPerMpptA = 31.0,
            batteryVoltageRange = "40-60 V", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = 167, maxChargePowerKw = 8.0, maxDischargePowerKw = 8.0, acOutputA = null, efficiencyPercent = null,
            surgePowerRatio = null, surgeDurationSeconds = null,
            type = "Hybrid", verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = "PV/MPPT/battery specs from LuxPower's own US-series inverter catalogue (sourceUrl).",
            engineeringNote = "",
            sourceUrl = "https://luxpowertek.com/wp-content/uploads/2026/04/LuxpowerTek-US-Series-Inverter-Catalogue-2026.04.10.pdf"
        ),
        InverterSpec(
            brand = "LuxPower", model = "GEN-LB-US 10K", series = "GEN-LB-US", region = "US",
            ratingLabel = "10kW split-phase", ratedOutputW = 10000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = 18000, maxPvV = 550, mpptVoltageMinV = 120, mpptVoltageMaxV = 440, startupVoltageV = 140,
            mpptCount = 2, stringsPerMppt = 2, maxInputCurrentPerMpptA = 26.0, maxShortCircuitCurrentPerMpptA = 31.0,
            batteryVoltageRange = "40-60 V", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = 208, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = null, efficiencyPercent = null,
            surgePowerRatio = null, surgeDurationSeconds = null,
            type = "Hybrid", verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = "PV/MPPT specs from LuxPower's own GEN-LB-US 5-13K manual (sourceUrl). Battery current is an approximate family-configuration figure — use the exact 10K model datasheet for final battery current/power limits.",
            engineeringNote = "",
            sourceUrl = "https://luxpowertek.com/wp-content/uploads/2026/06/GEN-LB-US-5-13K-User-Manual-2026.06.08.pdf"
        ),
        InverterSpec(
            // A89 (spec Phase 20/21 — "QUOTATION PRICING ENGINE": "USE THE LATEST ONE WITH THE
            // LATEST PRICE"): the installer's own current price list names this class's 13kW
            // LuxPower model "LXP-LB-US 12K/13K" (JMD 340,000), not "GEN-LB-US 13K" — model name
            // and price updated per that explicit instruction. NO new datasheet was supplied for
            // this exact model string, so every ELECTRICAL figure below (MPPT count/current, max
            // PV, battery current, etc.) is still the previously-verified GEN-LB-US 13K's own
            // datasheet data, carried over under the new name — see dataQualityNote.
            brand = "LuxPower", model = "LXP-LB-US 12K/13K", series = "GEN-LB-US", region = "US",
            ratingLabel = "13kW split-phase", ratedOutputW = 13000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = 18000, maxPvV = 550, mpptVoltageMinV = 120, mpptVoltageMaxV = 440, startupVoltageV = 140,
            mpptCount = 2, stringsPerMppt = 2, maxInputCurrentPerMpptA = 26.0, maxShortCircuitCurrentPerMpptA = 31.0,
            batteryVoltageRange = "40-60 V (48/51.2 V class)", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = 208, maxChargePowerKw = 10.0, maxDischargePowerKw = 10.0, acOutputA = 54, efficiencyPercent = 97.5,
            // A77 (spec Phase 14): the only two entries in this catalog whose source datasheet
            // excerpt actually stated a surge figure — see this class's own surgePowerRatio doc.
            surgePowerRatio = 2.0, surgeDurationSeconds = 0.5,
            type = "Hybrid", verificationStatus = VerificationStatus.NEEDS_VERIFICATION,
            dataQualityNote = "A89: renamed from GEN-LB-US 13K to LXP-LB-US 12K/13K (this app's price list's own model string for its 13kW LuxPower entry) — price updated to the price list's JMD 340,000. Electrical specs below are still GEN-LB-US 13K's own verified datasheet figures, carried over under the new name; not independently re-confirmed against a datasheet for the exact string \"LXP-LB-US 12K/13K.\" Re-verify before treating those figures as authoritative for this exact model.",
            engineeringNote = "13kW at 240V AC / 11.2kW at 208V AC — max PV array power (21kW) exceeds max PV input power (18kW); 18kW is the binding limit used for compatibility checks. UPS (backup) output is only 10kW even though AC output is 13kW — backup-coverage sizing should use 10kW, not 13kW. Max continuous AC passthrough 90A; surge 2x rated power for 0.5s; 20ms switching; 99.9% MPPT efficiency, 94% battery efficiency; IP66/NEMA 4X.",
            sourceUrl = "https://luxpowertek.com/wp-content/uploads/2025/08/GEN-LB-US-13K-Datasheet-1.pdf"
        ),
        InverterSpec(
            // A89: this app's price list's 12kW LuxPower entry is "SNA-US 12K" (JMD 300,000) — a
            // different product family name from LXP-LB-US. Renamed/repriced per the installer's
            // explicit "use the latest one with the latest price" instruction; electrical specs
            // below remain LXP-LB-US 12K's own verified datasheet data (no SNA-US 12K datasheet was
            // supplied) — see dataQualityNote. Flagged as the larger of the two family-name jumps
            // made this round (Deye's SG01->SG02 reads as a minor revision; SNA-US vs LXP-LB-US may
            // be a genuinely different architecture) — re-verify before trusting these figures for
            // an actual SNA-US 12K install.
            brand = "LuxPower", model = "SNA-US 12K", series = "SNA-US", region = "US",
            ratingLabel = "12kW split-phase", ratedOutputW = 12000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = 18000, maxPvV = 600, mpptVoltageMinV = 120, mpptVoltageMaxV = 500, startupVoltageV = 140,
            mpptCount = 3, stringsPerMppt = 2, maxInputCurrentPerMpptA = 25.0, maxShortCircuitCurrentPerMpptA = 31.0,
            batteryVoltageRange = "40-60 V (48 V class)", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = 250, maxChargePowerKw = 12.0, maxDischargePowerKw = 12.0, acOutputA = null, efficiencyPercent = 97.5,
            surgePowerRatio = null, surgeDurationSeconds = null,
            type = "Hybrid", verificationStatus = VerificationStatus.NEEDS_VERIFICATION,
            dataQualityNote = "A89: renamed from LXP-LB-US 12K to SNA-US 12K (this app's price list's own model string) — price updated to the price list's JMD 300,000. Electrical specs below are still LXP-LB-US 12K's own verified datasheet figures, carried over under the new name; not independently re-confirmed against a real SNA-US 12K datasheet. Re-verify before treating these as authoritative.",
            engineeringNote = "3 independent MPPT inputs, 2:1:1 configuration (corrected from an earlier 2:2:1 transcription error); per-MPPT max current 25/15/15A, max short-circuit 31/19/19A; full-power MPPT range 230-500V (120-500V overall); 20ms switching; 99.9% MPPT efficiency, 94% battery charge/discharge efficiency; IP65/NEMA 4X.",
            sourceUrl = "https://luxpowertek.com/wp-content/uploads/2025/09/LXP-LB-US-12K-Datasheet.pdf"
        ),
        // Deye SUN-*-SG01LP1-US family
        InverterSpec(
            // A89 (spec Phase 20/21 — "USE THE LATEST ONE WITH THE LATEST PRICE"): the installer's
            // own current price list names this model "SUN-6K-SG02LP2-US" (JMD 230,000), not
            // SG01LP1-US. Renamed/repriced accordingly. No new datasheet was supplied for SG02LP2 —
            // every electrical figure below is still SG01LP1-US's own verified datasheet data,
            // carried over under the new name (a same-brand, adjacent-generation model number —
            // see dataQualityNote for what that does and doesn't justify).
            brand = "Deye", model = "SUN-6K-SG02LP2-US", series = "SG02LP2-US", region = "US",
            ratingLabel = "6kW split-phase", ratedOutputW = 6000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = 7800, maxPvV = 500, mpptVoltageMinV = 150, mpptVoltageMaxV = 425, startupVoltageV = 125,
            mpptCount = 2, stringsPerMppt = 1, maxInputCurrentPerMpptA = 26.0, maxShortCircuitCurrentPerMpptA = 44.0,
            batteryVoltageRange = "40-60 V (48 V class)", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = 135, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = 25, efficiencyPercent = null,
            surgePowerRatio = null, surgeDurationSeconds = null,
            type = "Hybrid", verificationStatus = VerificationStatus.NEEDS_VERIFICATION,
            dataQualityNote = "A89: renamed from SUN-6K-SG01LP1-US to SUN-6K-SG02LP2-US (this app's price list's own model string) — price updated to the price list's JMD 230,000. Electrical specs below are still SG01LP1-US's own verified datasheet figures, carried over under the new name; not independently re-confirmed against a real SG02LP2-US datasheet. Re-verify before treating these as authoritative for this exact model.",
            engineeringNote = "MPPT1/MPPT2 current ratings differ (26A + 13A operating, 44A + 22A short-circuit) — the second MPPT is a smaller tracker on this model. Max apparent power 6.6kVA; max AC current approx 27.5A.",
            sourceUrl = "https://deyeinverters.net/datasheets/en/qa/sun-5-8k-sg01lp1-us.html"
        ),
        InverterSpec(
            brand = "Deye", model = "SUN-8K-SG01LP1-US", series = "SG01LP1-US", region = "US",
            ratingLabel = "8kW split-phase", ratedOutputW = 8000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = 10400, maxPvV = 500, mpptVoltageMinV = 150, mpptVoltageMaxV = 425, startupVoltageV = 125,
            mpptCount = 2, stringsPerMppt = 1, maxInputCurrentPerMpptA = 26.0, maxShortCircuitCurrentPerMpptA = 44.0,
            batteryVoltageRange = "40-60 V (48 V class)", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = 190, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = 33, efficiencyPercent = null,
            surgePowerRatio = null, surgeDurationSeconds = null,
            type = "Hybrid", verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = USER_PROVIDED,
            engineeringNote = "Both MPPTs equal on this model: 26A + 26A operating, 44A + 44A short-circuit. Max apparent power 8.8kVA; max AC current 36.7A.",
            sourceUrl = "https://deyeinverters.net/datasheets/en/qa/sun-5-8k-sg01lp1-us.html"
        ),
        // Deye 10K/12K deliberately absent — the source message explicitly forbids inserting a
        // generic "Deye 10K"/"Deye 12K" until the exact regional model number is confirmed.
        //
        // SRNE HESP 4-6.5K-HUS — family confirmed (120/240V split-phase, 48V, 2 MPPT, 50/60Hz);
        // no per-model current/power figures were given.
        InverterSpec(
            // A89 (spec Phase 20/21 — "USE THE LATEST ONE WITH THE LATEST PRICE"): the installer's
            // price list names its 6kW SRNE entry "ASF4860U80-H" (JMD 190,000) — a different SRNE
            // family (ASF, not HESP) from what was previously verified here. This is the largest
            // family-name jump made in this round's reconciliation (bigger than Deye's SG01->SG02
            // or LuxPower's LXP-LB->SNA-US) — renamed/repriced per the installer's explicit
            // instruction, but no ASF4860U80-H datasheet was supplied, so every electrical figure
            // below is still HESP4860U140-HUS's own (already only PARTIALLY_VERIFIED) data, carried
            // over under the new name. Treat these figures with real caution until a real
            // ASF4860U80-H datasheet is confirmed.
            brand = "SRNE", model = "ASF4860U80-H", series = "ASF-H", region = "US",
            ratingLabel = "6kW split-phase", ratedOutputW = 6000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = null, maxPvV = 600, mpptVoltageMinV = 120, mpptVoltageMaxV = 500, startupVoltageV = null,
            mpptCount = 1, stringsPerMppt = null, maxInputCurrentPerMpptA = null, maxShortCircuitCurrentPerMpptA = null,
            batteryVoltageRange = "40-60 V (48 V class)", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = 140, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = null, efficiencyPercent = null,
            // A77 (spec Phase 14): the only two entries in this catalog whose source datasheet
            // excerpt actually stated a surge figure — see this class's own surgePowerRatio doc.
            surgePowerRatio = 2.0, surgeDurationSeconds = 10.0,
            type = "Hybrid", verificationStatus = VerificationStatus.NEEDS_VERIFICATION,
            dataQualityNote = "A89: renamed from HESP4860U140-HUS to ASF4860U80-H (this app's price list's own model string, a different SRNE family — ASF, not HESP) — price updated to the price list's JMD 190,000; mpptCount updated to 1 to match this price list's own \"ASF4860U80-H\" naming (\"80\" reading as a single 80A-class MPPT, not confirmed). Every other electrical figure below is still HESP4860U140-HUS's own carried-over data — not independently confirmed against a real ASF4860U80-H datasheet. Re-verify before treating these as authoritative.",
            engineeringNote = "Max grid/hybrid charge current 140A. Peak power 2x rated for 10s; 150% single-phase unbalanced-load support.",
            sourceUrl = "https://www.srnesolar.com/productdetail/Hybrid-Inverter-HESP-4-6.5kW-US.html"
        ),
        InverterSpec(
            // A89: price list's 8kW SRNE entry is "ASF4880S180-H" (JMD 275,000) — see the 6kW
            // entry's own doc above for the family-name-jump caveat (ASF, not HESP), which applies
            // identically here.
            brand = "SRNE", model = "ASF4880S180-H", series = "ASF-H", region = "US",
            ratingLabel = "8kW split-phase", ratedOutputW = 8000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = null, maxPvV = 600, mpptVoltageMinV = 80, mpptVoltageMaxV = 500, startupVoltageV = null,
            mpptCount = 2, stringsPerMppt = null, maxInputCurrentPerMpptA = null, maxShortCircuitCurrentPerMpptA = null,
            batteryVoltageRange = "48 V class", batteryVoltageMinV = null, batteryVoltageMaxV = null,
            maxBatteryA = null, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = null, efficiencyPercent = null,
            surgePowerRatio = null, surgeDurationSeconds = null,
            type = "Hybrid", verificationStatus = VerificationStatus.NEEDS_VERIFICATION,
            dataQualityNote = "A89: renamed from HESP 8K-US to ASF4880S180-H (this app's price list's own model string) — price updated to the price list's JMD 275,000. Electrical specs below are still HESP 8K-US's own (already only PARTIALLY_VERIFIED) carried-over data — not independently confirmed against a real ASF4880S180-H datasheet. Re-verify before treating these as authoritative.",
            engineeringNote = "High-current PV inputs; generator input; AC coupling support (family-level features).",
            sourceUrl = "https://www.srnesolar.com/download/22/Download.html"
        ),
        InverterSpec(
            // A89: price list's 10kW SRNE entry is "HES48100U200-H" (JMD 300,000) — see the 6kW
            // entry's own doc above for the family-name-jump caveat.
            brand = "SRNE", model = "HES48100U200-H", series = "HES-H", region = "US",
            ratingLabel = "10kW split-phase", ratedOutputW = 10000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = null, maxPvV = 600, mpptVoltageMinV = 80, mpptVoltageMaxV = 500, startupVoltageV = null,
            mpptCount = 2, stringsPerMppt = null, maxInputCurrentPerMpptA = null, maxShortCircuitCurrentPerMpptA = null,
            batteryVoltageRange = "48 V class", batteryVoltageMinV = null, batteryVoltageMaxV = null,
            maxBatteryA = null, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = null, efficiencyPercent = null,
            surgePowerRatio = null, surgeDurationSeconds = null,
            type = "Hybrid", verificationStatus = VerificationStatus.NEEDS_VERIFICATION,
            dataQualityNote = "A89: renamed from HESP 10K-US to HES48100U200-H (this app's price list's own model string for its 10kW SRNE entry) — price updated to the price list's JMD 300,000. Electrical specs below are still HESP 10K-US's own A52-confirmed family-level figures, carried over under the new name; not independently re-confirmed against a datasheet for the exact string \"HES48100U200-H.\" Per-model max PV input power/current still not published for this rating. Re-verify before treating these figures as authoritative for this exact model.",
            engineeringNote = "High-current PV inputs; generator input; AC coupling support (family-level features).",
            sourceUrl = "https://www.srnesolar.com/download/22/Download.html"
        ),
        InverterSpec(
            brand = "SRNE", model = "HESP 12K-US", series = "HESP 8-12K-US", region = "US",
            ratingLabel = "12kW split-phase", ratedOutputW = 12000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = null, maxPvV = 600, mpptVoltageMinV = 80, mpptVoltageMaxV = 500, startupVoltageV = null,
            mpptCount = 2, stringsPerMppt = null, maxInputCurrentPerMpptA = null, maxShortCircuitCurrentPerMpptA = null,
            batteryVoltageRange = "48 V class", batteryVoltageMinV = null, batteryVoltageMaxV = null,
            maxBatteryA = null, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = null, efficiencyPercent = null,
            surgePowerRatio = null, surgeDurationSeconds = null,
            type = "Hybrid", verificationStatus = VerificationStatus.PARTIALLY_VERIFIED,
            dataQualityNote = "A52 upgrade: max PV voltage/MPPT range now confirmed for the whole 8-12K-US family. Per-model max PV input power/current for 12K specifically still not published — still excluded from automatic Load-Based selection until confirmed.",
            engineeringNote = "High-current PV inputs; generator input; AC coupling support (family-level features); parallel-capable architecture depending on configuration.",
            sourceUrl = "https://www.srnesolar.com/download/22/Download.html"
        ),
        // SRNE 13K deliberately absent — source message: "Do NOT invent a 13K SRNE split-phase
        // model... 13K = NOT AVAILABLE."
        //
        // Growatt SPH-HU family
        InverterSpec(
            brand = "Growatt", model = "SPH 8000TL-HU-US", series = "SPH-HU", region = "US",
            ratingLabel = "8kW split-phase", ratedOutputW = 8000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = 15000, maxPvV = 525, mpptVoltageMinV = 150, mpptVoltageMaxV = 450, startupVoltageV = 130,
            mpptCount = 3, stringsPerMppt = 2, maxInputCurrentPerMpptA = 22.0, maxShortCircuitCurrentPerMpptA = 27.0,
            batteryVoltageRange = "40-60 V (48 V class)", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = 190, maxChargePowerKw = 8.0, maxDischargePowerKw = 8.0, acOutputA = 40, efficiencyPercent = 97.5,
            surgePowerRatio = null, surgeDurationSeconds = null,
            type = "Hybrid", verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = "A52 upgrade: Growatt's own US product/datasheet page now confirms full PV/MPPT/battery specs for this exact model (previously excluded pending confirmation). Not added to Catalog's auto-selectable Load-Based pool, though — that stays on the single already-established Deye SUN-8K entry for the 8kW tier rather than introducing a multiple-verified-brands-per-tier selection feature this round; still available in MANUAL mode's own picker.",
            engineeringNote = "Max recommended PV is a family-level datasheet figure (15kW), not certified per-serial-number. Max grid passthrough 62.5A; generator input supported; ~10ms UPS switching.",
            sourceUrl = "https://us.growatt.com/products/sph-8000-10000tl-hu-us"
        ),
        InverterSpec(
            brand = "Growatt", model = "SPH 10000TL-HU-US", series = "SPH-HU", region = "US",
            ratingLabel = "10kW split-phase", ratedOutputW = 10000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = 15000, maxPvV = 525, mpptVoltageMinV = 150, mpptVoltageMaxV = 450, startupVoltageV = 150,
            mpptCount = 3, stringsPerMppt = 2, maxInputCurrentPerMpptA = 22.0, maxShortCircuitCurrentPerMpptA = 27.0,
            batteryVoltageRange = "40-60 V (48 V class)", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = 200, maxChargePowerKw = 10.0, maxDischargePowerKw = 10.0, acOutputA = 50, efficiencyPercent = null,
            surgePowerRatio = null, surgeDurationSeconds = null,
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
     * Exact-kW match. [modelHint] (typically an [InverterOption.name]/`QuoteResult.inverterName`
     * display string, which already embeds the model — see [Catalog]'s `displayName()`) is checked
     * first and, when it matches, wins outright — this is what actually disambiguates correctly
     * now that more than one real, fully VERIFIED inverter can share a wattage (Deye SUN-8K and
     * Growatt SPH 8000TL-HU-US are both 8000W as of A52). Without a hint (or when it doesn't
     * match anything), falls back to preferring the VERIFIED entry, then list order — a legacy
     * path kept only for callers that genuinely have no name to pass, not something new code
     * should rely on for a wattage with multiple VERIFIED candidates.
     */
    fun inverterSpecFor(kw: Double, modelHint: String? = null): InverterSpec? {
        val watts = (kw * 1000.0).toInt()
        val candidates = inverters.filter { it.ratedOutputW == watts }
        if (modelHint != null) {
            candidates.firstOrNull { modelHint.contains(it.model) }?.let { return it }
        }
        return candidates.minByOrNull { if (it.verificationStatus == VerificationStatus.VERIFIED) 0 else 1 }
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
