package com.lumix.estimator.domain.commercial

/**
 * Phase 44 (spec §4-§10, §20 — "The selected facility type must control the default appliance
 * list in BOTH ESTIMATE and SIMULATION"): for each [CommercialFacilityType], which
 * [CommercialIndustrialLoadCatalog.commercialLoads] ids are typical for that kind of business —
 * consumed by the wizard's Loads section (see `StepCommercialIndustrialDesign.kt`'s `LoadsSection`)
 * to surface a "Typical for this facility" group ahead of the full generic catalog, not to replace
 * it. This is ordering/grouping only: every [LoadInstance] this produces still starts at quantity 0
 * (not included) exactly like every other catalog row — per §1's own "Do NOT force facility
 * assumptions on the user," picking a facility type never auto-adds a single load or a single watt.
 *
 * The six facility types the spec gives an explicit worked-out load list for (§4 Supermarket, §5
 * School, §6 Call Centre, §7 Clinic, §8 Restaurant, §9 Office, §10 Hotel) map as closely to that
 * list as the existing catalog (plus this round's additions) supports. The remaining 19 commercial
 * types don't get their own spec section, so their lists here are a reasonable, editable starting
 * point built from the same catalog — not a literal spec transcription — exactly like every other
 * "illustrative starting point, not a measured figure" default this catalog already documents.
 * [CommercialFacilityType.CUSTOM] has no list of its own; the wizard falls back to the full generic
 * catalog, unprioritized, for a custom facility name.
 */
object FacilityLoadLibrary {

    fun defaultLoadIdsFor(type: CommercialFacilityType): List<String> = when (type) {
        CommercialFacilityType.SUPERMARKET -> listOf(
            "commercial_display_fridge", "commercial_refrigeration", "commercial_freezer", "commercial_ice_machine",
            "commercial_pos_system", "commercial_computer", "commercial_network_equipment", "commercial_cctv_nvr",
            "commercial_security_system", "commercial_interior_lighting", "commercial_exterior_lighting",
            "commercial_signage", "commercial_ac_package", "commercial_exhaust_fan", "commercial_pump",
            "commercial_water_heater", "commercial_conveyor", "commercial_automatic_door", "commercial_scale",
            "commercial_barcode_scanner", "commercial_bakery_equipment", "commercial_microwave"
        )
        CommercialFacilityType.CONVENIENCE_STORE -> listOf(
            "commercial_display_fridge", "commercial_freezer", "commercial_ice_machine", "commercial_pos_system",
            "commercial_interior_lighting", "commercial_exterior_lighting", "commercial_signage",
            "commercial_ac_split", "commercial_cctv_nvr", "commercial_security_system", "commercial_microwave"
        )
        CommercialFacilityType.RETAIL_STORE -> listOf(
            "commercial_interior_lighting", "commercial_exterior_lighting", "commercial_signage",
            "commercial_pos_system", "commercial_computer", "commercial_network_equipment", "commercial_cctv_nvr",
            "commercial_security_system", "commercial_ac_split", "commercial_ac_package"
        )
        CommercialFacilityType.SHOPPING_CENTRE -> listOf(
            "commercial_interior_lighting", "commercial_exterior_lighting", "commercial_signage",
            "commercial_ac_package", "commercial_elevator", "commercial_cctv_nvr", "commercial_security_system",
            "commercial_pump", "commercial_water_heater", "commercial_exhaust_fan", "commercial_automatic_door"
        )
        CommercialFacilityType.RESTAURANT -> listOf(
            "commercial_refrigeration", "commercial_freezer", "commercial_ice_machine", "commercial_oven",
            "commercial_electric_range", "commercial_fryer", "commercial_griddle", "commercial_microwave",
            "commercial_blender", "commercial_mixer", "commercial_dishwasher", "commercial_exhaust_hood",
            "commercial_exhaust_fan", "commercial_water_heater", "commercial_pump", "commercial_ac_package",
            "commercial_interior_lighting", "commercial_pos_system", "commercial_computer",
            "commercial_network_equipment", "commercial_cctv_nvr", "commercial_signage"
        )
        CommercialFacilityType.HOTEL_GUEST_HOUSE -> listOf(
            "commercial_ac_split", "commercial_interior_lighting", "commercial_display_fridge",
            "commercial_water_heater", "commercial_pump", "commercial_pool_pump", "commercial_pool_equipment",
            "commercial_laundry_equipment", "commercial_kitchen_equipment", "commercial_refrigeration",
            "commercial_freezer", "commercial_tv", "commercial_network_equipment", "commercial_cctv_nvr",
            "commercial_security_system", "commercial_exterior_lighting", "commercial_emergency_lighting",
            "commercial_elevator"
        )
        CommercialFacilityType.OFFICE -> listOf(
            "commercial_computer", "commercial_laptop", "commercial_printer", "commercial_copier",
            "commercial_server", "commercial_network_equipment", "commercial_ac_split",
            "commercial_interior_lighting", "commercial_cctv_nvr", "commercial_access_control",
            "commercial_water_cooler", "commercial_display_fridge", "commercial_microwave",
            "commercial_water_heater", "commercial_pump"
        )
        CommercialFacilityType.CALL_CENTRE -> listOf(
            "commercial_computer", "commercial_voip_phone", "commercial_network_equipment", "commercial_server",
            "commercial_ups", "commercial_ac_package", "commercial_interior_lighting", "commercial_cctv_nvr",
            "commercial_access_control", "commercial_printer", "commercial_copier", "commercial_display_fridge",
            "commercial_microwave", "commercial_water_cooler", "commercial_pump", "commercial_emergency_lighting"
        )
        CommercialFacilityType.BANK_FINANCIAL_INSTITUTION -> listOf(
            "commercial_computer", "commercial_server", "commercial_network_equipment", "commercial_ups",
            "commercial_ac_split", "commercial_interior_lighting", "commercial_cctv_nvr",
            "commercial_security_system", "commercial_access_control", "commercial_printer",
            "commercial_water_cooler", "commercial_emergency_lighting"
        )
        CommercialFacilityType.SCHOOL -> listOf(
            "commercial_interior_lighting", "commercial_exterior_lighting", "commercial_ceiling_fan",
            "commercial_ac_split", "commercial_computer", "commercial_laptop", "commercial_smart_board",
            "commercial_projector", "commercial_printer", "commercial_copier", "commercial_network_equipment",
            "commercial_server", "commercial_cctv_nvr", "commercial_security_system", "commercial_pa_system",
            "commercial_pump", "commercial_water_heater", "commercial_kitchen_equipment",
            "commercial_refrigeration", "commercial_freezer", "commercial_microwave",
            "commercial_laboratory_equipment", "commercial_workshop_equipment"
        )
        CommercialFacilityType.UNIVERSITY_COLLEGE -> listOf(
            "commercial_interior_lighting", "commercial_exterior_lighting", "commercial_ceiling_fan",
            "commercial_ac_split", "commercial_ac_package", "commercial_computer", "commercial_laptop",
            "commercial_smart_board", "commercial_projector", "commercial_printer", "commercial_copier",
            "commercial_network_equipment", "commercial_server", "commercial_cctv_nvr",
            "commercial_security_system", "commercial_pa_system", "commercial_pump", "commercial_water_heater",
            "commercial_kitchen_equipment", "commercial_refrigeration", "commercial_freezer",
            "commercial_laboratory_equipment", "commercial_workshop_equipment", "commercial_elevator"
        )
        CommercialFacilityType.CLINIC -> listOf(
            "commercial_ac_split", "commercial_display_fridge", "commercial_medical_refrigerator",
            "commercial_freezer", "commercial_computer", "commercial_printer", "commercial_network_equipment",
            "commercial_cctv_nvr", "commercial_interior_lighting", "commercial_examination_equipment",
            "commercial_patient_monitoring", "commercial_laboratory_equipment",
            "commercial_sterilization_equipment", "commercial_autoclave", "commercial_pump",
            "commercial_water_heater", "commercial_ups", "commercial_emergency_lighting"
        )
        CommercialFacilityType.MEDICAL_CENTRE -> listOf(
            "commercial_ac_split", "commercial_ac_package", "commercial_display_fridge",
            "commercial_medical_refrigerator", "commercial_freezer", "commercial_computer", "commercial_printer",
            "commercial_network_equipment", "commercial_cctv_nvr", "commercial_interior_lighting",
            "commercial_examination_equipment", "commercial_patient_monitoring",
            "commercial_laboratory_equipment", "commercial_sterilization_equipment", "commercial_autoclave",
            "commercial_medical_imaging", "commercial_dialysis_equipment", "commercial_diagnostic_equipment",
            "commercial_pump", "commercial_water_heater", "commercial_ups", "commercial_emergency_lighting"
        )
        CommercialFacilityType.PHARMACY -> listOf(
            "commercial_medical_refrigerator", "commercial_display_fridge", "commercial_computer",
            "commercial_pos_system", "commercial_printer", "commercial_network_equipment", "commercial_cctv_nvr",
            "commercial_security_system", "commercial_interior_lighting", "commercial_ac_split"
        )
        CommercialFacilityType.DENTAL_CLINIC -> listOf(
            "commercial_ac_split", "commercial_examination_equipment", "commercial_sterilization_equipment",
            "commercial_autoclave", "commercial_computer", "commercial_diagnostic_equipment",
            "commercial_interior_lighting", "commercial_water_heater", "commercial_pump",
            "commercial_network_equipment"
        )
        CommercialFacilityType.CHURCH -> listOf(
            "commercial_interior_lighting", "commercial_exterior_lighting", "commercial_pa_system",
            "commercial_ac_package", "commercial_ceiling_fan", "commercial_computer",
            "commercial_network_equipment", "commercial_cctv_nvr"
        )
        CommercialFacilityType.GYM_FITNESS_CENTRE -> listOf(
            "commercial_ac_package", "commercial_interior_lighting", "commercial_exterior_lighting",
            "commercial_water_heater", "commercial_pump", "commercial_display_fridge",
            "commercial_water_cooler", "commercial_cctv_nvr", "commercial_security_system",
            "commercial_network_equipment", "commercial_tv"
        )
        CommercialFacilityType.SALON_BARBERSHOP -> listOf(
            "commercial_ac_split", "commercial_interior_lighting", "commercial_water_heater",
            "commercial_pos_system", "commercial_computer", "commercial_tv"
        )
        CommercialFacilityType.WAREHOUSE -> listOf(
            "commercial_interior_lighting", "commercial_exterior_lighting", "commercial_exhaust_fan",
            "commercial_pump", "commercial_computer", "commercial_network_equipment", "commercial_cctv_nvr",
            "commercial_security_system", "commercial_automatic_door", "commercial_conveyor",
            "commercial_workshop_equipment"
        )
        CommercialFacilityType.WORKSHOP_GARAGE -> listOf(
            "commercial_workshop_equipment", "commercial_compressor", "commercial_motor",
            "commercial_interior_lighting", "commercial_exterior_lighting", "commercial_exhaust_fan",
            "commercial_computer", "commercial_network_equipment", "commercial_cctv_nvr"
        )
        CommercialFacilityType.CAR_WASH -> listOf(
            "commercial_pump", "commercial_motor", "commercial_compressor", "commercial_water_heater",
            "commercial_exterior_lighting", "commercial_interior_lighting", "commercial_cctv_nvr",
            "commercial_security_system", "commercial_pos_system"
        )
        CommercialFacilityType.GAS_STATION -> listOf(
            "commercial_pump", "commercial_exterior_lighting", "commercial_interior_lighting",
            "commercial_signage", "commercial_pos_system", "commercial_display_fridge", "commercial_freezer",
            "commercial_cctv_nvr", "commercial_security_system", "commercial_ac_split"
        )
        CommercialFacilityType.SMALL_MANUFACTURING -> listOf(
            "commercial_motor", "commercial_compressor", "commercial_workshop_equipment",
            "commercial_exhaust_fan", "commercial_interior_lighting", "commercial_computer",
            "commercial_network_equipment", "commercial_cctv_nvr", "commercial_security_system"
        )
        CommercialFacilityType.COLD_STORAGE_FACILITY -> listOf(
            "commercial_refrigeration", "commercial_freezer", "commercial_compressor", "commercial_exhaust_fan",
            "commercial_interior_lighting", "commercial_network_equipment", "commercial_cctv_nvr",
            "commercial_security_system"
        )
        CommercialFacilityType.DATA_IT_FACILITY -> listOf(
            "commercial_server", "commercial_network_equipment", "commercial_ups", "commercial_ac_package",
            "commercial_interior_lighting", "commercial_cctv_nvr", "commercial_access_control",
            "commercial_security_system"
        )
        CommercialFacilityType.CUSTOM -> emptyList()
    }
}
