package com.lumix.estimator.domain.commercial

import kotlinx.serialization.Serializable

/**
 * Phase 42 (spec "COMMERCIAL / INDUSTRIAL FACILITY LOAD PROFILES + ELECTRICAL SERVICE" §1-§3):
 * "On creating an estimate/simulation, first select Residential / Commercial / Industrial... When
 * Commercial or Industrial is selected, immediately ask: What type of facility is this?" The
 * facility selection drives the default load library ([FacilityLoadLibrary], added in a later
 * phase of this same update) and default operating schedule — but per §1's own "Do NOT force
 * facility assumptions on the user," every load it seeds remains an ordinary editable
 * [LoadInstance] the installer can add to, edit, or delete freely.
 *
 * §2/§3 give closed, numbered lists (26 commercial + 25 industrial), each ending in an explicit
 * "Custom ... Facility" escape hatch, plus "The list must be extensible through Settings" —
 * satisfied by [CommercialFacilityType.CUSTOM]/[IndustrialFacilityType.CUSTOM] carrying a
 * free-text name ([FacilitySelection.customFacilityName]) and by user-added extra names
 * ([com.lumix.estimator.data.SettingsRepository.customCommercialFacilityNames]/
 * [com.lumix.estimator.data.SettingsRepository.customIndustrialFacilityNames]) that the wizard's
 * facility picker offers alongside these 51 built-in presets.
 *
 * The `label` on every entry is verbatim from the spec's own numbered list — not paraphrased —
 * so the picker text matches what the installer was asked to choose between in the request itself.
 */
@Serializable
enum class CommercialFacilityType(val label: String) {
    SUPERMARKET("Supermarket / Grocery Store"),
    CONVENIENCE_STORE("Convenience Store"),
    RETAIL_STORE("Retail Store"),
    SHOPPING_CENTRE("Shopping Centre"),
    RESTAURANT("Restaurant"),
    HOTEL_GUEST_HOUSE("Hotel / Guest House"),
    OFFICE("Office"),
    CALL_CENTRE("Call Centre"),
    BANK_FINANCIAL_INSTITUTION("Bank / Financial Institution"),
    SCHOOL("School"),
    UNIVERSITY_COLLEGE("University / College"),
    CLINIC("Clinic"),
    MEDICAL_CENTRE("Medical Centre"),
    PHARMACY("Pharmacy"),
    DENTAL_CLINIC("Dental Clinic"),
    CHURCH("Church"),
    GYM_FITNESS_CENTRE("Gym / Fitness Centre"),
    SALON_BARBERSHOP("Salon / Barbershop"),
    WAREHOUSE("Warehouse"),
    WORKSHOP_GARAGE("Workshop / Garage"),
    CAR_WASH("Car Wash"),
    GAS_STATION("Gas Station"),
    SMALL_MANUFACTURING("Small Manufacturing"),
    COLD_STORAGE_FACILITY("Cold Storage Facility"),
    DATA_IT_FACILITY("Data / IT Facility"),
    CUSTOM("Custom Commercial Facility");

    val isCustom: Boolean get() = this == CUSTOM
}

@Serializable
enum class IndustrialFacilityType(val label: String) {
    FOOD_PROCESSING_FACILITY("Food Processing Facility"),
    BEVERAGE_PROCESSING_FACILITY("Beverage Processing Facility"),
    MEAT_PROCESSING_FACILITY("Meat Processing Facility"),
    BAKERY_INDUSTRIAL_BAKERY("Bakery / Industrial Bakery"),
    MANUFACTURING_PLANT("Manufacturing Plant"),
    PLASTIC_MANUFACTURING("Plastic Manufacturing"),
    METAL_FABRICATION("Metal Fabrication"),
    WELDING_FABRICATION_FACILITY("Welding / Fabrication Facility"),
    FURNITURE_MANUFACTURING("Furniture Manufacturing"),
    BLOCK_CONCRETE_PLANT("Block / Concrete Plant"),
    WATER_TREATMENT_FACILITY("Water Treatment Facility"),
    WASTEWATER_TREATMENT_FACILITY("Wastewater Treatment Facility"),
    PUMPING_STATION("Pumping Station"),
    COLD_STORAGE_REFRIGERATION_FACILITY("Cold Storage / Refrigeration Facility"),
    WAREHOUSE_DISTRIBUTION_CENTRE("Warehouse / Distribution Centre"),
    AGRICULTURAL_PROCESSING_FACILITY("Agricultural Processing Facility"),
    AGRICULTURAL_PUMPING_FACILITY("Agricultural Pumping Facility"),
    PACKAGING_FACILITY("Packaging Facility"),
    PRINTING_FACILITY("Printing Facility"),
    QUARRY_AGGREGATE_FACILITY("Quarry / Aggregate Facility"),
    WORKSHOP_HEAVY_EQUIPMENT_FACILITY("Workshop / Heavy Equipment Facility"),
    COMMERCIAL_LAUNDRY("Commercial Laundry"),
    INDUSTRIAL_HVAC_FACILITY("Industrial HVAC Facility"),
    DATA_CENTRE_SERVER_FACILITY("Data Centre / Server Facility"),
    CUSTOM("Custom Industrial Facility");

    val isCustom: Boolean get() = this == CUSTOM
}

/**
 * §1's "What type of facility is this?" answer, held on [CommercialIndustrialDesign.facility].
 * Only one of [commercialType]/[industrialType] is meaningful at a time — which one, is decided by
 * [com.lumix.estimator.domain.QuoteInputs.systemCategory] (COMMERCIAL vs INDUSTRIAL), exactly the
 * same "the other system type's fields just sit unused at their default" pattern
 * [CommercialIndustrialDesign] itself already uses for [CommercialIndustrialDesign.businessHours]
 * vs [CommercialIndustrialDesign.industrialShiftSchedule]. Both null is the honest "not yet
 * chosen" state — the wizard must ask, never silently default to a specific facility (§1).
 */
@Serializable
data class FacilitySelection(
    val commercialType: CommercialFacilityType? = null,
    val industrialType: IndustrialFacilityType? = null,
    /** Free-text name — required once [commercialType]/[industrialType] resolves to CUSTOM, otherwise ignored. */
    val customFacilityName: String = ""
) {
    /** The label to actually show/store — the enum's own label, or the installer's custom name. */
    val displayLabel: String
        get() = when {
            commercialType == CommercialFacilityType.CUSTOM || industrialType == IndustrialFacilityType.CUSTOM ->
                customFacilityName.ifBlank { "Custom Facility" }
            commercialType != null -> commercialType.label
            industrialType != null -> industrialType.label
            else -> ""
        }

    val isChosen: Boolean get() = commercialType != null || industrialType != null
}
