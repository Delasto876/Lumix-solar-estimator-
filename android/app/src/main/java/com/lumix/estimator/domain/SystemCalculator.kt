package com.lumix.estimator.domain

import com.lumix.estimator.domain.simulation.SimApplianceType
import com.lumix.estimator.domain.simulation.defaultDailyEnergyKwh
import com.lumix.estimator.domain.simulation.defaultEffectiveDailyHours
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object SystemCalculator {
    /**
     * The one mapping from the wizard's basic appliance picker to the simulation's real, richer
     * catalog — kept in sync with [com.lumix.estimator.domain.simulation.defaultApplianceStates]'s
     * own `stateFromWizard` pairings, since both exist to connect the exact same two enums.
     */
    private fun simTypeFor(type: ApplianceType): SimApplianceType = when (type) {
        ApplianceType.FAN -> SimApplianceType.CEILING_FAN
        ApplianceType.FRIDGE -> SimApplianceType.REFRIGERATOR
        ApplianceType.FREEZER -> SimApplianceType.CHEST_FREEZER
        ApplianceType.STOVE -> SimApplianceType.STOVE
        ApplianceType.OVEN -> SimApplianceType.OVEN
        ApplianceType.MICROWAVE -> SimApplianceType.MICROWAVE
        ApplianceType.ELECTRIC_KETTLE -> SimApplianceType.ELECTRIC_KETTLE
        ApplianceType.TOASTER -> SimApplianceType.TOASTER
        ApplianceType.BLENDER -> SimApplianceType.BLENDER
        ApplianceType.WATER_HEATER -> SimApplianceType.WATER_HEATER
        ApplianceType.WATER_PUMP -> SimApplianceType.WATER_PUMP
        ApplianceType.WASHER -> SimApplianceType.WASHING_MACHINE
        ApplianceType.DRYER -> SimApplianceType.CLOTHES_DRYER
        ApplianceType.IRON -> SimApplianceType.IRON
        ApplianceType.LIGHTS -> SimApplianceType.LED_LIVING
        ApplianceType.OUTDOOR_LIGHTS -> SimApplianceType.LED_EXTERIOR
        ApplianceType.TV -> SimApplianceType.TELEVISION
        ApplianceType.COMPUTER -> SimApplianceType.DESKTOP_COMPUTER
        ApplianceType.GAMING_CONSOLE -> SimApplianceType.GAME_CONSOLE
    }

    /** Fallback only — every real calculation uses [QuoteInputs.peakSunHours] (per-quote, editable, default 5.5) instead. */
    const val PSH = 5.5
    const val BATTERY_DOD = 0.8
    const val BLENDED_TARIFF = 50.0
    /** Minimum PSH floor so a stray 0/negative input can never divide-by-zero the panel-count math. */
    private const val MIN_PSH = 0.5

    private data class LoadResult(val dailyKwh: Double, val peakWatts: Double)

    private fun loadsKwhAndPeak(data: QuoteInputs): LoadResult {
        var dailyKwh = 0.0
        var peakWatts = 0.0

        // Sizing load and simulation behavior come from the SAME schedule/duty-cycle model
        // (SimAppliance.kt's defaultScheduleFor) by default — an installer no longer has to
        // manually estimate hours/day for the estimator to size correctly. "Standard" AC hours
        // uses the real evening-window + thermostat-duty-cycle shape (scaled by this appliance's
        // own real per-BTU-tier wattage, not the simulation catalog's generic AC wattage);
        // "Custom" still honors an explicit override.
        if (data.ac.hasAc) {
            val acEffectiveHours = defaultEffectiveDailyHours(SimApplianceType.AIR_CONDITIONER)
            data.ac.counts.forEach { (btu, count) ->
                if (count > 0) {
                    val w = btu / 10.0
                    val hours = if (data.ac.useStandardHours) acEffectiveHours else data.ac.customHours
                    dailyKwh += (w * hours * count) / 1000.0
                    peakWatts += w * count
                }
            }
        }

        data.appliances.forEach { (type, load) ->
            if (load.qty > 0) {
                dailyKwh += if (load.useAutoSchedule) {
                    defaultDailyEnergyKwh(simTypeFor(type), load.qty)
                } else {
                    (type.watts * load.hours * load.qty) / 1000.0
                }
                peakWatts += type.watts * load.qty
            }
        }

        dailyKwh += (data.otherWatts * data.otherHours) / 1000.0
        peakWatts += data.otherWatts

        return LoadResult(dailyKwh, peakWatts)
    }

    fun enforceEvenPanels(count: Double): Int {
        if (count <= 0) return 0
        var c = ceil(count).toInt()
        if (c % 2 == 1) c += 1
        return c
    }

    fun calculate(input: QuoteInputs, regularPrices: PriceList, discountPrices: PriceList): QuoteResult {
        val prices = if (input.useDiscountPriceList) discountPrices else regularPrices

        val (dailyKwhLoads, peakWatts) = loadsKwhAndPeak(input)

        val approxKwhFromBill = if (input.quoteMode == QuoteMode.GUIDED) {
            when (input.usageMode) {
                UsageMode.BILL -> if (input.avgBill > 0) input.avgBill / BLENDED_TARIFF else 0.0
                UsageMode.KWH -> if (input.avgKwh > 0) input.avgKwh else 0.0
                UsageMode.UNKNOWN -> 0.0
            }
        } else 0.0

        val designMonthlyKwh = when (input.quoteMode) {
            QuoteMode.LOAD -> dailyKwhLoads * 30
            QuoteMode.GUIDED -> {
                val v = max(dailyKwhLoads * 30, approxKwhFromBill)
                if (v == 0.0) dailyKwhLoads * 30 else v
            }
            QuoteMode.MANUAL -> if (dailyKwhLoads > 0) dailyKwhLoads * 30 else 10.0 * 30
        }

        val designDailyKwh = designMonthlyKwh / 30.0
        val requiredInverterKw = (peakWatts * 1.25) / 1000.0
        val psh = input.peakSunHours.coerceAtLeast(MIN_PSH)

        val criticalDailyKwh = when (input.backupCoverage) {
            // ESSENTIALS/CRITICAL_LOADS: back up a reduced, "just the essentials" load.
            BackupCoverage.ESSENTIALS, BackupCoverage.CRITICAL_LOADS -> dailyKwhLoads * 0.6
            // FULL/MOST_LOAD: size backup for (up to) the whole day's load.
            BackupCoverage.FULL, BackupCoverage.MOST_LOAD -> dailyKwhLoads
            // CUSTOM: the user's own chosen fraction of the day's load.
            BackupCoverage.CUSTOM -> dailyKwhLoads * input.customBackupCoverageFraction.coerceIn(0.0, 1.0)
        }
        val backupFractionOfDay = input.backupHours / 24.0
        var batteryRequiredKwh = (criticalDailyKwh * backupFractionOfDay) / BATTERY_DOD
        // The real energy the backup load must draw (before the flat-DOD nominal-capacity
        // conversion above) — what EquipmentSelectionEngine actually sizes batteries against,
        // per spec §14 (compare usable energy, not nominal kWh alone).
        val requiredBatteryUsableKwh = criticalDailyKwh * backupFractionOfDay

        var panelW = 595
        var effectiveSystemMode = input.systemMode
        var panelCount: Int
        var chosenInverter: InverterOption? = null
        var chosenBattery: BatteryOption? = null
        var batteryModuleCount = 0
        var totalBatteryKwh = 0.0
        var requiredPvKw = designDailyKwh / psh
        var panelSelectionReason: String? = null
        var inverterSelectionReason: String? = null
        var batterySelectionReason: String? = null

        // GUIDED and LOAD deliberately share this one equipment-aware sizing path (per spec §17 —
        // "Guided Mode should eventually call the same ... Equipment Selection Engine ... used by
        // Load-Based Mode. The difference is the INPUT METHOD."). What makes LOAD "Load-Based" is
        // that its requirement figures come straight from the appliance load audit rather than a
        // JPS bill estimate — not a separate sizing engine.
        if (input.quoteMode == QuoteMode.GUIDED || input.quoteMode == QuoteMode.LOAD) {
            var pvKw = designDailyKwh / psh

            val inverterPool = when (input.systemMode) {
                SystemMode.HYBRID -> Catalog.hybridInverters
                SystemMode.OFFGRID -> Catalog.offgridInverters
                SystemMode.GRIDTIE -> Catalog.gridtieInverters
            }
            val inverterChoice = EquipmentSelectionEngine.selectBestInverter(requiredInverterKw, inverterPool)
            val selectedInverter = inverterChoice.option
            chosenInverter = selectedInverter
            inverterSelectionReason = inverterChoice.reason

            when (input.systemMode) {
                SystemMode.HYBRID -> {
                    val batteryChoice = EquipmentSelectionEngine.selectBestHybridBattery(requiredBatteryUsableKwh)
                    chosenBattery = batteryChoice.option
                    batteryModuleCount = batteryChoice.moduleCount
                    totalBatteryKwh = batteryChoice.totalKwh
                    batterySelectionReason = batteryChoice.reason
                }
                SystemMode.OFFGRID -> {
                    if (batteryRequiredKwh > 0) {
                        batteryModuleCount = max(1, ceil(batteryRequiredKwh / Catalog.offgridModuleKwh).toInt())
                        chosenBattery = BatteryOption("12V AGM (approx 2.4kWh)", Catalog.offgridModuleKwh) { it.batteryAGM12V }
                        totalBatteryKwh = batteryModuleCount * Catalog.offgridModuleKwh
                        batterySelectionReason = "%.1f kWh usable backup required — %d × 12V AGM modules covers it."
                            .format(requiredBatteryUsableKwh, batteryModuleCount)
                    }
                }
                SystemMode.GRIDTIE -> {
                    chosenBattery = null
                    batteryModuleCount = 0
                    totalBatteryKwh = 0.0
                    batteryRequiredKwh = 0.0
                }
            }

            if (totalBatteryKwh > 0) {
                pvKw = max(pvKw, totalBatteryKwh / 4.0)
            }
            requiredPvKw = pvKw

            if (input.systemMode == SystemMode.OFFGRID) {
                // Off-grid systems here stay on the simpler fixed-550W/max-4-panel sizing this
                // catalog has always used for them (small stand-alone arrays) rather than the full
                // multi-wattage search — scope note, not an oversight: the multi-wattage search
                // below is aimed at hybrid/grid-interactive arrays, which is where the spec's own
                // "10 × 620W" style examples live.
                panelW = 550
                panelCount = min(enforceEvenPanels((pvKw * 1000) / panelW), 4)
                panelSelectionReason = "%.2f kW required — %d × %dW panels (off-grid arrays capped at 4)."
                    .format(pvKw, panelCount, panelW)
            } else {
                val maxPvKw = selectedInverter.kw * 1.3
                val panelChoice = EquipmentSelectionEngine.selectBestPanelConfiguration(pvKw, maxPvKw)
                panelW = panelChoice.panelWatts
                panelCount = panelChoice.panelCount
                panelSelectionReason = panelChoice.reason
            }
        } else {
            if (input.manualInverterId != null) {
                val invDef = Catalog.findManual(input.manualInverterId)
                if (invDef != null) {
                    effectiveSystemMode = invDef.mode
                    chosenInverter = invDef
                }
            }
            if (chosenInverter == null) {
                val pool = Catalog.poolFor(effectiveSystemMode)
                chosenInverter = pool.firstOrNull { it.kw >= requiredInverterKw } ?: pool.last()
            }

            panelW = if (input.manualPanelWatts > 0) input.manualPanelWatts else if (effectiveSystemMode == SystemMode.OFFGRID) 550 else 595

            when (effectiveSystemMode) {
                SystemMode.HYBRID -> {
                    val total5 = input.manualBatt5k * 5.0
                    val total10 = input.manualBatt10k * 10.0
                    val total15 = input.manualBatt15k * 15.0
                    totalBatteryKwh = total5 + total10 + total15
                    batteryModuleCount = input.manualBatt5k + input.manualBatt10k + input.manualBatt15k
                    if (batteryModuleCount > 0) {
                        val avgKwh = totalBatteryKwh / batteryModuleCount
                        chosenBattery = BatteryOption("Hybrid LiFePO4 mix", avgKwh) { 0.0 }
                    }
                }
                SystemMode.OFFGRID -> {
                    totalBatteryKwh = input.manualAgmCount * Catalog.offgridModuleKwh
                    batteryModuleCount = input.manualAgmCount
                    if (batteryModuleCount > 0) {
                        chosenBattery = BatteryOption("12V AGM (approx 2.4kWh)", Catalog.offgridModuleKwh) { it.batteryAGM12V }
                    }
                }
                SystemMode.GRIDTIE -> {
                    totalBatteryKwh = 0.0
                    batteryModuleCount = 0
                }
            }

            when (input.manualModeType) {
                ManualModeType.BATTERY_LED -> {
                    val pvKwForBattery = if (totalBatteryKwh > 0) totalBatteryKwh / 4.0 else designDailyKwh / psh
                    panelCount = enforceEvenPanels((pvKwForBattery * 1000) / panelW)
                }
                ManualModeType.PANEL_LED -> {
                    panelCount = enforceEvenPanels(input.manualPanelCount.toDouble())
                    val pvKw = (panelCount * panelW) / 1000.0
                    if (totalBatteryKwh == 0.0 && (effectiveSystemMode == SystemMode.HYBRID || effectiveSystemMode == SystemMode.OFFGRID)) {
                        totalBatteryKwh = max(batteryRequiredKwh, pvKw * 4)
                        if (effectiveSystemMode == SystemMode.HYBRID) {
                            when {
                                totalBatteryKwh <= 5 -> { chosenBattery = Catalog.hybridBatteries[0]; batteryModuleCount = 1; totalBatteryKwh = chosenBattery!!.kwh }
                                totalBatteryKwh <= 10 -> { chosenBattery = Catalog.hybridBatteries[1]; batteryModuleCount = 1; totalBatteryKwh = chosenBattery!!.kwh }
                                totalBatteryKwh <= 15 -> { chosenBattery = Catalog.hybridBatteries[2]; batteryModuleCount = 1; totalBatteryKwh = chosenBattery!!.kwh }
                                else -> {
                                    chosenBattery = Catalog.hybridBatteries[1]
                                    batteryModuleCount = ceil(totalBatteryKwh / 10.0).toInt()
                                    totalBatteryKwh = chosenBattery!!.kwh * batteryModuleCount
                                }
                            }
                        } else if (effectiveSystemMode == SystemMode.OFFGRID) {
                            batteryModuleCount = ceil(totalBatteryKwh / Catalog.offgridModuleKwh).toInt()
                            chosenBattery = BatteryOption("12V AGM (approx 2.4kWh)", Catalog.offgridModuleKwh) { it.batteryAGM12V }
                            totalBatteryKwh = batteryModuleCount * Catalog.offgridModuleKwh
                        }
                    }
                }
                ManualModeType.FULL_MANUAL -> {
                    panelCount = enforceEvenPanels(input.manualPanelCount.toDouble())
                    if (totalBatteryKwh > 0) {
                        val pvKwForBattery = totalBatteryKwh / 4.0
                        val currentPvKw = (panelCount * panelW) / 1000.0
                        if (currentPvKw < pvKwForBattery) {
                            panelCount = enforceEvenPanels((pvKwForBattery * 1000) / panelW)
                        }
                    }
                }
            }

            if (effectiveSystemMode == SystemMode.OFFGRID && panelW <= 550) {
                panelCount = min(panelCount, 4)
            }
        }

        var chargeControllerCount = 0
        if (effectiveSystemMode == SystemMode.OFFGRID) {
            val pvWatts = panelCount * panelW
            if (pvWatts > 0) chargeControllerCount = if (pvWatts <= 3800) 1 else 2
        }

        val inverter = chosenInverter!!

        // Requested backup coverage (Most Load / Custom) implies wanting to run close to the
        // whole house's peak draw during an outage, but the actually-selected inverter may have
        // been capped at the catalog's largest option (requiredInverterKw > every available
        // rating). Flag it rather than silently pretending the picked hardware can deliver more
        // than its own rated capacity — never surfaced for Critical Loads/Essentials, since that
        // coverage was never asking for the full peak in the first place.
        val backupCapacityWarningKw: Double? =
            if (input.backupCoverage != BackupCoverage.ESSENTIALS &&
                input.backupCoverage != BackupCoverage.CRITICAL_LOADS &&
                requiredInverterKw > inverter.kw
            ) requiredInverterKw else null

        // MANUAL only — GUIDED/LOAD equipment is chosen by EquipmentSelectionEngine specifically to
        // satisfy these same figures, so these should never fire there. Per spec §4/§29: an
        // installer's manual choice is never silently replaced — this is surfaced as a warning the
        // installer must explicitly review and accept (see StepSystemReview's Manual-mode gate),
        // not auto-corrected.
        val manualInverterWarning: String? =
            if (input.quoteMode == QuoteMode.MANUAL && inverter.kw < requiredInverterKw - 0.05) {
                "Selected inverter (%s, %.1f kW) may be undersized for the calculated peak load (%.2f kW required)."
                    .format(inverter.name, inverter.kw, requiredInverterKw)
            } else null
        val manualBatteryWarning: String? =
            if (input.quoteMode == QuoteMode.MANUAL && totalBatteryKwh > 0.0 && totalBatteryKwh < batteryRequiredKwh - 0.05) {
                "Selected battery (%.1f kWh) may be undersized for the requested backup (%.1f kWh needed)."
                    .format(totalBatteryKwh, batteryRequiredKwh)
            } else null

        val panelUnitPrice = prices.panelPrice(panelW)
        val inverterCost = inverter.price(prices)

        val batteryCostForWireRule = when {
            input.quoteMode == QuoteMode.GUIDED || input.quoteMode == QuoteMode.LOAD -> {
                if (chosenBattery != null && batteryModuleCount > 0) {
                    when (effectiveSystemMode) {
                        SystemMode.HYBRID -> when (chosenBattery.kwh) {
                            5.0 -> prices.batteryLFP5k * batteryModuleCount
                            10.0 -> prices.batteryLFP10k * batteryModuleCount
                            15.0 -> prices.batteryLFP15k * batteryModuleCount
                            else -> 0.0
                        }
                        SystemMode.OFFGRID -> prices.batteryAGM12V * batteryModuleCount
                        SystemMode.GRIDTIE -> 0.0
                    }
                } else 0.0
            }
            else -> when (effectiveSystemMode) {
                SystemMode.HYBRID -> input.manualBatt5k * prices.batteryLFP5k + input.manualBatt10k * prices.batteryLFP10k + input.manualBatt15k * prices.batteryLFP15k
                SystemMode.OFFGRID -> input.manualAgmCount * prices.batteryAGM12V
                SystemMode.GRIDTIE -> 0.0
            }
        }

        val panelCost = panelCount * panelUnitPrice
        val baseCostForWireRule = panelCost + inverterCost + batteryCostForWireRule
        val bigSystem = baseCostForWireRule > 750000

        val panelsPerRow = 4
        val rows = if (panelCount > 0) ceil(panelCount / panelsPerRow.toDouble()).toInt() else 0
        val railsPerRow = when (input.roofType) {
            RoofType.SLAB -> 3
            RoofType.ZINC -> if (input.zincCenterRail) 3 else 2
            RoofType.SHINGLE -> 3
        }
        val totalRails = railsPerRow * rows
        val midClampsPerRow = if (railsPerRow == 3) 9 else 6
        val endClampsPerRow = 4
        val totalMidClamps = midClampsPerRow * rows
        val totalEndClamps = endClampsPerRow * rows

        var totalBackLegs = 0
        var totalFrontLegs = 0
        var totalBolts = 0
        var totalLFoot = 0

        if (input.roofType == RoofType.SLAB) {
            val backLegsPerRow = 6
            val frontLegsPerRow = 3
            totalBackLegs = backLegsPerRow * rows
            totalFrontLegs = frontLegsPerRow * rows
            totalBolts = (backLegsPerRow + frontLegsPerRow) * rows * 2
        }
        if (input.roofType == RoofType.ZINC) {
            val lFeetPerRail = 4
            totalLFoot = totalRails * lFeetPerRail
        }

        val mc4CountPairs = if (effectiveSystemMode == SystemMode.OFFGRID) 4 else 6
        val weebCount = if (panelCount > 0) max(1, ceil(panelCount / 4.0).toInt()) else 0

        val pvWireFt: Int
        val wire10mmFt: Int
        val wire6mmFt: Int
        val battCableFt: Int
        val battLugCount: Int
        val drawBoxCount: Int
        val pvcHalfBundleCount: Int
        val pvcOneBundleCount: Int
        val dcBattBreakerCount: Int
        val pvDisconnectCount: Int
        val dcSurgeCount = 1
        val acSurgeCount = 1
        val db8WayCount = 1
        val din5Count = 1
        val din8Count = 1
        val trunkingCount = 1
        val earthRodCount = 1

        if (effectiveSystemMode == SystemMode.OFFGRID) {
            pvWireFt = 80
            battCableFt = 18
            wire6mmFt = 50
            wire10mmFt = 0
            drawBoxCount = 4
            pvcHalfBundleCount = 1
            pvcOneBundleCount = 0
            battLugCount = 6
            dcBattBreakerCount = if (batteryModuleCount > 0) 1 else 0
            pvDisconnectCount = 1
        } else {
            pvWireFt = if (bigSystem) 120 else 80
            wire10mmFt = if (bigSystem) 150 else 60
            wire6mmFt = if (bigSystem) 0 else 80
            drawBoxCount = 6
            pvcHalfBundleCount = 0
            pvcOneBundleCount = 1
            battCableFt = if (effectiveSystemMode == SystemMode.HYBRID && batteryModuleCount > 0) 10 else 0
            battLugCount = if (effectiveSystemMode == SystemMode.HYBRID && batteryModuleCount > 0) 8 else 0
            dcBattBreakerCount = if (effectiveSystemMode == SystemMode.HYBRID && batteryModuleCount > 0) 1 else 0
            pvDisconnectCount = if (panelCount > 0) 1 else 0
        }

        val materials = mutableListOf<MaterialLine>()

        if (panelCount > 0) materials += MaterialLine("${panelW}W PV panel", panelCount.toDouble(), panelUnitPrice)
        materials += MaterialLine(inverter.name, 1.0, inverterCost)

        if (input.quoteMode == QuoteMode.GUIDED || input.quoteMode == QuoteMode.LOAD) {
            if (effectiveSystemMode == SystemMode.HYBRID && batteryModuleCount > 0 && chosenBattery != null) {
                val line: MaterialLine? = when (chosenBattery.kwh) {
                    5.0 -> MaterialLine("5kWh LiFePO4", batteryModuleCount.toDouble(), prices.batteryLFP5k)
                    10.0 -> MaterialLine("10kWh LiFePO4", batteryModuleCount.toDouble(), prices.batteryLFP10k)
                    15.0 -> MaterialLine("15kWh LiFePO4", batteryModuleCount.toDouble(), prices.batteryLFP15k)
                    else -> null
                }
                if (line != null) materials += line
            } else if (effectiveSystemMode == SystemMode.OFFGRID && batteryModuleCount > 0) {
                materials += MaterialLine("12V AGM battery", batteryModuleCount.toDouble(), prices.batteryAGM12V)
            }
        } else {
            if (effectiveSystemMode == SystemMode.HYBRID) {
                if (input.manualBatt5k > 0) materials += MaterialLine("5kWh LiFePO4", input.manualBatt5k.toDouble(), prices.batteryLFP5k)
                if (input.manualBatt10k > 0) materials += MaterialLine("10kWh LiFePO4", input.manualBatt10k.toDouble(), prices.batteryLFP10k)
                if (input.manualBatt15k > 0) materials += MaterialLine("15kWh LiFePO4", input.manualBatt15k.toDouble(), prices.batteryLFP15k)
            } else if (effectiveSystemMode == SystemMode.OFFGRID && input.manualAgmCount > 0) {
                materials += MaterialLine("12V AGM battery", input.manualAgmCount.toDouble(), prices.batteryAGM12V)
            }
        }

        if (effectiveSystemMode == SystemMode.OFFGRID && chargeControllerCount > 0) {
            materials += MaterialLine("80A MPPT charge controller", chargeControllerCount.toDouble(), prices.chargeController80A)
        }

        if (totalRails > 0) materials += MaterialLine("Mounting rail 16ft", totalRails.toDouble(), prices.mountingRail16ft)
        if (totalMidClamps > 0) materials += MaterialLine("Mid clamp", totalMidClamps.toDouble(), prices.midClamp)
        if (totalEndClamps > 0) materials += MaterialLine("End clamp", totalEndClamps.toDouble(), prices.endClamp)
        if (input.roofType == RoofType.SLAB) {
            if (totalBackLegs > 0) materials += MaterialLine("Adjustable back leg", totalBackLegs.toDouble(), prices.backLeg)
            if (totalFrontLegs > 0) materials += MaterialLine("Front leg", totalFrontLegs.toDouble(), prices.frontLeg)
            if (totalBolts > 0) materials += MaterialLine("Expansion bolt M6/M8", totalBolts.toDouble(), prices.m6m8Bolt)
        }
        if (input.roofType == RoofType.ZINC && totalLFoot > 0) {
            materials += MaterialLine("L-FOOT bracket", totalLFoot.toDouble(), prices.lFoot)
        }

        if (mc4CountPairs > 0) materials += MaterialLine("MC4 connector pair", mc4CountPairs.toDouble(), prices.mc4Pair)
        if (weebCount > 0) materials += MaterialLine("WEEB clip", weebCount.toDouble(), prices.weebClip)

        if (pvWireFt > 0) materials += MaterialLine("AWG10 PV wire (ft)", pvWireFt.toDouble(), prices.pvWirePerFt)
        if (wire10mmFt > 0) materials += MaterialLine("10mm single wire (ft)", wire10mmFt.toDouble(), prices.ac10mmPerFt)
        if (wire6mmFt > 0) materials += MaterialLine("6mm single wire (ft)", wire6mmFt.toDouble(), prices.ac6mmPerFt)
        if (battCableFt > 0) materials += MaterialLine("#2/0 battery cable (ft)", battCableFt.toDouble(), prices.battCablePerFt)
        if (battLugCount > 0) materials += MaterialLine("Battery lugs", battLugCount.toDouble(), prices.battLug)

        if (dcSurgeCount > 0) materials += MaterialLine("DC 1000V surge arrestor", dcSurgeCount.toDouble(), prices.dcSurge)
        if (acSurgeCount > 0) materials += MaterialLine("AC 1000V surge arrestor", acSurgeCount.toDouble(), prices.acSurge)
        if (dcBattBreakerCount > 0) materials += MaterialLine("100A DC battery breaker", dcBattBreakerCount.toDouble(), prices.dcBatteryBreaker100A)
        if (pvDisconnectCount > 0) materials += MaterialLine("PV disconnect 32A", pvDisconnectCount.toDouble(), prices.pvDisconnect32A)

        materials += MaterialLine("5-way DIN-rail box", din5Count.toDouble(), prices.dinRailBox5Way)
        materials += MaterialLine("8-way DIN-rail box", din8Count.toDouble(), prices.dinRailBox8Way)
        materials += MaterialLine("8-way distribution panel", db8WayCount.toDouble(), prices.db8Way)
        materials += MaterialLine("Trunking", trunkingCount.toDouble(), prices.trunking)
        materials += MaterialLine("Earth rod with clamp", earthRodCount.toDouble(), prices.earthRod)

        var addTransferSwitch = false
        var addChangeoverSwitch = false
        if (input.quoteMode == QuoteMode.MANUAL && effectiveSystemMode == SystemMode.OFFGRID) {
            if (input.manualOffgridUseAutoTransfer) addTransferSwitch = true else addChangeoverSwitch = true
        } else {
            addTransferSwitch = true
        }
        if (addTransferSwitch) materials += MaterialLine("Transfer switch (63-125A auto/manual)", 1.0, prices.transferSwitch)
        if (addChangeoverSwitch) materials += MaterialLine("Off-grid changeover switch", 1.0, prices.changeOverSwitchOffgrid)

        if (drawBoxCount > 0) materials += MaterialLine("Draw box", drawBoxCount.toDouble(), prices.drawBox)
        if (pvcHalfBundleCount > 0) materials += MaterialLine("PVC conduit 1/2\" bundle", pvcHalfBundleCount.toDouble(), prices.pvcConduitHalfBundle)
        if (pvcOneBundleCount > 0) materials += MaterialLine("PVC conduit 1\" bundle", pvcOneBundleCount.toDouble(), prices.pvcConduitOneBundle)

        val materialsTotal = materials.sumOf { it.subtotal }
        val serviceCharge = materialsTotal * 0.15
        val preDiscountTotal = materialsTotal + serviceCharge + input.deliveryCharge

        var discountAmount = when (input.discountType) {
            DiscountType.PERCENT -> preDiscountTotal * (input.discountValue / 100.0)
            DiscountType.FIXED -> input.discountValue
            DiscountType.NONE -> 0.0
        }
        if (discountAmount < 0) discountAmount = 0.0
        if (discountAmount > preDiscountTotal) discountAmount = preDiscountTotal

        val grandTotal = preDiscountTotal - discountAmount

        // Resolved once, here, at calculation time — never re-matched against a possibly-newer
        // equipment catalog when a saved quote's simulation is opened later. See
        // QuoteResult.batteryMaxChargeKw's own doc for why this matters for reproducibility.
        val matchedBattery = EquipmentSpecs.batterySpecFor(chosenBattery?.name)
        val batteryMaxChargeKw: Double?
        val batteryMaxDischargeKw: Double?
        if (matchedBattery != null && totalBatteryKwh > 0) {
            val units = (totalBatteryKwh / matchedBattery.ratedEnergyKwh).roundToInt().coerceAtLeast(1)
            val inverterCeilingKw = inverter.kw.coerceAtLeast(0.1)
            batteryMaxChargeKw = (matchedBattery.maxChargeA * matchedBattery.voltageV / 1000.0 * units).coerceAtMost(inverterCeilingKw)
            batteryMaxDischargeKw = (matchedBattery.maxDischargeA * matchedBattery.voltageV / 1000.0 * units).coerceAtMost(inverterCeilingKw)
        } else {
            batteryMaxChargeKw = null
            batteryMaxDischargeKw = null
        }

        return QuoteResult(
            effectiveSystemMode = effectiveSystemMode,
            designDailyKwh = designDailyKwh,
            peakWatts = peakWatts,
            panelCount = panelCount,
            panelWatts = panelW,
            inverterName = inverter.name,
            inverterKw = inverter.kw,
            batteryName = chosenBattery?.name,
            batteryRequiredKwh = batteryRequiredKwh,
            totalBatteryKwh = totalBatteryKwh,
            rows = rows,
            railsPerRow = railsPerRow,
            totalRails = totalRails,
            totalMidClamps = totalMidClamps,
            totalEndClamps = totalEndClamps,
            totalBackLegs = totalBackLegs,
            totalFrontLegs = totalFrontLegs,
            totalBolts = totalBolts,
            totalLFoot = totalLFoot,
            materials = materials,
            materialsTotal = materialsTotal,
            serviceCharge = serviceCharge,
            deliveryCharge = input.deliveryCharge,
            discountAmount = discountAmount,
            grandTotal = grandTotal,
            backupCapacityWarningKw = backupCapacityWarningKw,
            batteryMaxChargeKw = batteryMaxChargeKw,
            batteryMaxDischargeKw = batteryMaxDischargeKw,
            requiredPvKw = requiredPvKw,
            requiredInverterKw = requiredInverterKw,
            requiredBatteryUsableKwh = requiredBatteryUsableKwh,
            panelSelectionReason = panelSelectionReason,
            inverterSelectionReason = inverterSelectionReason,
            batterySelectionReason = batterySelectionReason,
            manualInverterWarning = manualInverterWarning,
            manualBatteryWarning = manualBatteryWarning
        )
    }

    /**
     * A49: whether MANUAL mode's own equipment choice currently has an undersized-equipment
     * warning the installer hasn't explicitly accepted yet — used to gate the wizard's Calculate
     * button (see WizardScreen.kt). Runs a no-pricing-needed preview calc, the same
     * [PriceList.DEFAULT] pattern already used by StepSystemReview/StepHouseholdAppliances for
     * cheap previews that don't need the real, repository-loaded price list.
     */
    fun hasUnacknowledgedManualWarnings(input: QuoteInputs): Boolean {
        if (input.quoteMode != QuoteMode.MANUAL) return false
        val preview = calculate(input, PriceList.DEFAULT, PriceList.DEFAULT)
        return listOfNotNull(preview.manualInverterWarning, preview.manualBatteryWarning)
            .any { it !in input.manualWarningsAcknowledged }
    }
}
