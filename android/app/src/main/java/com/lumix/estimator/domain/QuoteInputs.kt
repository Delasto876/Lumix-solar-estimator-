package com.lumix.estimator.domain

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
enum class ApplianceType(val label: String, val watts: Int, val category: String) {
    // Kitchen
    FRIDGE("Refrigerators", 150, "Kitchen"),
    FREEZER("Deep Freezers", 200, "Kitchen"),
    ELECTRIC_KETTLE("Electric Kettle", 1500, "Kitchen"),
    MICROWAVE("Microwaves", 1200, "Kitchen"),
    TOASTER("Toaster", 1200, "Kitchen"),
    BLENDER("Blender", 400, "Kitchen"),
    STOVE("Stove", 3000, "Kitchen"),
    OVEN("Oven", 3000, "Kitchen"),
    AIR_FRYER("Air Fryer", 1500, "Kitchen"),
    RICE_COOKER("Rice Cooker", 700, "Kitchen"),
    PRESSURE_COOKER("Pressure Cooker", 1000, "Kitchen"),

    // Cooling & Comfort
    FAN("Fans", 60, "Cooling & Comfort"),
    STANDING_FAN("Pedestal/Standing Fan", 50, "Cooling & Comfort"),
    BEDROOM_FAN("Bedroom Fan", 50, "Cooling & Comfort"),

    // Lighting
    LIGHTS("Lights", 10, "Lighting"),
    OUTDOOR_LIGHTS("Outdoor Lights", 10, "Lighting"),
    LED_BEDROOM("LED Lighting — Bedroom", 10, "Lighting"),
    LED_KITCHEN("LED Lighting — Kitchen", 10, "Lighting"),
    LED_BATHROOM("LED Lighting — Bathroom", 10, "Lighting"),
    OUTDOOR_FLOODLIGHT("Outdoor Security Floodlight", 30, "Lighting"),

    // Electronics & Networking
    TV("TVs", 80, "Electronics & Networking"),
    COMPUTER("Computer", 150, "Electronics & Networking"),
    GAMING_CONSOLE("Gaming Console", 120, "Electronics & Networking"),
    SET_TOP_BOX("Set-Top/Cable Box", 15, "Electronics & Networking"),
    WIFI_ROUTER("Wi-Fi Router", 10, "Electronics & Networking"),
    MODEM("Modem/ONT", 12, "Electronics & Networking"),
    PHONE_CHARGERS("Phone Chargers", 10, "Electronics & Networking"),
    LAPTOP("Laptop", 65, "Electronics & Networking"),
    PRINTER("Computer Printer", 35, "Electronics & Networking"),
    SOUND_SYSTEM("Sound System", 100, "Electronics & Networking"),

    // Water & Heating
    WATER_HEATER("Water Heater", 3800, "Water & Heating"),
    WATER_PUMP("Water Pump", 750, "Water & Heating"),
    INSTANT_SHOWER("Instant Electric Shower", 4500, "Water & Heating"),

    // Personal Care
    HAIR_DRYER("Hair Dryer", 1500, "Personal Care"),
    CURLING_IRON("Curling/Flat Iron", 60, "Personal Care"),

    // Laundry
    WASHER("Washers", 600, "Laundry"),
    DRYER("Dryers", 1500, "Laundry"),
    IRON("Irons", 1200, "Laundry"),

    // Cleaning & Misc
    VACUUM_CLEANER("Vacuum Cleaner", 900, "Cleaning & Misc"),
    SEWING_MACHINE("Sewing Machine", 100, "Cleaning & Misc"),

    // Security & Access
    SECURITY_SYSTEM("Security/CCTV System", 40, "Security & Access"),
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

@Serializable
data class AcLoad(
    val hasAc: Boolean = false,
    val counts: Map<Int, Int> = mapOf(9000 to 0, 12000 to 0, 18000 to 0, 24000 to 0),
    // "Standard" now means the automatic realistic AC schedule (evening window, thermostat duty
    // cycle) from the same engine the simulation uses — not a flat 4h/day guess, which is what
    // this used to mean before the automatic schedule engine existed. "Custom" still lets an
    // installer override with an explicit hours/day figure via [customHours].
    val useStandardHours: Boolean = true,
    val customHours: Double = 4.0
)

fun defaultAppliances(): Map<ApplianceType, ApplianceLoad> = ApplianceType.entries.associateWith { type ->
    // FRIDGE is the one appliance almost every household already has — same starting default as before.
    if (type == ApplianceType.FRIDGE) ApplianceLoad(qty = 1) else ApplianceLoad()
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
    /** Peak Sun Hours for this site. Estimator default (not a measured value) — see SystemCalculator.PSH doc. */
    val peakSunHours: Double = 5.5,

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
    val manualOffgridUseAutoTransfer: Boolean = true,
    /**
     * A49: the exact warning message text(s) the installer has explicitly accepted via MANUAL
     * mode's "ACCEPT WITH WARNING" gate (see StepSystemReview.kt). A set rather than a plain
     * boolean so that changing equipment to something with a *different* warning automatically
     * re-triggers the gate — no separate reset wiring needed in the equipment-selection steps.
     */
    val manualWarningsAcknowledged: Set<String> = emptySet(),

    val budgetBand: String = "none",
    val deliveryCharge: Double = 0.0,
    // A57 (spec §11): the separate "discount price list" toggle this field used to drive was
    // removed — one price list, discount applied via discountType/discountValue only. Field
    // itself is gone too (kotlinx.serialization's ignoreUnknownKeys=true means older saved quotes
    // with this key still decode fine; it's just no longer read).
    val discountType: DiscountType = DiscountType.NONE,
    val discountValue: Double = 0.0,

    val customerName: String = "",
    val customerContact: String = "",
    val customerEmail: String = "",
    val customerAddress: String = "",
    val customerNotes: String = ""
) {
    val backupHours: Double get() = (backupHoursPreset?.toDouble()) ?: backupHoursCustom
}
