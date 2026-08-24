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

/**
 * Inverter Engine round (spec "Inverter Architecture Logic"/"Inverter Mode Logic" sheets — "Every
 * inverter record must have exactly one primary mode: Hybrid, Off-grid, or Grid-tie"): the real,
 * programmatic distinction [InverterSpec.type] never actually was — every one of the 13 pre-existing
 * entries has `type = "Hybrid"` as free descriptive text, read by nothing. [InverterSpec.architecture]
 * is what UI/calculation code should branch on: HYBRID = grid+battery+PV (existing residential/
 * commercial logic, unchanged), OFF_GRID = battery+PV only, GRID_TIE = PV+grid only, no battery
 * fields, no backup/battery sizing — "Do NOT calculate backup hours or battery capacity for a
 * grid-tie-only inverter." Defaults to [HYBRID] so every existing entry (and any already-saved quote
 * referencing one) keeps its exact current behavior without needing this field touched.
 */
enum class InverterArchitecture { HYBRID, OFF_GRID, GRID_TIE }

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
    val sourceUrl: String,
    /**
     * Phase 27 §8 ("Do not assume every inverter can be paralleled"): defaults to `false` for
     * every entry below since no supplied datasheet excerpt on file has confirmed parallel/multi-
     * unit operation for any of them yet — "unconfirmed" is deliberately encoded as "not
     * supported," never as "assume yes," consistent with this whole file's "no invented
     * manufacturer specifications" rule. Flip to `true` (with [maxParallelUnits] set from the real
     * datasheet, where the model states a limit) only once a real source confirms it.
     */
    val supportsParallel: Boolean = false,
    /** Null = no confirmed limit on file — only meaningful once [supportsParallel] is true. */
    val maxParallelUnits: Int? = null,
    /** Inverter Engine round: see [InverterArchitecture]'s own doc. Defaults [InverterArchitecture.HYBRID] for every pre-existing entry. */
    val architecture: InverterArchitecture = InverterArchitecture.HYBRID,
    /**
     * Inverter Engine round (spec — "Max Apparent Power kVA... size transformer from inverter
     * maximum apparent output"): a grid-tie inverter's own maximum apparent-power rating, which can
     * exceed [ratedOutputW]/1000 at reduced power factor (e.g. 33kVA max vs 30kW rated). Null for
     * every hybrid/off-grid entry, where this figure was never part of the supplied datasheet data
     * and [ratedOutputW] alone has always been what sizing reads.
     */
    val maxApparentPowerKva: Double? = null,
    /**
     * Inverter Engine round (spec "Transformer required: YES when inverter line-to-line voltage
     * does not match the selected site/grid voltage"): the real line-to-line AC voltage(s) this
     * inverter's datasheet confirms — a list because some real models (e.g. the S5-GC50K) support
     * more than one regional voltage variant (220/380V or 230/400V). Empty for every hybrid/off-grid
     * entry (their [acVoltage] stays free text, never parsed/compared numerically) — only populated
     * for grid-tie entries this transformer-matching logic actually needs a real number for.
     */
    val acLineToLineVoltageOptionsV: List<Double> = emptyList()
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
            // Phase 41 (inverter datasheet compendium, 2026-08-24): supportsParallel/maxParallelUnits
            // added — the compendium's own GEN-LB-US 6K entry couldn't expose a clean exact-6K row
            // (same limitation this file's own note already had), but confirms "GEN-LB-US family
            // supports up to 10 units in parallel" as a family-wide characteristic, and explicitly
            // recommends "supportsParallel = true; exact numeric electrical fields = pending exact
            // 6K source" — so the parallel fields are added, every other numeric field below is
            // deliberately left untouched (still not confirmed against an exact 6K datasheet).
            dataQualityNote = "PV/MPPT specs from LuxPower's own GEN-LB-US 5-13K manual/catalogue (sourceUrl). Battery charge/discharge current is model-dependent and not given per-model — do not inherit the 13K's battery-current figure for this unit. Phase 41: parallel support confirmed family-wide (GEN-LB-US family, up to 10 units) per a separate compendium source (luxpowertek.com/wp-content/uploads/2026/07/GEN-LB-US-5K_13K-Spanish20260629.pdf) — every other field here is still not confirmed against an exact 6K-model datasheet row.",
            engineeringNote = "4 PV inputs total (2 MPPT x 2 strings each).",
            sourceUrl = "https://luxpowertek.com/wp-content/uploads/2026/06/GEN-LB-US-5-13K-User-Manual-2026.06.08.pdf",
            supportsParallel = true, maxParallelUnits = 10
        ),
        InverterSpec(
            brand = "LuxPower", model = "GEN-LB-US 8K", series = "GEN-LB-US", region = "US",
            ratingLabel = "8kW split-phase", ratedOutputW = 8000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            // Phase 41: maxPvW corrected 16000 -> 18000 — the SAME source catalogue re-read gives
            // "PV max array power 21,000W" (the STC-nameplate figure, before clipping — NOT what
            // maxPvW represents in this app; see EquipmentSelectionEngine's own "exceeds inverter
            // max PV input" wording) vs "PV max input power 18,000W" (the real DC/MPPT-stage
            // binding ceiling, matching the GEN-LB-US 13K entry's own established precedent below).
            maxPvW = 18000, maxPvV = 550, mpptVoltageMinV = 120, mpptVoltageMaxV = 440, startupVoltageV = 140,
            mpptCount = 2, stringsPerMppt = 2, maxInputCurrentPerMpptA = 26.0, maxShortCircuitCurrentPerMpptA = 31.0,
            batteryVoltageRange = "40-60 V", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = 167, maxChargePowerKw = 8.0, maxDischargePowerKw = 8.0, acOutputA = 33, efficiencyPercent = 97.5,
            surgePowerRatio = 2.0, surgeDurationSeconds = 0.5,
            type = "Hybrid", verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = "PV/MPPT/battery specs from LuxPower's own US-series inverter catalogue (sourceUrl). Phase 41 (inverter datasheet compendium): the same catalogue re-read confirms max PV input power 18,000W (corrected from an earlier 16,000W transcription — max PV ARRAY power is 21,000W, a different, non-binding figure), rated AC output current 33.3A@240V, max inverter efficiency 97.5%, and UPS surge 2x rated power for 0.5s. Parallel operation confirmed: yes, up to 10 units.",
            engineeringNote = "",
            sourceUrl = "https://luxpowertek.com/wp-content/uploads/2026/04/LuxpowerTek-US-Series-Inverter-Catalogue-2026.04.10.pdf",
            supportsParallel = true, maxParallelUnits = 10
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
            // Phase 41: supportsParallel/maxParallelUnits added (GEN-LB-US family, confirmed
            // family-wide up to 10 units — same finding as the 6K/8K/13K siblings). A separate
            // compendium source's own "family catalogue" 10K row (PV max input power 12,000W) was
            // deliberately NOT applied here — it's lower than this SAME family's own 8K figure
            // (18,000W), which isn't physically plausible for a bigger-rated unit, and the source
            // itself flags "should be matched to the exact revision/model label before production
            // database use." The existing maxPvW=18000 stays as the better-sourced figure.
            dataQualityNote = "PV/MPPT specs from LuxPower's own GEN-LB-US 5-13K manual (sourceUrl). Battery current is an approximate family-configuration figure — use the exact 10K model datasheet for final battery current/power limits. Phase 41: parallel support confirmed family-wide (GEN-LB-US family, up to 10 units); a separate source's own family-catalogue PV figure for this tier (12,000W) was NOT applied — inconsistent with this same family's own confirmed 8K figure (18,000W) and the source itself flags it as needing exact-model confirmation.",
            engineeringNote = "",
            sourceUrl = "https://luxpowertek.com/wp-content/uploads/2026/06/GEN-LB-US-5-13K-User-Manual-2026.06.08.pdf",
            supportsParallel = true, maxParallelUnits = 10
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
            type = "Hybrid", verificationStatus = VerificationStatus.VERIFIED,
            // Phase 41 (inverter datasheet compendium): a fresh, independent read of LuxPower's own
            // GEN-LB-US 13K datasheet re-confirms every electrical figure already stored here
            // exactly (PV/MPPT/battery/AC/surge all match) — upgraded NEEDS_VERIFICATION -> VERIFIED
            // on that basis. The compendium's own recommendation is explicit: "the manufacturer
            // documentation identifies the 13K as GEN-LB-US 13K" — i.e. this catalog entry's own
            // model string ("LXP-LB-US 12K/13K") is this app's PRICE LIST's naming, not the
            // manufacturer's. Deliberately NOT renamed here: this string is the durable identifier
            // [ParallelInverterDesign.inverterModelId]/[Catalog]'s manual-mode picker match against,
            // and any already-saved quote that selected this inverter stores it by this exact name —
            // renaming it would silently break that match. Parallel support now confirmed too.
            dataQualityNote = "A89: renamed from GEN-LB-US 13K to LXP-LB-US 12K/13K (this app's price list's own model string for its 13kW LuxPower entry) — price updated to the price list's JMD 340,000. Phase 41: a fresh, independent compendium read of the manufacturer's real GEN-LB-US 13K datasheet re-confirms every figure below exactly, plus adds parallel support (yes, up to 10 units). The manufacturer's own name for this unit is \"GEN-LB-US 13K\" — kept as \"LXP-LB-US 12K/13K\" here since that's the price list's own durable model string, matched against by saved quotes; a display-label distinction, not a data-quality gap anymore.",
            engineeringNote = "13kW at 240V AC / 11.2kW at 208V AC — max PV array power (21kW) exceeds max PV input power (18kW); 18kW is the binding limit used for compatibility checks. UPS (backup) output is only 10kW even though AC output is 13kW — backup-coverage sizing should use 10kW, not 13kW. Max continuous AC passthrough 90A; surge 2x rated power for 0.5s; 20ms switching; 99.9% MPPT efficiency, 94% battery efficiency; IP66/NEMA 4X.",
            sourceUrl = "https://luxpowertek.com/wp-content/uploads/2025/08/GEN-LB-US-13K-Datasheet-1.pdf",
            supportsParallel = true, maxParallelUnits = 10
        ),
        InverterSpec(
            // A89: this app's price list's 12kW LuxPower entry is "SNA-US 12K" (JMD 300,000) — a
            // different product family name from LXP-LB-US. Originally renamed/repriced with
            // LXP-LB-US 12K's own borrowed data (no SNA-US 12K datasheet was on file at the time).
            //
            // Phase 41 (inverter datasheet compendium): a real, dedicated SNA-US 12K manufacturer
            // manual was located — the borrowed LXP-LB-US figures below are now REPLACED with this
            // model's own genuine data (they were a real, different-architecture product all along,
            // exactly as the prior note's own caution flagged: 2 MPPT here, not 3; different
            // voltages/currents throughout). efficiencyPercent is set back to null (it was also a
            // borrowed LXP-LB-US figure — no SNA-US-specific inverter efficiency is published in
            // the new source, so "not published" is the honest state now, not a carried-over guess).
            brand = "LuxPower", model = "SNA-US 12K", series = "SNA-US", region = "US",
            ratingLabel = "12kW split-phase", ratedOutputW = 12000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = 24000, maxPvV = 480, mpptVoltageMinV = 120, mpptVoltageMaxV = 440, startupVoltageV = 100,
            mpptCount = 2, stringsPerMppt = 2, maxInputCurrentPerMpptA = 35.0, maxShortCircuitCurrentPerMpptA = 44.0,
            batteryVoltageRange = "38.4-60 V (46.4-60 V Li-ion / 38.4-60 V lead-acid)", batteryVoltageMinV = 38.4, batteryVoltageMaxV = 60.0,
            maxBatteryA = 250, maxChargePowerKw = 12.0, maxDischargePowerKw = 12.0, acOutputA = 50, efficiencyPercent = null,
            surgePowerRatio = 2.0, surgeDurationSeconds = 5.0,
            type = "Hybrid", verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = "A89: renamed from LXP-LB-US 12K to SNA-US 12K (this app's price list's own model string) — price updated to the price list's JMD 300,000. Phase 41: replaced with this model's own genuine datasheet data (SNA-US 12-15K manual) — maxPvW is the manufacturer's own stated \"max PV array power\" (24,000W = 12,000+12,000; this source doesn't separately publish a lower \"max input power\" figure the way the GEN-LB-US family does, unlike other entries in this file where maxPvW means the lower input-power ceiling). efficiencyPercent reset to null — no SNA-US-specific inverter efficiency figure is published in this source.",
            engineeringNote = "2 independent MPPT, 2 inputs per MPPT (corrected from an earlier borrowed 3-MPPT/2:1:1 LXP-LB-US figure). Recommended battery capacity >400Ah per inverter. Overload: 5s at >=150%, 10s at 110-150% (L-N/L-L). Smart load output 6,000W L-N / 12,000W L-L. Pure sine wave.",
            sourceUrl = "https://luxpowertek.com/wp-content/uploads/2026/04/SNA-US-12-15K-User-Manual-2025.3.10.pdf",
            supportsParallel = true, maxParallelUnits = 16
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
            maxPvW = 9000, maxPvV = 500, mpptVoltageMinV = 150, mpptVoltageMaxV = 425, startupVoltageV = 125,
            mpptCount = 2, stringsPerMppt = 2, maxInputCurrentPerMpptA = 26.0, maxShortCircuitCurrentPerMpptA = 44.0,
            batteryVoltageRange = "40-60 V (48 V class)", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = 135, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = 25, efficiencyPercent = 97.6,
            surgePowerRatio = 2.0, surgeDurationSeconds = 10.0,
            type = "Hybrid", verificationStatus = VerificationStatus.VERIFIED,
            // Phase 41: a real "SUN-6K-SG02LP2-US-AM2" datasheet was located — an "-AM2" hardware-
            // revision suffix beyond this catalog's own "SUN-6K-SG02LP2-US" string, close enough to
            // treat as this model (same base part number, AM2 typically denotes a minor regional/
            // certification revision, not a different electrical architecture) — noted honestly
            // rather than silently assumed. Corrects: maxPvW 7800->9000 ("max PV input power 9,000W"
            // vs the prior estimate), stringsPerMppt 1->2 ("2 MPPT / 2+2 strings" — was wrongly 1),
            // adds efficiency (97.6% max) and surge (2x rated for 10s, matching this model's own
            // "peak off-grid power" figure) that were previously unconfirmed.
            dataQualityNote = "A89: renamed from SUN-6K-SG01LP1-US to SUN-6K-SG02LP2-US (this app's price list's own model string) — price updated to the price list's JMD 230,000. Phase 41: replaced with a real SUN-6K-SG02LP2-US-AM2 datasheet's own figures (the '-AM2' suffix is a hardware revision beyond this catalog's model string — treated as the same base model, flagged rather than silently assumed identical).",
            engineeringNote = "2 MPPT, 2 strings each (corrected from an earlier asymmetric 26A+13A/1-string transcription). Max continuous passthrough 40A. Euro efficiency 96.5%; MPPT efficiency >99%. Parallel support confirmed: yes, up to 16 units.",
            sourceUrl = "https://www.deyeinverter.com/product/split-phase-hybrid-inverter-1/sun5-6-7-6-8-10-12ksg02lp2usam2-am3-512kw-2-mppt-hybrid-inverter-lv-battery-supported.html",
            supportsParallel = true, maxParallelUnits = 16
        ),
        InverterSpec(
            brand = "Deye", model = "SUN-8K-SG01LP1-US", series = "SG01LP1-US", region = "US",
            ratingLabel = "8kW split-phase", ratedOutputW = 8000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = 10400, maxPvV = 500, mpptVoltageMinV = 150, mpptVoltageMaxV = 425, startupVoltageV = 125,
            mpptCount = 2, stringsPerMppt = 2, maxInputCurrentPerMpptA = 26.0, maxShortCircuitCurrentPerMpptA = 44.0,
            batteryVoltageRange = "40-60 V (48 V class)", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = 190, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = 33, efficiencyPercent = 97.6,
            surgePowerRatio = 2.0, surgeDurationSeconds = 10.0,
            type = "Hybrid", verificationStatus = VerificationStatus.VERIFIED,
            // Phase 41 (inverter datasheet compendium): exact model match, independently re-confirms
            // every existing figure. Corrects stringsPerMppt 1->2 ("2 MPPT / 2+2 strings" — was
            // wrongly 1) and adds efficiency (97.6% max) and surge (2x rated for 10s, "peak off-grid
            // power") that were previously unconfirmed, plus parallel support.
            dataQualityNote = "$USER_PROVIDED Phase 41: independently re-confirmed against a real SUN-8K-SG01LP1-US manufacturer product page/datasheet.",
            engineeringNote = "Both MPPTs equal on this model: 26A + 26A operating, 44A + 44A short-circuit, 2 strings each. Max apparent power 8.8kVA; max AC current 36.7A (40A max). Euro efficiency 97.0%; MPPT efficiency >99%. Self-adapting BMS communication for Li-ion.",
            sourceUrl = "https://www.deyeinverter.com/product/split-phase-hybrid-inverter-1/sun5-6-7-6-8ksg01lp1us-58kw-single-phase-2-mppt-hybrid-inverter-lv-battery-supported-63.html",
            supportsParallel = true, maxParallelUnits = 16
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
            // Phase 41 (inverter datasheet compendium): a targeted search for "ASF4860U80-H" in
            // SRNE's own manufacturer sources found NO matching model — the closest real SRNE
            // product under a similar name is "HF4850U80-H," which is a 5,000W-rated unit (not
            // 6,000W). Because that rated-power mismatch means HF4850U80-H can't safely stand in
            // for this catalog's own 6kW price tier, its numeric fields were deliberately NOT
            // copied here — doing so would misrepresent a genuinely 5kW-rated product as this
            // catalog's 6kW entry. supportsParallel stays false: the HF4850U80-H datasheet doesn't
            // state a parallel capacity either, so there's nothing to confirm true from even if the
            // name match were closer.
            dataQualityNote = "A89: renamed from HESP4860U140-HUS to ASF4860U80-H (this app's price list's own model string, a different SRNE family — ASF, not HESP) — price updated to the price list's JMD 190,000; mpptCount updated to 1 to match this price list's own \"ASF4860U80-H\" naming (\"80\" reading as a single 80A-class MPPT, not confirmed). Every other electrical figure below is still HESP4860U140-HUS's own carried-over data — not independently confirmed against a real ASF4860U80-H datasheet. Re-verify before treating these as authoritative. Phase 41: no SRNE manufacturer source for the exact string \"ASF4860U80-H\" was found — the closest real match, HF4850U80-H, is a 5,000W-rated unit, so its data was NOT substituted here (rated-power mismatch vs. this catalog's 6kW tier). Confirm the exact model/rating with the installer's own supplier before quoting this line.",
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
            maxPvW = 11000, maxPvV = 500, mpptVoltageMinV = 125, mpptVoltageMaxV = 425, startupVoltageV = null,
            mpptCount = 2, stringsPerMppt = null, maxInputCurrentPerMpptA = 22.0, maxShortCircuitCurrentPerMpptA = null,
            batteryVoltageRange = "40-60 V (48 V class)", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = 180, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = null, efficiencyPercent = 92.0,
            surgePowerRatio = null, surgeDurationSeconds = null,
            type = "Hybrid", verificationStatus = VerificationStatus.PARTIALLY_VERIFIED,
            // Phase 41 (inverter datasheet compendium): an EXACT model match was found — and the
            // manufacturer manual is explicit and definitive: "parallel capacity is '/' — the model
            // does NOT support parallel connection." supportsParallel stays false, now confirmed
            // rather than default-unconfirmed. DC/PV/battery-side fields above were updated from
            // this real datasheet (maxPvW, maxPvV, MPPT voltage range, per-MPPT current, battery
            // range/current, efficiency) since those don't depend on AC topology.
            //
            // IMPORTANT UNRESOLVED FINDING: the real ASF4880S180-H datasheet describes a 230 Vac
            // (or 220 Vac on an older revision) SINGLE-PHASE output, not the 120/240V split-phase
            // topology this catalog entry (and this app's whole Jamaica residential model) assumes.
            // The AC-side fields (acVoltage/splitPhase/acOutputA) were deliberately left unchanged
            // pending confirmation of which literal regional variant is actually being quoted —
            // verify with the supplier before relying on this entry for a split-phase installation.
            dataQualityNote = "A89: renamed from HESP 8K-US to ASF4880S180-H (this app's price list's own model string) — price updated to the price list's JMD 275,000. Phase 41: an exact-model SRNE datasheet was located, confirming parallel is NOT supported and correcting the PV/battery-side figures above. UNRESOLVED: the real datasheet describes a 230/220 Vac single-phase output, conflicting with this entry's own 120/240V split-phase assumption — verify the actual regional variant with the supplier before relying on the AC-side fields (acVoltage/splitPhase/acOutputA), which were deliberately left unchanged pending that confirmation.",
            engineeringNote = "MPPT efficiency 99.9%; max battery inverter efficiency 92%. Motor start capacity 5 HP. Max mains/generator charging current 100A; max hybrid charging current 180A. Grid/generator input 90-275 Vac.",
            sourceUrl = "https://www.srnesolar.com/wp-content/uploads/2022/11/SRNE_ASF-series_48V_8-10kW_S_solar-charge-inverter_datasheet_1.1.pdf"
        ),
        InverterSpec(
            // A89: price list's 10kW SRNE entry is "HES48100U200-H" (JMD 300,000) — see the 6kW
            // entry's own doc above for the family-name-jump caveat.
            brand = "SRNE", model = "HES48100U200-H", series = "HES-H", region = "US",
            ratingLabel = "10kW split-phase", ratedOutputW = 10000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = 11000, maxPvV = 500, mpptVoltageMinV = 125, mpptVoltageMaxV = 425, startupVoltageV = null,
            mpptCount = 2, stringsPerMppt = null, maxInputCurrentPerMpptA = 22.0, maxShortCircuitCurrentPerMpptA = null,
            batteryVoltageRange = "40-60 V (48 V class)", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = 200, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = null, efficiencyPercent = 97.5,
            surgePowerRatio = null, surgeDurationSeconds = null,
            type = "Hybrid", verificationStatus = VerificationStatus.VERIFIED,
            // Phase 41 (inverter datasheet compendium): an exact-model SRNE HES family datasheet
            // confirms the 240Vac split-phase L1+L2+N+PE topology (matches this entry's own
            // assumption — no conflict here, unlike the ASF4880S180-H sibling), battery range/
            // current, and MPPT voltage/current. maxPvW (11,000W = 5,500+5,500) is the family
            // table's own merged-cell row — the datasheet itself notes the 12K row differs
            // (6,500+6,500W), so this figure is family-level, not independently confirmed for the
            // exact 10K SKU; kept with that caveat rather than left null, matching how this file's
            // Growatt entries already treat a family-level PV figure. supportsParallel stays false —
            // the compendium's own finding is explicit: "the cited HES datasheet does not state a
            // parallel capacity; do not mark true from this document alone."
            dataQualityNote = "A89: renamed from HESP 10K-US to HES48100U200-H (this app's price list's own model string for its 10kW SRNE entry) — price updated to the price list's JMD 300,000. Phase 41: replaced with the exact-model HES family datasheet's own figures — battery/MPPT-voltage/current now confirmed; maxPvW is the family table's merged-cell row (5,500+5,500W) — the source itself notes the 12K row differs (6,500+6,500W), so treat as family-level, not exact-10K-confirmed. Parallel capacity not stated in this datasheet — do not mark true.",
            engineeringNote = "European efficiency 97%; MPPT efficiency 99.9%. Max mains/generator charging current 120A; max hybrid charging current 200A. IP65; -25 to 60C operating range.",
            sourceUrl = "https://www.srnesolar.com/userfiles/files/2023/07/18/SRNE_HES%20series_US_48V_8-12kW_240V_split%20phase_hybrid%20solar%20charge%20inverter_datasheet_1.3.pdf"
        ),
        InverterSpec(
            brand = "SRNE", model = "HESP 12K-US", series = "HESP 8-12K-US", region = "US",
            ratingLabel = "12kW split-phase", ratedOutputW = 12000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = 13200, maxPvV = 550, mpptVoltageMinV = 125, mpptVoltageMaxV = 450, startupVoltageV = null,
            mpptCount = 2, stringsPerMppt = null, maxInputCurrentPerMpptA = 25.0, maxShortCircuitCurrentPerMpptA = null,
            batteryVoltageRange = "40-60 V (48 V class)", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = 200, maxChargePowerKw = null, maxDischargePowerKw = null, acOutputA = 50, efficiencyPercent = 97.5,
            surgePowerRatio = null, surgeDurationSeconds = null,
            type = "Hybrid", verificationStatus = VerificationStatus.VERIFIED,
            // Phase 41 (inverter datasheet compendium): a real, comprehensive manufacturer brochure
            // for the exact model was located — "SRNE HESP48120U200-H" (12,000W @ 240V / 10,400W @
            // 208V — matches this entry's own 12kW rating and price-list tier). Upgraded
            // PARTIALLY_VERIFIED -> VERIFIED with a full PV/battery/AC data set, plus confirmed
            // parallel support: "1-6 units."
            dataQualityNote = "A52 upgrade: max PV voltage/MPPT range confirmed for the whole 8-12K-US family. Phase 41: replaced with the exact-model SRNE HESP48120U200-H brochure's own confirmed data (the manufacturer's real model string for this 12kW SRNE unit) — kept as \"HESP 12K-US\" here since that's this catalog's own durable identifier, matched against by saved quotes.",
            engineeringNote = "CEC efficiency 96.5%; MPPT efficiency 99.9%. Max grid charging current 120A; max generator charging current 60A; max hybrid charging current 200A. Dimensions 750x440x240mm, 42kg. Supports Li-ion BMS communication.",
            sourceUrl = "https://www.srnesolar.com/userfiles/files/2026/01/20/SRNE_Solar%20Storage%20Inverter_US_%20for%20Residential_Brochure_V5.1_compressed.pdf",
            supportsParallel = true, maxParallelUnits = 6
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
            // Phase 41 (inverter datasheet compendium): every existing field independently
            // re-confirmed exactly; adds overload/surge (13,000W for 5s = 1.625x rated) and
            // confirmed parallel support ("max stackable/parallel units 6") that weren't captured
            // before.
            surgePowerRatio = 1.625, surgeDurationSeconds = 5.0,
            type = "Hybrid", verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = "A52 upgrade: Growatt's own US product/datasheet page now confirms full PV/MPPT/battery specs for this exact model (previously excluded pending confirmation). Not added to Catalog's auto-selectable Load-Based pool, though — that stays on the single already-established Deye SUN-8K entry for the 8kW tier rather than introducing a multiple-verified-brands-per-tier selection feature this round; still available in MANUAL mode's own picker. Phase 41: independently re-confirmed, plus overload/surge and parallel support added.",
            engineeringNote = "Max recommended PV is a family-level datasheet figure (15kW), not certified per-serial-number. Max grid passthrough 62.5A; generator input supported; <10ms UPS switching. CEC efficiency 97%; MPPT efficiency >=99.5%. Noise <=30dB(A); self-consumption <60W.",
            sourceUrl = "https://us.growatt.com/products/sph-8000-10000tl-hu-us",
            supportsParallel = true, maxParallelUnits = 6
        ),
        InverterSpec(
            brand = "Growatt", model = "SPH 10000TL-HU-US", series = "SPH-HU", region = "US",
            ratingLabel = "10kW split-phase", ratedOutputW = 10000, acVoltage = "120/240 V split-phase",
            frequencyHzRaw = "50/60", splitPhase = true,
            maxPvW = 15000, maxPvV = 525, mpptVoltageMinV = 150, mpptVoltageMaxV = 450, startupVoltageV = 150,
            mpptCount = 3, stringsPerMppt = 2, maxInputCurrentPerMpptA = 22.0, maxShortCircuitCurrentPerMpptA = 27.0,
            batteryVoltageRange = "40-60 V (48 V class)", batteryVoltageMinV = 40.0, batteryVoltageMaxV = 60.0,
            maxBatteryA = 200, maxChargePowerKw = 10.0, maxDischargePowerKw = 10.0, acOutputA = 50, efficiencyPercent = 97.5,
            // Phase 41 (inverter datasheet compendium): every existing field independently
            // re-confirmed exactly against a real, now-cited datasheet — adds efficiency, overload/
            // surge (13,000W for 5s = 1.3x rated), and confirmed parallel support (up to 6 units)
            // that weren't captured before, when this entry had no sourceUrl at all.
            surgePowerRatio = 1.3, surgeDurationSeconds = 5.0,
            type = "Hybrid", verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = "Phase 41: replaced a generic user-provided note with a real, cited Growatt SPH 10000TL-HU-US datasheet — every existing field independently re-confirmed exactly, plus efficiency/surge/parallel-support fields added.",
            engineeringNote = "3 MPPT trackers, 2 PV strings per MPPT. CEC efficiency 97%; MPPT efficiency >=99.5%. Max grid passthrough 62.5A. Noise <=30dB(A); self-consumption <60W. Dimensions 440x855x256mm, 48.84kg.",
            sourceUrl = "https://us.growatt.com/upload/file/SPH_10000TL-HU-US_Datasheet_EN_202406.pdf",
            supportsParallel = true, maxParallelUnits = 6
        ),
        // Growatt 12K/13K deliberately absent — source message: do not invent these if an exact
        // US split-phase model cannot be verified.

        // Inverter Engine round (user-supplied "Lumix Load Sheet Defaults 2" spreadsheet's Inverter
        // Catalog sheet): the first two GRID_TIE entries in this catalog — commercial/industrial
        // three-phase string inverters, no battery port at all (batteryVoltageRange/maxBatteryA/etc.
        // all null/"N/A", consistent with every other field here being null wherever the source data
        // genuinely has nothing to report, never invented). This model's existing `acOutputA: Int?`
        // field only holds a single rated-current figure — the source also gives a separate, higher
        // MAX AC current for both models; rather than adding a new field for one pair of entries,
        // that max figure (and the 50K's second rated-current voltage variant) is recorded in
        // `engineeringNote` instead, the same "note it rather than force a field that doesn't fit
        // the rest of this catalog" precedent this file's own surge-rating history already set.
        InverterSpec(
            brand = "Solis", model = "S5-GC30K-LV", series = "S5-GC", region = "220 V three-phase LV (Caribbean/regional grid-tie)",
            ratingLabel = "30kW three-phase grid-tie", ratedOutputW = 30000, acVoltage = "3/(N)/PE, 220 V three-phase",
            frequencyHzRaw = "50/60", splitPhase = false,
            maxPvW = 45000, maxPvV = 1100, mpptVoltageMinV = 180, mpptVoltageMaxV = 1000, startupVoltageV = 195,
            mpptCount = 4, stringsPerMppt = 2, maxInputCurrentPerMpptA = 32.0, maxShortCircuitCurrentPerMpptA = 40.0,
            batteryVoltageRange = "N/A — grid-tie, no battery port", batteryVoltageMinV = null, batteryVoltageMaxV = null,
            maxBatteryA = null, maxChargePowerKw = null, maxDischargePowerKw = null,
            acOutputA = 79, efficiencyPercent = 98.4,
            surgePowerRatio = null, surgeDurationSeconds = null,
            type = "Grid-tie", verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = "User-supplied \"Lumix Load Sheet Defaults 2\" spreadsheet, Inverter Catalog sheet — every field below taken directly from that data. The source's own note: \"Verify exact regional variant before procurement.\"",
            engineeringNote = "Rated AC current 78.7A, max AC current 86.6A (acOutputA above holds the rated figure, rounded to 79A; this model has no dedicated max-AC-current field, so the 86.6A max is recorded here in prose only). Max apparent power 33kVA / max AC output 33kW. PF >0.99 (0.8 lead-0.8 lag), THDi <3%. Protection: AFCI, DC/AC surge, reverse-polarity, over-current, anti-islanding, grid monitoring. Transformerless topology, IP66. Dimensions 691x578x338mm, 54.5kg. Parallel AC inverters: yes, subject to manufacturer/site design (no confirmed unit-count limit given).",
            sourceUrl = "",
            supportsParallel = true, maxParallelUnits = null,
            architecture = InverterArchitecture.GRID_TIE,
            maxApparentPowerKva = 33.0,
            acLineToLineVoltageOptionsV = listOf(220.0)
        ),
        InverterSpec(
            brand = "Solis", model = "S5-GC50K", series = "S5-GC", region = "220/380 V or 230/400 V three-phase (Caribbean/regional grid-tie)",
            ratingLabel = "50kW three-phase grid-tie", ratedOutputW = 50000, acVoltage = "3/N/PE, 220/380 V or 230/400 V three-phase",
            frequencyHzRaw = "50/60", splitPhase = false,
            maxPvW = 66500, maxPvV = 1100, mpptVoltageMinV = 180, mpptVoltageMaxV = 1000, startupVoltageV = 195,
            mpptCount = 5, stringsPerMppt = 2, maxInputCurrentPerMpptA = 32.0, maxShortCircuitCurrentPerMpptA = 40.0,
            batteryVoltageRange = "N/A — grid-tie, no battery port", batteryVoltageMinV = null, batteryVoltageMaxV = null,
            maxBatteryA = null, maxChargePowerKw = null, maxDischargePowerKw = null,
            acOutputA = 76, efficiencyPercent = 98.7,
            surgePowerRatio = null, surgeDurationSeconds = null,
            type = "Grid-tie", verificationStatus = VerificationStatus.VERIFIED,
            dataQualityNote = "User-supplied \"Lumix Load Sheet Defaults 2\" spreadsheet, Inverter Catalog sheet — every field below taken directly from that data. Two voltage variants are both real per the source (220/380V and 230/400V) — acOutputA holds the higher 380V-variant rated current (76.0A); the 400V variant's own rated current is 72.2A (see engineeringNote).",
            engineeringNote = "Rated AC current 76.0A at 380V / 72.2A at 400V; max AC current 83.6A. Max apparent power 55kVA / max AC output 55kW; 66.5kW recommended max PV input. PF >0.99 (0.8 lead-0.8 lag), THDi <3%. Protection: AFCI, DC/AC surge, reverse-polarity, over-current, anti-islanding, grid monitoring, string monitoring, I/V scan. Transformerless topology, IP66. Dimensions 691x578x338mm, 54.5kg. Parallel AC inverters: yes, subject to manufacturer/site design (no confirmed unit-count limit given).",
            sourceUrl = "",
            supportsParallel = true, maxParallelUnits = null,
            architecture = InverterArchitecture.GRID_TIE,
            maxApparentPowerKva = 55.0,
            acLineToLineVoltageOptionsV = listOf(380.0, 400.0)
        )
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
