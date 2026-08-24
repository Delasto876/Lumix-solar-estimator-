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
 *
 * Load-Sheet round (user-supplied "Lumix Load Sheet Defaults" spreadsheet, a structured engineering
 * reference rather than a single manufacturer datasheet): `defaultRatedWatts` for every entry with a
 * clean 1:1 semantic match to a sheet row was corrected to the sheet's own "Most Likely Operating W"
 * (its Modeling Rules sheet's own stated purpose: "the default average/typical operating point for
 * energy estimates; user can edit") — a real improvement on this file's earlier hand-guessed
 * figures, not a second guess. `defaultPowerFactor` and (new) `defaultHoursPerDay` were corrected/
 * added the same way. The sheet's separate "Peak/Nameplate W" column (its own stated purpose: "peak/
 * inverter checks, not continuous energy consumption") is NOT wired in this round — this catalog has
 * no existing concept distinct from `defaultRatedWatts` to hold it without changing what
 * `connectedLoadKw`/`connectedApparentPowerKva` (§5's own "Connected Load") mean for every load, a
 * bigger change than a defaults correction; deferred, not silently dropped. Sheet rows describing a
 * whole-facility aggregate rather than a single unit (e.g. "Office LED lighting," "Commercial AC" as
 * a central-plant figure) were left untouched for entries that are genuinely per-unit in this catalog
 * (e.g. `commercial_interior_lighting`'s per-fixture wattage) — applying an aggregate figure to a
 * per-unit field would have been wrong, not a correction.
 */
object CommercialIndustrialLoadCatalog {

    val commercialLoads: List<LoadDefinition> = listOf(
        // Phase 29 ("use BTU for AC unit instead of watts"): isAcLoad = true switches the editor
        // to a BTU input, matching the residential wizard's own AC picker. defaultBtu -> watts uses
        // the same 10 BTU/W ratio as SystemCalculator.acBtuPerWatt(NON_INVERTER) — 15000 BTU/10 =
        // 1500W, 75000 BTU/10 = 7500W, so the existing defaultRatedWatts figures are unchanged.
        LoadDefinition("commercial_ac_split", "Air Conditioning (split unit)", LoadCategory.COMMERCIAL, defaultRatedWatts = 1500.0, defaultPowerFactor = 0.9, defaultOperationType = LoadOperationType.INTERMITTENT, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.0, isAcLoad = true, defaultBtu = 15000.0),
        LoadDefinition("commercial_ac_package", "Air Conditioning (packaged/rooftop unit)", LoadCategory.COMMERCIAL, defaultRatedWatts = 6000.0, defaultVoltage = 240.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.9, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.0, isAcLoad = true, defaultBtu = 75000.0, defaultHoursPerDay = 8.0),
        LoadDefinition("commercial_refrigeration", "Commercial Refrigeration (walk-in cooler)", LoadCategory.COMMERCIAL, defaultRatedWatts = 2500.0, defaultPowerFactor = 0.9, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.0, defaultHoursPerDay = 24.0),
        LoadDefinition("commercial_freezer", "Freezer (commercial)", LoadCategory.COMMERCIAL, defaultRatedWatts = 1200.0, defaultPowerFactor = 0.9, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.0, defaultHoursPerDay = 24.0),
        LoadDefinition("commercial_display_fridge", "Display Refrigerator", LoadCategory.COMMERCIAL, defaultRatedWatts = 800.0, defaultPowerFactor = 0.9, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 2.5, defaultHoursPerDay = 24.0),
        LoadDefinition("commercial_lighting", "Commercial Lighting (per fixture)", LoadCategory.COMMERCIAL, defaultRatedWatts = 40.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS),
        LoadDefinition("commercial_office_equipment", "Office Equipment (general)", LoadCategory.COMMERCIAL, defaultRatedWatts = 150.0, defaultPowerFactor = 0.98),
        LoadDefinition("commercial_pos_system", "POS System", LoadCategory.COMMERCIAL, defaultRatedWatts = 100.0, defaultPowerFactor = 0.95, defaultPriority = LoadPriority.CRITICAL, defaultHoursPerDay = 8.0),
        LoadDefinition("commercial_computer", "Computer / Workstation", LoadCategory.COMMERCIAL, defaultRatedWatts = 275.0, defaultPowerFactor = 0.9, defaultHoursPerDay = 8.0),
        LoadDefinition("commercial_server", "Server / Network Equipment", LoadCategory.COMMERCIAL, defaultRatedWatts = 1500.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS, defaultPriority = LoadPriority.CRITICAL, defaultHoursPerDay = 24.0),
        LoadDefinition("commercial_pump", "Pump (commercial)", LoadCategory.COMMERCIAL, defaultRatedWatts = 1500.0, defaultPowerFactor = 0.8, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.5, defaultHoursPerDay = 2.0),
        LoadDefinition("commercial_compressor", "Compressor (commercial)", LoadCategory.COMMERCIAL, defaultRatedWatts = 2200.0, defaultPowerFactor = 0.85, isMotorLoad = true, defaultStartingSurgeMultiplier = 4.0),
        LoadDefinition("commercial_kitchen_equipment", "Commercial Kitchen Equipment", LoadCategory.COMMERCIAL, defaultRatedWatts = 3000.0, defaultPowerFactor = 0.95),
        LoadDefinition("commercial_water_heater", "Water Heater (commercial)", LoadCategory.COMMERCIAL, defaultRatedWatts = 6000.0, defaultPowerFactor = 1.0, defaultOperationType = LoadOperationType.INTERMITTENT, defaultHoursPerDay = 2.0),
        LoadDefinition("commercial_motor", "Motor (general commercial)", LoadCategory.COMMERCIAL, defaultRatedWatts = 750.0, defaultPowerFactor = 0.82, isMotorLoad = true, defaultStartingSurgeMultiplier = 4.0),
        LoadDefinition("commercial_elevator", "Elevator", LoadCategory.COMMERCIAL, defaultRatedWatts = 5000.0, defaultVoltage = 240.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.85, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.0, defaultPriority = LoadPriority.CRITICAL),
        LoadDefinition("commercial_workshop_equipment", "Workshop Equipment", LoadCategory.COMMERCIAL, defaultRatedWatts = 1000.0, defaultPowerFactor = 0.85, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.0),
        // Phase 28 §7 ("Expand the commercial appliance database"): additive entries — every id
        // above is untouched, so an already-saved LoadInstance referencing one still resolves.
        LoadDefinition("commercial_ice_machine", "Ice Machine", LoadCategory.COMMERCIAL, defaultRatedWatts = 1200.0, defaultPowerFactor = 0.9, defaultOperationType = LoadOperationType.INTERMITTENT, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.0, defaultHoursPerDay = 4.0),
        LoadDefinition("commercial_blender", "Commercial Blender", LoadCategory.COMMERCIAL, defaultRatedWatts = 1000.0, defaultPowerFactor = 0.9, isMotorLoad = true, defaultStartingSurgeMultiplier = 2.5),
        LoadDefinition("commercial_coffee_machine", "Coffee Machine", LoadCategory.COMMERCIAL, defaultRatedWatts = 1500.0, defaultPowerFactor = 1.0, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("commercial_microwave", "Microwave", LoadCategory.COMMERCIAL, defaultRatedWatts = 1800.0, defaultPowerFactor = 0.98, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("commercial_oven", "Commercial Oven", LoadCategory.COMMERCIAL, defaultRatedWatts = 7000.0, defaultVoltage = 240.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.INTERMITTENT, defaultHoursPerDay = 4.0),
        LoadDefinition("commercial_electric_range", "Electric Range", LoadCategory.COMMERCIAL, defaultRatedWatts = 5000.0, defaultVoltage = 240.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.INTERMITTENT, defaultHoursPerDay = 4.0),
        LoadDefinition("commercial_printer", "Printer", LoadCategory.COMMERCIAL, defaultRatedWatts = 400.0, defaultPowerFactor = 0.9, defaultOperationType = LoadOperationType.INTERMITTENT, defaultHoursPerDay = 0.5),
        LoadDefinition("commercial_copier", "Copier", LoadCategory.COMMERCIAL, defaultRatedWatts = 900.0, defaultPowerFactor = 0.9, defaultOperationType = LoadOperationType.INTERMITTENT, defaultHoursPerDay = 0.75),
        LoadDefinition("commercial_network_equipment", "Network Equipment (router/switch/AP)", LoadCategory.COMMERCIAL, defaultRatedWatts = 150.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS, defaultPriority = LoadPriority.CRITICAL, defaultHoursPerDay = 24.0),
        LoadDefinition("commercial_security_system", "Security System", LoadCategory.COMMERCIAL, defaultRatedWatts = 60.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS, defaultPriority = LoadPriority.CRITICAL),
        LoadDefinition("commercial_cctv_nvr", "CCTV / NVR", LoadCategory.COMMERCIAL, defaultRatedWatts = 100.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS, defaultPriority = LoadPriority.CRITICAL),
        LoadDefinition("commercial_exhaust_fan", "Exhaust Fan", LoadCategory.COMMERCIAL, defaultRatedWatts = 1000.0, defaultPowerFactor = 0.8, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 2.5, defaultHoursPerDay = 8.0),
        LoadDefinition("commercial_signage", "Signage", LoadCategory.COMMERCIAL, defaultRatedWatts = 800.0, defaultPowerFactor = 1.0, defaultOperationType = LoadOperationType.INTERMITTENT, defaultHoursPerDay = 6.0),
        LoadDefinition("commercial_exterior_lighting", "Exterior Lighting", LoadCategory.COMMERCIAL, defaultRatedWatts = 60.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS),
        LoadDefinition("commercial_interior_lighting", "Interior Lighting (per fixture)", LoadCategory.COMMERCIAL, defaultRatedWatts = 40.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS),
        // Phase 44 (spec §4-§10 — the per-facility-type default load lists): additive entries this
        // round added to cover items those sections name that the existing catalog above didn't yet
        // have a match for. Every id above this comment is untouched — an already-saved LoadInstance
        // referencing one still resolves, same as every prior additive round to this catalog.
        LoadDefinition("commercial_conveyor", "Conveyor Motor", LoadCategory.COMMERCIAL, defaultRatedWatts = 2000.0, defaultPowerFactor = 0.8, defaultOperationType = LoadOperationType.INTERMITTENT, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.5, defaultHoursPerDay = 4.0),
        LoadDefinition("commercial_automatic_door", "Automatic Door", LoadCategory.COMMERCIAL, defaultRatedWatts = 300.0, defaultPowerFactor = 0.85, defaultOperationType = LoadOperationType.INTERMITTENT, isMotorLoad = true, defaultStartingSurgeMultiplier = 2.5),
        LoadDefinition("commercial_scale", "Scale (retail/bakery)", LoadCategory.COMMERCIAL, defaultRatedWatts = 50.0, defaultPowerFactor = 0.95),
        LoadDefinition("commercial_barcode_scanner", "Barcode Scanner", LoadCategory.COMMERCIAL, defaultRatedWatts = 15.0, defaultPowerFactor = 0.95),
        LoadDefinition("commercial_bakery_equipment", "Bakery Equipment", LoadCategory.COMMERCIAL, defaultRatedWatts = 5000.0, defaultVoltage = 240.0, defaultPowerFactor = 1.0, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("commercial_ceiling_fan", "Ceiling Fan", LoadCategory.COMMERCIAL, defaultRatedWatts = 75.0, defaultPowerFactor = 0.9, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 2.0),
        LoadDefinition("commercial_smart_board", "Smart Board", LoadCategory.COMMERCIAL, defaultRatedWatts = 150.0, defaultPowerFactor = 0.98, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("commercial_projector", "Projector", LoadCategory.COMMERCIAL, defaultRatedWatts = 300.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("commercial_pa_system", "PA / Audio System", LoadCategory.COMMERCIAL, defaultRatedWatts = 200.0, defaultPowerFactor = 0.9, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("commercial_laptop", "Laptop / Mobile Computer", LoadCategory.COMMERCIAL, defaultRatedWatts = 120.0, defaultPowerFactor = 0.95, defaultHoursPerDay = 8.0),
        LoadDefinition("commercial_voip_phone", "VoIP Phone", LoadCategory.COMMERCIAL, defaultRatedWatts = 8.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS),
        LoadDefinition("commercial_ups", "UPS System", LoadCategory.COMMERCIAL, defaultRatedWatts = 300.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS, defaultPriority = LoadPriority.CRITICAL),
        LoadDefinition("commercial_access_control", "Access Control System", LoadCategory.COMMERCIAL, defaultRatedWatts = 40.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS, defaultPriority = LoadPriority.CRITICAL),
        LoadDefinition("commercial_water_cooler", "Water Cooler / Dispenser", LoadCategory.COMMERCIAL, defaultRatedWatts = 100.0, defaultPowerFactor = 0.9, defaultOperationType = LoadOperationType.CONTINUOUS),
        LoadDefinition("commercial_emergency_lighting", "Emergency Lighting", LoadCategory.COMMERCIAL, defaultRatedWatts = 50.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS, defaultPriority = LoadPriority.CRITICAL),
        LoadDefinition("commercial_fryer", "Deep Fryer", LoadCategory.COMMERCIAL, defaultRatedWatts = 9000.0, defaultVoltage = 240.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.INTERMITTENT, defaultHoursPerDay = 4.0),
        LoadDefinition("commercial_griddle", "Griddle", LoadCategory.COMMERCIAL, defaultRatedWatts = 3500.0, defaultVoltage = 240.0, defaultPowerFactor = 1.0, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("commercial_mixer", "Commercial Mixer", LoadCategory.COMMERCIAL, defaultRatedWatts = 750.0, defaultPowerFactor = 0.85, defaultOperationType = LoadOperationType.INTERMITTENT, isMotorLoad = true, defaultStartingSurgeMultiplier = 2.5),
        LoadDefinition("commercial_dishwasher", "Commercial Dishwasher", LoadCategory.COMMERCIAL, defaultRatedWatts = 6000.0, defaultVoltage = 240.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.INTERMITTENT, defaultHoursPerDay = 2.0),
        LoadDefinition("commercial_exhaust_hood", "Exhaust Hood", LoadCategory.COMMERCIAL, defaultRatedWatts = 500.0, defaultPowerFactor = 0.85, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 2.5),
        LoadDefinition("commercial_pool_pump", "Pool Pump", LoadCategory.COMMERCIAL, defaultRatedWatts = 1500.0, defaultPowerFactor = 0.8, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.0, defaultHoursPerDay = 6.0),
        LoadDefinition("commercial_pool_equipment", "Pool Equipment (filter/heater)", LoadCategory.COMMERCIAL, defaultRatedWatts = 1500.0, defaultPowerFactor = 0.9, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("commercial_laundry_equipment", "Laundry Equipment (washer/dryer)", LoadCategory.COMMERCIAL, defaultRatedWatts = 5000.0, defaultVoltage = 240.0, defaultPowerFactor = 1.0, defaultOperationType = LoadOperationType.INTERMITTENT, isMotorLoad = true, defaultStartingSurgeMultiplier = 2.5, defaultHoursPerDay = 3.0),
        LoadDefinition("commercial_tv", "Television", LoadCategory.COMMERCIAL, defaultRatedWatts = 120.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.INTERMITTENT),
        // Phase 44 (spec §7 — "IMPORTANT: Medical equipment must NOT receive arbitrary wattage
        // values. Where manufacturer-rated power is unknown, mark the load as: 'Verify equipment
        // specification' and allow manual entry"): these all default to 0W rather than an invented
        // "typical" figure — the label itself carries the disclaimer, and the installer types in the
        // real nameplate rating once they have it (0W simply contributes nothing until they do).
        LoadDefinition("commercial_medical_refrigerator", "Medical Refrigerator (verify equipment specification)", LoadCategory.COMMERCIAL, defaultRatedWatts = 0.0, defaultOperationType = LoadOperationType.CONTINUOUS),
        LoadDefinition("commercial_examination_equipment", "Examination Equipment (verify equipment specification)", LoadCategory.COMMERCIAL, defaultRatedWatts = 0.0),
        LoadDefinition("commercial_patient_monitoring", "Patient Monitoring Equipment (verify equipment specification)", LoadCategory.COMMERCIAL, defaultRatedWatts = 0.0, defaultOperationType = LoadOperationType.CONTINUOUS, defaultPriority = LoadPriority.CRITICAL),
        LoadDefinition("commercial_sterilization_equipment", "Sterilization Equipment (verify equipment specification)", LoadCategory.COMMERCIAL, defaultRatedWatts = 0.0, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("commercial_autoclave", "Autoclave (verify equipment specification)", LoadCategory.COMMERCIAL, defaultRatedWatts = 0.0, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("commercial_medical_imaging", "Medical Imaging Equipment (verify equipment specification)", LoadCategory.COMMERCIAL, defaultRatedWatts = 0.0, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("commercial_dialysis_equipment", "Dialysis Equipment (verify equipment specification)", LoadCategory.COMMERCIAL, defaultRatedWatts = 0.0, defaultOperationType = LoadOperationType.INTERMITTENT, defaultPriority = LoadPriority.CRITICAL),
        LoadDefinition("commercial_diagnostic_equipment", "Diagnostic / Scanner Equipment (verify equipment specification)", LoadCategory.COMMERCIAL, defaultRatedWatts = 0.0, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("commercial_laboratory_equipment", "Laboratory Equipment (verify equipment specification)", LoadCategory.COMMERCIAL, defaultRatedWatts = 0.0),
        LoadDefinition("commercial_custom", "Custom Commercial Load", LoadCategory.COMMERCIAL, defaultRatedWatts = 0.0, isCustom = true)
    )

    val industrialLoads: List<LoadDefinition> = listOf(
        LoadDefinition("industrial_motor", "Motor (industrial)", LoadCategory.INDUSTRIAL, defaultRatedWatts = 5000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.82, isMotorLoad = true, defaultStartingSurgeMultiplier = 5.0),
        LoadDefinition("industrial_pump", "Pump (industrial)", LoadCategory.INDUSTRIAL, defaultRatedWatts = 12000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.8, isMotorLoad = true, defaultStartingSurgeMultiplier = 5.0, defaultHoursPerDay = 8.0),
        LoadDefinition("industrial_compressor", "Compressor (industrial)", LoadCategory.INDUSTRIAL, defaultRatedWatts = 20000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.8, isMotorLoad = true, defaultStartingSurgeMultiplier = 5.5, defaultHoursPerDay = 8.0),
        LoadDefinition("industrial_refrigeration", "Industrial Refrigeration", LoadCategory.INDUSTRIAL, defaultRatedWatts = 20000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.8, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 4.5, defaultHoursPerDay = 24.0),
        LoadDefinition("industrial_production_machinery", "Production Machinery", LoadCategory.INDUSTRIAL, defaultRatedWatts = 12000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.8, isMotorLoad = true, defaultStartingSurgeMultiplier = 4.0, defaultHoursPerDay = 8.0),
        LoadDefinition("industrial_three_phase_equipment", "Three-Phase Equipment (general)", LoadCategory.INDUSTRIAL, defaultRatedWatts = 8000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.85),
        LoadDefinition("industrial_welder", "Welder", LoadCategory.INDUSTRIAL, defaultRatedWatts = 8000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.7, defaultOperationType = LoadOperationType.INTERMITTENT, defaultStartingSurgeMultiplier = 2.0, defaultHoursPerDay = 2.0),
        LoadDefinition("industrial_large_hvac", "Large HVAC", LoadCategory.INDUSTRIAL, defaultRatedWatts = 40000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.9, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.5, isAcLoad = true, defaultBtu = 200000.0, defaultHoursPerDay = 8.0),
        LoadDefinition("industrial_conveyor", "Conveyor System", LoadCategory.INDUSTRIAL, defaultRatedWatts = 6000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.8, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 4.0, defaultHoursPerDay = 8.0),
        LoadDefinition("industrial_process_equipment", "Process Equipment", LoadCategory.INDUSTRIAL, defaultRatedWatts = 12000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.85, defaultOperationType = LoadOperationType.CONTINUOUS),
        LoadDefinition("industrial_large_lighting", "Large Lighting System", LoadCategory.INDUSTRIAL, defaultRatedWatts = 10000.0, defaultPowerFactor = 1.0, defaultOperationType = LoadOperationType.CONTINUOUS, defaultHoursPerDay = 8.0),
        LoadDefinition("industrial_plc_controls", "Controls / PLC", LoadCategory.INDUSTRIAL, defaultRatedWatts = 500.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS, defaultPriority = LoadPriority.CRITICAL, defaultHoursPerDay = 24.0),
        LoadDefinition("industrial_electronics", "Industrial Electronics", LoadCategory.INDUSTRIAL, defaultRatedWatts = 800.0, defaultPowerFactor = 0.95, defaultPriority = LoadPriority.CRITICAL),
        // Phase 28 §8 ("Add an industrial load category... Include: Fans, CNC machines, Workshop
        // equipment, Servers/network equipment, Security systems"): additive entries.
        LoadDefinition("industrial_fan", "Ventilation / Exhaust Fan (industrial)", LoadCategory.INDUSTRIAL, defaultRatedWatts = 6000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.8, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.0, defaultHoursPerDay = 8.0),
        LoadDefinition("industrial_cnc_machine", "CNC Machine", LoadCategory.INDUSTRIAL, defaultRatedWatts = 12000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.85, defaultOperationType = LoadOperationType.INTERMITTENT, isMotorLoad = true, defaultStartingSurgeMultiplier = 4.0, defaultHoursPerDay = 8.0),
        LoadDefinition("industrial_workshop_equipment", "Workshop Equipment (industrial)", LoadCategory.INDUSTRIAL, defaultRatedWatts = 2000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.85, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.5),
        LoadDefinition("industrial_servers_network", "Servers / Network Equipment", LoadCategory.INDUSTRIAL, defaultRatedWatts = 1500.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS, defaultPriority = LoadPriority.CRITICAL, defaultHoursPerDay = 24.0),
        LoadDefinition("industrial_security_system", "Security System (industrial)", LoadCategory.INDUSTRIAL, defaultRatedWatts = 100.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS, defaultPriority = LoadPriority.CRITICAL),
        // Phase 45 (spec §11-§13 — Food Processing/Manufacturing/Refrigeration's own load lists,
        // plus reasonable coverage for the other 22 IndustrialFacilityType presets): additive
        // entries. Every id above this comment is untouched, so an already-saved LoadInstance
        // referencing one still resolves.
        LoadDefinition("industrial_freezer", "Freezer (industrial)", LoadCategory.INDUSTRIAL, defaultRatedWatts = 12000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.8, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 4.0, defaultHoursPerDay = 24.0),
        LoadDefinition("industrial_cold_room", "Cold Room", LoadCategory.INDUSTRIAL, defaultRatedWatts = 12000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.8, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 4.0, defaultHoursPerDay = 24.0),
        LoadDefinition("industrial_mixer", "Industrial Mixer", LoadCategory.INDUSTRIAL, defaultRatedWatts = 12000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.8, defaultOperationType = LoadOperationType.INTERMITTENT, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.5, defaultHoursPerDay = 6.0),
        LoadDefinition("industrial_oven", "Industrial Oven", LoadCategory.INDUSTRIAL, defaultRatedWatts = 30000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 1.0, defaultOperationType = LoadOperationType.INTERMITTENT, defaultHoursPerDay = 8.0),
        LoadDefinition("industrial_packaging_machine", "Packaging Machine", LoadCategory.INDUSTRIAL, defaultRatedWatts = 6000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.85, defaultOperationType = LoadOperationType.INTERMITTENT, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.0, defaultHoursPerDay = 8.0),
        LoadDefinition("industrial_filling_machine", "Filling Machine", LoadCategory.INDUSTRIAL, defaultRatedWatts = 2500.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.85, defaultOperationType = LoadOperationType.INTERMITTENT, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.0),
        LoadDefinition("industrial_sealing_machine", "Sealing Machine", LoadCategory.INDUSTRIAL, defaultRatedWatts = 1500.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.9, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("industrial_computer", "Computer / Workstation (industrial)", LoadCategory.INDUSTRIAL, defaultRatedWatts = 200.0, defaultPowerFactor = 0.98),
        LoadDefinition("industrial_emergency_lighting", "Emergency Lighting (industrial)", LoadCategory.INDUSTRIAL, defaultRatedWatts = 500.0, defaultPowerFactor = 1.0, defaultOperationType = LoadOperationType.CONTINUOUS, defaultPriority = LoadPriority.CRITICAL, defaultHoursPerDay = 24.0),
        LoadDefinition("industrial_fabrication_equipment", "Fabrication Equipment", LoadCategory.INDUSTRIAL, defaultRatedWatts = 8000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.83, defaultOperationType = LoadOperationType.INTERMITTENT, isMotorLoad = true, defaultStartingSurgeMultiplier = 4.0),
        LoadDefinition("industrial_hydraulic_equipment", "Hydraulic Equipment", LoadCategory.INDUSTRIAL, defaultRatedWatts = 7000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.83, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 4.0),
        LoadDefinition("industrial_dust_extraction", "Dust Extraction System", LoadCategory.INDUSTRIAL, defaultRatedWatts = 4000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.85, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.5),
        LoadDefinition("industrial_condenser_fan", "Condenser Fan", LoadCategory.INDUSTRIAL, defaultRatedWatts = 2200.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.85, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.0),
        LoadDefinition("industrial_evaporator_fan", "Evaporator Fan", LoadCategory.INDUSTRIAL, defaultRatedWatts = 1100.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.85, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.0),
        LoadDefinition("industrial_refrigeration_controls", "Refrigeration Controls", LoadCategory.INDUSTRIAL, defaultRatedWatts = 300.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS, defaultPriority = LoadPriority.CRITICAL),
        LoadDefinition("industrial_defrost_heater", "Defrost Heater", LoadCategory.INDUSTRIAL, defaultRatedWatts = 3000.0, defaultVoltage = 240.0, defaultPowerFactor = 1.0, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("industrial_cctv", "CCTV / Monitoring System (industrial)", LoadCategory.INDUSTRIAL, defaultRatedWatts = 150.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS, defaultPriority = LoadPriority.CRITICAL),
        LoadDefinition("industrial_concrete_mixer", "Concrete / Batching Mixer", LoadCategory.INDUSTRIAL, defaultRatedWatts = 15000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.83, defaultOperationType = LoadOperationType.INTERMITTENT, isMotorLoad = true, defaultStartingSurgeMultiplier = 5.0),
        LoadDefinition("industrial_aerator", "Aerator (water/wastewater treatment)", LoadCategory.INDUSTRIAL, defaultRatedWatts = 3700.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.83, defaultOperationType = LoadOperationType.CONTINUOUS, isMotorLoad = true, defaultStartingSurgeMultiplier = 4.0),
        LoadDefinition("industrial_forklift_charger", "Forklift Charging Station", LoadCategory.INDUSTRIAL, defaultRatedWatts = 3000.0, defaultVoltage = 240.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("industrial_printing_press", "Printing Press", LoadCategory.INDUSTRIAL, defaultRatedWatts = 10000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.85, defaultOperationType = LoadOperationType.INTERMITTENT, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.5),
        LoadDefinition("industrial_crusher", "Crusher (quarry/aggregate)", LoadCategory.INDUSTRIAL, defaultRatedWatts = 30000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.83, defaultOperationType = LoadOperationType.INTERMITTENT, isMotorLoad = true, defaultStartingSurgeMultiplier = 5.5),
        LoadDefinition("industrial_laundry_equipment", "Industrial Laundry Equipment", LoadCategory.INDUSTRIAL, defaultRatedWatts = 15000.0, defaultVoltage = 415.0, defaultPhase = LoadPhaseType.THREE_PHASE, defaultPowerFactor = 0.9, defaultOperationType = LoadOperationType.INTERMITTENT, isMotorLoad = true, defaultStartingSurgeMultiplier = 3.0, defaultHoursPerDay = 6.0),
        LoadDefinition("industrial_water_heater", "Water Heater (industrial)", LoadCategory.INDUSTRIAL, defaultRatedWatts = 6000.0, defaultVoltage = 415.0, defaultPowerFactor = 1.0, defaultOperationType = LoadOperationType.INTERMITTENT),
        LoadDefinition("industrial_ups", "UPS System (industrial)", LoadCategory.INDUSTRIAL, defaultRatedWatts = 500.0, defaultPowerFactor = 0.95, defaultOperationType = LoadOperationType.CONTINUOUS, defaultPriority = LoadPriority.CRITICAL),
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
