package com.lumix.estimator.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A72 (spec Phase 7 — "fix battery calculations"): regression tests for
 * [SystemCalculator.resolvedBatteryPowerKw]'s real DC battery-port ceiling — the same
 * AC-rating-as-DC-proxy bug A69 (PV input) and A71 (MPPT) already found and fixed for the other
 * two DC-side ports on this hardware, now fixed for the battery port too.
 */
class SystemCalculatorBatteryPowerCeilingTest {

    @Test
    fun `LuxPower 13K's real 10kW battery-port datasheet figure binds lower than its 13kW AC rating`() {
        // SRNE SR-EOS15B (Catalog's "15 kWh" tier): maxChargeA=200, maxDischargeA=200, voltageV=51.2
        // -> raw per-module figures of 200 x 51.2 / 1000 = 10.24kW each, uncapped. LuxPower
        // LXP-LB-US 12K/13K's (A89/Ph21: renamed from GEN-LB-US 13K, same carried-over datasheet
        // figures) own confirmed maxChargePowerKw/maxDischargePowerKw is 10.0kW - LOWER than both
        // the raw 10.24kW battery figure AND its own 13kW AC rating. Before A72, only the 13kW AC
        // rating was checked, so this scenario would have (wrongly) returned 10.24kW - a real
        // battery figure, but not run through the real inverter-side ceiling that actually binds
        // tighter here.
        val fifteenKwhTier = Catalog.hybridBatteries.first { it.name.contains("15 kWh") }
        val (chargeKw, dischargeKw) = SystemCalculator.resolvedBatteryPowerKw(
            chosenBattery = fifteenKwhTier,
            totalBatteryKwh = fifteenKwhTier.kwh,
            inverterKw = 13.0,
            inverterName = "LuxPower LXP-LB-US 12K/13K"
        )
        assertEquals(10.0, chargeKw!!, 0.01)
        assertEquals(10.0, dischargeKw!!, 0.01)
    }

    @Test
    fun `an unmatched inverter falls back to the AC rating alone, exactly as before A72`() {
        // 6.3kW matches no ratedOutputW in the catalog at all (ratings are 6/8/10/12/13kW), so
        // EquipmentSpecs.inverterSpecFor returns null regardless of the name hint -> both new
        // battery-port ceilings resolve to Double#MAX_VALUE (no additional constraint), so only
        // inverterKw binds - a direct regression guard that nothing changed for the unmatched case.
        val tenKwhTier = Catalog.hybridBatteries.first { it.name.contains("10 kWh") }
        val (chargeKw, dischargeKw) = SystemCalculator.resolvedBatteryPowerKw(
            chosenBattery = tenKwhTier,
            totalBatteryKwh = tenKwhTier.kwh,
            inverterKw = 6.3,
            inverterName = "Definitely Not A Real Inverter Model"
        )
        // Raw per-module: 150 x 51.2 / 1000 = 7.68kW charge, 200 x 51.2 / 1000 = 10.24kW discharge
        // - both above the 6.3kW AC rating, which is the only ceiling in play here.
        assertEquals(6.3, chargeKw!!, 0.01)
        assertEquals(6.3, dischargeKw!!, 0.01)
    }
}
