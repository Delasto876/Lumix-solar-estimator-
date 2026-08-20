package com.lumix.estimator.domain.commercial

import com.lumix.estimator.domain.SystemType

/**
 * Phase 27 §2 ("Expand the load/appliance system so available loads depend on selected system
 * type... The load catalog must be extensible rather than hard-coded to only these examples"):
 * seeded with the spec's own worked example lists for §2 (Commercial/Industrial), same "extensible
 * list, not a closed enum" shape [LoadDefinition] itself documents. [ApplianceType]
 * [com.lumix.estimator.domain.ApplianceType] (residential) is deliberately left untouched — this
 * is a wholly separate, additive catalog, not a replacement.
 *
 * Wattage figures below are typical/illustrative starting points for the picker (an installer can
 * always override [LoadInstance.ratedWatts] with the real nameplate value) — NOT manufacturer
 * datasheet figures, the same "generic engineering placeholder, not a measured figure" caveat this
 * codebase already applies everywhere a real spec isn't available (see e.g. [com.lumix.estimator
 * .domain.simulation.SimSystemConfig.inverterSelfConsumptionKw]'s own doc for the established
 * pattern this follows).
 */
object CommercialIndustrialLoadCatalog {

    val commercialLoads: List<LoadDefinition> = listOf(
        LoadDefinition("commercial_ac_split", "Air Conditioning (split unit)", LoadCategory.COMMERCIAL, defaultRatedWatts = 1500.0, defaultPowerFactor = 0.9, defaultOperationType = LoadOperationType.INTERMITTENT, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.0),
        LoadDefinition("commercial_ac_package", "Air Conditioning (packaged/rooftop unit)", LoadCategory.COMMERCIAL, defaultRatedWatts = 7500.0, defaultVoltage = 240.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.85, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.0),
        LoadDefinition("commercial_refrigeration", "Commercial Refrigeration (walk-in cooler)", LoadCategory.COMMERCIAL, defaultRatedWatts = 1200.0, defaultPowerFactor = 0.85, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.0),
        LoadDefinition("commercial_freezer", "Freezer (commercial)", LoadCategory.COMMERCIAL, defaultRatedWatts = 900.0, defaultPowerFactor = 0.85, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.0),
        LoadDefinition("commercial_display_fridge", "Display Refrigerator", LoadCategory.COMMERCIAL, defaultRatedWatts = 600.0, defaultPowerFactor = 0.9, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 2.5),
        LoadDefinition("commercial_lighting", "Commercial Lighting (per fixture)", LoadCategory.COMMERCIAL, defaultRatedWatts = 40.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS),
        LoadDefinition("commercial_office_equipment", "Office Equipment (general)", LoadCategory.COMMERCIAL, defaultRatedWatts = 150.0, defaultPowerFactor = 0.98),
        LoadDefinition("commercial_pos_system", "POS System", LoadCategory.COMMERCIAL, defaultRatedWatts = 80.0, defaultPowerFactor = 0.98, defaultPriority = LoadPriority.CRITICAL),
        LoadDefinition("commercial_computer", "Computer / Workstation", LoadCategory.COMMERCIAL, defaultRatedWatts = 200.0, defaultPowerFactor = 0.98),
        LoadDefinition("commercial_server", "Server / Network Equipment", LoadCategory.COMMERCIAL, defaultRatedWatts = 500.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS, defaultPriority = LoadPriority.CRITICAL),
        LoadDefinition("commercial_pump", "Pump (commercial)", LoadCategory.COMMERCIAL, defaultRatedWatts = 1500.0, defaultPowerFactor = 0.85, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.5),
        LoadDefinition("commercial_compressor", "Compressor (commercial)", LoadCategory.COMMERCIAL, defaultRatedWatts = 2200.0, defaultPowerFactor = 0.85, isMotorLoad = true, defaultStartingSurgeMultiplier = 4.0),
        LoadDefinition("commercial_kitchen_equipment", "Commercial Kitchen Equipment", LoadCategory.COMMERCIAL, defaultRatedWatts = 3000.0, defaultPowerFactor = 0.95),
        LoadDefinition("commercial_water_heater", "Water Heater (commercial)", LoadCategory.COMMERCIAL, defaultRatedWatts = 4500.0, defaultPowerFactor = 1.0, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("commercial_motor", "Motor (general commercial)", LoadCategory.COMMERCIAL, defaultRatedWatts = 750.0, defaultPowerFactor = 0.82, isMotorLoad = true, defaultStartingSurgeMultiplier = 4.0),
        LoadDefinition("commercial_elevator", "Elevator", LoadCategory.COMMERCIAL, defaultRatedWatts = 5000.0, defaultVoltage = 240.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.85, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.0, defaultPriority = LoadPriority.CRITICAL),
        LoadDefinition("commercial_workshop_equipment", "Workshop Equipment", LoadCategory.COMMERCIAL, defaultRatedWatts = 1000.0, defaultPowerFactor = 0.85, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.0),
        // Phase 28 §7 ("Expand the commercial appliance database"): additive entries — every id
        // above is untouched, so an already-saved LoadInstance referencing one still resolves.
        LoadDefinition("commercial_ice_machine", "Ice Machine", LoadCategory.COMMERCIAL, defaultRatedWatts = 800.0, defaultPowerFactor = 0.85, defaultOperationType = LoadOperationType.INTERMITTENT, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.0),
        LoadDefinition("commercial_blender", "Commercial Blender", LoadCategory.COMMERCIAL, defaultRatedWatts = 1000.0, defaultPowerFactor = 0.9, isMotorLoad = true, defaultStartingSurgeMultiplier = 2.5),
        LoadDefinition("commercial_coffee_machine", "Coffee Machine", LoadCategory.COMMERCIAL, defaultRatedWatts = 1500.0, defaultPowerFactor = 1.0, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("commercial_microwave", "Microwave", LoadCategory.COMMERCIAL, defaultRatedWatts = 1800.0, defaultPowerFactor = 0.98, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("commercial_oven", "Commercial Oven", LoadCategory.COMMERCIAL, defaultRatedWatts = 6000.0, defaultVoltage = 240.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 1.0, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("commercial_electric_range", "Electric Range", LoadCategory.COMMERCIAL, defaultRatedWatts = 8000.0, defaultVoltage = 240.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 1.0, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("commercial_printer", "Printer", LoadCategory.COMMERCIAL, defaultRatedWatts = 50.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("commercial_copier", "Copier", LoadCategory.COMMERCIAL, defaultRatedWatts = 1200.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("commercial_network_equipment", "Network Equipment (router/switch/AP)", LoadCategory.COMMERCIAL, defaultRatedWatts = 150.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS, defaultPriority = LoadPriority.CRITICAL),
        LoadDefinition("commercial_security_system", "Security System", LoadCategory.COMMERCIAL, defaultRatedWatts = 60.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS, defaultPriority = LoadPriority.CRITICAL),
        LoadDefinition("commercial_cctv_nvr", "CCTV / NVR", LoadCategory.COMMERCIAL, defaultRatedWatts = 100.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS, defaultPriority = LoadPriority.CRITICAL),
        LoadDefinition("commercial_exhaust_fan", "Exhaust Fan", LoadCategory.COMMERCIAL, defaultRatedWatts = 400.0, defaultPowerFactor = 0.85, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 2.5),
        LoadDefinition("commercial_signage", "Signage", LoadCategory.COMMERCIAL, defaultRatedWatts = 150.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS),
        LoadDefinition("commercial_exterior_lighting", "Exterior Lighting", LoadCategory.COMMERCIAL, defaultRatedWatts = 60.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS),
        LoadDefinition("commercial_interior_lighting", "Interior Lighting (per fixture)", LoadCategory.COMMERCIAL, defaultRatedWatts = 40.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS),
        LoadDefinition("commercial_custom", "Custom Commercial Load", LoadCategory.COMMERCIAL, defaultRatedWatts = 0.0, isCustom = true)
    )

    val industrialLoads: List<LoadDefinition> = listOf(
        LoadDefinition("industrial_motor", "Motor (industrial)", LoadCategory.INDUSTRIAL, defaultRatedWatts = 5000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.82, isMotorLoad = true, defaultStartingSurgeMultiplier = 5.0),
        LoadDefinition("industrial_pump", "Pump (industrial)", LoadCategory.INDUSTRIAL, defaultRatedWatts = 7500.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.83, isMotorLoad = true, defaultStartingSurgeMultiplier = 5.0),
        LoadDefinition("industrial_compressor", "Compressor (industrial)", LoadCategory.INDUSTRIAL, defaultRatedWatts = 11000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.83, isMotorLoad = true, defaultStartingSurgeMultiplier = 5.5),
        LoadDefinition("industrial_refrigeration", "Industrial Refrigeration", LoadCategory.INDUSTRIAL, defaultRatedWatts = 15000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.85, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 4.5),
        LoadDefinition("industrial_production_machinery", "Production Machinery", LoadCategory.INDUSTRIAL, defaultRatedWatts = 10000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.8, isMotorLoad = true, defaultStartingSurgeMultiplier = 4.0),
        LoadDefinition("industrial_three_phase_equipment", "Three-Phase Equipment (general)", LoadCategory.INDUSTRIAL, defaultRatedWatts = 8000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.85),
        LoadDefinition("industrial_welder", "Welder", LoadCategory.INDUSTRIAL, defaultRatedWatts = 6000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.7, defaultOperationType = LoadOperationType.INTERMITTENT, defaultStartingSurgeMultiplier = 2.0),
        LoadDefinition("industrial_large_hvac", "Large HVAC", LoadCategory.INDUSTRIAL, defaultRatedWatts = 20000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.85, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.5),
        LoadDefinition("industrial_conveyor", "Conveyor System", LoadCategory.INDUSTRIAL, defaultRatedWatts = 3700.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.82, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 4.0),
        LoadDefinition("industrial_process_equipment", "Process Equipment", LoadCategory.INDUSTRIAL, defaultRatedWatts = 12000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.85, defaultOperationType = LoadOperationType.CONTINUOUS),
        LoadDefinition("industrial_large_lighting", "Large Lighting System", LoadCategory.INDUSTRIAL, defaultRatedWatts = 5000.0, defaultPowerFactor = 0.9, defaultOperationType = LoadOperationType.CONTINUOUS),
        LoadDefinition("industrial_plc_controls", "Controls / PLC", LoadCategory.INDUSTRIAL, defaultRatedWatts = 500.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS, defaultPriority = LoadPriority.CRITICAL),
        LoadDefinition("industrial_electronics", "Industrial Electronics", LoadCategory.INDUSTRIAL, defaultRatedWatts = 800.0, defaultPowerFactor = 0.95, defaultPriority = LoadPriority.CRITICAL),
        // Phase 28 §8 ("Add an industrial load category... Include: Fans, CNC machines, Workshop
        // equipment, Servers/network equipment, Security systems"): additive entries.
        LoadDefinition("industrial_fan", "Ventilation / Exhaust Fan (industrial)", LoadCategory.INDUSTRIAL, defaultRatedWatts = 1500.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.85, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.0),
        LoadDefinition("industrial_cnc_machine", "CNC Machine", LoadCategory.INDUSTRIAL, defaultRatedWatts = 9000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.85, defaultOperationType = LoadOperationType.INTERMITTENT, isMotorLoad = true, defaultStartingSurgeMultiplier = 4.0),
        LoadDefinition("industrial_workshop_equipment", "Workshop Equipment (industrial)", LoadCategory.INDUSTRIAL, defaultRatedWatts = 2000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.85, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.5),
        LoadDefinition("industrial_servers_network", "Servers / Network Equipment", LoadCategory.INDUSTRIAL, defaultRatedWatts = 1000.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS, defaultPriority = LoadPriority.CRITICAL),
        LoadDefinition("industrial_security_system", "Security System (industrial)", LoadCategory.INDUSTRIAL, defaultRatedWatts = 100.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS, defaultPriority = LoadPriority.CRITICAL),
        LoadDefinition("industrial_custom", "Custom Industrial Load", LoadCategory.INDUSTRIAL, defaultRatedWatts = 0.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, isCustom = true)
    )

    /** RESIDENTIAL has no entries here — it keeps using [com.lumix.estimator.domain.ApplianceType], unchanged. */
    fun loadsFor(systemType: SystemType): List<LoadDefinition> = when (systemType) {
        SystemType.RESIDENTIAL -> emptyList()
        SystemType.COMMERCIAL -> commercialLoads
        SystemType.INDUSTRIAL -> industrialLoads
    }

    fun definitionById(id: String): LoadDefinition? =
        (commercialLoads + industrialLoads).firstOrNull { it.id == id }
}
