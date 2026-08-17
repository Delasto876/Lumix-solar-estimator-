package com.lumix.estimator.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A66 (spec Phase 27/56 — "there must never be two separate appliance lists... avoid duplicated
 * information"): the wizard's [ApplianceType] and the simulation's
 * [com.lumix.estimator.domain.simulation.SimApplianceType] are deliberately two different Kotlin
 * types (different roles — see [ApplianceType]'s own doc), bridged by
 * [SystemCalculator.simTypeFor]. That bridge lets the two declare their wattage independently,
 * which is exactly how they drifted apart undetected: a 2026-08-16 audit found FREEZER (200 vs.
 * the real 180), WASHER (600 vs. 500), and DRYER (a badly stale 1500 vs. the real 5000) disagreeing
 * with their own mapped [SimApplianceType] counterpart. The DRYER case was safety-relevant, not
 * cosmetic: [SystemCalculator.loadsKwhAndPeak] used [ApplianceType.watts] for PEAK load but
 * [SimApplianceType]'s watts (via `defaultDailyEnergyKwh`) for DAILY ENERGY on the exact same
 * selected appliance — so a selected dryer quietly undercounted `requiredInverterKw` by 3.5kW per
 * unit relative to what its own energy figure already assumed.
 *
 * This test is the guardrail: every mapped pair's wattage must match, so this class of drift fails
 * a build immediately instead of silently shipping.
 */
class ApplianceTypeConsistencyTest {

    @Test
    fun `every wizard appliance type's wattage matches its mapped simulation appliance type`() {
        val mismatches = ApplianceType.entries.mapNotNull { type ->
            val simType = SystemCalculator.simTypeFor(type)
            if (type.watts != simType.watts) {
                "ApplianceType.${type.name}=${type.watts}W vs SimApplianceType.${simType.name}=${simType.watts}W"
            } else null
        }
        assertEquals(
            "found appliance wattage drift between the wizard and simulation catalogs:\n${mismatches.joinToString("\n")}",
            emptyList<String>(),
            mismatches
        )
    }
}
