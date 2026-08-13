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
 * A29/A48: the ONE master appliance catalog for the wizard side (Guided/Load-Based/Manual all
 * share this single `StepHouseholdAppliances` step — see `WizardScreen.kt`'s step dispatch,
 * which doesn't branch by mode for step 6). Each entry's name is the exact same one the
 * Simulation's own picker uses for its wizard-linked counterpart (see
 * [com.lumix.estimator.domain.simulation.defaultApplianceStates]'s `stateFromWizard` calls) —
 * that shared enum identity, not a display-string match, is the "stable ID" connecting the two.
 * Deliberately kept to the basic categories/types requested — richer/secondary appliances (a
 * second fridge, security system, EV charger, etc.) stay reachable from the Simulation's own
 * fuller picker, not duplicated here.
 */
@Serializable
enum class ApplianceType(val label: String, val watts: Int, val category: String) {
    // Cooling
    FAN("Fans", 60, "Cooling"),
    // Kitchen
    FRIDGE("Refrigerators", 150, "Kitchen"),
    FREEZER("Deep Freezers", 200, "Kitchen"),
    STOVE("Stove", 3000, "Kitchen"),
    OVEN("Oven", 3000, "Kitchen"),
    MICROWAVE("Microwaves", 1200, "Kitchen"),
    ELECTRIC_KETTLE("Electric Kettle", 1500, "Kitchen"),
    TOASTER("Toaster", 1200, "Kitchen"),
    BLENDER("Blender", 400, "Kitchen"),
    // Water
    WATER_HEATER("Water Heater", 3800, "Water"),
    WATER_PUMP("Water Pump", 750, "Water"),
    // Laundry
    WASHER("Washers", 600, "Laundry"),
    DRYER("Dryers", 1500, "Laundry"),
    IRON("Irons", 1200, "Laundry"),
    // Lighting
    LIGHTS("Lights", 10, "Lighting"),
    OUTDOOR_LIGHTS("Outdoor Lights", 10, "Lighting"),
    // Entertainment
    TV("TVs", 80, "Entertainment"),
    COMPUTER("Computer", 150, "Entertainment"),
    GAMING_CONSOLE("Gaming Console", 120, "Entertainment")
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

fun defaultAppliances(): Map<ApplianceType, ApplianceLoad> = mapOf(
    ApplianceType.FAN to ApplianceLoad(),
    ApplianceType.FRIDGE to ApplianceLoad(qty = 1),
    ApplianceType.FREEZER to ApplianceLoad(),
    ApplianceType.STOVE to ApplianceLoad(),
    ApplianceType.OVEN to ApplianceLoad(),
    ApplianceType.MICROWAVE to ApplianceLoad(),
    ApplianceType.ELECTRIC_KETTLE to ApplianceLoad(),
    ApplianceType.TOASTER to ApplianceLoad(),
    ApplianceType.BLENDER to ApplianceLoad(),
    ApplianceType.WATER_HEATER to ApplianceLoad(),
    ApplianceType.WATER_PUMP to ApplianceLoad(),
    ApplianceType.WASHER to ApplianceLoad(),
    ApplianceType.DRYER to ApplianceLoad(),
    ApplianceType.IRON to ApplianceLoad(),
    ApplianceType.LIGHTS to ApplianceLoad(),
    ApplianceType.OUTDOOR_LIGHTS to ApplianceLoad(),
    ApplianceType.TV to ApplianceLoad(),
    ApplianceType.COMPUTER to ApplianceLoad(),
    ApplianceType.GAMING_CONSOLE to ApplianceLoad()
)

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
    val useDiscountPriceList: Boolean = false,
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
