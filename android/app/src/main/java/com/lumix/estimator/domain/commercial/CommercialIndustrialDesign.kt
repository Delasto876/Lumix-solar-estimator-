package com.lumix.estimator.domain.commercial

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * Phase 43 (spec §16 — "add 'Electrical Service' with options: 1. 120V single-phase; 2. 220/240V
 * single-phase; 3. 120/240V split-phase; 4. 120/208V three-phase; 5. 220/380V three-phase; 6.
 * 230/400V three-phase; 7. Custom"): a quick-fill shortcut for [ElectricalService.phase]/
 * [ElectricalService.nominalVoltage]/[ElectricalService.lineToNeutralVoltage] — picking one of the
 * six named presets seeds those three fields with the spec's own figures, but every field stays a
 * plain editable value afterward, never locked to the preset. [CUSTOM] is also the default for a
 * fresh [ElectricalService] — per §16's own "Do NOT automatically assume every commercial or
 * industrial building uses split phase," a new design starts unconfirmed, not silently pinned to
 * any one of the six real presets.
 */
@Serializable
enum class ElectricalServicePreset(
    val label: String,
    val presetPhase: LoadPhaseType?,
    val presetNominalVoltage: Double?,
    val presetLineToNeutralVoltage: Double?
) {
    V120_SINGLE_PHASE("120 V single-phase", LoadPhaseType.SINGLE_PHASE, 120.0, null),
    V220_240_SINGLE_PHASE("220/240 V single-phase", LoadPhaseType.SINGLE_PHASE, 240.0, null),
    V120_240_SPLIT_PHASE("120/240 V split-phase", LoadPhaseType.SPLIT_PHASE, 240.0, 120.0),
    V120_208_THREE_PHASE("120/208 V three-phase", LoadPhaseType.THREE_PHASE, 208.0, 120.0),
    V220_380_THREE_PHASE("220/380 V three-phase", LoadPhaseType.THREE_PHASE, 380.0, 220.0),
    V230_400_THREE_PHASE("230/400 V three-phase", LoadPhaseType.THREE_PHASE, 400.0, 230.0),
    CUSTOM("Custom", null, null, null)
}

/**
 * Phase 27 §14 ("Single phase / split phase / three phase, nominal voltage, frequency, existing
 * utility service capacity, main breaker/service rating... Do not assume residential 120/240 V
 * split phase for commercial/industrial projects"): the site's utility service, as distinct from
 * any individual [LoadInstance]'s own [LoadInstance.phase]/[LoadInstance.voltage] (a building's
 * service can be three-phase while individual branch circuits are single-phase).
 */
@Serializable
data class ElectricalService(
    /** Phase 43 (spec §16): which named preset, if any, seeded the fields below — [ElectricalServicePreset.CUSTOM] whenever the installer typed their own values directly (including every already-saved quote from before this field existed). Purely a UI/provenance hint; nothing downstream reads it except for the §19 "requires installer verification" guidance below. */
    val preset: ElectricalServicePreset = ElectricalServicePreset.CUSTOM,
    val phase: LoadPhaseType = LoadPhaseType.THREE_PHASE,
    /** For THREE_PHASE, the line-to-line voltage (the spec's own "V(line-line)" in §17's formulas); for SINGLE_PHASE/SPLIT_PHASE, the service's own voltage. */
    val nominalVoltage: Double = 240.0,
    /** Phase 43 (spec §17 — "Line-to-neutral voltage where applicable"): only meaningful for THREE_PHASE/SPLIT_PHASE services; null for a single-phase service with no separate neutral-referenced voltage. */
    val lineToNeutralVoltage: Double? = null,
    /** Phase 43 (spec §15 — "DEFAULT FREQUENCY = 50 Hz. This is the default for Lumix"): was 60.0 before this phase — Jamaica's grid is 50Hz, so the old default didn't even match this app's own single real market. Installer-changeable, per §15's own "Allow the installer to change it if required." */
    val frequencyHz: Double = 50.0,
    /** Amps, if known/confirmed — null means "not yet entered," never assumed. */
    val utilityServiceCapacityAmps: Double? = null,
    val mainBreakerRatingAmps: Double? = null
) {
    /**
     * Phase 43 (spec §17's own formulas, inverted to solve for current — "kW = √3 x V(line-line) x
     * I x PF / 1000; kVA = √3 x V(line-line) x I / 1000" for three-phase, "kW = V x I x PF / 1000"
     * for single phase): total current the service must carry, from the design's own apparent power
     * (which already folds in PF — see [ElectricalPower.apparentPowerKva]). SPLIT_PHASE uses the
     * same single-voltage formula as SINGLE_PHASE — the spec's own formula list names only "single
     * phase" and "three-phase," and a balanced split-phase (e.g. 240V) load is electrically
     * equivalent to a single-phase load at that same voltage for this aggregate calculation. Null
     * when [nominalVoltage] isn't set to a positive figure yet (nothing to divide by).
     */
    fun totalCurrentAmps(designApparentPowerKva: Double): Double? {
        if (nominalVoltage <= 0.0) return null
        val denominatorVoltage = if (phase == LoadPhaseType.THREE_PHASE) SQRT_3 * nominalVoltage else nominalVoltage
        return (designApparentPowerKva * 1000.0) / denominatorVoltage
    }

    private companion object {
        val SQRT_3 = sqrt(3.0)
    }
}

/**
 * Phase 27 §5 ("configurable Diversity/Simultaneous-Use Factor... Do not silently apply a
 * residential assumption"): the spec's own preset list plus a custom fraction. [fraction] is what
 * every calculation actually reads — [PERCENT_100]..[PERCENT_50] resolve directly, [CUSTOM] reads
 * [DiversityFactor.customFraction] instead.
 */
@Serializable
enum class DiversityFactorPreset(val fraction: Double?) {
    PERCENT_100(1.0),
    PERCENT_90(0.9),
    PERCENT_80(0.8),
    PERCENT_70(0.7),
    PERCENT_60(0.6),
    PERCENT_50(0.5),
    CUSTOM(null)
}

@Serializable
data class DiversityFactor(
    /**
     * Phase 34 ("diversity should be defaulted at 60 percent but a pass should be able to handle
     * up to 85 to 100 percent of the load if all is running at once"): a fresh design starts at a
     * realistic assumption — not everything runs at once — rather than the previous PERCENT_100
     * default, which silently sized as if it did. The full 0-100% range (including 85-100% for a
     * site that genuinely does run everything simultaneously) stays fully reachable via the
     * slider/[CUSTOM] — this only changes the untouched starting point, never a ceiling.
     * [CommercialIndustrialCalculator]'s own "not confirmed" warning triggers off this same default.
     */
    val preset: DiversityFactorPreset = DiversityFactorPreset.PERCENT_60,
    val customFraction: Double = 1.0
) {
    val fraction: Double get() = (preset.fraction ?: customFraction).coerceIn(0.0, 1.0)
}

/**
 * Phase 27 §13/§19 ("Do not create separate disconnected sizing engines for residential, commercial
 * and industrial. Use one SystemDesign architecture... extend it with: electricalService, phase,
 * nominalVoltage, powerFactor, diversityFactor, connectedLoad, designLoad..."): the single bundle
 * [com.lumix.estimator.domain.QuoteInputs.commercialIndustrialDesign] holds all of Phase 27's new
 * commercial/industrial-only data, rather than 15+ new flat fields directly on [com.lumix.estimator
 * .domain.QuoteInputs] — "Residential should simply use fewer fields where they are unnecessary" is
 * satisfied by this being entirely absent (null) for a RESIDENTIAL quote, not by empty fields sitting
 * unused on every quote regardless of [com.lumix.estimator.domain.SystemType].
 *
 * §7/§8/§11's parallel-inverter, PV string/MPPT, and battery-per-inverter data are added here as
 * further nullable fields once those models exist (see [com.lumix.estimator.domain.commercial]
 * package doc / the Phase 27 task list) — additive, same pattern as every other field on this class.
 */
@Serializable
data class CommercialIndustrialDesign(
    val electricalService: ElectricalService = ElectricalService(),
    /**
     * Phase 42 (spec §1 — "immediately ask: What type of facility is this?"): defaults to
     * "not yet chosen" ([FacilitySelection.isChosen] false) rather than any specific preset — see
     * [FacilitySelection]'s own doc. Drives the default load library added in a later phase of this
     * same update; never overrides an installer's own edits to [loads] once loaded.
     */
    val facility: FacilitySelection = FacilitySelection(),
    val loads: List<LoadInstance> = emptyList(),
    val diversityFactor: DiversityFactor = DiversityFactor(),
    /** Phase 27 §7-§10: null until the installer has picked an inverter model and PV configuration in the manual design flow — see [ParallelInverterDesign]'s own doc. */
    val parallelInverterDesign: ParallelInverterDesign? = null,
    /** Phase 27 §11-§12: null until the installer has allocated batteries per inverter unit — see [BatteryPerInverterDesign]'s own doc. */
    val batteryPerInverterDesign: BatteryPerInverterDesign? = null,
    /**
     * Phase 28 (Commercial default schedule model — "User must be able to edit business opening/
     * closing days and hours"): the site's own operating hours, defaulting to the spec's own
     * worked example (M-F 7am-6pm, Sat 8am-1pm, Sun closed). Only meaningful for COMMERCIAL — see
     * [BusinessHours]'s own doc. INDUSTRIAL ignores this entirely in favor of its own manual
     * shift-based schedule (§13 "Industrial = MANUAL MODE ONLY... no assumed working hours") —
     * kept here rather than made nullable/industrial-specific so the same [CommercialIndustrialDesign]
     * shape still works for both system types without a second, parallel design class.
     */
    val businessHours: BusinessHours = BusinessHours(),
    /**
     * Phase 28 §1 (Industrial — "DO NOT create assumed industrial working hours"): fully
     * unconfigured by default — see [IndustrialShiftSchedule]'s own doc for why this, unlike
     * [businessHours], ships with no default times at all. Only meaningful for INDUSTRIAL;
     * COMMERCIAL uses [businessHours] instead.
     */
    val industrialShiftSchedule: IndustrialShiftSchedule = IndustrialShiftSchedule()
) {
    /** §5 "Connected Load" — raw nameplate sum, no duty-cycle/simultaneity/diversity reduction. */
    val connectedLoadKw: Double get() = loads.sumOf { it.connectedRealPowerKw }

    /** §5 "Maximum Expected Load" — Connected Load reduced by each load's own duty cycle and per-load simultaneity, before the system-level [diversityFactor] is applied. */
    val maximumExpectedLoadKw: Double get() = loads.sumOf { it.maximumExpectedRealPowerKw }

    /** §5 "Design Load" — Maximum Expected Load x the system-level [diversityFactor]. This is the figure equipment sizing should read, never [connectedLoadKw] alone. */
    val designLoadKw: Double get() = maximumExpectedLoadKw * diversityFactor.fraction

    /** §4: apparent-power (kVA) equivalents of the same three load figures, since kW alone can understate what the inverter must actually supply for a low-power-factor load mix. */
    val connectedApparentPowerKva: Double get() = loads.sumOf { it.connectedApparentPowerKva }
    val maximumExpectedApparentPowerKva: Double get() = loads.sumOf { it.maximumExpectedApparentPowerKva }
    val designApparentPowerKva: Double get() = maximumExpectedApparentPowerKva * diversityFactor.fraction

    /** §4: the blended power factor implied by [designLoadKw]/[designApparentPowerKva] — what actually drives inverter kVA selection, not any single load's own power factor. */
    val blendedPowerFactor: Double get() = if (designApparentPowerKva > 0.0) (designLoadKw / designApparentPowerKva).coerceIn(0.0, 1.0) else 1.0

    /**
     * Real power x actual operating hours, summed per load and scaled by the system-level
     * [diversityFactor] — the commercial/industrial equivalent of the residential wizard's daily-
     * kWh sizing figure, built from each [LoadInstance]'s own [LoadInstance.operatingHoursPerDay]
     * rather than any assumed household usage pattern.
     */
    val estimatedDailyEnergyKwh: Double get() = loads.sumOf { it.maximumExpectedRealPowerKw * it.operatingHoursPerDay } * diversityFactor.fraction

    /** Phase 43 (spec §17): the electrical service's own total current, from this design's real [designApparentPowerKva] — see [ElectricalService.totalCurrentAmps] for the formula. Null until [electricalService.nominalVoltage] is set to something usable. */
    val totalServiceCurrentAmps: Double? get() = electricalService.totalCurrentAmps(designApparentPowerKva)

    /**
     * Phase 43 (spec §19 — "Do NOT make this decision purely from facility name. Evaluate: Total
     * kW, Total kVA, Largest individual load, Motor loads, Starting demand... If uncertain: show:
     * 'Electrical service requires installer verification.'"): a plain-language summary of the
     * load-side signals actually relevant to the choice, plus the verification disclaimer whenever
     * the installer hasn't confirmed a named preset ([ElectricalService.preset] still
     * [ElectricalServicePreset.CUSTOM]). Advisory text only — this never selects a service itself.
     */
    val electricalServiceGuidance: String
        get() {
            val largestLoadKw = loads.maxOfOrNull { it.connectedRealPowerKw } ?: 0.0
            val hasStartingDemand = loads.any { it.startingSurgeKw != null && it.startingSurgeKw!! > it.connectedRealPowerKw }
            val summary = buildString {
                append("Design load %.1f kW / %.1f kVA".format(designLoadKw, designApparentPowerKva))
                if (largestLoadKw > 0.0) append(", largest single load %.1f kW".format(largestLoadKw))
                if (hasStartingDemand) append(", includes motor/starting demand")
                append(".")
            }
            return if (electricalService.preset == ElectricalServicePreset.CUSTOM) {
                "$summary Electrical service requires installer verification."
            } else {
                summary
            }
        }
}
