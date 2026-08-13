package com.lumix.estimator.domain

import kotlin.math.ceil
import kotlin.math.max

/**
 * A49: the equipment-selection logic that makes LOAD-BASED (and GUIDED, which shares it — see
 * [SystemCalculator]) an actual design engine rather than a figure that stops at "you need ~6kW."
 * Given a calculated engineering requirement, these functions search the real catalog this app
 * actually sells and prices ([Catalog]) and return the smallest/least-oversized configuration that
 * satisfies it, plus a plain-language reason a reviewer can read back.
 *
 * MANUAL mode never calls this object — an installer's explicit equipment choice in Manual mode is
 * used exactly as selected (see [SystemCalculator]'s manual branch and its `manualInverterWarning`/
 * `manualBatteryWarning` fields, which flag rather than override an undersized choice).
 */
object EquipmentSelectionEngine {

    data class PanelChoice(
        val panelWatts: Int,
        val panelCount: Int,
        val totalPvKw: Double,
        val reason: String
    )

    /**
     * Evaluates every panel wattage in [wattages] (not one fixed wattage) and returns whichever
     * configuration meets [requiredPvKw] with the least oversizing, preferring the fewest panels on
     * a tie. Candidates above [maxPvKw] (the inverter's own practical DC input ceiling) are excluded
     * unless every candidate would exceed it, in which case the least-oversized one is still
     * returned — sizing should never silently produce zero panels.
     */
    fun selectBestPanelConfiguration(
        requiredPvKw: Double,
        maxPvKw: Double,
        wattages: List<Int> = Catalog.panelWattages
    ): PanelChoice {
        if (requiredPvKw <= 0.0) {
            val w = wattages.first()
            return PanelChoice(w, 0, 0.0, "No PV capacity required.")
        }

        val candidates = wattages.map { w ->
            val count = SystemCalculator.enforceEvenPanels((requiredPvKw * 1000.0) / w)
            PanelChoice(w, count, count * w / 1000.0, "")
        }

        val withinCeiling = candidates.filter { it.totalPvKw <= maxPvKw + 0.05 }
        val pool = withinCeiling.ifEmpty { candidates }
        val best = pool.minWith(compareBy({ it.totalPvKw }, { it.panelCount }))

        val reason = "%.2f kW required — %d × %dW panels = %.2f kW covers it%s."
            .format(
                requiredPvKw, best.panelCount, best.panelWatts, best.totalPvKw,
                if (withinCeiling.isEmpty()) " (exceeds the inverter's typical DC input ceiling — review)" else ""
            )
        return best.copy(reason = reason)
    }

    data class InverterChoice(val option: InverterOption, val reason: String)

    /** Picks the smallest catalog inverter meeting [requiredKw], never the largest just because it's largest. */
    fun selectBestInverter(requiredKw: Double, pool: List<InverterOption>): InverterChoice {
        val chosen = pool.firstOrNull { it.kw >= requiredKw } ?: pool.maxBy { it.kw }
        val reason = if (chosen.kw >= requiredKw - 0.05) {
            "%.2f kW required — the %s (%.1f kW) is the smallest available unit that covers it."
                .format(requiredKw, chosen.name, chosen.kw)
        } else {
            "%.2f kW required — the largest available unit, the %s (%.1f kW), is still below the calculated requirement."
                .format(requiredKw, chosen.name, chosen.kw)
        }
        return InverterChoice(chosen, reason)
    }

    data class BatteryChoice(
        val option: BatteryOption?,
        val moduleCount: Int,
        val totalKwh: Double,
        val totalUsableKwh: Double,
        val reason: String
    )

    /** Real usable fraction from the matched datasheet ([EquipmentSpecs]) when known, else the flat design assumption. */
    private fun usableFractionFor(tierKwh: Double): Double {
        val label = when (tierKwh) {
            5.0 -> "5 kWh"
            10.0 -> "10 kWh"
            15.0 -> "15 kWh"
            else -> null
        }
        val spec = EquipmentSpecs.batterySpecFor(label)
        return spec?.let { it.usableEnergyKwh / it.ratedEnergyKwh } ?: SystemCalculator.BATTERY_DOD
    }

    /**
     * [requiredUsableKwh] is the actual energy the backup load needs to draw — not a nominal
     * capacity — so tiers are compared on real usable energy (per spec §14), not nominal kWh alone.
     */
    fun selectBestHybridBattery(requiredUsableKwh: Double): BatteryChoice {
        if (requiredUsableKwh <= 0.0) return BatteryChoice(null, 0, 0.0, 0.0, "No battery backup required.")

        val candidates = Catalog.hybridBatteries.map { tier ->
            val usablePerModule = tier.kwh * usableFractionFor(tier.kwh)
            val modules = max(1, ceil(requiredUsableKwh / usablePerModule).toInt())
            Triple(tier, modules, modules * usablePerModule)
        }
        val (tier, modules, usableTotal) = candidates.minWith(compareBy({ it.third }, { it.second }))

        val reason = "%.1f kWh usable backup required — %d × %s (%.1f kWh usable each) covers it."
            .format(requiredUsableKwh, modules, tier.name, tier.kwh * usableFractionFor(tier.kwh))
        return BatteryChoice(tier, modules, tier.kwh * modules, usableTotal, reason)
    }
}
