package com.lumix.estimator.domain.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A73 (spec Phase 8 — "connect PV+battery+inverter+load into one deterministic model," installer's
 * explicit decision on the open question raised while auditing that integration): battery-sourced
 * house power now pays the same inverter DC->AC conversion loss ([SystemLosses.INVERTER_EFFICIENCY],
 * 0.97) the PV->house path already paid — both are the same physical inverter conversion stage in a
 * real hybrid inverter. Before this round, `batteryToHouseKw` was subtracted from the battery's SOC
 * 1:1, as if battery-sourced house power reached the house at 100% efficiency while solar-sourced
 * house power paid ~3% to get there through the identical hardware.
 */
class SimulationEngineBatteryDischargeEfficiencyTest {

    /** No PV at all (isolates the discharge path from any charge/curtailment interaction) — plenty of battery power/capacity so the load is served in full. */
    private fun noPvConfig(startSocFraction: Double = 0.6) = SimSystemConfig(
        pvCapacityKw = 0.0, panelCount = 0, panelWatts = 0,
        inverterKw = 10.0, inverterName = "Test Inverter",
        batteryCapacityKwh = 10.0, batteryName = "Test Battery",
        hasBattery = true, gridConnectable = false,
        avgDailyLoadKwh = 24.0, peakLoadKw = 5.0,
        batteryMaxChargeKw = 5.0, batteryMaxDischargeKw = 5.0,
        batteryChargeEfficiency = 0.95,
        batteryDepthOfDischargeFraction = SimulationEngine.BATTERY_MIN_SOC_FRACTION
    )

    @Test
    fun `battery discharge draws more real DC energy from SOC than the AC power it delivers to the house`() {
        // Midnight (no PV either way), avgDailyLoadKwh=24 -> background load floor doesn't bind
        // (24/24 x 0.4 = 0.4kW already above the 0.15kW floor); loadFactor(0) = 0.42 / (weekday
        // shape's own mean, 0.580417) = 0.723618 -> load = 0.723618 x 0.4 = 0.289447kW. Battery has
        // ample power/capacity, so the full demand is served from the battery. A87: "demand" here
        // is houseLoadKw PLUS the inverter's own self-consumption (config.inverterKw x 0.005,
        // floored at 0.02 -> 10.0 x 0.005 = 0.05kW here, above the floor) — batteryToHouseKw now
        // covers both, not houseLoadKw alone (see SimulationEngine.buildDayTimeline's own comment).
        val config = noPvConfig(startSocFraction = 0.6)
        val frame = SimulationEngine.buildDayTimeline(
            config = config, gridConnected = false, startSocFraction = 0.6,
            startHour = 0.0, durationHours = 0.0, resolutionMinutes = 5
        ).first()

        assertEquals(0.289447, frame.houseLoadKw, 0.001)
        assertEquals(0.05, frame.inverterSelfConsumptionKw, 0.0001)
        assertEquals(
            "expected the full demand (house load + inverter self-consumption) to be served from the battery (AC-side, delivered to the house)",
            frame.houseLoadKw + frame.inverterSelfConsumptionKw, frame.batteryToHouseKw, 0.0001
        )

        // A73: the DC energy actually drawn from SOC is batteryToHouseKw / 0.97 (more than what was
        // delivered), over a 5-minute (1/12 hour) step, starting from 60% of a 10kWh pack (6.0kWh).
        val dtHours = 5.0 / 60.0
        val dcDrawnKw = frame.batteryToHouseKw / SystemLosses.INVERTER_EFFICIENCY
        val expectedSocKwh = (10.0 * 0.6) - dcDrawnKw * dtHours
        val expectedSocPercent = (expectedSocKwh / 10.0 * 100.0).toFloat()
        assertEquals(59.70838f, expectedSocPercent, 0.001f) // hand-traced (via Python, A87-updated), matches the assertion below
        assertEquals(expectedSocPercent, frame.batterySocPercent, 0.001f)

        // Regression guard against the OLD (pre-A73) 100%-efficient-discharge behavior, which would
        // have deducted batteryToHouseKw directly (a smaller, wrong SOC drop).
        val oldWrongSocKwh = (10.0 * 0.6) - frame.batteryToHouseKw * dtHours
        val oldWrongSocPercent = (oldWrongSocKwh / 10.0 * 100.0).toFloat()
        assertEquals(59.71713f, oldWrongSocPercent, 0.001f)
        assertTrue(
            "the real (efficiency-aware) SOC must be lower than the old 100%-efficient figure would have been",
            frame.batterySocPercent < oldWrongSocPercent
        )
    }

    @Test
    fun `the energy-balance invariant still holds exactly - batteryToHouseKw stays the AC-facing figure`() {
        // A47's own energyImbalanceKw check compares batteryToHouseKw (not the new DC-side draw)
        // against houseLoadKw — A73 deliberately keeps batteryToHouseKw as the AC-delivered figure,
        // only changing how much SOC that costs, so this invariant must still read exactly zero.
        val config = noPvConfig(startSocFraction = 0.6)
        val frame = SimulationEngine.buildDayTimeline(
            config = config, gridConnected = false, startSocFraction = 0.6,
            startHour = 0.0, durationHours = 0.0, resolutionMinutes = 5
        ).first()
        assertEquals(0.0, SimulationEngine.energyImbalanceKw(frame), 0.0001)
    }
}
