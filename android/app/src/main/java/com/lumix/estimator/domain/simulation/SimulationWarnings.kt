package com.lumix.estimator.domain.simulation

/** How urgently a [SimWarning] should read — drives its color, not its presence/absence logic. */
enum class WarningLevel { CAUTION, ALERT }

data class SimWarning(val label: String, val level: WarningLevel)

/**
 * A59 (spec §33–34 — "Inverter: 80% HIGH LOAD, 90% NEAR LIMIT, 100% LIMIT REACHED. Battery: 25%
 * LOW BATTERY, 20% RESERVE. PV: Morning/Evening LOW PV OUTPUT, Night PV OFF — NO SUN"): threshold
 * warnings for the live simulation status — always visible in Basic mode (not gated behind the
 * Technical toggle), since these are meant to read as operational alerts at a glance, the same way
 * a real hybrid inverter's own front panel would flash a warning rather than requiring a
 * diagnostics menu. Every input here ([SimFrame.inverterLoadKw], [SimFrame.batterySocPercent],
 * [SimFrame.potentialPvKw], [SimulationEngine.irradianceFactor]) already exists — this only adds
 * the threshold labels, not a new physics model.
 */
object SimulationWarnings {

    /** Below this fraction of STC-equivalent irradiance, PV is meaningfully into its shoulder — the same "ramping up/down" period a morning or evening naturally is, not full production yet. */
    private const val LOW_PV_IRRADIANCE_THRESHOLD = 0.35

    private const val INVERTER_HIGH_LOAD_FRACTION = 0.80
    private const val INVERTER_NEAR_LIMIT_FRACTION = 0.90
    private const val INVERTER_AT_LIMIT_FRACTION = 1.00

    /** Matches the spec's own 25% figure — a caution band above the real reserve floor ([SimulationEngine.BATTERY_MIN_SOC_FRACTION], 20%), not the same threshold restated. */
    private const val BATTERY_LOW_PERCENT = 25.0

    fun warningsFor(frame: SimFrame, config: SimSystemConfig): List<SimWarning> {
        val warnings = mutableListOf<SimWarning>()

        if (config.inverterKw > 0.0) {
            val loadFraction = frame.inverterLoadKw / config.inverterKw
            when {
                loadFraction >= INVERTER_AT_LIMIT_FRACTION -> warnings += SimWarning("INVERTER LIMIT REACHED", WarningLevel.ALERT)
                loadFraction >= INVERTER_NEAR_LIMIT_FRACTION -> warnings += SimWarning("INVERTER NEAR LIMIT", WarningLevel.ALERT)
                loadFraction >= INVERTER_HIGH_LOAD_FRACTION -> warnings += SimWarning("INVERTER HIGH LOAD", WarningLevel.CAUTION)
            }
        }

        if (config.hasBattery) {
            val reserveFloorPercent = config.batteryDepthOfDischargeFraction * 100.0
            when {
                frame.batterySocPercent <= reserveFloorPercent -> warnings += SimWarning("BATTERY AT RESERVE", WarningLevel.ALERT)
                frame.batterySocPercent <= BATTERY_LOW_PERCENT -> warnings += SimWarning("LOW BATTERY", WarningLevel.CAUTION)
            }
        }

        pvStatusWarning(frame)?.let { warnings += it }

        return warnings
    }

    private fun pvStatusWarning(frame: SimFrame): SimWarning? {
        if (frame.potentialPvKw <= 0.01) return SimWarning("PV OFF — NO SUN", WarningLevel.CAUTION)
        val irradiance = SimulationEngine.irradianceFactor(frame.hour)
        return if (irradiance in 0.0..LOW_PV_IRRADIANCE_THRESHOLD) SimWarning("LOW PV OUTPUT", WarningLevel.CAUTION) else null
    }
}
