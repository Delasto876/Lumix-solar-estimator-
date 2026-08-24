package com.lumix.estimator.domain.commercial

import com.lumix.estimator.domain.simulation.ApplianceRun
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.sqrt

/**
 * Phase 27 §14 ("Single phase / split phase / three phase... Do not assume residential 120/240 V
 * split phase for commercial/industrial projects"): the electrical-service phase topology, tracked
 * per load too (§3 — each load's own [LoadInstance.phase]) since a commercial building's loads are
 * rarely all the same phase configuration (e.g. lighting/office equipment on single-phase branch
 * circuits, a compressor motor on three-phase).
 */
@Serializable
enum class LoadPhaseType { SINGLE_PHASE, SPLIT_PHASE, THREE_PHASE }

/** Phase 27 §3 ("Continuous/intermittent"). */
@Serializable
enum class LoadOperationType { CONTINUOUS, INTERMITTENT }

/** Phase 27 §3 ("Critical/non-critical... Load priority") — priority is the same two-tier signal; a separate numeric priority isn't warranted without a real use for more than two tiers yet. */
@Serializable
enum class LoadPriority { CRITICAL, NON_CRITICAL }

/** Which catalog ([CommercialIndustrialLoadCatalog]) a [LoadDefinition] belongs to — filters the picker by [SystemType][com.lumix.estimator.domain.SystemType]. */
@Serializable
enum class LoadCategory { COMMERCIAL, INDUSTRIAL }

/**
 * Phase 27 §2 ("The load catalog must be extensible rather than hard-coded to only these
 * examples"): a catalog ENTRY (the template an installer picks from) — [LoadInstance] is the
 * per-quote, per-installer-edited usage of one. Deliberately a plain data class the catalog is a
 * `List<LoadDefinition>` of, not a closed enum — a new load only needs a new list entry (or, later,
 * an installer-added custom one via [isCustom]), never a code change to a `when` branch anywhere
 * that reads [LoadInstance].
 */
@Serializable
data class LoadDefinition(
    /** Stable identity — kotlinx.serialization-safe key a [LoadInstance] is keyed by; never reused across renames (add a new id instead, the same convention [com.lumix.estimator.domain.ApplianceType]'s own doc already establishes for this app). */
    val id: String,
    val label: String,
    val category: LoadCategory,
    val defaultRatedWatts: Double,
    val defaultVoltage: Double? = null,
    val defaultPhase: LoadPhaseType = LoadPhaseType.SINGLE_PHASE,
    val defaultFrequencyHz: Double = 60.0,
    val defaultPowerFactor: Double = 1.0,
    val defaultOperationType: LoadOperationType = LoadOperationType.INTERMITTENT,
    val defaultPriority: LoadPriority = LoadPriority.NON_CRITICAL,
    /** Phase 27 §3 ("For motors and other inductive loads, power factor and starting requirements must be represented"). */
    val isMotorLoad: Boolean = false,
    /** Typical starting/surge multiplier of [defaultRatedWatts] for this load type (e.g. ~3x for a motor's locked-rotor surge) — a starting point [LoadInstance.startingSurgeWatts] can override with a real nameplate/datasheet figure. Null for non-motor loads with no meaningful surge. */
    val defaultStartingSurgeMultiplier: Double? = null,
    /** True only for the catalog's own "Custom Load" entries (e.g. `commercial_custom`/`industrial_custom` in [CommercialIndustrialLoadCatalog]) — the installer fills in [LoadInstance.label]/[LoadInstance.ratedWatts]/etc. themselves rather than picking a preset. */
    val isCustom: Boolean = false,
    /**
     * True for an air-conditioning load — the catalog picker/editor uses BTU (the unit installers
     * actually spec AC by, matching the residential wizard's own AC picker) instead of raw watts
     * for this entry. [defaultRatedWatts] is still the authoritative starting wattage
     * (`defaultBtu / 10`, the same non-inverter BTU/W ratio [com.lumix.estimator.domain
     * .SystemCalculator.acBtuPerWatt] uses) — [defaultBtu] exists purely so the UI has a BTU figure
     * to seed the editor with, not a second source of truth for sizing.
     */
    val isAcLoad: Boolean = false,
    val defaultBtu: Double? = null,
    /**
     * Load-Sheet round ("Lumix Load Sheet Defaults" — Modeling Rules' own "Default Hours/Day...
     * seeds the estimate; user can edit"): a starting `operatingHoursPerDay` for a newly-added
     * [LoadInstance] of this type — see [com.lumix.estimator.ui.components.newInstanceFrom]. Null
     * (every entry before this field existed, and any without a sourced figure) means "start at 0
     * hours," the original Phase 31.2 behavior — this only ever seeds a NEW instance, and only once
     * the installer has already set a quantity above 0 for it, so it never counts toward sizing
     * until the installer has actively included the load; nothing here is a forced assumption.
     */
    val defaultHoursPerDay: Double? = null
)

/**
 * Phase 27 §3: one load, as actually configured on a quote — [definition] is the catalog template,
 * every other field is editable per §3's full characteristics list ("Do not assume every load
 * operates at 100% simultaneously").
 */
@Serializable
data class LoadInstance(
    val definitionId: String,
    val label: String,
    val quantity: Int = 1,
    val ratedWatts: Double,
    val voltage: Double? = null,
    val phase: LoadPhaseType = LoadPhaseType.SINGLE_PHASE,
    val frequencyHz: Double = 60.0,
    /** 0 < powerFactor <= 1. [totalApparentPowerKva] coerces defensively, but a value outside this range is itself worth flagging — see [com.lumix.estimator.domain.commercial.EngineeringWarning.MissingLoadInformation]. */
    val powerFactor: Double = 1.0,
    val operatingHoursPerDay: Double = 0.0,
    /** Fraction (0..1) of "on" time this load actually draws its rated power (vs. idling/cycling) — e.g. a compressor's real run-time fraction within its "on" window. Distinct from [simultaneousFactor], which is about THIS load overlapping with every OTHER load, not its own internal cycling. */
    val dutyCycleFraction: Double = 1.0,
    /** Real starting/surge watts for this load, if known — overrides [LoadDefinition.defaultStartingSurgeMultiplier] × [ratedWatts] when set. Null means "use the catalog default multiplier," itself null for a non-motor load (no surge modeled). */
    val startingSurgeWatts: Double? = null,
    val operationType: LoadOperationType = LoadOperationType.INTERMITTENT,
    val priority: LoadPriority = LoadPriority.NON_CRITICAL,
    /** Phase 27 §3 ("Simultaneous operation/diversity factor") applied AT THE INDIVIDUAL LOAD level — e.g. "only 1 of these 4 identical compressors ever runs at once" (0.25). Separate from and multiplicative with the SYSTEM-level [DiversityFactor] (§5), which covers "not every load group peaks together." */
    val simultaneousFactor: Double = 1.0,
    val notes: String = "",
    /** For an AC load ([LoadDefinition.isAcLoad]) — the BTU rating the editor derived [ratedWatts] from (`btu / 10`), kept alongside so the editor can round-trip a BTU figure rather than back-computing it from watts. Null for a non-AC load, or an AC load whose watts were typed directly instead of via BTU. */
    val btu: Double? = null,
    /**
     * Phase 31 ("pick runtime and amount and when they are likely to run"): the hour (0-23.99,
     * decimal — same HH:MM-editable convention as [com.lumix.estimator.domain.commercial.Shift]/
     * [com.lumix.estimator.domain.commercial.BusinessHours]) this load typically STARTS its daily
     * run. Paired with [operatingHoursPerDay] this gives a single contiguous typical window
     * (start -> start + hours) — informational/planning only for now, not yet fed into
     * [CommercialIndustrialCalculator]'s connected/design-load math (which stays a flat daily-hours
     * multiplier, same as before this field existed) or into any coincident-peak/overlap
     * calculation. Null means "not specified." A full drag-editable multi-block time-bar (the
     * residential [com.lumix.estimator.domain.simulation.SimAppliance] treatment) remains
     * deferred — see the Phase 28/29 completion notes.
     */
    val typicalStartHour: Double? = null,
    /**
     * Phase 37 ("this is how I want it exactly" — matching the residential Appliances sheet's own
     * multi-run, day-type-aware schedule editor): whether this load currently contributes any
     * power — the simulation's own Switch, distinct from list-presence (removing a load from
     * [CommercialIndustrialDesign.loads] entirely is still how the wizard's own catalog picker
     * turns a load off; this field lets the SIMULATION toggle a load off/on for a session without
     * losing its configured [runs]/wattage/duty-cycle, exactly like [com.lumix.estimator.domain
     * .simulation.ApplianceState.enabled] already does for residential). Defaults `true` so every
     * load configured before this field existed keeps contributing exactly as before.
     */
    @Transient
    val enabled: Boolean = true,
    /**
     * Phase 37: an optional richer schedule — a real list of [ApplianceRun]s (own start/duration/
     * day-types each, reusing the exact residential type rather than a parallel one), matching what
     * the residential Appliances sheet's own schedule editor already offers ("start time end time...
     * add another run... select days"). Null (the default, and what the wizard's own [typicalStartHour]/
     * [operatingHoursPerDay] editing in `CatalogLoadRow` always produces) means "derive one run from
     * those two fields" — see [effectiveRuns]. Non-null replaces that single-window model entirely;
     * [typicalStartHour]/[operatingHoursPerDay] are then just the last single-window snapshot before
     * multi-run editing began, kept for round-tripping back to single-window display, not read by
     * [effectiveRuns] once [runs] is set.
     *
     * `@Transient` (not persisted): [ApplianceRun] is not itself `@Serializable`, and — matching the
     * residential Appliances sheet's own precedent, where [com.lumix.estimator.domain.simulation
     * .ApplianceState]/its `runs` are already session-only and re-derived fresh on every quote load —
     * a commercial/industrial load's rich multi-run schedule is a simulation-session customization,
     * not a saved-quote field. It resets to `null` (single-window mode) whenever a quote is reloaded.
     */
    @Transient
    val runs: List<ApplianceRun>? = null
) {
    /**
     * Phase 37: the actual schedule to simulate/display against, regardless of which of the two
     * representations above is active — every caller (the timeline bar, [commercialLoadKwAt], the
     * schedule editor) reads this instead of choosing between [runs] and [typicalStartHour]/
     * [operatingHoursPerDay] itself, so there's exactly one place that decision is made.
     */
    val effectiveRuns: List<ApplianceRun> get() = runs ?: listOfNotNull(
        if (operatingHoursPerDay > 0.0) ApplianceRun(quantity = quantity, startHour = typicalStartHour ?: 0.0, durationHours = operatingHoursPerDay) else null
    )

    val totalConnectedWatts: Double get() = ratedWatts * quantity

    /** This load's real-power contribution to Connected Load — quantity x rating, with no duty-cycle/simultaneity reduction (Connected Load is the raw nameplate sum, per §5's own "Connected Load" definition). */
    val connectedRealPowerKw: Double get() = totalConnectedWatts / 1000.0

    /** This load's contribution to Maximum Expected Load — Connected Load reduced by its own duty cycle and per-load simultaneity, before the system-level [DiversityFactor] is applied. */
    val maximumExpectedRealPowerKw: Double get() = connectedRealPowerKw * dutyCycleFraction.coerceIn(0.0, 1.0) * simultaneousFactor.coerceIn(0.0, 1.0)

    val effectivePowerFactor: Double get() = powerFactor.coerceIn(0.01, 1.0)

    val connectedApparentPowerKva: Double get() = ElectricalPower.apparentPowerKva(connectedRealPowerKw, effectivePowerFactor)
    val maximumExpectedApparentPowerKva: Double get() = ElectricalPower.apparentPowerKva(maximumExpectedRealPowerKw, effectivePowerFactor)

    val startingSurgeKw: Double? get() = startingSurgeWatts?.let { it * quantity / 1000.0 }
}

/** Phase 27 §4: `kVA = kW / powerFactor`, `kVAR = sqrt(kVA^2 - kW^2)` — the exact formulas the spec names. */
object ElectricalPower {
    fun apparentPowerKva(realPowerKw: Double, powerFactor: Double): Double {
        val pf = powerFactor.coerceIn(0.01, 1.0)
        return realPowerKw / pf
    }

    fun reactivePowerKvar(realPowerKw: Double, apparentPowerKva: Double): Double {
        val underRoot = (apparentPowerKva * apparentPowerKva) - (realPowerKw * realPowerKw)
        return sqrt(underRoot.coerceAtLeast(0.0))
    }
}
