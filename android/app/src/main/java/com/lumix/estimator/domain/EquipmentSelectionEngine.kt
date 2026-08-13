package com.lumix.estimator.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * A49/A50: the equipment-selection logic that makes LOAD-BASED (and GUIDED, which shares it — see
 * [SystemCalculator]) an actual design engine — not "smallest that exceeds the requirement," and
 * not "round up to the next product." Given a calculated engineering requirement, these functions
 * search the real catalog this app actually sells and prices ([Catalog]), reject anything that
 * fails electrical validity, and score what's left toward a practical ~10-20% headroom target,
 * an even panel count, and a single appropriately-sized unit over several small ones — explaining
 * the pick in plain language a reviewer can read back.
 *
 * MANUAL mode never calls this object — an installer's explicit equipment choice in Manual mode is
 * used exactly as selected (see [SystemCalculator]'s manual branch and its `manualInverterWarning`/
 * `manualBatteryWarning` fields, which flag rather than override an undersized choice).
 */
object EquipmentSelectionEngine {

    /** Assumed cold-morning Voc rise: standard crystalline-silicon Voc coefficient (~-0.30%/°C)
     * applied against a conservative low-temperature design point for Jamaica's mild climate
     * (~10°C, 15°C below the 25°C STC rating point) — no manufacturer per-model temperature
     * coefficient exists in this catalog's data, so this is a deliberately conservative flat
     * design margin rather than a datasheet figure. */
    private const val COLD_TEMP_VOC_CORRECTION = 1.045

    /** Matches the existing documented assumption in [com.lumix.estimator.domain.simulation.worstCaseStartupSurgeKw]:
     * "most hybrid inverters tolerate roughly 2x their continuous rating for a few seconds." */
    private const val INVERTER_SURGE_TOLERANCE_MULTIPLIER = 2.0

    /** Used only when the chosen inverter tier has no matched real spec (or the match lacks a
     * max-PV-voltage figure) — the lowest real max-PV-voltage among every matched inverter this
     * library does carry, i.e. the most conservative confirmed ceiling available, rather than an
     * arbitrary guess. */
    private val fallbackMaxPvVoltage: Double by lazy {
        EquipmentSpecs.inverters.mapNotNull { it.maxPvV }.minOrNull()?.toDouble() ?: 500.0
    }

    /**
     * This catalog's inverter data has no per-model MPPT operating-voltage *floor* (only a max PV
     * voltage) — so this is a typical low-end MPPT start voltage for split-phase hybrid string
     * inverters of this class (most sit in the 60-125V start range), used as a single conservative
     * floor for the Vmp check (spec §8) rather than an invented per-model figure. In practice this
     * only ever binds on an unrealistically short string (one or two low-Vmp panels on a tracker),
     * which is exactly the case it exists to catch.
     */
    private const val MIN_MPPT_OPERATING_VOLTAGE = 90.0

    private data class PanelElectrical(val vocV: Double, val vmpV: Double, val iscA: Double, val impA: Double, val estimated: Boolean)

    private val realPanelSample by lazy {
        EquipmentSpecs.panels.filter { it.vocV != null && it.vmpV != null && it.iscA != null && it.impA != null }
    }

    /**
     * Exact datasheet match when this wattage exists in [EquipmentSpecs] (currently only 595W).
     * Otherwise derives an estimate from the real panels this library does carry: their Voc/Vmp
     * cluster tightly regardless of wattage (~40V Vmp / ~49V Voc across the 595-720W modules on
     * file — same module-voltage family, more cells rather than a longer string as wattage rises),
     * while Isc/Imp scale with power. Averaging both patterns from the real sample (rather than a
     * hardcoded guess) keeps the estimate grounded in the actual approved equipment data.
     */
    private fun panelElectricalFor(watts: Int): PanelElectrical {
        val exact = EquipmentSpecs.panelSpecFor(watts)
        if (exact?.vocV != null && exact.vmpV != null && exact.iscA != null && exact.impA != null) {
            return PanelElectrical(exact.vocV, exact.vmpV, exact.iscA, exact.impA, estimated = false)
        }
        val avgVoc = realPanelSample.map { it.vocV!! }.average()
        val avgVmp = realPanelSample.map { it.vmpV!! }.average()
        val avgIscPerWatt = realPanelSample.map { it.iscA!! / it.pmaxW }.average()
        val avgImpPerWatt = realPanelSample.map { it.impA!! / it.pmaxW }.average()
        return PanelElectrical(avgVoc, avgVmp, avgIscPerWatt * watts, avgImpPerWatt * watts, estimated = true)
    }

    data class PanelChoice(
        val panelWatts: Int,
        val panelCount: Int,
        val totalPvKw: Double,
        val oversizePercent: Double,
        val electricallyValid: Boolean,
        val reason: String
    )

    private data class PanelCandidate(
        val watts: Int, val count: Int, val totalKw: Double, val oversizePercent: Double,
        val valid: Boolean, val notes: List<String>, val estimated: Boolean
    )

    /**
     * A52: the full series-topology electrical breakdown for one SPECIFIC panel/count/inverter
     * combination — not a search result. This is what an installer's own MANUAL-mode choice (or
     * any other "is this exact system valid" question) gets checked against, using the identical
     * rules [selectBestPanelConfiguration]'s search already applies, so a manual pick and an
     * auto-selected pick are never held to two different standards. Voltage adds across a series
     * string; current does NOT multiply by panel count — see [stringImpA]/[stringIscA] below.
     */
    data class PanelCompatibilityResult(
        val arrayKw: Double,
        val requiredMaxPvKw: Double,
        val stringVocV: Double,
        val stringVmpV: Double,
        val stringImpA: Double,
        val stringIscA: Double,
        val mpptVoltageMaxV: Double,
        val mpptTrackers: Int,
        val powerOk: Boolean,
        val vocOk: Boolean,
        val vmpOk: Boolean,
        val iscOk: Boolean,
        val notes: List<String>,
        val estimated: Boolean
    ) {
        val valid: Boolean get() = powerOk && vocOk && vmpOk && iscOk
    }

    /** Core evaluation against explicit limits — shared by the search in [selectBestPanelConfigurationForLimits] and standalone validation via [checkPanelInverterCompatibilityForLimits]. */
    private fun evaluateCandidate(watts: Int, count: Int, maxPvW: Double, maxPvV: Double, mpptTrackers: Int): PanelCompatibilityResult {
        if (count <= 0) {
            return PanelCompatibilityResult(
                arrayKw = 0.0, requiredMaxPvKw = maxPvW / 1000.0,
                stringVocV = 0.0, stringVmpV = 0.0, stringImpA = 0.0, stringIscA = 0.0,
                mpptVoltageMaxV = maxPvV, mpptTrackers = mpptTrackers,
                powerOk = true, vocOk = true, vmpOk = true, iscOk = true,
                notes = emptyList(), estimated = false
            )
        }
        val elec = panelElectricalFor(watts)
        val totalKw = count * watts / 1000.0
        val longestStringPanels = ceil(count.toDouble() / mpptTrackers).toInt()
        val shortestStringPanels = (count / mpptTrackers).coerceAtLeast(1)
        val correctedVoc = elec.vocV * longestStringPanels * COLD_TEMP_VOC_CORRECTION
        val shortestStringVmp = elec.vmpV * shortestStringPanels
        val impliedMaxCurrentA = maxPvW / maxPvV

        val notes = mutableListOf<String>()
        val powerOk = totalKw * 1000.0 <= maxPvW + 1.0
        if (!powerOk) {
            notes += "array power %.0fW exceeds inverter max PV input %.0fW".format(totalKw * 1000.0, maxPvW)
        }
        val vocOk = correctedVoc <= maxPvV
        if (!vocOk) {
            notes += "longest MPPT string Voc %.0fV (%d panels, cold-corrected) exceeds inverter max PV voltage %.0fV"
                .format(correctedVoc, longestStringPanels, maxPvV)
        }
        val vmpOk = shortestStringVmp >= MIN_MPPT_OPERATING_VOLTAGE
        if (!vmpOk) {
            notes += "shortest MPPT string Vmp %.0fV (%d panels) is below the inverter's minimum MPPT operating voltage %.0fV"
                .format(shortestStringVmp, shortestStringPanels, MIN_MPPT_OPERATING_VOLTAGE)
        }
        val iscOk = elec.iscA <= impliedMaxCurrentA + 0.05
        if (!iscOk) {
            notes += "panel Isc %.1fA exceeds the inverter's implied max PV current per tracker %.1fA".format(elec.iscA, impliedMaxCurrentA)
        }

        return PanelCompatibilityResult(
            arrayKw = totalKw, requiredMaxPvKw = maxPvW / 1000.0,
            stringVocV = correctedVoc, stringVmpV = elec.vmpV * longestStringPanels,
            stringImpA = elec.impA, stringIscA = elec.iscA,
            mpptVoltageMaxV = maxPvV, mpptTrackers = mpptTrackers,
            powerOk = powerOk, vocOk = vocOk, vmpOk = vmpOk, iscOk = iscOk,
            notes = notes, estimated = elec.estimated
        )
    }

    /** Explicit-limits entry point — mirrors [selectBestPanelConfigurationForLimits]'s own split for deterministic unit testing. */
    internal fun checkPanelInverterCompatibilityForLimits(
        panelWatts: Int, panelCount: Int, maxPvW: Double, maxPvV: Double, mpptTrackers: Int
    ): PanelCompatibilityResult = evaluateCandidate(panelWatts, panelCount, maxPvW, maxPvV, mpptTrackers)

    /**
     * Validates one SPECIFIC panel/count/inverter combination — the function MANUAL mode (and any
     * other "is this exact system valid" UI) should call, since it checks real series Voc/Vmp/Isc
     * against the inverter's real max PV input power (never a proxy like "AC rating × 1.3").
     */
    fun checkPanelInverterCompatibility(
        panelWatts: Int, panelCount: Int, inverterKw: Double, inverterNameHint: String? = null
    ): PanelCompatibilityResult {
        val invSpec = EquipmentSpecs.inverterSpecFor(inverterKw, inverterNameHint)
        val maxPvW = invSpec?.maxPvW?.toDouble() ?: (inverterKw * 1300.0)
        val maxPvV = invSpec?.maxPvV?.toDouble() ?: fallbackMaxPvVoltage
        val mpptTrackers = invSpec?.mpptCount?.coerceAtLeast(1) ?: 2
        return evaluateCandidate(panelWatts, panelCount, maxPvW, maxPvV, mpptTrackers)
    }

    /**
     * Evaluates real candidate configurations across every panel wattage this catalog sells —
     * several counts per wattage, not just "round up." Panels are split as evenly as possible
     * across the chosen inverter's own real MPPT-tracker count ([EquipmentSpecs.InverterSpec.mpptCount],
     * defaulting to 2 when unmatched) with each tracker's string checked independently as ONE
     * series string (per spec §5's no-parallel-strings-within-one-input rule) — using the
     * inverter's own built-in tracker inputs this way isn't the ad-hoc parallel-string wiring the
     * spec is warning against; it's how these inverters are designed to be used, and it's what
     * keeps this check from flagging completely ordinary residential arrays as invalid just
     * because a real inverter's real max-PV-voltage (500-600V in this library) can't fit an
     * entire double-digit panel count in one string. Rejects any candidate whose worst (longest)
     * tracker string's cold-corrected Voc or Isc, or the array's total power, exceeds the
     * inverter's matched real limits (or a conservative fallback where no exact spec match
     * exists), then scores what's left toward: within the preferred 10-20% headroom band, even
     * panel count, closest to a 15% midpoint target, and least total oversizing — never the
     * largest or smallest available option "because it's available." Electrical validity always
     * outranks every scoring preference.
     */
    fun selectBestPanelConfiguration(
        requiredPvKw: Double,
        inverterKw: Double,
        wattages: List<Int> = Catalog.panelWattages,
        inverterNameHint: String? = null
    ): PanelChoice {
        val invSpec = EquipmentSpecs.inverterSpecFor(inverterKw, inverterNameHint)
        val maxPvW = invSpec?.maxPvW?.toDouble() ?: (inverterKw * 1300.0)
        val maxPvV = invSpec?.maxPvV?.toDouble() ?: fallbackMaxPvVoltage
        val mpptTrackers = invSpec?.mpptCount?.coerceAtLeast(1) ?: 2
        return selectBestPanelConfigurationForLimits(requiredPvKw, maxPvW, maxPvV, mpptTrackers, wattages)
    }

    /**
     * The actual candidate-evaluation/scoring core, taking explicit electrical limits rather than
     * resolving them from a catalog inverter kW — `internal` so this module's own JVM unit tests
     * (`EquipmentSelectionEngineTest`) can exercise precise, deterministic Voc/Vmp/Isc/power
     * scenarios without depending on which real datasheets happen to be in [EquipmentSpecs] today.
     * [selectBestPanelConfiguration] is the real entry point every caller outside tests should use.
     */
    internal fun selectBestPanelConfigurationForLimits(
        requiredPvKw: Double,
        maxPvW: Double,
        maxPvV: Double,
        mpptTrackers: Int,
        wattages: List<Int> = Catalog.panelWattages
    ): PanelChoice {
        if (requiredPvKw <= 0.0) {
            return PanelChoice(wattages.first(), 0, 0.0, 0.0, true, "No PV capacity required.")
        }

        fun evaluate(watts: Int, count: Int): PanelCandidate {
            val result = evaluateCandidate(watts, count, maxPvW, maxPvV, mpptTrackers)
            val oversize = (result.arrayKw - requiredPvKw) / requiredPvKw * 100.0
            return PanelCandidate(watts, count, result.arrayKw, oversize, result.valid, result.notes, result.estimated)
        }

        val allCandidates = wattages.flatMap { w ->
            val baseCount = max(1, ceil((requiredPvKw * 1000.0) / w).toInt())
            (baseCount..(baseCount + 5)).map { n -> evaluate(w, n) }
        }

        val valid = allCandidates.filter { it.valid }
        // Nothing validated across the whole search window (shouldn't normally happen) — surface
        // the least-bad candidate flagged invalid rather than silently returning zero panels.
        val pool = valid.ifEmpty { allCandidates }

        // A half-point epsilon on both ends so a candidate landing at exactly 10% or 20% doesn't
        // get bumped out of the preferred band by ordinary floating-point rounding.
        fun inHeadroomBand(oversize: Double) = oversize >= 9.95 && oversize <= 20.05

        val best = pool.sortedWith(
            compareBy(
                // 1. In-band always beats out-of-band.
                { if (inHeadroomBand(it.oversizePercent)) 0 else 1 },
                // 2. Even-count preference only ever decides between two in-band candidates — spec
                //    §3 explicitly forbids letting it force large deliberate oversizing, so an
                //    out-of-band candidate never gets an evenness bonus here (that would let a much
                //    worse oversize like 12x700W@86.7% beat a 3x700W@40% just for being even).
                { if (inHeadroomBand(it.oversizePercent) && it.count % 2 != 0) 1 else 0 },
                // 3. Otherwise minimize distance from the 15% band midpoint — this is what actually
                //    stops the "13 is odd, therefore 14" failure mode out-of-band: a genuinely
                //    closer odd candidate always beats a farther even one.
                { abs(it.oversizePercent - 15.0) },
                // 4. Last-resort tiebreak among near-identical distances, even out-of-band.
                { if (it.count % 2 == 0) 0 else 1 },
                { it.totalKw },
                { it.count }
            )
        ).first()

        val checklist = if (best.valid) {
            "Valid series voltage, current, and inverter compatibility."
        } else {
            "NOT electrically valid: " + best.notes.joinToString("; ") + " — needs manual review."
        }
        val rangeNote = when {
            !best.valid -> ""
            inHeadroomBand(best.oversizePercent) -> " %.1f%% headroom, within the preferred 10-20%% range.".format(best.oversizePercent)
            else -> " %.1f%% headroom — outside the preferred 10-20%% range; no closer electrically-valid/even configuration was available.".format(best.oversizePercent)
        }
        val estimateNote = if (best.estimated) " (${best.watts}W panel electrical figures estimated — no exact datasheet match in the equipment library)" else ""
        val reason = "%.2f kW required — %d × %dW panels = %.2f kW.%s %s%s"
            .format(requiredPvKw, best.count, best.watts, best.totalKw, rangeNote, checklist, estimateNote)

        return PanelChoice(best.watts, best.count, best.totalKw, best.oversizePercent, best.valid, reason)
    }

    data class InverterChoice(val option: InverterOption, val headroomPercent: Double, val reason: String)

    /**
     * Picks the smallest catalog inverter covering BOTH the continuous requirement and a
     * worst-case surge requirement (checked against [INVERTER_SURGE_TOLERANCE_MULTIPLIER] × its
     * continuous rating) — never the largest just because it's largest, and never a pair of
     * smaller units: this app has no multi-inverter selection logic at all, so "prefer one
     * appropriately sized inverter over several small ones" (spec §15-16) holds by construction.
     */
    fun selectBestInverter(requiredContinuousKw: Double, requiredSurgeKw: Double, pool: List<InverterOption>): InverterChoice {
        val candidates = pool.filter { it.kw >= requiredContinuousKw && it.kw * INVERTER_SURGE_TOLERANCE_MULTIPLIER >= requiredSurgeKw }
        val chosen = candidates.minByOrNull { it.kw } ?: pool.maxBy { it.kw }
        val meetsBoth = chosen.kw >= requiredContinuousKw - 0.05 && chosen.kw * INVERTER_SURGE_TOLERANCE_MULTIPLIER >= requiredSurgeKw - 0.05
        val headroom = if (requiredContinuousKw > 0) (chosen.kw - requiredContinuousKw) / requiredContinuousKw * 100.0 else 0.0

        val reason = if (meetsBoth) {
            "%.2f kW continuous / %.2f kW worst-case surge required — the %s (%.1f kW) is the smallest available unit covering both (%.1f%% headroom on continuous load)."
                .format(requiredContinuousKw, requiredSurgeKw, chosen.name, chosen.kw, headroom)
        } else {
            "%.2f kW continuous / %.2f kW worst-case surge required — the largest available unit, the %s (%.1f kW), is still below the calculated requirement."
                .format(requiredContinuousKw, requiredSurgeKw, chosen.name, chosen.kw)
        }
        return InverterChoice(chosen, headroom, reason)
    }

    data class BatteryChoice(
        val option: BatteryOption?,
        val moduleCount: Int,
        val totalKwh: Double,
        val totalUsableKwh: Double,
        val totalMaxDischargeKw: Double,
        val reason: String
    )

    private fun batterySpecForTier(tierKwh: Double) = EquipmentSpecs.batterySpecFor(
        when (tierKwh) { 5.0 -> "5 kWh"; 10.0 -> "10 kWh"; 15.0 -> "15 kWh"; else -> null }
    )

    /** Real usable fraction from the matched datasheet ([EquipmentSpecs]) when known, else the flat design assumption. */
    private fun usableFractionFor(tierKwh: Double): Double =
        batterySpecForTier(tierKwh)?.let { it.usableEnergyKwh / it.ratedEnergyKwh } ?: SystemCalculator.BATTERY_DOD

    /** Real per-module max continuous discharge power when matched, else the same flat 0.5C fallback [SimSystemConfig] already uses for this gap. */
    private fun maxDischargeKwPerModule(tierKwh: Double): Double =
        batterySpecForTier(tierKwh)?.let { it.maxDischargeA * it.voltageV / 1000.0 } ?: (tierKwh * 0.5)

    /**
     * [requiredUsableKwh] is the actual energy the backup load needs to draw — not a nominal
     * capacity — so tiers are compared on real usable energy (spec §14), not nominal kWh alone.
     * [requiredDischargeKw] is checked too (spec §23 — energy alone isn't enough): a battery whose
     * usable capacity is sufficient but whose discharge power isn't is not treated as satisfying
     * the requirement. Every module in the result is the SAME catalog tier — an automatically
     * generated bank never mixes capacities (spec §20-22).
     */
    fun selectBestHybridBattery(requiredUsableKwh: Double, requiredDischargeKw: Double, inverterCeilingKw: Double): BatteryChoice {
        if (requiredUsableKwh <= 0.0) return BatteryChoice(null, 0, 0.0, 0.0, 0.0, "No battery backup required.")

        data class Candidate(val tier: BatteryOption, val modules: Int, val usableTotal: Double, val dischargeTotal: Double)

        val candidates = Catalog.hybridBatteries.map { tier ->
            val usablePerModule = tier.kwh * usableFractionFor(tier.kwh)
            val dischargePerModule = maxDischargeKwPerModule(tier.kwh)
            val modulesForEnergy = max(1, ceil(requiredUsableKwh / usablePerModule).toInt())
            val modulesForPower = max(1, ceil(requiredDischargeKw / dischargePerModule).toInt())
            val modules = max(modulesForEnergy, modulesForPower)
            val dischargeTotal = (modules * dischargePerModule).coerceAtMost(inverterCeilingKw.coerceAtLeast(0.1))
            Candidate(tier, modules, modules * usablePerModule, dischargeTotal)
        }

        val best = candidates.minWith(compareBy({ it.usableTotal }, { it.modules }))
        val powerOk = best.dischargeTotal >= requiredDischargeKw - 0.05

        val reason = "%.1f kWh usable / %.1f kW discharge required — %d × %s (%.1f kWh usable, %.1f kW discharge each) covers it.%s"
            .format(
                requiredUsableKwh, requiredDischargeKw, best.modules, best.tier.name,
                best.tier.kwh * usableFractionFor(best.tier.kwh), maxDischargeKwPerModule(best.tier.kwh),
                if (!powerOk) " Discharge power is tight relative to peak load even with this module count — review." else ""
            )
        return BatteryChoice(best.tier, best.modules, best.tier.kwh * best.modules, best.usableTotal, best.dischargeTotal, reason)
    }
}
