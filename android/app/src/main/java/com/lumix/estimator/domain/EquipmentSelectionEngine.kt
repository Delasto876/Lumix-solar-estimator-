package com.lumix.estimator.domain

import kotlin.math.ceil
import kotlin.math.max

/**
 * A49/A50/A63: the equipment-selection logic that makes LOAD-BASED (and GUIDED, which shares it —
 * see [SystemCalculator]) an actual design engine — not "smallest that exceeds the requirement,"
 * and not "round up to the next product." Given a calculated engineering requirement, these
 * functions search the real catalog this app actually sells and prices ([Catalog]), reject
 * anything that fails electrical validity, and return the smallest array that's still
 * electrically sound (A63 — no headroom-percentage target, no evenness bonus; see
 * [selectBestPanelConfigurationForLimits]'s own doc), and a single appropriately-sized inverter
 * over several small ones — explaining the pick in plain language a reviewer can read back.
 *
 * This object's own search is deliberately just the ELECTRICAL/catalog minimum for panels — it has
 * no simulation dependency and can't tell whether that minimum actually recharges a battery on
 * time. [SystemCalculator]'s own A63 recharge-aware refinement handles that on top, for hybrid
 * systems with a battery to charge.
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

    /**
     * A71: the real, binding MPPT voltage floor for a matched inverter — the HIGHER of its
     * continuous tracking-range floor ([InverterSpec.mpptVoltageMinV]) and its startup threshold
     * ([InverterSpec.startupVoltageV]), not the tracking floor alone. These are genuinely
     * different figures on some real models: every LuxPower
     * GEN-LB-US/LXP-LB-US entry in this catalog has a real startup voltage (140V) HIGHER than its
     * own continuous tracking floor (120V) — the unit needs the higher voltage just to wake its
     * MPPT algorithm up in the first place, even though it can track down to the lower figure once
     * already running. A string that only cleared the tracking floor would, on a real LuxPower
     * unit, never actually start producing. Falls back to [MIN_MPPT_OPERATING_VOLTAGE] when no
     * confirmed per-model tracking floor exists (the startup figure alone, without a confirmed
     * tracking floor to compare it against, isn't used as a floor by itself).
     */
    internal fun effectiveMpptFloorV(invSpec: InverterSpec?): Double {
        val trackingFloor = invSpec?.mpptVoltageMinV?.toDouble() ?: return MIN_MPPT_OPERATING_VOLTAGE
        val startupFloor = invSpec.startupVoltageV?.toDouble() ?: 0.0
        return max(trackingFloor, startupFloor)
    }

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

    /** Site Survey / Solar Mapping round: thin public wrapper around [panelElectricalFor] so [com.lumix.estimator.domain.commercial.ParallelInverterSizer] can plan MPPT string splits (via [MpptStringPlanner]) using the same real-or-estimated Vmp this engine's own residential search already relies on, without duplicating the estimation logic. */
    internal fun estimatedOrRealVmpV(watts: Int): Double = panelElectricalFor(watts).vmpV

    data class PanelChoice(
        val panelWatts: Int,
        val panelCount: Int,
        val totalPvKw: Double,
        val oversizePercent: Double,
        val electricallyValid: Boolean,
        val reason: String,
        /** A62: panel counts per string, longest-first, as actually chosen — see [MpptStringPlanner]. Empty when [panelCount] is 0. */
        val stringCounts: List<Int> = emptyList(),
        /** A62/A63: whether the chosen array's longest string clears the preferred 5% MPPT voltage design margin, not just the hard ceiling — see [PanelCompatibilityResult.withinPreferredVoltageMargin]. */
        val withinPreferredVoltageMargin: Boolean = true
    )

    private data class PanelCandidate(
        val watts: Int, val count: Int, val totalKw: Double, val oversizePercent: Double,
        val valid: Boolean, val notes: List<String>, val estimated: Boolean,
        val stringCounts: List<Int> = emptyList(), val withinPreferredMargin: Boolean = true
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
        val estimated: Boolean,
        /** A62: panel counts per string, longest-first, as actually chosen by [MpptStringPlanner] — may use fewer than [mpptTrackers] strings (see that object's own doc). Sums to the candidate's total panel count. */
        val stringCounts: List<Int> = emptyList(),
        /** A65 (spec's own "15% VOLTAGE OPERATING MARGIN... 380 x 0.95 = 361V" worked example): true when the longest string's cold-corrected Voc stays within [PREFERRED_VOC_MARGIN_FRACTION] of [mpptVoltageMaxV], not just under the hard ceiling. A softer, preferred signal — [vocOk] alone still decides electrical validity. */
        val withinPreferredVoltageMargin: Boolean = true,
        /**
         * A71 (spec Phase 6 — "fix inverter/MPPT/string calculations"): true when the longest
         * string's (uncorrected) operating voltage stays at or under the inverter's real MPPT
         * *tracking-range* ceiling — a genuinely different, lower figure than [mpptVoltageMaxV]
         * (the absolute PV input voltage the DC stage can safely tolerate, [vocOk]'s own ceiling).
         * A string can pass [vocOk] yet still run its operating point outside the range the
         * tracker actually sweeps for MPPT — always true (no check) when the matched inverter
         * spec has no confirmed per-model MPPT-range-max figure. See [evaluateCandidate]'s own doc
         * for why this is checked separately from [vmpOk] (the range's *floor*).
         */
        val vmpUpperOk: Boolean = true,
        /**
         * A71: true when the string's real operating current (Imp) stays at or under the
         * inverter's real per-tracker *continuous* max input current — a genuinely different,
         * datasheet figure from the max PV *short-circuit* current [iscOk] checks, not the same
         * number doing both jobs. Always true (no check) when the matched inverter spec has no
         * confirmed per-model continuous-current figure (falls back to the same derived
         * power/voltage approximation [iscOk] uses in that case, so nothing regresses to
         * "unchecked").
         */
        val impOk: Boolean = true
    ) {
        val valid: Boolean get() = powerOk && vocOk && vmpOk && vmpUpperOk && iscOk && impOk
        /** How many of [mpptTrackers] the chosen [stringCounts] actually populate. */
        val trackersUsed: Int get() = stringCounts.size
    }

    /**
     * A65 (explicit installer decision, 2026-08-15): the "how tight can a string design run
     * against the inverter's max PV voltage" fraction changed twice now — A62 read a prior spec's
     * own "10% design margin... 380 x 0.85 = 323V" text and followed the arithmetic (0.85, an
     * actual 15% reduction) over its "10%" label. This round's spec instead gives "15% VOLTAGE
     * OPERATING MARGIN... 380 x 0.95 = 361V" as its own worked example — asked directly which
     * fraction to use, the installer chose to follow THIS round's arithmetic, 0.95. Flagged
     * honestly rather than silently: 0.95 is actually only a 5% reduction from the ceiling, not
     * 15% — the "15%" label in both this round's and the prior round's spec text has never
     * actually matched its own worked arithmetic. Implemented as instructed (0.95) regardless;
     * this is the installer's explicit, informed choice to make, not this engine's to second-guess.
     */
    private const val PREFERRED_VOC_MARGIN_FRACTION = 0.95

    /**
     * Core evaluation against explicit limits — shared by the search in
     * [selectBestPanelConfigurationForLimits] and standalone validation via
     * [checkPanelInverterCompatibilityForLimits].
     *
     * A71 (spec Phase 6 — "fix inverter/MPPT/string calculations"): [mpptTrackingMinV]/
     * [mpptTrackingMaxV]/[maxContinuousCurrentPerMpptA]/[maxShortCircuitCurrentPerMpptA] are the
     * real per-model MPPT electrical figures [EquipmentSpecs.InverterSpec] already carries for
     * every LuxPower/Deye/Growatt entry — [mpptVoltageMinV]/[mpptVoltageMaxV]/
     * [maxInputCurrentPerMpptA]/[maxShortCircuitCurrentPerMpptA] — but which this function used to
     * ignore entirely, substituting a single flat [MIN_MPPT_OPERATING_VOLTAGE] constant for every
     * inverter's Vmp floor (too permissive for Deye's real 150V/Growatt's real 150V, too strict for
     * SRNE's real 80V) and deriving BOTH the Isc-limit AND the (previously unchecked) Imp-limit
     * from the same crude `maxPvW / maxPvV` ratio — a proxy for the array's continuous current
     * capability, not the real datasheet current rating at all, and not the same figure as the
     * inverter's real max PV *short-circuit* current either (a genuinely different, higher number
     * on every model that discloses both — see each [EquipmentSpecs.InverterSpec] entry). All four
     * parameters default to the old behavior (flat floor, no ceiling check, derived-ratio current)
     * so a caller with no confirmed per-model spec — or an explicit-limits test — keeps working
     * exactly as before.
     */
    private fun evaluateCandidate(
        watts: Int, count: Int, maxPvW: Double, maxPvV: Double, mpptTrackers: Int,
        mpptTrackingMinV: Double = MIN_MPPT_OPERATING_VOLTAGE,
        mpptTrackingMaxV: Double? = null,
        maxContinuousCurrentPerMpptA: Double? = null,
        maxShortCircuitCurrentPerMpptA: Double? = null
    ): PanelCompatibilityResult {
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
        // A62: real per-string topology (may use fewer than mpptTrackers strings — see
        // MpptStringPlanner's own doc for when and why), replacing the old always-split-across-
        // every-tracker assumption. A71: uses the real per-model MPPT floor (mpptTrackingMinV),
        // not a flat constant — see this function's own doc.
        val stringCounts = MpptStringPlanner.planStrings(count, mpptTrackers, elec.vmpV, mpptTrackingMinV)
        val longestStringPanels = stringCounts.max()
        val shortestStringPanels = stringCounts.min()
        val correctedVoc = elec.vocV * longestStringPanels * COLD_TEMP_VOC_CORRECTION
        val longestStringVmp = elec.vmpV * longestStringPanels
        val shortestStringVmp = elec.vmpV * shortestStringPanels
        // Fallback approximation for whichever real current figure wasn't matched — the same
        // "no exact datasheet, so infer from power/voltage" reasoning this function has always
        // used, now scoped explicitly to the case that actually needs it rather than applied
        // unconditionally to both current checks.
        val impliedMaxCurrentA = maxPvW / maxPvV
        val continuousCurrentLimitA = maxContinuousCurrentPerMpptA ?: impliedMaxCurrentA
        val shortCircuitCurrentLimitA = maxShortCircuitCurrentPerMpptA ?: impliedMaxCurrentA

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
        val withinPreferredVoltageMargin = correctedVoc <= maxPvV * PREFERRED_VOC_MARGIN_FRACTION
        if (vocOk && !withinPreferredVoltageMargin) {
            notes += "longest MPPT string Voc %.0fV (%d panels, cold-corrected) is within the inverter's hard limit but outside the preferred 5%% design margin (target <= %.0fV)"
                .format(correctedVoc, longestStringPanels, maxPvV * PREFERRED_VOC_MARGIN_FRACTION)
        }
        val vmpOk = shortestStringVmp >= mpptTrackingMinV
        if (!vmpOk) {
            notes += "shortest MPPT string Vmp %.0fV (%d panels) is below the inverter's minimum MPPT operating voltage %.0fV"
                .format(shortestStringVmp, shortestStringPanels, mpptTrackingMinV)
        }
        // A71: genuinely new check — the longest string's operating voltage against the real MPPT
        // *tracking-range* ceiling, a lower figure than maxPvV (vocOk's own ceiling, the absolute
        // PV input the DC stage tolerates). Only checked when a confirmed per-model figure exists.
        val vmpUpperOk = mpptTrackingMaxV == null || longestStringVmp <= mpptTrackingMaxV
        if (!vmpUpperOk && mpptTrackingMaxV != null) {
            notes += "longest MPPT string Vmp %.0fV (%d panels) exceeds the inverter's real MPPT tracking-range ceiling %.0fV (separate from, and lower than, its %.0fV absolute max PV voltage)"
                .format(longestStringVmp, longestStringPanels, mpptTrackingMaxV, maxPvV)
        }
        val iscOk = elec.iscA <= shortCircuitCurrentLimitA + 0.05
        if (!iscOk) {
            val basis = if (maxShortCircuitCurrentPerMpptA != null) "max PV short-circuit current" else "implied max PV current"
            notes += "panel Isc %.1fA exceeds the inverter's %s per tracker %.1fA".format(elec.iscA, basis, shortCircuitCurrentLimitA)
        }
        // A71: genuinely new check — the string's real *operating* current (Imp) against the
        // inverter's real continuous max input current per tracker, a separate (and typically
        // lower) datasheet figure from the short-circuit rating iscOk checks above. Falls back to
        // the same implied ratio as iscOk when no confirmed per-model figure exists, so this never
        // regresses a design that was passing before this check existed.
        val impOk = elec.impA <= continuousCurrentLimitA + 0.05
        if (!impOk) {
            val basis = if (maxContinuousCurrentPerMpptA != null) "max continuous PV input current" else "implied max PV current"
            notes += "panel Imp %.1fA exceeds the inverter's %s per tracker %.1fA".format(elec.impA, basis, continuousCurrentLimitA)
        }
        if (stringCounts.size < mpptTrackers) {
            notes += "using %d of %d available MPPT trackers (%s panels) — splitting across all %d would undervolt the shortest string"
                .format(stringCounts.size, mpptTrackers, stringCounts.joinToString("+"), mpptTrackers)
        }

        return PanelCompatibilityResult(
            arrayKw = totalKw, requiredMaxPvKw = maxPvW / 1000.0,
            stringVocV = correctedVoc, stringVmpV = longestStringVmp,
            stringImpA = elec.impA, stringIscA = elec.iscA,
            mpptVoltageMaxV = maxPvV, mpptTrackers = mpptTrackers,
            powerOk = powerOk, vocOk = vocOk, vmpOk = vmpOk, iscOk = iscOk,
            notes = notes, estimated = elec.estimated,
            stringCounts = stringCounts, withinPreferredVoltageMargin = withinPreferredVoltageMargin,
            vmpUpperOk = vmpUpperOk, impOk = impOk
        )
    }

    /** Explicit-limits entry point — mirrors [selectBestPanelConfigurationForLimits]'s own split for deterministic unit testing. */
    internal fun checkPanelInverterCompatibilityForLimits(
        panelWatts: Int, panelCount: Int, maxPvW: Double, maxPvV: Double, mpptTrackers: Int,
        mpptTrackingMinV: Double = MIN_MPPT_OPERATING_VOLTAGE,
        mpptTrackingMaxV: Double? = null,
        maxContinuousCurrentPerMpptA: Double? = null,
        maxShortCircuitCurrentPerMpptA: Double? = null
    ): PanelCompatibilityResult = evaluateCandidate(
        panelWatts, panelCount, maxPvW, maxPvV, mpptTrackers,
        mpptTrackingMinV, mpptTrackingMaxV, maxContinuousCurrentPerMpptA, maxShortCircuitCurrentPerMpptA
    )

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
        // A71: the real per-model MPPT range/current figures, when this inverter has a confirmed
        // spec match — see evaluateCandidate's own doc for why these are checked separately from
        // maxPvV/the derived current ratio, and effectiveMpptFloorV's own doc for why the floor
        // itself is the higher of two genuinely different real figures, not mpptVoltageMinV alone.
        val mpptTrackingMinV = effectiveMpptFloorV(invSpec)
        val mpptTrackingMaxV = invSpec?.mpptVoltageMaxV?.toDouble()
        val maxContinuousCurrentPerMpptA = invSpec?.maxInputCurrentPerMpptA
        val maxShortCircuitCurrentPerMpptA = invSpec?.maxShortCircuitCurrentPerMpptA
        return evaluateCandidate(
            panelWatts, panelCount, maxPvW, maxPvV, mpptTrackers,
            mpptTrackingMinV, mpptTrackingMaxV, maxContinuousCurrentPerMpptA, maxShortCircuitCurrentPerMpptA
        )
    }

    /**
     * Evaluates real candidate configurations across every panel wattage this catalog sells —
     * several counts per wattage, not just "round up." Panels are split across the chosen
     * inverter's own real MPPT-tracker count ([EquipmentSpecs.InverterSpec.mpptCount], defaulting
     * to 2 when unmatched) via [MpptStringPlanner] (A62, spec §24-28) — as evenly as possible
     * across however many trackers actually get used, preferring full tracker utilization but
     * allowing an uneven split, or even leaving a tracker unused, when that's what keeps every
     * string's voltage inside the inverter's valid range (see that object's own doc) — with each
     * tracker's string checked independently as ONE series string (per spec §5's no-parallel-
     * strings-within-one-input rule). Using the inverter's own built-in tracker inputs this way
     * isn't the ad-hoc parallel-string wiring the spec is warning against; it's how these
     * inverters are designed to be used, and it's what keeps this check from flagging completely
     * ordinary residential arrays as invalid just because a real inverter's real max-PV-voltage
     * (500-600V in this library) can't fit an entire double-digit panel count in one string.
     * Rejects any candidate whose worst (longest) tracker string's cold-corrected Voc or Isc, or
     * the array's total power, exceeds the inverter's matched real limits (or a conservative
     * fallback where no exact spec match exists), then returns the smallest array that's still
     * electrically sound — preferring one that also clears the preferred 5% MPPT voltage design
     * margin (A62, fraction updated by A65) — never the largest or smallest available option
     * "because it's available," and (A63) no longer scored toward any headroom-percentage target.
     * Electrical validity always
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
        // A71: same real per-model MPPT figures as checkPanelInverterCompatibility — see that
        // function's own doc.
        val mpptTrackingMinV = effectiveMpptFloorV(invSpec)
        val mpptTrackingMaxV = invSpec?.mpptVoltageMaxV?.toDouble()
        val maxContinuousCurrentPerMpptA = invSpec?.maxInputCurrentPerMpptA
        val maxShortCircuitCurrentPerMpptA = invSpec?.maxShortCircuitCurrentPerMpptA
        return selectBestPanelConfigurationForLimits(
            requiredPvKw, maxPvW, maxPvV, mpptTrackers, wattages,
            mpptTrackingMinV, mpptTrackingMaxV, maxContinuousCurrentPerMpptA, maxShortCircuitCurrentPerMpptA
        )
    }

    /** How many counts past the theoretical minimum to search per wattage before giving up and
     * surfacing the least-bad (still-invalid) candidate — generous, since this is pure arithmetic,
     * not a simulation; real catalog wattages validate within a handful of counts in practice. */
    private const val MAX_SEARCH_STEPS_PAST_MINIMUM = 20

    /**
     * The actual candidate-evaluation/scoring core, taking explicit electrical limits rather than
     * resolving them from a catalog inverter kW — `internal` so this module's own JVM unit tests
     * (`EquipmentSelectionEngineTest`) can exercise precise, deterministic Voc/Vmp/Isc/power
     * scenarios without depending on which real datasheets happen to be in [EquipmentSpecs] today.
     * [selectBestPanelConfiguration] is the real entry point every caller outside tests should use.
     *
     * A63 (spec §24-28's own ranking — "Do NOT rank simply by lowest panel count," but also
     * explicitly "SMALLEST PRACTICAL ARRAY," not a headroom target): this used to score toward a
     * preferred 10-20% headroom band with an even-panel-count tiebreak — a heuristic from before
     * this spec existed. That's gone. This now returns the smallest electrically-valid array
     * (preferring one that also clears the preferred 5% MPPT voltage margin, per priority 2 in the
     * spec's own ranking) — literally "smallest practical array," not a target percentage. It is
     * deliberately still just the ELECTRICAL minimum: the caller ([SystemCalculator]) is
     * responsible for the spec's remaining ranking criteria this function has no context for —
     * whether that minimum can actually recharge the battery to target SOC and support daytime
     * load, which needs a real day simulation this pure catalog/electrical search doesn't run (see
     * [SystemCalculator]'s own A63 recharge-aware refinement, which may add 1-2 panels on top of
     * whatever this function returns).
     */
    internal fun selectBestPanelConfigurationForLimits(
        requiredPvKw: Double,
        maxPvW: Double,
        maxPvV: Double,
        mpptTrackers: Int,
        wattages: List<Int> = Catalog.panelWattages,
        mpptTrackingMinV: Double = MIN_MPPT_OPERATING_VOLTAGE,
        mpptTrackingMaxV: Double? = null,
        maxContinuousCurrentPerMpptA: Double? = null,
        maxShortCircuitCurrentPerMpptA: Double? = null
    ): PanelChoice {
        if (requiredPvKw <= 0.0) {
            return PanelChoice(wattages.first(), 0, 0.0, 0.0, true, "No PV capacity required.")
        }

        fun evaluate(watts: Int, count: Int): PanelCandidate {
            val result = evaluateCandidate(
                watts, count, maxPvW, maxPvV, mpptTrackers,
                mpptTrackingMinV, mpptTrackingMaxV, maxContinuousCurrentPerMpptA, maxShortCircuitCurrentPerMpptA
            )
            val oversize = (result.arrayKw - requiredPvKw) / requiredPvKw * 100.0
            return PanelCandidate(
                watts, count, result.arrayKw, oversize, result.valid, result.notes, result.estimated,
                result.stringCounts, result.withinPreferredVoltageMargin
            )
        }

        val allCandidates = wattages.flatMap { w ->
            val baseCount = max(1, ceil((requiredPvKw * 1000.0) / w).toInt())
            (baseCount..(baseCount + MAX_SEARCH_STEPS_PAST_MINIMUM)).map { n -> evaluate(w, n) }
        }

        val valid = allCandidates.filter { it.valid }
        // Nothing validated across the whole search window (shouldn't normally happen) — surface
        // the least-bad candidate flagged invalid rather than silently returning zero panels.
        val pool = valid.ifEmpty { allCandidates }

        val best = pool.sortedWith(
            compareBy(
                // 1. A62/A63 (spec §24-28 ranking, priorities 1-2): electrical validity is already
                //    guaranteed by the pool filter above whenever any valid candidate exists; among
                //    those, one whose longest string clears the preferred 5% MPPT voltage design
                //    margin (not just the hard ceiling) is preferred.
                { if (it.withinPreferredMargin) 0 else 1 },
                // 2. A63 (spec priority 9, "MINIMAL OVERSIZING" — and the message's own explicit
                //    "SMALLEST PRACTICAL ARRAY... NOT always use the maximum number of panels"):
                //    smallest resulting array wins. Replaces the old 10-20% headroom-band target
                //    and even-panel-count preference entirely — this function no longer aims for a
                //    percentage, it aims for the minimum that's still electrically sound.
                { it.totalKw },
                // 3. Deterministic tiebreak for the (rare) case two wattages land on the same kW.
                { it.count }
            )
        ).first()

        val checklist = if (best.valid) {
            "Valid series voltage, current, and inverter compatibility."
        } else {
            "NOT electrically valid: " + best.notes.joinToString("; ") + " — needs manual review."
        }
        val oversizeNote = if (best.valid) " %.1f%% over the calculated requirement.".format(best.oversizePercent) else ""
        val estimateNote = if (best.estimated) " (${best.watts}W panel electrical figures estimated — no exact datasheet match in the equipment library)" else ""
        val marginNote = if (best.valid && !best.withinPreferredMargin) " Longest string is inside the inverter's hard voltage limit but outside the preferred 5% design margin." else ""
        val stringNote = if (best.stringCounts.size > 1) {
            " MPPT strings: " + best.stringCounts.mapIndexed { i, n -> "String ${i + 1} = $n panels" }.joinToString(", ") + "."
        } else if (best.stringCounts.size == 1) {
            " Single MPPT string (${best.stringCounts[0]} panels)."
        } else ""
        val reason = "%.2f kW required — smallest electrically valid array: %d × %dW panels = %.2f kW.%s %s%s%s%s"
            .format(requiredPvKw, best.count, best.watts, best.totalKw, oversizeNote, checklist, marginNote, stringNote, estimateNote)

        return PanelChoice(best.watts, best.count, best.totalKw, best.oversizePercent, best.valid, reason, best.stringCounts, best.withinPreferredMargin)
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
        val reason: String,
        /** A64 (spec §7 — "the system must flag BATTERY POWER LIMIT"): true when [totalMaxDischargeKw] covers the requested discharge power, not just [totalUsableKwh] covering the energy requirement — a bank can have enough energy but still not enough power. */
        val powerOk: Boolean = true
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
     * Phase 38 ("do not parallel more than 2 5kwh battery and do not parallel more than 2 10kwh
     * battery... if the backup required is over 20kwh instead of paralleling 3 10kwh battery use 2
     * 15kwh"): real-world parallel limits per catalog tier — installers/manufacturers commonly cap
     * how many smaller modules can share one battery bus. Keyed by nominal kWh (matches
     * [BatteryOption.kwh]); a tier absent from this map (only the 15 kWh tier today) has no imposed
     * cap — the user's own examples only ever escalate INTO the 15 kWh tier, never past it.
     */
    private val MAX_PARALLEL_MODULES_PER_TIER: Map<Double, Int> = mapOf(5.0 to 2, 10.0 to 2)

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

        data class Candidate(val tier: BatteryOption, val modules: Int, val usableTotal: Double, val dischargeTotal: Double, val exceedsParallelCap: Boolean)

        val candidates = Catalog.hybridBatteries.map { tier ->
            val usablePerModule = tier.kwh * usableFractionFor(tier.kwh)
            val dischargePerModule = maxDischargeKwPerModule(tier.kwh)
            val modulesForEnergy = max(1, ceil(requiredUsableKwh / usablePerModule).toInt())
            val modulesForPower = max(1, ceil(requiredDischargeKw / dischargePerModule).toInt())
            val modules = max(modulesForEnergy, modulesForPower)
            val dischargeTotal = (modules * dischargePerModule).coerceAtMost(inverterCeilingKw.coerceAtLeast(0.1))
            val cap = MAX_PARALLEL_MODULES_PER_TIER[tier.kwh]
            Candidate(tier, modules, modules * usablePerModule, dischargeTotal, cap != null && modules > cap)
        }

        // Phase 38: a tier needing more parallel modules than its real-world limit allows is
        // excluded in favor of a larger tier — e.g. 13 kWh usable required no longer proposes 3x
        // 5 kWh in parallel when a single 15 kWh module already covers it alone, and 22 kWh usable
        // no longer proposes 3x 10 kWh when 2x 15 kWh covers it instead. Falls back to the
        // unfiltered pool only if every tier is capped out (not possible with today's catalog —
        // the largest tier, 15 kWh, carries no cap — kept as a defensive floor only).
        val withinCap = candidates.filterNot { it.exceedsParallelCap }
        val pool = withinCap.ifEmpty { candidates }

        val best = pool.minWith(compareBy({ it.usableTotal }, { it.modules }))
        val powerOk = best.dischargeTotal >= requiredDischargeKw - 0.05

        val reason = "%.1f kWh usable / %.1f kW discharge required — %d × %s (%.1f kWh usable, %.1f kW discharge each) covers it.%s"
            .format(
                requiredUsableKwh, requiredDischargeKw, best.modules, best.tier.name,
                best.tier.kwh * usableFractionFor(best.tier.kwh), maxDischargeKwPerModule(best.tier.kwh),
                if (!powerOk) " Discharge power is tight relative to peak load even with this module count — review." else ""
            )
        return BatteryChoice(best.tier, best.modules, best.tier.kwh * best.modules, best.usableTotal, best.dischargeTotal, reason, powerOk)
    }

    /**
     * A72 (spec Phase 7 — "fix battery calculations"): real per-model battery voltage windows
     * ([EquipmentSpecs.BatterySpecSheet.minVoltageV]/`maxVoltageV`) and real per-model inverter
     * battery-port voltage windows ([EquipmentSpecs.InverterSpec.batteryVoltageMinV]/
     * `batteryVoltageMaxV`) both already exist in the equipment database — the same "real data sat
     * unused" pattern A71 found for MPPT voltage — but nothing ever cross-checked them: a battery
     * whose voltage swing (nominal ± charge/discharge) doesn't fully fit inside the inverter's
     * accepted battery-port range would never actually have been flagged. Every real combination in
     * this catalog today happens to be compatible (every SRNE tier's 44.8-58.4V window sits inside
     * every real inverter's 40-60V battery range), so this never actually fires yet — added anyway,
     * the same reasoning A71's Vmp-upper-bound/Imp checks used: a real check that currently always
     * passes still catches the next equipment addition that doesn't.
     */
    data class BatteryVoltageCompatibilityResult(
        val ok: Boolean,
        val batteryMinV: Double?,
        val batteryMaxV: Double?,
        val inverterMinV: Double?,
        val inverterMaxV: Double?
    )

    /** Explicit-limits core, split out for deterministic unit testing — see [checkBatteryVoltageCompatibility]'s own doc. Compatible (true) whenever either side has no confirmed voltage-window data, since there is nothing to contradict. */
    internal fun batteryVoltageCompatibleForLimits(
        batteryMinV: Double?, batteryMaxV: Double?, inverterMinV: Double?, inverterMaxV: Double?
    ): Boolean {
        if (batteryMinV == null || batteryMaxV == null || inverterMinV == null || inverterMaxV == null) return true
        return batteryMinV >= inverterMinV && batteryMaxV <= inverterMaxV
    }

    /** Real entry point: resolves both real specs by name/kW and checks the battery's full voltage window fits inside the inverter's accepted battery-port range. */
    fun checkBatteryVoltageCompatibility(batteryName: String?, inverterKw: Double, inverterNameHint: String? = null): BatteryVoltageCompatibilityResult {
        val batterySpec = EquipmentSpecs.batterySpecFor(batteryName)
        val invSpec = EquipmentSpecs.inverterSpecFor(inverterKw, inverterNameHint)
        val ok = batteryVoltageCompatibleForLimits(
            batterySpec?.minVoltageV, batterySpec?.maxVoltageV, invSpec?.batteryVoltageMinV, invSpec?.batteryVoltageMaxV
        )
        return BatteryVoltageCompatibilityResult(ok, batterySpec?.minVoltageV, batterySpec?.maxVoltageV, invSpec?.batteryVoltageMinV, invSpec?.batteryVoltageMaxV)
    }
}
