package com.lumix.estimator.domain.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A87 (spec Phase 24 — "FINAL ENGINEERING PHYSICS, POWER DISPATCH, SIZING AND SIMULATION
 * VALIDATION"): regression tests for TEST 1-7 of that phase's own §27 ("REGRESSION TESTS") —
 * exercised against the real [SimulationEngine.buildDayTimeline] (the one engine every mode/
 * screen already shares — see §1's own "one engine" requirement, already satisfied
 * architecturally by [SimSystemConfig.from] before this round), not a second, parallel model.
 *
 * §27's own worked numbers (400W/100W/1500W/2000W) are explicitly "examples only" (§5's own
 * words) — this file follows this codebase's established testing convention instead (see e.g.
 * [SimulationEngineBatteryDischargeEfficiencyTest]): hand-trace the REAL formula's actual output
 * for a deliberately chosen, precisely reproducible config, rather than force the day-shape model
 * to land on round numbers it has no reason to hit exactly. Every expected figure below was
 * computed by literally replicating [SimulationEngine.buildDayTimeline]'s own formulas (weekday
 * load shape, [SystemLosses] temperature derate, [BatteryPowerCurve] taper) in Python, not
 * invented — see each test's own comment for the shape of that computation.
 *
 * TEST 8 (MPPT validation — invalid strings fail, valid uneven strings like 13 panels/2 MPPTs ->
 * 7+6 pass) already has thorough, pre-existing coverage in
 * [com.lumix.estimator.domain.MpptStringPlannerTest] and
 * [com.lumix.estimator.domain.EquipmentSelectionEngineTest] (Voc/Vmp/Isc/Imp invalidation, the
 * exact 13-panel/2-tracker split, and even-vs-uneven preference) — intentionally not duplicated
 * here.
 */
class Phase24EngineeringValidationTest {

    /**
     * 2026-08-19 ("put 100w for inverter use"): [SimSystemConfig.inverterSelfConsumptionKw] now
     * defaults to a flat 0.1kW (100W) regardless of inverterKw — previously 20.0 x 0.005 = 0.1kW
     * happened to land on the same number for this fixture's specific 20kW inverter, which is why
     * every assertion below that reads `frame.inverterSelfConsumptionKw` (expecting 0.1) still
     * holds unchanged; only this comment needed updating, since the value is no longer derived
     * from inverterKw at all. Still matches §27's own "100W inverter consumption" example exactly.
     */
    private fun config(
        pvCapacityKw: Double = 10.0,
        batteryCapacityKwh: Double = 10.0,
        batteryMaxChargeKw: Double = 5.0,
        batteryMaxDischargeKw: Double = 5.0,
        gridConnectable: Boolean = true
    ) = SimSystemConfig(
        pvCapacityKw = pvCapacityKw, panelCount = 1, panelWatts = (pvCapacityKw * 1000).toInt(),
        inverterKw = 20.0, inverterName = "Test 20kW Inverter",
        batteryCapacityKwh = batteryCapacityKwh, batteryName = "Test Battery",
        hasBattery = batteryCapacityKwh > 0, gridConnectable = gridConnectable,
        avgDailyLoadKwh = 0.0, peakLoadKw = 10.0,
        batteryMaxChargeKw = batteryMaxChargeKw, batteryMaxDischargeKw = batteryMaxDischargeKw,
        batteryChargeEfficiency = 0.95,
        batteryDepthOfDischargeFraction = SimulationEngine.BATTERY_MIN_SOC_FRACTION,
        maxPvInputKw = 30.0
    )

    private fun singleFrame(cfg: SimSystemConfig, hour: Double, startSocFraction: Double, applianceLoadKw: Double, gridConnected: Boolean = true) =
        SimulationEngine.buildDayTimeline(
            config = cfg, gridConnected = gridConnected, startSocFraction = startSocFraction,
            startHour = hour, durationHours = 0.0, resolutionMinutes = 5, applianceLoadKw = applianceLoadKw
        ).first()

    @Test
    fun `TEST 1 - battery charge taper is continuous and reaches exactly zero at 100 percent SOC`() {
        // BatteryPowerCurve.chargeTaperFraction directly, isolated from the day-shape model —
        // "90%: substantial charging... 95%: begins reducing... 97%: noticeably reduced...
        // 98-99%: increasingly limited... 100%: charging = 0W" (all qualitative examples, not
        // hard-coded target values — this asserts the SHAPE, not spec-literal numbers).
        val f80 = BatteryPowerCurve.chargeTaperFraction(0.80)
        val f90 = BatteryPowerCurve.chargeTaperFraction(0.90)
        val f95 = BatteryPowerCurve.chargeTaperFraction(0.95)
        val f97 = BatteryPowerCurve.chargeTaperFraction(0.97)
        val f99 = BatteryPowerCurve.chargeTaperFraction(0.99)
        val f100 = BatteryPowerCurve.chargeTaperFraction(1.00)

        assertEquals(1.0, f80, 0.0001) // at/below the taper start, full rated power
        assertTrue("90% must still allow substantial charging", f90 > 0.3)
        assertTrue("each step nearer 100% must charge less than the previous", f90 > f95 && f95 > f97 && f97 > f99 && f99 > f100)
        assertEquals(0.0, f100, 0.0001)
        // 2026-08-18 trickle-charge fix: the last percent must still trickle at a REAL rate, not
        // collapse toward zero and stall at ~99% into the evening. Below-100% SOC is held at a
        // nonzero floor so the pack actually completes to 100% (the room clamp finishes it).
        assertTrue("99% must still trickle at a meaningful rate, not a near-zero stall", f99 >= 0.09)

        // Integration: a battery already AT 100% SOC (roomKwh == 0) must show exactly 0W charging
        // in a real timeline frame, independent of the taper fraction's own value at that point —
        // the hard room-based clamp in buildDayTimeline is what actually guarantees this.
        val cfg = config()
        val frame = singleFrame(cfg, hour = 12.0, startSocFraction = 1.0, applianceLoadKw = 0.3)
        assertEquals(0.0, frame.solarToBatteryKw, 0.0001)
        assertEquals(100f, frame.batterySocPercent, 0.01f)
    }

    @Test
    fun `TEST 7 - full battery plus low load throttles the array back to the load instead of over-producing`() {
        // hour=12 (near solar noon), pvCapacityKw=10 -> harvestable (post-loss) PV ~7.87kW
        // (hand-traced via SimulationEngine's own irradiance/temperature-derate formulas). Battery
        // starts at 100% SOC (no room to charge) and load is small (0.3kW appliance override +
        // background + the 0.1kW inverter self-consumption default for this config's 20kW inverter).
        // 2026-08-18 charging-physics fix: a real MPPT hybrid inverter walks the array back off its
        // max-power point here rather than over-producing and dumping — so *harvested* PV
        // (frame.pvKw) drops to just the served load, while the harvestable ceiling
        // (frame.harvestablePvKw) stays ~7.87kW and the gap is throttled off (curtailed).
        val cfg = config()
        val frame = singleFrame(cfg, hour = 12.0, startSocFraction = 1.0, applianceLoadKw = 0.3)

        // The ceiling the array *could* make is unchanged (~7.87kW) — the fix is about what it
        // actually harvests, not about the array's potential.
        assertEquals(7.867, frame.harvestablePvKw, 0.01)
        assertEquals(0.1, frame.inverterSelfConsumptionKw, 0.0001)
        val expectedDemand = frame.houseLoadKw + frame.inverterSelfConsumptionKw
        assertEquals(0.5344, expectedDemand, 0.01)

        // The demand gets served in full from solar...
        assertEquals(expectedDemand, frame.solarToHouseKw, 0.001)
        // ...and harvested production drops to exactly that — the array is throttled, not over-run.
        assertEquals(frame.solarToHouseKw, frame.pvKw, 0.001)
        // The throttled-off remainder is the gap between the ceiling and what was harvested.
        assertEquals(frame.harvestablePvKw - frame.pvKw, frame.curtailedSolarKw, 0.001)
        assertTrue("most of the available PV must be throttled off, not consumed, with the battery full and load small", frame.curtailedSolarKw > 7.0)

        // Conservation still holds exactly — harvested PV is fully accounted by house + battery.
        assertEquals(0.0, SimulationEngine.energyImbalanceKw(frame), 0.0001)
    }

    @Test
    fun `TEST 2 - PV accepted rises toward the new demand when load increases, without discharging the battery`() {
        val cfg = config()
        val lowLoad = singleFrame(cfg, hour = 12.0, startSocFraction = 1.0, applianceLoadKw = 0.3)
        val higherLoad = singleFrame(cfg, hour = 12.0, startSocFraction = 1.0, applianceLoadKw = 0.9)

        val lowDemand = lowLoad.houseLoadKw + lowLoad.inverterSelfConsumptionKw
        val higherDemand = higherLoad.houseLoadKw + higherLoad.inverterSelfConsumptionKw
        assertTrue("the higher-load scenario must actually demand more", higherDemand > lowDemand)

        // Ample curtailed PV was available in both cases (see TEST 7), so the *entire* extra
        // demand gets served by solar that would otherwise have been curtailed — accepted
        // (solarToHouseKw) PV rises by exactly the load increase, not partially or via the battery.
        assertEquals(higherDemand - lowDemand, higherLoad.solarToHouseKw - lowLoad.solarToHouseKw, 0.001)
        assertEquals(100f, lowLoad.batterySocPercent, 0.01f)
        assertEquals(100f, higherLoad.batterySocPercent, 0.01f) // battery remains ~100% — no unnecessary discharge
        assertEquals(0.0, higherLoad.batteryToHouseKw, 0.0001)
    }

    @Test
    fun `TEST 3 - load exceeding PV draws the deficit from the battery, within its discharge limit`() {
        // hour=8 (morning, PV well below full) with a load large enough to exceed it but well
        // within the battery's rated discharge power — SOL/off-grid so the deficit has nowhere
        // else to come from, isolating the battery's own response.
        val cfg = config(batteryMaxDischargeKw = 5.0)
        val frame = singleFrame(cfg, hour = 8.0, startSocFraction = 0.6, applianceLoadKw = 5.0, gridConnected = false)

        assertEquals(4.244, frame.pvKw, 0.01)
        val demand = frame.houseLoadKw + frame.inverterSelfConsumptionKw
        assertTrue("demand must exceed available PV for this scenario to test the battery deficit path", demand > frame.pvKw)

        // All available PV is used (none curtailed — it's needed), and the battery covers exactly
        // the remaining deficit.
        assertEquals(frame.pvKw, frame.solarToHouseKw, 0.001)
        assertEquals(0.0, frame.curtailedSolarKw, 0.001)
        assertEquals(demand - frame.pvKw, frame.batteryToHouseKw, 0.005)
        assertEquals(0.0, frame.unmetLoadKw, 0.0001)
        assertEquals(0.0, SimulationEngine.energyImbalanceKw(frame), 0.0001)
    }

    @Test
    fun `TEST 4 - grid supplies the remaining demand once PV and battery discharge limit are both exhausted`() {
        // Same morning hour as TEST 3, but the battery is deliberately undersized on power
        // (1.0kW max discharge) and the load is pushed well past what PV + that capped battery
        // discharge can cover — SBU mode (the default) falls back to the grid for the remainder,
        // per §12's own "when battery reaches configured reserve: utility supplies load" priority
        // (here the binding constraint is the battery's power limit, not its SOC reserve, but the
        // fallback-to-grid behavior is the same).
        val cfg = config(batteryMaxDischargeKw = 1.0)
        val frame = singleFrame(cfg, hour = 8.0, startSocFraction = 0.6, applianceLoadKw = 8.0, gridConnected = true)

        assertEquals(4.244, frame.pvKw, 0.01)
        val demand = frame.houseLoadKw + frame.inverterSelfConsumptionKw
        assertEquals(0.97, frame.batteryToHouseKw, 0.01) // capped at 1.0kW rated x 0.97 inverter efficiency, per §9's discharge-limit requirement
        val expectedGridToHouse = demand - frame.pvKw - frame.batteryToHouseKw
        assertTrue("this scenario must actually need the grid to close the gap", expectedGridToHouse > 0.5)
        assertEquals(expectedGridToHouse, frame.gridToHouseKw, 0.01)
        assertEquals(0.0, frame.unmetLoadKw, 0.0001) // the grid connection's own service-amp ceiling isn't binding here
        assertEquals(0.0, SimulationEngine.energyImbalanceKw(frame), 0.0001)
    }

    @Test
    fun `TEST 5 - midnight does not create an artificial SOC jump when one day's ending SOC seeds the next`() {
        // §11's own "known bug": SOC=20% at midnight incorrectly becoming SOC=60%. The underlying
        // mechanism that prevents this (SimulationViewModel.advanceHour carrying the previous
        // day's real ending SOC into the next day's startSocFraction, rather than always reusing a
        // fixed default) already exists (A45) — this test locks in the engine-level guarantee that
        // mechanism depends on: building tomorrow's timeline from today's actual ending SOC must
        // reproduce that exact SOC at hour 0, not silently reset to some other value (e.g. the
        // unrelated 0.6 default startSocFraction every OTHER caller in this file uses).
        // pvCapacityKw=0 and gridConnectable=false: nothing but the battery can move SOC, so it
        // declines monotonically across the day (no daytime PV recharge to muddy the trace).
        // batteryCapacityKwh=50 gives enough headroom above the 20% reserve floor that a modest
        // 0.3kW appliance load doesn't hit the floor and pin there before the day ends either.
        val cfg = config(pvCapacityKw = 0.0, batteryCapacityKwh = 50.0, gridConnectable = false)
        val overnight = SimulationEngine.buildDayTimeline(
            config = cfg, gridConnected = false, startSocFraction = 0.6,
            applianceLoadKw = 0.3, resolutionMinutes = 5, durationHours = 24.0
        )
        val endingSocFraction = overnight.last().batterySocKwh / cfg.batteryCapacityKwh
        // Sanity: a full day of load with no PV/grid recharge must have actually drawn the battery
        // down from its 0.6 start, not left it unchanged (otherwise this test would prove nothing).
        assertTrue("expected the battery to have discharged over the day", endingSocFraction < 0.6)
        assertTrue("expected the battery to still be above its reserve floor (a clean decline, not floor-pinned)", endingSocFraction > SimulationEngine.BATTERY_MIN_SOC_FRACTION + 0.01)

        val nextDayFirstFrame = SimulationEngine.buildDayTimeline(
            config = cfg, gridConnected = false, startSocFraction = endingSocFraction,
            applianceLoadKw = 0.3, resolutionMinutes = 5, durationHours = 0.0
        ).first()

        assertEquals(
            "the next day's starting SOC must exactly match the previous day's real ending SOC - no reset to a fixed default",
            overnight.last().batterySocPercent, nextDayFirstFrame.batterySocPercent, 0.01f
        )
    }

    @Test
    fun `TEST 6 - a cloudier weather curve reduces PV and increases what the battery or grid must supply`() {
        // Compared over a FULL day (summed across every 5-minute frame), not one single instant —
        // WeatherCurve overlays randomly-placed transient cloud events on top of each scenario's
        // own baseline (see WeatherEngine.generate's own doc), so any one arbitrary hour could
        // land on/off an event by chance. RAINY's baseline is a large, deterministic reduction from
        // TYPICAL's regardless of event placement (JamaicaClimatology.ANNUAL_AVERAGE.cloudinessBaseline
        // = 0.28 -> baseline 1 - 0.28*0.85 = 0.762 for TYPICAL vs 1 - (0.28*2.2)*0.85 = 0.4764 for
        // RAINY, a ~37% cut before any events even apply) — summing across the whole day makes the
        // comparison robust to exactly where those random events happen to fall.
        val cfg = config()
        val clear = WeatherEngine.generate(WeatherScenario.TYPICAL, month = null)
        val rainy = WeatherEngine.generate(WeatherScenario.RAINY, month = null)

        val clearTimeline = SimulationEngine.buildDayTimeline(
            config = cfg, gridConnected = true, startSocFraction = 0.6, applianceLoadKw = 1.0,
            resolutionMinutes = 5, durationHours = 24.0, weatherCurve = clear
        )
        val rainyTimeline = SimulationEngine.buildDayTimeline(
            config = cfg, gridConnected = true, startSocFraction = 0.6, applianceLoadKw = 1.0,
            resolutionMinutes = 5, durationHours = 24.0, weatherCurve = rainy
        )

        // 2026-08-18 charging-physics fix: compare the harvestable ceiling (the weather-driven
        // generation potential), not harvested pvKw — otherwise a clear day whose battery tops off
        // and throttles the array back would understate its own generation and muddy the
        // weather comparison this test is actually about.
        val clearTotalPv = clearTimeline.sumOf { it.harvestablePvKw }
        val rainyTotalPv = rainyTimeline.sumOf { it.harvestablePvKw }
        assertTrue(
            "a RAINY scenario must allow meaningfully less total daily PV than TYPICAL",
            rainyTotalPv < clearTotalPv * 0.85
        )

        // Demand is identical in both runs (same config/appliance load) - only PV differs - so
        // whatever solar no longer covers must be picked up by the battery and/or grid instead of
        // simply vanishing (energy conservation, not a smaller total supplied).
        val clearNonSolar = clearTimeline.sumOf { it.batteryToHouseKw + it.gridToHouseKw }
        val rainyNonSolar = rainyTimeline.sumOf { it.batteryToHouseKw + it.gridToHouseKw }
        assertTrue(
            "the cloudier run's total deficit must be picked up by battery/grid across the day, not silently unmet or ignored",
            rainyNonSolar > clearNonSolar
        )

        clearTimeline.forEach { assertEquals(0.0, SimulationEngine.energyImbalanceKw(it), 0.0001) }
        rainyTimeline.forEach { assertEquals(0.0, SimulationEngine.energyImbalanceKw(it), 0.0001) }
    }

    @Test
    fun `inverter self-consumption is included in served demand exactly once, never double-counted`() {
        // A87 (spec Phase 24 §3 - "Do not double-count inverter consumption"): houseLoadKw stays
        // pure (excludes the inverter's own overhead), but the flows that serve it
        // (solarToHouseKw/batteryToHouseKw/gridToHouseKw/unmetLoadKw) must sum to houseLoadKw PLUS
        // inverterSelfConsumptionKw exactly once - not zero times (silently dropped) and not twice.
        val cfg = config(batteryMaxDischargeKw = 1.0)
        val frame = singleFrame(cfg, hour = 8.0, startSocFraction = 0.6, applianceLoadKw = 8.0, gridConnected = true)

        assertTrue("this fixture must have a nonzero inverter self-consumption to be a meaningful check", frame.inverterSelfConsumptionKw > 0.0)
        val servedTotal = frame.solarToHouseKw + frame.batteryToHouseKw + frame.gridToHouseKw + frame.unmetLoadKw
        assertEquals(frame.houseLoadKw + frame.inverterSelfConsumptionKw, servedTotal, 0.0001)
    }
}
