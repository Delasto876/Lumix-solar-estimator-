package com.lumix.estimator.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 28 ("update the load/appliance engine... make appliance usage schedules more realistic...
 * make the system automatically adapt to RESIDENTIAL, COMMERCIAL, or INDUSTRIAL"): regression tests
 * for the residential-side calculation changes — AC BTU/W ratios + inverter part-load averaging,
 * the weekly (Mon-Sun) kWh aggregation replacing the old Weekday-only figure, and the schedule-
 * based coincident peak replacing the old flat nameplate-sum peak. `SystemCalculator.loadsKwhAndPeak`
 * is `internal` specifically so these tests can exercise the weekly/peak breakdown directly, without
 * needing a full MANUAL-mode inverter/battery/pricing chain just to reach it.
 */
class Phase28ResidentialLoadEngineTest {

    private val noAppliances = ApplianceType.entries.associateWith { ApplianceLoad(qty = 0) }

    private fun inputs(
        ac: AcLoad = AcLoad(),
        appliances: Map<ApplianceType, ApplianceLoad> = noAppliances
    ) = QuoteInputs(ac = ac, appliances = appliances)

    @Test
    fun `non-inverter AC uses the unchanged 10 BTU per watt ratio for both peak and energy`() {
        val result = SystemCalculator.loadsKwhAndPeak(
            inputs(ac = AcLoad(hasAc = true, counts = mapOf(9000 to 1), useStandardHours = false, customHours = 5.0, acType = AcInverterType.NON_INVERTER))
        )
        // 9000 BTU / 10 BTU per W = 900W. Custom hours bypasses the day-type-varying automatic
        // schedule, so every day type reads the same flat figure.
        assertEquals(900.0, result.peakWatts, 0.01)
        assertEquals(4.5, result.weekdayDailyKwh, 0.001) // 900W x 5h / 1000
        assertEquals(4.5, result.saturdayDailyKwh, 0.001)
        assertEquals(4.5, result.averageDailyKwh, 0.001)
    }

    @Test
    fun `inverter AC uses a higher BTU per watt ratio for peak but a reduced part-load average for energy`() {
        val result = SystemCalculator.loadsKwhAndPeak(
            inputs(ac = AcLoad(hasAc = true, counts = mapOf(9000 to 1), useStandardHours = false, customHours = 5.0, acType = AcInverterType.INVERTER))
        )
        val expectedPeakW = 9000.0 / 13.0
        val expectedDailyKwh = expectedPeakW * 0.55 * 5.0 / 1000.0
        // Peak stays at the full rated input (no part-load reduction) — only the energy figure
        // is reduced, per "keep peak demand separate from average energy consumption."
        assertEquals(expectedPeakW, result.peakWatts, 0.01)
        assertEquals(expectedDailyKwh, result.weekdayDailyKwh, 0.001)
    }

    @Test
    fun `a weekend-only appliance contributes zero on weekdays but a real share of the weekly average`() {
        val result = SystemCalculator.loadsKwhAndPeak(
            inputs(appliances = noAppliances + (ApplianceType.BLENDER to ApplianceLoad(qty = 1)))
        )
        // Blender's default schedule is now Saturday/Sunday only (Phase 28), 6 minutes/day. Load-Sheet
        // round: blender is now 1000W nameplate x 0.6 duty factor (a real sourced average/peak split,
        // was a flat 400W x 1.0): 1000W x 0.6 x 0.1h / 1000 = 0.06 kWh each weekend day, zero on a weekday.
        assertEquals(0.0, result.weekdayDailyKwh, 0.0001)
        assertEquals(0.06, result.saturdayDailyKwh, 0.0001)
        assertEquals(0.06, result.sundayDailyKwh, 0.0001)
        assertEquals(0.12, result.weeklyKwh, 0.0001)
        assertEquals(0.12 / 7.0, result.averageDailyKwh, 0.0001)
    }

    @Test
    fun `coincident peak is lower than the flat nameplate sum when two appliances never overlap`() {
        val result = SystemCalculator.loadsKwhAndPeak(
            inputs(appliances = noAppliances + mapOf(ApplianceType.IRON to ApplianceLoad(qty = 1), ApplianceType.TV to ApplianceLoad(qty = 1)))
        )
        // Load-Sheet round: iron is now 1800W nameplate (was 1200W), TV 200W (was 80W). Flat
        // nameplate sum would have been 1800W (iron) + 200W (TV) = 2000W. Iron only runs
        // 7:00-7:15am; TV only from 6:30pm (weekday) or 10am (weekend) onward — the two never
        // coincide, so the real coincident peak is iron's own 1800W alone, not their sum.
        assertEquals(1800.0, result.peakWatts, 0.01)
    }

    @Test
    fun `coincident peak includes a genuinely simultaneous background load plus an active event`() {
        val result = SystemCalculator.loadsKwhAndPeak(
            inputs(appliances = noAppliances + mapOf(ApplianceType.FRIDGE to ApplianceLoad(qty = 1), ApplianceType.IRON to ApplianceLoad(qty = 1)))
        )
        // Load-Sheet round: fridge is now 300W nameplate (was 150W), iron 1800W (was 1200W).
        // Fridge is scheduled 24 hours, every day; iron is active 7:00-7:15am, every day —
        // during that window both are genuinely coincident, so the real peak DOES include their
        // sum (300 + 1800 = 2100W), not a diversity-discounted lower figure.
        assertEquals(2100.0, result.peakWatts, 0.01)
    }
}
