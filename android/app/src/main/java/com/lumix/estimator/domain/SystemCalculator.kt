package com.lumix.estimator.domain

import com.lumix.estimator.domain.simulation.BackupEstimator
import com.lumix.estimator.domain.simulation.OvernightLoadProfile
import com.lumix.estimator.domain.simulation.RechargeFeasibility
import com.lumix.estimator.domain.simulation.SimApplianceType
import com.lumix.estimator.domain.simulation.SimSystemConfig
import com.lumix.estimator.domain.simulation.SimulationEngine
import com.lumix.estimator.domain.simulation.defaultApplianceStates
import com.lumix.estimator.domain.simulation.defaultDailyEnergyKwh
import com.lumix.estimator.domain.simulation.defaultEffectiveDailyHours
import com.lumix.estimator.domain.simulation.WeatherEngine
import com.lumix.estimator.domain.simulation.WeatherScenario
import com.lumix.estimator.domain.pricing.MaterialTakeoffEngine
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object SystemCalculator {
    /**
     * The one mapping from the wizard's basic appliance picker to the simulation's real, richer
     * catalog — kept in sync with [com.lumix.estimator.domain.simulation.defaultApplianceStates]'s
     * own `stateFromWizard` pairings, since both exist to connect the exact same two enums.
     * `internal` (not `private`) so `ApplianceTypeConsistencyTest` can walk every mapped pair and
     * assert their wattages never drift apart again — see [ApplianceType]'s own doc for the A66
     * bug this guards against.
     */
    internal fun simTypeFor(type: ApplianceType): SimApplianceType = when (type) {
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
        // A54: the rest of SimApplianceType's catalog, newly reachable from the wizard's own
        // picker (previously only selectable from the Simulation screen's fuller picker).
        ApplianceType.AIR_FRYER -> SimApplianceType.AIR_FRYER
        ApplianceType.RICE_COOKER -> SimApplianceType.RICE_COOKER
        ApplianceType.PRESSURE_COOKER -> SimApplianceType.PRESSURE_COOKER
        ApplianceType.STANDING_FAN -> SimApplianceType.STANDING_FAN
        ApplianceType.BEDROOM_FAN -> SimApplianceType.BEDROOM_FAN
        ApplianceType.LED_BEDROOM -> SimApplianceType.LED_BEDROOM
        ApplianceType.LED_KITCHEN -> SimApplianceType.LED_KITCHEN
        ApplianceType.LED_BATHROOM -> SimApplianceType.LED_BATHROOM
        ApplianceType.OUTDOOR_FLOODLIGHT -> SimApplianceType.OUTDOOR_FLOODLIGHT
        ApplianceType.SET_TOP_BOX -> SimApplianceType.SET_TOP_BOX
        ApplianceType.WIFI_ROUTER -> SimApplianceType.WIFI_ROUTER
        ApplianceType.MODEM -> SimApplianceType.MODEM
        ApplianceType.PHONE_CHARGERS -> SimApplianceType.PHONE_CHARGERS
        ApplianceType.LAPTOP -> SimApplianceType.LAPTOP
        ApplianceType.PRINTER -> SimApplianceType.PRINTER
        ApplianceType.SOUND_SYSTEM -> SimApplianceType.SOUND_SYSTEM
        ApplianceType.INSTANT_SHOWER -> SimApplianceType.INSTANT_SHOWER
        ApplianceType.HAIR_DRYER -> SimApplianceType.HAIR_DRYER
        ApplianceType.CURLING_IRON -> SimApplianceType.CURLING_IRON
        ApplianceType.VACUUM_CLEANER -> SimApplianceType.VACUUM_CLEANER
        ApplianceType.SEWING_MACHINE -> SimApplianceType.SEWING_MACHINE
        ApplianceType.SECURITY_SYSTEM -> SimApplianceType.SECURITY_SYSTEM
        ApplianceType.GATE_OPENER -> SimApplianceType.GATE_OPENER
        ApplianceType.EV_CHARGER_L1 -> SimApplianceType.EV_CHARGER_L1
        ApplianceType.EV_CHARGER_L2 -> SimApplianceType.EV_CHARGER_L2
        ApplianceType.POOL_PUMP -> SimApplianceType.POOL_PUMP
    }

    /** Fallback only — every real calculation uses [QuoteInputs.peakSunHours] (per-quote, editable, default 5.5) instead. */
    const val PSH = 5.5
    const val BATTERY_DOD = 0.8
    const val BLENDED_TARIFF = 50.0
    /** Minimum PSH floor so a stray 0/negative input can never divide-by-zero the panel-count math. */
    private const val MIN_PSH = 0.5

    /**
     * Phase 28 ("use the selected BTU and equipment data to determine appropriate electrical
     * input power"): typical, disclosed generic BTU/W ratios — NOT a manufacturer datasheet figure
     * for any specific model, the same "generic engineering placeholder, not a measured figure"
     * caveat this codebase already applies wherever a real per-model spec isn't available (see e.g.
     * [com.lumix.estimator.domain.simulation.SimSystemConfig.inverterSelfConsumptionKw]'s own doc).
     * 10.0 (non-inverter) is this app's own pre-existing assumption, unchanged. 13.0 (inverter) is
     * a commonly-cited generic figure for variable-speed mini-split/inverter AC rated efficiency —
     * meaningfully higher than a fixed-speed unit's, consistent with why inverter ACs cost more.
     */
    private const val NON_INVERTER_BTU_PER_WATT = 10.0
    private const val INVERTER_BTU_PER_WATT = 13.0

    /**
     * Phase 28 ("for inverter AC, model variable/part-load operation rather than assuming full
     * rated input continuously... keep peak demand separate from average energy consumption"): a
     * non-inverter unit cycles its compressor fully on/off (already modeled by
     * [SimApplianceType.AIR_CONDITIONER]'s own duty factor inside [defaultEffectiveDailyHours]) —
     * while it runs, it draws close to its full rated input. An inverter unit instead modulates
     * compressor speed to match the actual cooling load, so its real AVERAGE draw during its "on"
     * window is meaningfully below its rated peak input — this factor scales the ENERGY (kWh)
     * calculation only. [peakWatts] deliberately stays at the full rated input for both types (the
     * inverter surge/starting-current check and the AC's worst-case instantaneous draw don't get
     * smaller just because its average is lower). A disclosed generic assumption, not a
     * manufacturer-specific figure — a real per-model part-load curve isn't available in this
     * catalog.
     */
    private const val INVERTER_PART_LOAD_AVERAGE_FACTOR = 0.55

    /** `internal` (not `private`) so [com.lumix.estimator.domain.simulation.defaultApplianceStates] can derive the same real per-unit AC wattage this file's own sizing uses — one source of truth, never two separate BTU/W assumptions that could drift apart. */
    internal fun acBtuPerWatt(type: AcInverterType): Double = when (type) {
        AcInverterType.NON_INVERTER -> NON_INVERTER_BTU_PER_WATT
        AcInverterType.INVERTER -> INVERTER_BTU_PER_WATT
    }

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
            val btuPerWatt = acBtuPerWatt(data.ac.acType)
            val partLoadFactor = if (data.ac.acType == AcInverterType.INVERTER) INVERTER_PART_LOAD_AVERAGE_FACTOR else 1.0
            data.ac.counts.forEach { (btu, count) ->
                if (count > 0) {
                    val w = btu / btuPerWatt
                    val hours = if (data.ac.useStandardHours) acEffectiveHours else data.ac.customHours
                    dailyKwh += (w * partLoadFactor * hours * count) / 1000.0
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

    /**
     * A50: the worst case if every selected motor/compressor load happened to start at the exact
     * same instant — the same conservative "all at once" philosophy, and the same real per-type
     * [com.lumix.estimator.domain.simulation.SimApplianceType.startupSurgeMultiplier] data, as the
     * simulation's own [com.lumix.estimator.domain.simulation.worstCaseStartupSurgeKw] (which is
     * hour/day-scoped, for a different purpose — a live readout, not a sizing ceiling). Feeds
     * [EquipmentSelectionEngine.selectBestInverter]'s surge check (spec §17).
     */
    private fun worstCaseSurgeKw(data: QuoteInputs): Double {
        var surgeWatts = 0.0
        if (data.ac.hasAc) {
            val btuPerWatt = acBtuPerWatt(data.ac.acType)
            data.ac.counts.forEach { (btu, count) ->
                if (count > 0) surgeWatts += (btu / btuPerWatt) * count * SimApplianceType.AIR_CONDITIONER.startupSurgeMultiplier
            }
        }
        data.appliances.forEach { (type, load) ->
            if (load.qty > 0) surgeWatts += type.watts * load.qty * simTypeFor(type).startupSurgeMultiplier
        }
        // "Other loads" has no per-type surge data — treated as a plain 1.0x continuous contribution.
        surgeWatts += data.otherWatts
        return surgeWatts / 1000.0
    }

    fun enforceEvenPanels(count: Double): Int {
        if (count <= 0) return 0
        var c = ceil(count).toInt()
        if (c % 2 == 1) c += 1
        return c
    }

    /**
     * A63: the real per-model charge/discharge power a matched battery datasheet supports, capped
     * at the inverter's own ceiling — shared by [calculate]'s final [QuoteResult] fields and its
     * own A63 recharge-aware panel-count refinement below, so both read the exact same figures
     * (resolved once, from the equipment library, rather than computed twice and risking drift).
     *
     * A72 (spec Phase 7 — "fix battery calculations"): the ceiling used to be [inverterKw] alone
     * (the inverter's continuous AC output rating) — the same AC-rating-as-DC-proxy pattern A69
     * (PV input) and A71 (MPPT) already found and fixed for the *other* two DC-side ports on this
     * same hardware. The matched inverter's own real DC battery-port rating is a genuinely
     * different figure on some models — e.g. LuxPower GEN-LB-US 13K's own confirmed datasheet
     * `maxChargePowerKw`/`maxDischargePowerKw` is 10.0kW, LOWER than its 13kW AC rating — meaning
     * the old code would have let a large enough battery bank charge/discharge up to 13kW when the
     * real hardware caps it at 10.0kW. Prefers the inverter's own direct datasheet
     * `maxChargePowerKw`/`maxDischargePowerKw` when confirmed (the most authoritative figure);
     * falls back to deriving one from `maxBatteryA` at the matched battery's own real bus voltage
     * when only the current rating is confirmed; falls back to [inverterCeilingKw] alone (the old
     * behavior) when neither exists. Whichever of the resulting ceiling and [inverterCeilingKw] is
     * LOWER binds — exactly like a real system where the AC stage and the DC battery port are two
     * independent hardware limits.
     */
    /** `internal` (not `private`) so this module's own JVM unit tests (`SystemCalculatorBatteryPowerCeilingTest`) can exercise this directly against real catalog data, the same reason several other single-purpose helpers in this file (`sizeHybridBatteryForBackup`, `recheckPanelCountForRecharge`) already are. */
    internal fun resolvedBatteryPowerKw(chosenBattery: BatteryOption?, totalBatteryKwh: Double, inverterKw: Double, inverterName: String? = null): Pair<Double?, Double?> {
        val matchedBattery = EquipmentSpecs.batterySpecFor(chosenBattery?.name)
        if (matchedBattery == null || totalBatteryKwh <= 0) return null to null
        val units = (totalBatteryKwh / matchedBattery.ratedEnergyKwh).roundToInt().coerceAtLeast(1)
        val inverterCeilingKw = inverterKw.coerceAtLeast(0.1)
        val invSpec = EquipmentSpecs.inverterSpecFor(inverterKw, inverterName)
        // A72: the inverter's own real DC battery-port power ceiling — the direct datasheet
        // kW figure when confirmed, else derived from the real max battery current at the matched
        // battery's own real bus voltage, else no additional ceiling beyond inverterCeilingKw.
        val batteryPortChargeCeilingKw = invSpec?.maxChargePowerKw
            ?: invSpec?.maxBatteryA?.let { it * matchedBattery.voltageV / 1000.0 }
            ?: Double.MAX_VALUE
        val batteryPortDischargeCeilingKw = invSpec?.maxDischargePowerKw
            ?: invSpec?.maxBatteryA?.let { it * matchedBattery.voltageV / 1000.0 }
            ?: Double.MAX_VALUE
        val chargeKw = (matchedBattery.maxChargeA * matchedBattery.voltageV / 1000.0 * units)
            .coerceAtMost(minOf(inverterCeilingKw, batteryPortChargeCeilingKw))
        val dischargeKw = (matchedBattery.maxDischargeA * matchedBattery.voltageV / 1000.0 * units)
            .coerceAtMost(minOf(inverterCeilingKw, batteryPortDischargeCeilingKw))
        return chargeKw to dischargeKw
    }

    /** Bounded, mirroring A63's own "+1/+2, never indefinitely" philosophy — see [sizeHybridBatteryForBackup]. */
    private const val MAX_BATTERY_SIZING_ATTEMPTS = 4
    private const val BACKUP_TARGET_EPSILON_HOURS = 0.1

    internal data class HybridBatterySizing(
        val choice: EquipmentSelectionEngine.BatteryChoice,
        /** The usable-energy figure actually used for the winning attempt — becomes [QuoteResult.requiredBatteryUsableKwh], so the displayed "required" figure and the actual selection can never show two different numbers for the same system (spec §6's "do not double-count efficiency... ONE centralized battery-energy calculation"). */
        val requiredUsableKwh: Double,
        /** Null only when there's no backup requested at all ([QuoteInputs.backupHours] <= 0). */
        val backupEstimate: BackupEstimator.BackupEstimate?
    )

    /**
     * A64 (2026-08-14 "FIX 12-HOUR OVERNIGHT BACKUP SIZING" §1-9, §32-33): replaces the old flat
     * `criticalDailyKwh * (backupHours / 24) / BATTERY_DOD` battery-sizing formula, which the
     * installer's own reported bug showed producing a "requires ~17kWh" figure alongside a 15kWh
     * selection — an average-load estimate with no relationship to what the selected appliances
     * actually draw overnight, or to what the real simulation would say about the pick.
     *
     * This instead: (1) integrates the REAL appliance-schedule load curve over the actual backup
     * window via [OvernightLoadProfile] — the same window [BackupEstimator]'s own verification
     * simulation uses, by construction, so requirement and verification can never silently
     * describe two different periods; (2) runs [EquipmentSelectionEngine.selectBestHybridBattery]'s
     * real tier/module search against that figure; (3) VERIFIES the pick with an actual
     * [BackupEstimator] day-simulation rather than assuming a kWh number implies a runtime; (4) if
     * the simulated backup still falls short of the requested [QuoteInputs.backupHours], scales the
     * usable-energy target up by the observed shortfall ratio and searches again — bounded at
     * [MAX_BATTERY_SIZING_ATTEMPTS] attempts, never indefinitely (spec §9's "do not mix incompatible
     * battery capacities" holds automatically: each attempt is still one single-tier
     * [EquipmentSelectionEngine.selectBestHybridBattery] search, same as before).
     *
     * Deliberately reuses [EquipmentSelectionEngine.selectBestHybridBattery]'s existing
     * smallest-total-usable-energy-across-tiers search on every attempt, rather than hand-coding a
     * "5 -> 10 -> 15 -> 16 -> 2x10" escalation path: the installer's own catalog's "15kWh" tier is
     * already the real SRNE SR-EOS15B, whose real usable energy is 15.42kWh (see
     * [EquipmentSpecs.batteries]' own note — there is no separate distinct 16kWh SKU to escalate
     * to), so the actual escalation space is "more modules of whichever tier ends up smallest,"
     * exactly what that search already computes fresh for whatever target this function feeds it.
     *
     * PV is deliberately a zero-capacity placeholder in every trial [SimSystemConfig] built here —
     * not an oversight: [SimulationEngine.irradianceFactor] is 0 for the entire window this
     * simulates (starting at dusk, [SimulationEngine.SUNSET_HOUR], for [MAX_BATTERY_SIZING_ATTEMPTS]
     * attempts of up to a day's worth of hours), so PV capacity has no effect on the outcome at all
     * for any backup request up to ~12 hours — the real panel count isn't even chosen yet at this
     * point in [calculate] (panel sizing depends on the battery this function is choosing).
     */
    internal fun sizeHybridBatteryForBackup(
        input: QuoteInputs,
        designDailyKwh: Double,
        inverterKw: Double,
        inverterName: String
    ): HybridBatterySizing {
        val windowHours = input.backupHours
        if (windowHours <= 0.0) {
            return HybridBatterySizing(EquipmentSelectionEngine.selectBestHybridBattery(0.0, 0.0, inverterKw), 0.0, null)
        }

        val coverageFraction = BackupEstimator.coverageFraction(input.backupCoverage, input.customBackupCoverageFraction)
        val appliances = defaultApplianceStates(input)
        val profile = OvernightLoadProfile.evaluate(
            avgDailyLoadKwh = designDailyKwh,
            applianceStates = appliances,
            windowHours = windowHours,
            loadMultiplier = coverageFraction
        )

        var targetUsableKwh = profile.energyKwh
        var lastChoice = EquipmentSelectionEngine.selectBestHybridBattery(0.0, 0.0, inverterKw)
        var lastEstimate: BackupEstimator.BackupEstimate? = null
        var lastTargetUsableKwh = targetUsableKwh

        for (attempt in 0 until MAX_BATTERY_SIZING_ATTEMPTS) {
            val choice = EquipmentSelectionEngine.selectBestHybridBattery(targetUsableKwh, profile.peakKw, inverterKw)
            lastChoice = choice
            lastTargetUsableKwh = targetUsableKwh
            if (choice.option == null || choice.totalKwh <= 0.0) {
                lastEstimate = null
                break
            }

            val (chargeKw, dischargeKw) = resolvedBatteryPowerKw(choice.option, choice.totalKwh, inverterKw, inverterName)
            val fallbackKw = min(choice.totalKwh * 0.5, inverterKw.coerceAtLeast(0.1))
            val trialConfig = SimSystemConfig(
                pvCapacityKw = 0.0, panelCount = 0, panelWatts = 0,
                inverterKw = inverterKw, inverterName = inverterName,
                batteryCapacityKwh = choice.totalKwh, batteryName = choice.option.name, hasBattery = true,
                gridConnectable = false, avgDailyLoadKwh = designDailyKwh,
                peakLoadKw = profile.peakKw.coerceAtLeast(designDailyKwh / 10.0),
                batteryMaxChargeKw = chargeKw ?: fallbackKw,
                batteryMaxDischargeKw = dischargeKw ?: fallbackKw,
                batteryChargeEfficiency = 0.95,
                batteryDepthOfDischargeFraction = SimulationEngine.BATTERY_MIN_SOC_FRACTION
            )
            val estimate = BackupEstimator.estimate(trialConfig, input)
            lastEstimate = estimate

            val hours = estimate?.hours ?: 0.0
            if (hours >= windowHours - BACKUP_TARGET_EPSILON_HOURS) break

            // Scale the target by the observed shortfall ratio (plus a small margin) rather than a
            // fixed increment — a battery that only lasted half the window needs roughly double the
            // usable energy, not "the next tier up regardless of how far off it was."
            targetUsableKwh = if (hours > 0.05) targetUsableKwh * (windowHours / hours) * 1.05 else targetUsableKwh * 1.5
        }

        return HybridBatterySizing(lastChoice, lastTargetUsableKwh, lastEstimate)
    }

    /**
     * A63 (spec §24-28's "ADD ONE OR TWO PANELS" rule): [baseline] is [EquipmentSelectionEngine]'s
     * own smallest-electrically-valid pick for a hybrid array with a battery to charge. This checks
     * whether that array can actually recharge the battery to a usable SOC by early afternoon —
     * not assumed, a real simulated day via [RechargeFeasibility] (the same engine everything else
     * in this app runs) — and, only if it can't, tries +1 then +2 panels (same wattage,
     * re-validated for electrical compatibility at the larger count) until one does. If none of
     * the three reach the target, the one that gets closest is kept rather than adding panels
     * indefinitely for no proven benefit; if the baseline already meets the target, it's returned
     * completely unchanged (no wasted simulations, no unnecessary oversizing).
     *
     * Deliberately narrow: this is the panel-COUNT refinement only, one wattage (whatever
     * [EquipmentSelectionEngine] already picked), never called for MANUAL mode (an installer's own
     * equipment choice is used exactly as selected — see this file's MANUAL branch) or when there's
     * no battery to charge (off-grid's own fixed sizing path, grid-tie).
     */
    internal fun recheckPanelCountForRecharge(
        baseline: EquipmentSelectionEngine.PanelChoice,
        inverter: InverterOption,
        chosenBattery: BatteryOption?,
        totalBatteryKwh: Double,
        batteryMaxChargeKw: Double?,
        batteryMaxDischargeKw: Double?,
        peakWatts: Double,
        designDailyKwh: Double,
        input: QuoteInputs
    ): EquipmentSelectionEngine.PanelChoice {
        if (baseline.panelCount <= 0 || totalBatteryKwh <= 0.0) return baseline

        fun trial(count: Int): Pair<EquipmentSelectionEngine.PanelCompatibilityResult, RechargeFeasibility.RechargeResult>? {
            if (count <= 0) return null
            val compat = EquipmentSelectionEngine.checkPanelInverterCompatibility(
                baseline.panelWatts, count, inverter.kw, inverterNameHint = inverter.name
            )
            if (!compat.valid) return null
            val fallbackKw = min(totalBatteryKwh * 0.5, inverter.kw.coerceAtLeast(0.1))
            val config = SimSystemConfig(
                pvCapacityKw = count * baseline.panelWatts / 1000.0,
                panelCount = count, panelWatts = baseline.panelWatts,
                inverterKw = inverter.kw, inverterName = inverter.name,
                batteryCapacityKwh = totalBatteryKwh, batteryName = chosenBattery?.name, hasBattery = true,
                gridConnectable = true, avgDailyLoadKwh = designDailyKwh,
                peakLoadKw = (peakWatts / 1000.0).coerceAtLeast(designDailyKwh / 10.0),
                batteryMaxChargeKw = batteryMaxChargeKw ?: fallbackKw,
                batteryMaxDischargeKw = batteryMaxDischargeKw ?: fallbackKw,
                batteryChargeEfficiency = 0.95,
                batteryDepthOfDischargeFraction = SimulationEngine.BATTERY_MIN_SOC_FRACTION,
                // A70: same site-specific PSH the final config will use — without this, this
                // trial's recharge-feasibility check would simulate against the curve's unscaled
                // reference amplitude, silently over-crediting recharge capability at any site
                // with a below-reference PSH (which is most of them — see REFERENCE_CURVE_PSH_HOURS).
                pshHours = input.peakSunHours.coerceAtLeast(MIN_PSH),
                // A80: month-aware recharge check — an October-designed system's +1/+2 panel
                // decision should be tested against October's own weather model, not the flat
                // annual-average curve, when the installer provided an install month.
                installMonth = input.installMonth
            )
            val result = RechargeFeasibility.evaluate(config, input) ?: return null
            return compat to result
        }

        // oversizePercent is intentionally left as baseline's own figure (this function has no
        // access to the original requiredPvKw to recompute it against) — harmless, since nothing
        // downstream of PanelChoice reads oversizePercent; only panelWatts/panelCount/reason do.
        fun choose(count: Int, compat: EquipmentSelectionEngine.PanelCompatibilityResult, note: String): EquipmentSelectionEngine.PanelChoice {
            return baseline.copy(
                panelCount = count,
                totalPvKw = count * baseline.panelWatts / 1000.0,
                stringCounts = compat.stringCounts,
                withinPreferredVoltageMargin = compat.withinPreferredVoltageMargin,
                reason = "${baseline.reason} Adjusted from ${baseline.panelCount} to $count panels — $note"
            )
        }

        val base = trial(baseline.panelCount) ?: return baseline
        if (base.second.targetMet) return baseline

        val plusOne = trial(baseline.panelCount + 1)
        if (plusOne != null && plusOne.second.targetMet) {
            return choose(baseline.panelCount + 1, plusOne.first, "reaches a usable SOC by ~2 PM in a simulated day; the smaller array did not.")
        }

        val plusTwo = trial(baseline.panelCount + 2)
        if (plusTwo != null && plusTwo.second.targetMet) {
            return choose(baseline.panelCount + 2, plusTwo.first, "reaches a usable SOC by ~2 PM in a simulated day, where +1 panel alone still did not.")
        }

        val candidates = listOfNotNull(
            Triple(baseline.panelCount, base.first, base.second),
            plusOne?.let { Triple(baseline.panelCount + 1, it.first, it.second) },
            plusTwo?.let { Triple(baseline.panelCount + 2, it.first, it.second) }
        )
        val best = candidates.maxBy { it.third.socAtTargetHourPercent }
        return if (best.first == baseline.panelCount) baseline
        else choose(
            best.first, best.second,
            "reaches a higher simulated SOC by ~2 PM (%.0f%%) than the smaller array, though neither fully reaches the 90%% recharge target."
                .format(best.third.socAtTargetHourPercent)
        )
    }

    /**
     * A57 (spec §11 — "remove the separate 'use discounted price' option... do not maintain two
     * competing price systems"): ONE price list. What used to be a second, fully separate
     * "discount price list" the installer could swap the whole quote onto is gone; a discount is
     * now only ever [QuoteInputs.discountType]/[QuoteInputs.discountValue] — percent or fixed —
     * applied on top of this one price list's subtotal, below.
     */
    fun calculate(input: QuoteInputs, prices: PriceList): QuoteResult {
        // Phase 27 ("COMMERCIAL & INDUSTRIAL SYSTEM ARCHITECTURE" — "Use one SystemDesign
        // architecture... Residential should simply use fewer fields where they are unnecessary"):
        // the ONLY change this whole phase makes to the residential path. RESIDENTIAL (the default
        // for every existing/older quote) falls straight through to the untouched code below;
        // COMMERCIAL/INDUSTRIAL dispatch to a separate calculator that reuses this same QuoteInputs/
        // QuoteResult/EquipmentSelectionEngine/MpptStringPlanner architecture rather than forking it
        // — see CommercialIndustrialCalculator's own doc.
        if (input.systemCategory != SystemType.RESIDENTIAL) {
            return com.lumix.estimator.domain.commercial.CommercialIndustrialCalculator.calculate(input, prices)
        }

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
        // per spec §14 (compare usable energy, not nominal kWh alone). A64: this flat
        // criticalDailyKwh*(backupHours/24) figure is only the OFFGRID/starting-point value now —
        // HYBRID mode overwrites both this and batteryRequiredKwh below with the real
        // simulation-driven figure from sizeHybridBatteryForBackup, so the "required" number shown
        // to the installer always matches what actually drove the selection (the exact mismatch
        // the installer's own 2026-08-14 bug report was about — "~17kWh needed, 15kWh selected").
        var requiredBatteryUsableKwh = criticalDailyKwh * backupFractionOfDay

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
            val requiredSurgeKw = worstCaseSurgeKw(input)
            val inverterChoice = EquipmentSelectionEngine.selectBestInverter(requiredInverterKw, requiredSurgeKw, inverterPool)
            val selectedInverter = inverterChoice.option
            chosenInverter = selectedInverter
            inverterSelectionReason = inverterChoice.reason

            when (input.systemMode) {
                SystemMode.HYBRID -> {
                    // A64: replaces the flat criticalDailyKwh*(backupHours/24) target with a real
                    // overnight-load-simulation-driven, simulation-VERIFIED search — see
                    // sizeHybridBatteryForBackup's own doc for the full rationale.
                    val sizing = sizeHybridBatteryForBackup(input, designDailyKwh, selectedInverter.kw, selectedInverter.name)
                    val batteryChoice = sizing.choice
                    chosenBattery = batteryChoice.option
                    batteryModuleCount = batteryChoice.moduleCount
                    totalBatteryKwh = batteryChoice.totalKwh
                    requiredBatteryUsableKwh = sizing.requiredUsableKwh
                    // Nominal "required" figure for display, using this same battery's own real
                    // usable-energy fraction rather than the generic BATTERY_DOD assumption, so it
                    // stays consistent with whatever tier sizing.choice actually picked.
                    val usableFraction = if (batteryChoice.totalKwh > 0) batteryChoice.totalUsableKwh / batteryChoice.totalKwh else BATTERY_DOD
                    batteryRequiredKwh = sizing.requiredUsableKwh / usableFraction.coerceAtLeast(0.01)
                    val estimate = sizing.backupEstimate
                    val backupNote = when {
                        estimate == null -> ""
                        estimate.hours >= input.backupHours - BACKUP_TARGET_EPSILON_HOURS ->
                            " Simulated overnight backup: %.1f hours — meets the %.0f-hour target.".format(estimate.hours, input.backupHours)
                        else ->
                            " Simulated overnight backup: %.1f hours — BACKUP TARGET NOT MET (%.0f hours requested); this is the closest this catalog's battery tiers get within %d sizing attempts."
                                .format(estimate.hours, input.backupHours, MAX_BATTERY_SIZING_ATTEMPTS)
                    }
                    batterySelectionReason = batteryChoice.reason + backupNote
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
                // Off-grid systems here stay on the simpler fixed-smallest-wattage/max-4-panel
                // sizing this catalog has always used for them (small stand-alone arrays) rather
                // than the full multi-wattage search — scope note, not an oversight: the
                // multi-wattage search below is aimed at hybrid/grid-interactive arrays, which is
                // where the spec's own "10 × 620W" style examples live. A51 replaced the old
                // fixed 550W (no longer a verified catalog wattage) with the smallest wattage the
                // verified equipment database actually offers.
                panelW = Catalog.panelWattages.first()
                panelCount = min(enforceEvenPanels((pvKw * 1000) / panelW), 4)
                panelSelectionReason = "%.2f kW required — %d × %dW panels (off-grid arrays capped at 4)."
                    .format(pvKw, panelCount, panelW)
            } else {
                var panelChoice = EquipmentSelectionEngine.selectBestPanelConfiguration(
                    pvKw, selectedInverter.kw, inverterNameHint = selectedInverter.name
                )
                // A63 (spec §24-28's "ADD ONE OR TWO PANELS" rule): EquipmentSelectionEngine only
                // just found the smallest electrically valid array — for a hybrid system with a
                // battery to charge, that's not the whole answer yet. Check whether it can actually
                // recharge the battery to a usable SOC by early afternoon via a real simulated day,
                // and only then, if it can't, grow it by 1 or 2 panels until it does (or until +2
                // stops helping) — never for MANUAL mode, never when there's no battery.
                if (input.systemMode == SystemMode.HYBRID && totalBatteryKwh > 0) {
                    val (earlyChargeKw, earlyDischargeKw) = resolvedBatteryPowerKw(chosenBattery, totalBatteryKwh, selectedInverter.kw, selectedInverter.name)
                    panelChoice = recheckPanelCountForRecharge(
                        baseline = panelChoice,
                        inverter = selectedInverter,
                        chosenBattery = chosenBattery,
                        totalBatteryKwh = totalBatteryKwh,
                        batteryMaxChargeKw = earlyChargeKw,
                        batteryMaxDischargeKw = earlyDischargeKw,
                        peakWatts = peakWatts,
                        designDailyKwh = designDailyKwh,
                        input = input
                    )
                }
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
                // 2026-08-18 audit fix: MANUAL's own "let system choose inverter" fallback used to
                // pick by continuous kW alone, skipping the surge-tolerance check
                // EquipmentSelectionEngine.selectBestInverter already enforces for GUIDED/LOAD (a
                // unit that covers the continuous load but not a worst-case motor/compressor
                // startup surge). Reusing the same engine here means "let the system choose" means
                // the same thing regardless of which mode asked for it.
                val pool = Catalog.poolFor(effectiveSystemMode)
                val autoChoice = EquipmentSelectionEngine.selectBestInverter(requiredInverterKw, worstCaseSurgeKw(input), pool)
                chosenInverter = autoChoice.option
                inverterSelectionReason = autoChoice.reason
            }

            panelW = if (input.manualPanelWatts > 0) input.manualPanelWatts else Catalog.panelWattages.first()

            when (effectiveSystemMode) {
                SystemMode.HYBRID -> {
                    val total5 = input.manualBatt5k * 5.0
                    val total10 = input.manualBatt10k * 10.0
                    val total15 = input.manualBatt15k * 15.0
                    val total16 = input.manualBatt16k * 16.0
                    val total20 = input.manualBatt20k * 20.0
                    // A54: custom capacity/count fields ("add your own capacity") — a negative or
                    // zero entry on either side contributes nothing, same guard as every count field.
                    val totalCustom = if (input.manualBattCustomKwh > 0 && input.manualBattCustomCount > 0) {
                        input.manualBattCustomKwh * input.manualBattCustomCount
                    } else 0.0
                    totalBatteryKwh = total5 + total10 + total15 + total16 + total20 + totalCustom
                    batteryModuleCount = input.manualBatt5k + input.manualBatt10k + input.manualBatt15k +
                        input.manualBatt16k + input.manualBatt20k +
                        (if (totalCustom > 0) input.manualBattCustomCount else 0)
                    if (batteryModuleCount > 0) {
                        val avgKwh = totalBatteryKwh / batteryModuleCount
                        chosenBattery = BatteryOption("Hybrid LiFePO4 mix", avgKwh) { 0.0 }
                    }
                    // 2026-08-18 audit fix: manualBatteryWarning (below) used to compare the
                    // installer's own pick against the pre-A64 flat
                    // criticalDailyKwh*(backupHours/24)/BATTERY_DOD estimate — the exact formula
                    // sizeHybridBatteryForBackup's own doc says was replaced for GUIDED/LOAD
                    // because it disagreed with what the real overnight-load simulation actually
                    // requires (the installer's own "~17kWh needed, 15kWh selected" bug report).
                    // Runs that same real simulation here too, purely to get an accurate
                    // "required" figure for the warning check — does NOT touch totalBatteryKwh/
                    // chosenBattery/batteryModuleCount above, which stay exactly what the
                    // installer picked.
                    val sizing = sizeHybridBatteryForBackup(input, designDailyKwh, chosenInverter!!.kw, chosenInverter!!.name)
                    requiredBatteryUsableKwh = sizing.requiredUsableKwh
                    batteryRequiredKwh = sizing.requiredUsableKwh / BATTERY_DOD
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

            // 2026-08-18 audit fix: this cap used to only fire when panelW equaled the smallest
            // catalog wattage (595W) — the exact wattage GUIDED/LOAD always force off-grid to (see
            // that branch's own "small stand-alone arrays" scope-note doc). MANUAL mode lets the
            // installer pick any of the five catalog wattages for an off-grid array, so picking
            // anything BIGGER than 595W bypassed the cap entirely — an installer could configure
            // an arbitrarily large off-grid array this catalog's off-grid inverter tier (3-3.2kW
            // units) was never scoped to serve. The scope boundary is about off-grid systems in
            // general, not specifically the smallest panel — apply it unconditionally, same as
            // GUIDED/LOAD.
            if (effectiveSystemMode == SystemMode.OFFGRID) {
                panelCount = min(panelCount, 4)
            }
        }

        // A81 (Phase 18, restored): cap the energy-optimal panel count at what the roof can
        // physically hold (from Solar Site's real geometric panel-packing result), if lower.
        // Rounds the cap DOWN to even rather than reusing enforceEvenPanels (which rounds up) —
        // exceeding the roof's actual capacity to satisfy the even-row convention would violate
        // the "never overclaim what fits" guarantee the panel-packing engine provides.
        //
        // 2026-08-18 audit fix: [constraint.maxPanelCount] was computed by Solar Site's real
        // panel-packing geometry for a SPECIFIC panel footprint ([constraint.panelWattage] —
        // whatever size the installer typed/assumed when tracing the roof, not necessarily a real
        // catalog SKU), but the electrical sizing above can select a DIFFERENT, larger real panel
        // (up to 720W vs. a roof survey done against, say, a 600W assumption — a ~20% bigger
        // physical footprint per panel). Capping by raw panel COUNT alone silently let a
        // larger-footprint selection through under the smaller-panel count ceiling, overclaiming
        // what the mapped roof can actually hold. [RoofConstraint] doesn't carry the roof's raw
        // polygon/usable-area (only the panel-count result for its own assumed panel), so an exact
        // re-pack against the real selected panel isn't possible here without a bigger plumbing
        // change — capping by the roof's implied kWp ceiling ([constraint.maxCapacityKw], area
        // being roughly proportional to wattage for real panels) converted to a count of the
        // ACTUALLY selected panel is a close, conservative proxy and a real fix for the count-only
        // comparison, not just a cosmetic change.
        val energyOptimalPanelCount = panelCount
        input.roofConstraint?.let { constraint ->
            val maxCountForSelectedPanel = floor((constraint.maxCapacityKw * 1000.0) / panelW).toInt()
            if (maxCountForSelectedPanel < panelCount) {
                panelCount = if (maxCountForSelectedPanel % 2 == 1) maxCountForSelectedPanel - 1 else maxCountForSelectedPanel
                panelCount = max(0, panelCount)
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

        // A89/Ph21 (master prompt — always-2-rails-per-set, confirmed with the project owner via
        // AskUserQuestion 2026-08-18: "Force always-2-rails per your literal spec"): replaces the
        // old roof-type-dependent 2-or-3-rail count. Duplicated here (not just inside
        // MaterialTakeoffEngine, which independently computes and prices the same figures) purely
        // to keep QuoteResult's pre-existing display fields (rows/railsPerRow/totalRails/...)
        // populated — both read the identical RailLayoutCalculator call, so they can't drift.
        val panelWidthMm = EquipmentSpecs.panelSpecFor(panelW)?.dimensions?.widthMm ?: 1134.0
        val panelsPerSet = RailLayoutCalculator.layoutFor(panelWidthMm).maxPracticalModules.coerceAtLeast(1)
        val rows = if (panelCount > 0) ceil(panelCount / panelsPerSet.toDouble()).toInt() else 0
        val railsPerRow = 2
        val totalRails = rows * railsPerRow
        val fullSets = if (panelCount > 0) panelCount / panelsPerSet else 0
        val setsRemainder = if (panelCount > 0) panelCount % panelsPerSet else 0
        fun midClampsForSet(panelsInSet: Int) = (2 * (panelsInSet - 1)).coerceAtLeast(0)
        val totalMidClamps = fullSets * midClampsForSet(panelsPerSet) +
            (if (setsRemainder > 0) midClampsForSet(setsRemainder) else 0)
        val totalEndClamps = rows * 4
        val totalBackLegs = if (input.roofType == RoofType.SLAB) rows * 4 else 0
        val totalFrontLegs = if (input.roofType == RoofType.SLAB) rows * 4 else 0
        val totalBolts = if (input.roofType == RoofType.SLAB) rows * 8 * 2 else 0
        // A89/Ph21 follow-up (2026-08-18 — "shingle roof and zinc roof carrys the same rule"):
        // SHINGLE now gets the same 8-L-foot-per-set treatment as ZINC, fixing a pre-existing gap
        // (the code before this round only ever branched on SLAB/ZINC, leaving SHINGLE with no
        // mounting hardware at all).
        val totalLFoot = if (input.roofType == RoofType.ZINC || input.roofType == RoofType.SHINGLE) totalRails * 4 else 0

        // A89/Ph21 follow-up (2026-08-18): real price/quantity from the project owner — J$450/pair,
        // 4 pairs on every system regardless of string count, replacing the old off-grid-vs-other
        // brand-mode split.
        val mc4CountPairs = 4

        // Off-grid keeps its own pre-existing AC-wiring convention — no spreadsheet/master-prompt
        // rule addresses off-grid AC wiring specifically; see MaterialTakeoffEngine's own doc for
        // why the new red/black/ground bundle below is HYBRID/GRIDTIE-only.
        val wire6mmFt = if (effectiveSystemMode == SystemMode.OFFGRID) 50 else 0

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
                if (input.manualBatt16k > 0) materials += MaterialLine("16kWh LiFePO4", input.manualBatt16k.toDouble(), prices.batteryLFP16k)
                if (input.manualBatt20k > 0) materials += MaterialLine("20kWh LiFePO4", input.manualBatt20k.toDouble(), prices.batteryLFP20k)
                if (input.manualBattCustomKwh > 0 && input.manualBattCustomCount > 0) {
                    materials += MaterialLine(
                        "%.1fkWh LiFePO4 (custom)".format(input.manualBattCustomKwh),
                        input.manualBattCustomCount.toDouble(),
                        prices.batteryLFPCustomPerKwh?.let { input.manualBattCustomKwh * it }
                    )
                }
            } else if (effectiveSystemMode == SystemMode.OFFGRID && input.manualAgmCount > 0) {
                materials += MaterialLine("12V AGM battery", input.manualAgmCount.toDouble(), prices.batteryAGM12V)
            }
        }

        if (effectiveSystemMode == SystemMode.OFFGRID && chargeControllerCount > 0) {
            materials += MaterialLine("80A MPPT charge controller", chargeControllerCount.toDouble(), prices.chargeController80A)
        }

        if (mc4CountPairs > 0) materials += MaterialLine("MC4 connector pair", mc4CountPairs.toDouble(), prices.mc4Pair)
        if (wire6mmFt > 0) materials += MaterialLine("6mm single wire, off-grid (ft)", wire6mmFt.toDouble(), prices.ac6mmPerFt)
        // A89/Ph21 follow-up (2026-08-18 — "leave out battery wires and lugs these batteries
        // comes with their own lugs and cable"): battery interconnect cable/lugs are no longer
        // quoted as separate materials — every battery this catalog offers ships with its own.

        // A89/Ph21 (master prompt — "ask if automatic switch or manual or no transfer switch"):
        // resolved to one TransferSwitchMode for this quote. MANUAL+OFFGRID keeps its own
        // pre-existing toggle (see TransferSwitchMode's own doc for why); every other combination
        // reads the new universal field, which defaults to AUTOMATIC — the same behavior every
        // other quote already had before this field existed.
        val resolvedTransferSwitchMode = if (input.quoteMode == QuoteMode.MANUAL && effectiveSystemMode == SystemMode.OFFGRID) {
            if (input.manualOffgridUseAutoTransfer) TransferSwitchMode.AUTOMATIC else TransferSwitchMode.MANUAL
        } else {
            input.transferSwitchMode
        }

        // A89/Ph21: mounting hardware, PV wire bundle, DC string protection, AC wire bundle + AC
        // breaker pair, changeover switch, trunking, distribution panel, battery DC connection,
        // surge arresters, enclosures, conduit, grounding, misc, voltage regulator — the master
        // prompt's own material-takeoff formulas, all priced from the real spreadsheet-sourced
        // PriceList fields (see MaterialTakeoffEngine's own doc; never invents a price).
        materials += MaterialTakeoffEngine.compute(
            MaterialTakeoffEngine.TakeoffInput(
                panelW = panelW,
                panelCount = panelCount,
                effectiveSystemMode = effectiveSystemMode,
                roofType = input.roofType,
                inverter = inverter,
                batteryModuleCount = batteryModuleCount,
                transferSwitchMode = resolvedTransferSwitchMode,
                useVoltageRegulator = input.useVoltageRegulator,
                use8WayDistributionPanel = input.use8WayDistributionPanel
            ),
            prices
        )

        // A89/Ph21 (master prompt §"QUANTITY AND PRICE OVERRIDES" — "quantity and price overrides
        // at the quote level; catalog price stays unchanged"): applied last, on top of the
        // engine's own calculated lines, keyed by MaterialLine.calcKey — never mutates prices, so
        // every OTHER quote's catalog defaults are unaffected.
        val finalMaterials: List<MaterialLine> = if (input.materialOverrides.isEmpty()) materials else materials.map { line ->
            val override = line.calcKey?.let { input.materialOverrides[it] } ?: return@map line
            line.copy(qty = override.qtyOverride ?: line.qty, unitPrice = override.priceOverride ?: line.unitPrice)
        }

        // A89/Ph21 (master prompt, repeated twice — "NEVER INVENT A PRICE... A BLANK PRICE MUST
        // ALWAYS REMAIN BLANK UNTIL THE INSTALLER ENTERS IT"): every MaterialLine with no price,
        // plus delivery's own toll line below — see QuoteResult.missingPriceItems/canFinalize.
        val missingPriceItems = mutableListOf<String>()
        finalMaterials.filterNot { it.hasPrice }.forEach { missingPriceItems += it.name }

        val materialsTotal = finalMaterials.sumOf { it.subtotal }
        // A79 (spec Phase 16, §40 "Labour rates"): reads the now-configurable rate instead of a
        // hard-coded 0.15 literal — see PriceList.serviceRatePercent's own doc.
        val serviceCharge = materialsTotal * (prices.serviceRatePercent / 100.0)

        // A89/Ph21 follow-up (2026-08-18 — "let me manually enter delivery price"): the base
        // delivery figure is always the installer's own manual entry (input.deliveryCharge) —
        // DeliveryCalculator's proportional Junction->Santa Cruz formula stays built for later
        // (see its own doc) but is no longer used to compute or override this field. Toll is still
        // a separate, explicit add-on: only added when the route is flagged as a toll route, and
        // never invented when the rate hasn't been entered.
        val tollCharge = if (input.deliveryIsTollRoute) prices.deliveryTollJmd else 0.0
        val tollMissing = input.deliveryIsTollRoute && tollCharge == null
        val resolvedDeliveryCharge = input.deliveryCharge + (tollCharge ?: 0.0)
        if (tollMissing) {
            missingPriceItems += "Toll charge (route crosses a toll — enter the current official rate in Settings)"
        }

        // 2026-08-18 ("i will manually enter the cost for installation and commission"): same
        // always-manual pattern as deliveryCharge above — never computed from system size.
        val preDiscountTotal = materialsTotal + serviceCharge + resolvedDeliveryCharge + input.installationCommissioningCharge

        var discountAmount = when (input.discountType) {
            DiscountType.PERCENT -> preDiscountTotal * (input.discountValue / 100.0)
            DiscountType.FIXED -> input.discountValue
            DiscountType.NONE -> 0.0
        }
        if (discountAmount < 0) discountAmount = 0.0
        if (discountAmount > preDiscountTotal) discountAmount = preDiscountTotal

        // A79 (spec Phase 16, §40 "Tax settings"): reads the now-configurable rate, applied to the
        // post-discount subtotal — see PriceList.taxRatePercent's own doc for why tax follows
        // discount rather than the other way around. Still 0.0 by default (PriceList.DEFAULT), so
        // an installer who never opens Settings sees identical totals to before this round.
        val taxAmount = (preDiscountTotal - discountAmount) * (prices.taxRatePercent / 100.0)
        val grandTotal = preDiscountTotal - discountAmount + taxAmount

        // Resolved once, here, at calculation time — never re-matched against a possibly-newer
        // equipment catalog when a saved quote's simulation is opened later. See
        // QuoteResult.batteryMaxChargeKw's own doc for why this matters for reproducibility.
        val (batteryMaxChargeKw, batteryMaxDischargeKw) = resolvedBatteryPowerKw(chosenBattery, totalBatteryKwh, inverter.kw, inverter.name)
        // A69: same reproducibility rationale, for the inverter's real max PV DC input power — see
        // QuoteResult.inverterMaxPvKw's own doc.
        val inverterMaxPvKw = EquipmentSpecs.inverterSpecFor(inverter.kw, inverter.name)?.maxPvW?.let { it / 1000.0 }

        val result = QuoteResult(
            effectiveSystemMode = effectiveSystemMode,
            designDailyKwh = designDailyKwh,
            peakWatts = peakWatts,
            panelCount = panelCount,
            panelWatts = panelW,
            energyOptimalPanelCount = energyOptimalPanelCount,
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
            materials = finalMaterials,
            materialsTotal = materialsTotal,
            serviceCharge = serviceCharge,
            deliveryCharge = resolvedDeliveryCharge,
            installationCommissioningCharge = input.installationCommissioningCharge,
            subtotalBeforeDiscount = preDiscountTotal,
            discountAmount = discountAmount,
            taxAmount = taxAmount,
            grandTotal = grandTotal,
            missingPriceItems = missingPriceItems,
            canFinalize = missingPriceItems.isEmpty(),
            backupCapacityWarningKw = backupCapacityWarningKw,
            batteryMaxChargeKw = batteryMaxChargeKw,
            batteryMaxDischargeKw = batteryMaxDischargeKw,
            inverterMaxPvKw = inverterMaxPvKw,
            designPeakSunHours = psh,
            designInstallMonth = input.installMonth,
            requiredPvKw = requiredPvKw,
            requiredInverterKw = requiredInverterKw,
            requiredBatteryUsableKwh = requiredBatteryUsableKwh,
            panelSelectionReason = panelSelectionReason,
            inverterSelectionReason = inverterSelectionReason,
            batterySelectionReason = batterySelectionReason,
            manualInverterWarning = manualInverterWarning,
            manualBatteryWarning = manualBatteryWarning
        )

        // A54: run the real backup estimate against the system that was JUST built (not a
        // separate ratio) — see BackupEstimator's own doc for why this must be the one figure
        // every screen (System Review, Results, PDF) reads instead of recomputing its own.
        val simConfig = SimSystemConfig.from(result, input)
        val backupEstimate = BackupEstimator.estimate(simConfig, input)
        val rechargeCheck = RechargeFeasibility.evaluate(simConfig, input)
        // A80 (spec Phase 17 §"SIZING MUST USE WEATHER LOGIC" — "report: Estimated typical daily
        // solar production, Estimated conservative daily solar production"): only computed when an
        // install month was actually picked — without one, there's no month-specific day to
        // integrate and the field stays null (see QuoteResult.estimatedTypicalDailyPvKwh's own
        // doc). This is a *generation* figure for the quote, so it sums harvestablePvKw (the
        // array's post-loss production ceiling) rather than pvKw — as of the 2026-08-18
        // charging-physics fix, pvKw is the *harvested* figure, which a real MPPT throttles back
        // when the battery is full, so summing it here would understate the array's real monthly
        // production for reasons that have nothing to do with the weather this figure is about.
        // harvestablePvKw is battery-SOC-independent (see buildDayTimeline's own `pv` derivation),
        // so a single full-day timeline per scenario is still enough to sum.
        val dailyProductionEstimate = input.installMonth?.let { month ->
            val dt = 5.0 / 60.0
            fun dailyPvKwh(scenario: WeatherScenario): Double {
                val curve = WeatherEngine.generate(scenario, month)
                val timeline = SimulationEngine.buildDayTimeline(
                    config = simConfig,
                    resolutionMinutes = 5,
                    installMonth = month,
                    weatherCurve = curve
                )
                return timeline.sumOf { it.harvestablePvKw * dt }
            }
            dailyPvKwh(WeatherScenario.TYPICAL) to dailyPvKwh(WeatherScenario.CLOUDIER)
        }
        // A64 (spec §8/§25 — "Do not display '12-hour backup'... display the real simulated hours
        // and BACKUP TARGET NOT MET"): distinct from estimatedBackupSufficient, which only means
        // "survived the whole multi-day stress window tested" — this compares against what the
        // installer actually requested.
        val backupTargetMet = if (simConfig.hasBattery) backupEstimate.hours >= input.backupHours - BACKUP_TARGET_EPSILON_HOURS else null

        return result.copy(
            estimatedBackupHours = backupEstimate.hours,
            estimatedBackupSufficient = backupEstimate.sufficientForFullWindow,
            estimatedBackupReason = backupEstimate.reason,
            batteryBackupTargetMet = backupTargetMet,
            batteryRechargeTargetMet = rechargeCheck?.targetMet,
            batteryRechargeSocAt2pmPercent = rechargeCheck?.socAtTargetHourPercent,
            batteryRechargeHour = rechargeCheck?.hourReachedTarget,
            estimatedTypicalDailyPvKwh = dailyProductionEstimate?.first,
            estimatedConservativeDailyPvKwh = dailyProductionEstimate?.second
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
        val preview = calculate(input, PriceList.DEFAULT)
        return listOfNotNull(preview.manualInverterWarning, preview.manualBatteryWarning)
            .any { it !in input.manualWarningsAcknowledged }
    }
}
