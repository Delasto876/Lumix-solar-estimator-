package com.lumix.estimator.domain

import com.lumix.estimator.domain.commercial.CommercialIndustrialDesign
import kotlinx.serialization.Serializable

@Serializable
enum class QuoteMode { GUIDED, MANUAL, LOAD }

@Serializable
enum class PropertyType(val label: String) {
    HOUSE("House (residential)"),
    BUSINESS("Business / Shop"),
    SCHOOL("School"),
    SUPERMARKET("Supermarket / Cold storage")
}

@Serializable
enum class SystemTypeNew { NEW, UPGRADE }

/**
 * Phase 27 ("COMMERCIAL & INDUSTRIAL SYSTEM ARCHITECTURE" — "Add a required system category:
 * Residential, Commercial, Industrial"): the field name is [QuoteInputs.systemCategory], not
 * `systemType` — [SystemTypeNew] (NEW/UPGRADE, an unrelated "new install vs. upgrade" concept)
 * already owns that name on [QuoteInputs]. Defaults to [RESIDENTIAL] so every existing/older
 * quote — real or in a test fixture — decodes with, and every code path that doesn't explicitly
 * branch on this field keeps behaving, exactly as it did before this field existed.
 *
 * RESIDENTIAL keeps the entire existing wizard/[SystemCalculator] workflow untouched. COMMERCIAL/
 * INDUSTRIAL route to [com.lumix.estimator.domain.commercial.CommercialIndustrialCalculator]
 * instead — see [SystemCalculator.calculate]'s own doc for the exact dispatch point. INDUSTRIAL is
 * manual-design-only (spec §1/§13); COMMERCIAL supports both a manual design and a recommendation
 * (spec §16) — this enum alone doesn't encode that difference, [QuoteInputs.quoteMode] still does,
 * the same as it already does for RESIDENTIAL.
 */
@Serializable
enum class SystemType { RESIDENTIAL, COMMERCIAL, INDUSTRIAL }

@Serializable
enum class JpsRate { RESIDENTIAL, COMMERCIAL, UNKNOWN }

@Serializable
enum class RoofType { ZINC, SLAB, SHINGLE }

@Serializable
enum class UsageMode { BILL, KWH, UNKNOWN }

/**
 * ESSENTIALS/FULL are kept (not renamed) purely so quotes saved before CRITICAL_LOADS/MOST_LOAD/
 * CUSTOM existed still decode correctly — kotlinx.serialization encodes enum constants by name.
 * ESSENTIALS and CRITICAL_LOADS are treated identically in [SystemCalculator]; likewise FULL and
 * MOST_LOAD. New quotes always pick from CRITICAL_LOADS/MOST_LOAD/CUSTOM going forward.
 */
@Serializable
enum class BackupCoverage { ESSENTIALS, FULL, CRITICAL_LOADS, MOST_LOAD, CUSTOM }

@Serializable
enum class ManualModeType { BATTERY_LED, PANEL_LED, FULL_MANUAL }

@Serializable
enum class DiscountType { NONE, PERCENT, FIXED }

/**
 * A89/Ph21 (master prompt — "ask if automatic switch or manual or no transfer switch"): the
 * universal 3-way ask, for every [QuoteMode]/[SystemMode] combination — not just MANUAL+OFFGRID's
 * pre-existing [QuoteInputs.manualOffgridUseAutoTransfer] toggle, which this supersedes for every
 * OTHER combination (see that field's own doc for why it's still read as-is for its one original
 * UI path this round, rather than silently going dead). AUTOMATIC sizes to one of 40/50/100/120A by
 * the system's real AC load (NEC-style breaker-sizing logic) — see [MaterialTakeoffEngine].
 */
@Serializable
enum class TransferSwitchMode { NONE, MANUAL, AUTOMATIC }

/**
 * A29/A48/A54: the ONE master appliance catalog for the wizard side (Guided/Load-Based/Manual all
 * share this single `StepHouseholdAppliances` step — see `WizardScreen.kt`'s step dispatch,
 * which doesn't branch by mode for step 6). Each entry's name is the exact same one the
 * Simulation's own picker uses for its wizard-linked counterpart (see
 * [com.lumix.estimator.domain.simulation.defaultApplianceStates]'s `stateFromWizard` calls) —
 * that shared enum identity, not a display-string match, is the "stable ID" connecting the two.
 *
 * A54 (installer request, 2026-08-14: "the section to choose appliances ... that list only show
 * some appliances" — update it to match the Simulation window's fuller picker): this now mirrors
 * every entry in [com.lumix.estimator.domain.simulation.SimApplianceType] (except Air Conditioner,
 * which keeps its own dedicated wizard step/[AcLoad]) — label, watts, and category all copied
 * verbatim from that catalog so the two pickers show the identical list. The original 19 entries
 * keep their original constant names (kotlinx.serialization encodes enums by name — renaming would
 * break decoding older saved quotes); only their `category` strings were updated to match
 * [com.lumix.estimator.domain.simulation.SimApplianceType]'s category naming exactly.
 */
@Serializable
/**
 * A66 (spec Phase 27/56 — "there must never be two separate appliance lists... avoid duplicated
 * information"): [watts] here must always equal the [watts][com.lumix.estimator.domain.simulation.SimApplianceType.watts]
 * of whichever [com.lumix.estimator.domain.simulation.SimApplianceType] [com.lumix.estimator.domain.SystemCalculator]'s
 * own `simTypeFor` maps this entry to — this is the WIZARD's simpler selection-oriented catalog
 * (checkbox + quantity, no duty-cycle/schedule fields), [SimApplianceType] is the SIMULATION's
 * richer runtime catalog (duty cycle, startup surge, electrical tier); they're deliberately two
 * different Kotlin types for two different roles, not an accidental duplicate, but the underlying
 * WATTAGE FACT about e.g. "a clothes dryer" is one real number and must never be declared twice
 * with two different values. `ApplianceTypeConsistencyTest` asserts this holds for every entry.
 * (Found drifted during the A66 audit: FREEZER was 200 here vs. CHEST_FREEZER's real 180, WASHER
 * was 600 vs. WASHING_MACHINE's 500, and DRYER was a badly stale 1500 vs. CLOTHES_DRYER's real
 * 5000 — meaning a selected dryer's contribution to `SystemCalculator.loadsKwhAndPeak`'s PEAK watts
 * used to undercount by 3.5kW per unit relative to what its own auto-schedule DAILY ENERGY
 * calculation already assumed, understating `requiredInverterKw` for any quote that included one.
 * All three corrected here to match [SimApplianceType]'s own, more carefully documented figures.)
 */
enum class ApplianceType(val label: String, val watts: Int, val category: String) {
    // Kitchen
    FRIDGE("Refrigerators", 300, "Kitchen"),
    FREEZER("Deep Freezers", 400, "Kitchen"),
    ELECTRIC_KETTLE("Electric Kettle", 2500, "Kitchen"),
    MICROWAVE("Microwaves", 1800, "Kitchen"),
    TOASTER("Toaster", 1500, "Kitchen"),
    BLENDER("Blender", 1000, "Kitchen"),
    STOVE("Stove", 3000, "Kitchen"),
    OVEN("Oven", 3500, "Kitchen"),
    AIR_FRYER("Air Fryer", 1500, "Kitchen"),
    RICE_COOKER("Rice Cooker", 700, "Kitchen"),
    PRESSURE_COOKER("Pressure Cooker", 1000, "Kitchen"),

    // Cooling & Comfort
    FAN("Fans", 100, "Cooling & Comfort"),
    STANDING_FAN("Pedestal/Standing Fan", 50, "Cooling & Comfort"),
    BEDROOM_FAN("Bedroom Fan", 50, "Cooling & Comfort"),

    // Lighting
    LIGHTS("Lights", 10, "Lighting"),
    OUTDOOR_LIGHTS("Outdoor Lights", 10, "Lighting"),
    LED_BEDROOM("LED Lighting — Bedroom", 25, "Lighting"),
    LED_KITCHEN("LED Lighting — Kitchen", 10, "Lighting"),
    LED_BATHROOM("LED Lighting — Bathroom", 10, "Lighting"),
    OUTDOOR_FLOODLIGHT("Outdoor Security Floodlight", 30, "Lighting"),

    // Electronics & Networking
    TV("TVs", 200, "Electronics & Networking"),
    COMPUTER("Computer", 400, "Electronics & Networking"),
    GAMING_CONSOLE("Gaming Console", 120, "Electronics & Networking"),
    SET_TOP_BOX("Set-Top/Cable Box", 15, "Electronics & Networking"),
    WIFI_ROUTER("Wi-Fi Router", 10, "Electronics & Networking"),
    MODEM("Modem/ONT", 12, "Electronics & Networking"),
    PHONE_CHARGERS("Phone Chargers", 10, "Electronics & Networking"),
    LAPTOP("Laptop", 150, "Electronics & Networking"),
    PRINTER("Computer Printer", 35, "Electronics & Networking"),
    SOUND_SYSTEM("Sound System", 100, "Electronics & Networking"),

    // Water & Heating
    WATER_HEATER("Water Heater", 4500, "Water & Heating"),
    WATER_PUMP("Water Pump", 1500, "Water & Heating"),
    INSTANT_SHOWER("Instant Electric Shower", 4500, "Water & Heating"),

    // Personal Care
    HAIR_DRYER("Hair Dryer", 2000, "Personal Care"),
    CURLING_IRON("Curling/Flat Iron", 60, "Personal Care"),

    // Laundry
    WASHER("Washers", 1000, "Laundry"),
    DRYER("Dryers", 5000, "Laundry"),
    IRON("Irons", 1800, "Laundry"),

    // Cleaning & Misc
    VACUUM_CLEANER("Vacuum Cleaner", 900, "Cleaning & Misc"),
    SEWING_MACHINE("Sewing Machine", 100, "Cleaning & Misc"),

    // Security & Access
    SECURITY_SYSTEM("Security/CCTV System", 150, "Security & Access"),
    GATE_OPENER("Electric Gate/Garage Opener", 500, "Security & Access"),

    // EV & Outdoor
    EV_CHARGER_L1("EV Charger — 110V (L1)", 1400, "EV & Outdoor"),
    EV_CHARGER_L2("EV Charger — 220V (L2)", 5000, "EV & Outdoor"),
    POOL_PUMP("Pool Pump", 1000, "EV & Outdoor")
}

@Serializable
data class ApplianceLoad(
    val qty: Int = 0,
    /**
     * Explicit override, only consumed when [useAutoSchedule] is false — the estimator's default
     * path sizes this appliance from the same realistic schedule/duty-cycle model the simulation
     * itself uses ([com.lumix.estimator.domain.simulation.defaultDailyEnergyKwh]), not a manually
     * entered hours/day figure. Kept (rather than removed) so an installer who deliberately wants
     * an exact figure still can, and so older saved quotes still decode with their original value.
     */
    val hours: Double = 0.0,
    val useAutoSchedule: Boolean = true
)

/**
 * Phase 28 ("update the load/appliance engine... create selectable BTU options... also allow
 * Inverter AC / Non-inverter AC"): applies to every populated tier in [AcLoad.counts] on this
 * quote — one quote's AC units are treated as one purchasing decision (all-inverter or
 * all-non-inverter), not mixed per-tier, since [AcLoad] has no per-tier metadata beyond a bare
 * unit count. [SystemCalculator]'s own doc on the BTU/W constants explains the disclosed, generic
 * (not manufacturer-specific) efficiency figures each option implies.
 */
@Serializable
enum class AcInverterType { NON_INVERTER, INVERTER }

@Serializable
data class AcLoad(
    val hasAc: Boolean = false,
    /**
     * Phase 28: expanded from 4 tiers (9-24k BTU) to the spec's own full 8-tier list (9-60k BTU) —
     * additive only. A quote saved before this round only ever populated the original 4 keys;
     * decoding it here just leaves the 4 new tiers absent from the map, read as 0 everywhere this
     * map is iterated (`data.ac.counts.forEach`/`inputs.ac.counts[btu] ?: 0`), the same as any
     * other tier nobody selected — no migration needed, no behavior change for an old quote.
     */
    val counts: Map<Int, Int> = mapOf(
        9000 to 0, 12000 to 0, 18000 to 0, 24000 to 0,
        30000 to 0, 36000 to 0, 48000 to 0, 60000 to 0
    ),
    // "Standard" now means the automatic realistic AC schedule (evening window, thermostat duty
    // cycle) from the same engine the simulation uses — not a flat 4h/day guess, which is what
    // this used to mean before the automatic schedule engine existed. "Custom" still lets an
    // installer override with an explicit hours/day figure via [customHours].
    val useStandardHours: Boolean = true,
    val customHours: Double = 4.0,
    /** Defaults to [AcInverterType.NON_INVERTER] — the BTU/W ratio ([SystemCalculator]'s own `NON_INVERTER_BTU_PER_WATT`) an old quote's AC sizing already implicitly assumed, so a decoded pre-Phase-28 quote's AC wattage/peak figures are unchanged by this field's addition. */
    val acType: AcInverterType = AcInverterType.NON_INVERTER
)

fun defaultAppliances(): Map<ApplianceType, ApplianceLoad> = ApplianceType.entries.associateWith { type ->
    // FRIDGE is the one appliance almost every household already has — same starting default as before.
    if (type == ApplianceType.FRIDGE) ApplianceLoad(qty = 1) else ApplianceLoad()
}

/**
 * A81 (Phase 18): carries a Solar Site roof plane's physical panel-fit result into the
 * estimator, so the recommended system can be capped at what the roof can actually hold rather
 * than only what the electricity usage calls for. [maxPanelCount] comes from
 * `PanelLayoutOptimizer` — real geometric placement, not `usableArea / panelArea`.
 *
 * [latitude]/[longitude] (the site's location, carried over so the digital-twin simulation can
 * compute real sun position later without a separate lookup) default to 0.0 for quotes saved
 * before these fields existed — harmless, since [azimuthDegrees]/[pitchDegrees] being non-null
 * is what actually gates the site-aware simulation path, not the coordinates alone.
 */
@Serializable
data class RoofConstraint(
    val sourceSiteId: String,
    val sourceRoofPlaneId: String,
    val roofLabel: String,
    val maxPanelCount: Int,
    val panelWattage: Int,
    val azimuthDegrees: Double?,
    val pitchDegrees: Double?,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
) {
    val maxCapacityKw: Double get() = maxPanelCount * panelWattage / 1000.0
}

@Serializable
data class QuoteInputs(
    val quoteMode: QuoteMode = QuoteMode.GUIDED,
    val propertyType: PropertyType = PropertyType.HOUSE,
    val parish: String = "",
    val nearestTown: String = "",
    val systemType: SystemTypeNew = SystemTypeNew.NEW,
    val systemMode: SystemMode = SystemMode.HYBRID,
    val jpsRate: JpsRate = JpsRate.RESIDENTIAL,
    /** Phase 27 §1/§19: RESIDENTIAL (default) keeps every existing field/behavior below exactly as before — see this field's own type doc, [SystemType], for why it isn't named `systemType`. */
    val systemCategory: SystemType = SystemType.RESIDENTIAL,
    /** Phase 27 §19: null for RESIDENTIAL (and for any COMMERCIAL/INDUSTRIAL quote not yet through the manual design flow) — see [CommercialIndustrialDesign]'s own doc. */
    val commercialIndustrialDesign: CommercialIndustrialDesign? = null,

    val roofType: RoofType = RoofType.ZINC,
    val twoOrMoreStoreys: Boolean = false,
    val zincCenterRail: Boolean = true,

    val ac: AcLoad = AcLoad(),
    val appliances: Map<ApplianceType, ApplianceLoad> = defaultAppliances(),
    val otherWatts: Double = 0.0,
    val otherHours: Double = 0.0,

    val usageMode: UsageMode = UsageMode.BILL,
    val avgBill: Double = 16000.0,
    val avgKwh: Double = 0.0,
    /**
     * Peak Sun Hours for this site. A60: defaults from [SolarResource.estimatedPshFor] once
     * [parish] is set (a rough regional estimate, not measured data — see that object's own doc)
     * rather than one flat national number; still just an estimate either way, always editable.
     */
    val peakSunHours: Double = 5.5,
    /** A60: true once the installer has directly edited [peakSunHours] — stops a later [parish] change from silently overwriting a figure they deliberately chose. */
    val peakSunHoursManuallySet: Boolean = false,
    /**
     * A80 (spec Phase 17 — "REALISTIC JAMAICA WEATHER/SOLAR SIMULATION", §"INSTALLATION MONTH" —
     * "ask: which month is this system being designed/installed for?"): 1-12, or null when the
     * installer hasn't picked one yet. Deliberately does NOT change equipment sizing (panel count/
     * inverter/battery) — the spec's own explicit "do not oversize the system simply because the
     * model contains occasional cloudy days" — it instead makes the *simulation/evaluation* of the
     * already-sized system (recharge feasibility, backup estimate, the live Simulation screen)
     * reflect that month's real day length and climatological weather tendency instead of the
     * fixed annual-average assumption every quote used before this field existed. See
     * [com.lumix.estimator.domain.simulation.SolarPosition]/[com.lumix.estimator.domain.simulation
     * .JamaicaClimatology]'s own docs for what "that month's real conditions" actually means here.
     */
    val installMonth: Int? = null,

    /** A81 (Phase 18, restored): set only via a Solar Site "Use This Roof" flow — see [RoofConstraint]'s own doc. */
    val roofConstraint: RoofConstraint? = null,

    val backupHoursPreset: Int? = 12,
    val backupHoursCustom: Double = 6.0,
    val backupCoverage: BackupCoverage = BackupCoverage.MOST_LOAD,
    /** Fraction (0..1) of daily load to size backup for when backupCoverage == CUSTOM. */
    val customBackupCoverageFraction: Double = 0.8,

    val manualModeType: ManualModeType = ManualModeType.BATTERY_LED,
    val manualInverterId: String? = null,
    val manualPanelWatts: Int = 595,
    val manualPanelCount: Int = 0,
    val manualBatt5k: Int = 0,
    val manualBatt10k: Int = 0,
    val manualBatt15k: Int = 0,
    // A54: installer request ("make it 5, 10, 15, 16, 20 and a custom field to add the capacity").
    val manualBatt16k: Int = 0,
    val manualBatt20k: Int = 0,
    /** Custom nominal capacity per unit (kWh) for MANUAL mode's "add your own capacity" battery field — paired with [manualBattCustomCount]. Ignored (treated as 0 total kWh) when either is <= 0. */
    val manualBattCustomKwh: Double = 0.0,
    val manualBattCustomCount: Int = 0,
    val manualAgmCount: Int = 0,
    /** Read only for QuoteMode.MANUAL + SystemMode.OFFGRID — see [TransferSwitchMode]'s own doc for why every other combination now reads [transferSwitchMode] instead. */
    val manualOffgridUseAutoTransfer: Boolean = true,
    /** A89/Ph21: the universal transfer-switch ask — see [TransferSwitchMode]'s own doc. Defaults to AUTOMATIC, matching the pre-existing behavior every GUIDED/LOAD/non-offgrid-MANUAL quote already had (a transfer switch was always added) before this field existed. */
    val transferSwitchMode: TransferSwitchMode = TransferSwitchMode.AUTOMATIC,
    /** A89/Ph21 (master prompt — "voltage regulator ask if using changeover switch or jps as backup"): an explicit installer decision, never auto-added — see [MaterialTakeoffEngine]. */
    val useVoltageRegulator: Boolean = false,
    /** A89/Ph21 (master prompt — "use a 4 way distribution panel for all systems unless changed to a 8 way distribution panel in set up"): false = 4-way default, matching the spreadsheet's own "Default"/"Selectable" framing. No UI sets this yet — same deferral as [deliveryIsTollRoute]. */
    val use8WayDistributionPanel: Boolean = false,

    /**
     * A89/Ph21 follow-up (2026-08-18 — "let me manually enter delivery price"): the project owner
     * explicitly chose to enter [deliveryCharge] manually for every quote rather than have it
     * auto-computed from a distance formula — [DeliveryCalculator]'s proportional
     * Junction-\>Santa-Cruz formula stays built (a "build now, activate later" seam for whenever
     * live routing arrives) but [SystemCalculator.calculate] no longer calls it to override this
     * field. Whether this route crosses a toll is still tracked separately — see
     * [deliveryIsTollRoute] — since toll pricing is its own explicit ask, not part of the base
     * delivery figure the installer types in here.
     */
    val deliveryIsTollRoute: Boolean = false,
    /**
     * A49: the exact warning message text(s) the installer has explicitly accepted via MANUAL
     * mode's "ACCEPT WITH WARNING" gate (see StepSystemReview.kt). A set rather than a plain
     * boolean so that changing equipment to something with a *different* warning automatically
     * re-triggers the gate — no separate reset wiring needed in the equipment-selection steps.
     */
    val manualWarningsAcknowledged: Set<String> = emptySet(),

    val budgetBand: String = "none",
    val deliveryCharge: Double = 0.0,
    /**
     * 2026-08-18 ("i will manually enter the cost for installation and commission"): the same
     * always-manual pattern [deliveryCharge] already uses — never computed/estimated from system
     * size or any other figure, always the installer's own typed amount.
     */
    val installationCommissioningCharge: Double = 0.0,
    // A57 (spec §11): the separate "discount price list" toggle this field used to drive was
    // removed — one price list, discount applied via discountType/discountValue only. Field
    // itself is gone too (kotlinx.serialization's ignoreUnknownKeys=true means older saved quotes
    // with this key still decode fine; it's just no longer read).
    val discountType: DiscountType = DiscountType.NONE,
    val discountValue: Double = 0.0,

    /**
     * A89/Ph21 (master prompt §"QUANTITY AND PRICE OVERRIDES" — "quantity and price overrides at
     * the quote level; catalog price stays unchanged"): per-line-item overrides for THIS quote
     * only, keyed by [MaterialLine.calcKey]. Never mutates [PriceList] — the catalog price/default
     * quantity [MaterialTakeoffEngine] computes stays the source of truth for every OTHER quote.
     * No UI sets this yet ("do not redesign the UI during this phase") — domain plumbing only,
     * verified by [MaterialTakeoffEngineOverrideTest]/equivalent, ready for a future Settings/
     * Review-step control to write into.
     */
    val materialOverrides: Map<String, MaterialOverride> = emptyMap(),

    val customerName: String = "",
    val customerContact: String = "",
    val customerEmail: String = "",
    val customerAddress: String = "",
    val customerNotes: String = ""
) {
    val backupHours: Double get() = (backupHoursPreset?.toDouble()) ?: backupHoursCustom
}

/** See [QuoteInputs.materialOverrides]'s own doc. Either field left null means "use the calculated value" for that one aspect. */
@Serializable
data class MaterialOverride(
    val qtyOverride: Double? = null,
    val priceOverride: Double? = null
)
