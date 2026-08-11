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

@Serializable
enum class BackupCoverage { ESSENTIALS, FULL }

@Serializable
enum class ManualModeType { BATTERY_LED, PANEL_LED, FULL_MANUAL }

@Serializable
enum class DiscountType { NONE, PERCENT, FIXED }

@Serializable
enum class ApplianceType(val label: String, val watts: Int) {
    FRIDGE("Refrigerators", 150),
    FREEZER("Deep Freezers", 200),
    FAN("Fans", 60),
    IRON("Irons", 1200),
    MICROWAVE("Microwaves", 1200),
    WASHER("Washers", 600),
    DRYER("Dryers", 1500),
    TV("TVs", 80)
}

@Serializable
data class ApplianceLoad(val qty: Int = 0, val hours: Double = 0.0)

@Serializable
data class AcLoad(
    val hasAc: Boolean = false,
    val counts: Map<Int, Int> = mapOf(9000 to 0, 12000 to 0, 18000 to 0, 24000 to 0),
    val useStandardHours: Boolean = true,
    val customHours: Double = 4.0
) {
    val hours: Double get() = if (!hasAc) 0.0 else if (useStandardHours) 4.0 else customHours
}

/**
 * Carries a Solar Site roof plane's physical panel-fit result into the estimator, so the
 * recommended system can be capped at what the roof can actually hold rather than only what
 * the electricity usage calls for. [maxPanelCount] comes from
 * `PanelLayoutOptimizer` — real geometric placement, not `usableArea / panelArea`.
 */
@Serializable
data class RoofConstraint(
    val sourceSiteId: String,
    val sourceRoofPlaneId: String,
    val roofLabel: String,
    val maxPanelCount: Int,
    val panelWattage: Int,
    val azimuthDegrees: Double?,
    val pitchDegrees: Double?
) {
    val maxCapacityKw: Double get() = maxPanelCount * panelWattage / 1000.0
}

fun defaultAppliances(): Map<ApplianceType, ApplianceLoad> = mapOf(
    ApplianceType.FRIDGE to ApplianceLoad(qty = 1, hours = 24.0),
    ApplianceType.FREEZER to ApplianceLoad(),
    ApplianceType.FAN to ApplianceLoad(),
    ApplianceType.IRON to ApplianceLoad(),
    ApplianceType.MICROWAVE to ApplianceLoad(),
    ApplianceType.WASHER to ApplianceLoad(),
    ApplianceType.DRYER to ApplianceLoad(),
    ApplianceType.TV to ApplianceLoad()
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
    val avgBill: Double = 50000.0,
    val avgKwh: Double = 0.0,

    val backupHoursPreset: Int? = 4,
    val backupHoursCustom: Double = 6.0,
    val backupCoverage: BackupCoverage = BackupCoverage.ESSENTIALS,

    val manualModeType: ManualModeType = ManualModeType.BATTERY_LED,
    val manualInverterId: String? = null,
    val manualPanelWatts: Int = 595,
    val manualPanelCount: Int = 0,
    val manualBatt5k: Int = 0,
    val manualBatt10k: Int = 0,
    val manualBatt15k: Int = 0,
    val manualAgmCount: Int = 0,
    val manualOffgridUseAutoTransfer: Boolean = true,

    val budgetBand: String = "none",
    val deliveryCharge: Double = 0.0,
    val useDiscountPriceList: Boolean = false,
    val discountType: DiscountType = DiscountType.NONE,
    val discountValue: Double = 0.0,

    val customerName: String = "",
    val customerContact: String = "",

    val roofConstraint: RoofConstraint? = null
) {
    val backupHours: Double get() = (backupHoursPreset?.toDouble()) ?: backupHoursCustom
}
