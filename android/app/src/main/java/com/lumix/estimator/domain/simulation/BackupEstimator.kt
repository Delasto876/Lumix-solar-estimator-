package com.lumix.estimator.domain.simulation

import com.lumix.estimator.domain.BackupCoverage
import com.lumix.estimator.domain.QuoteInputs

/**
 * A54: the ONE backup-runtime calculation, shared by the Sizing recommendation, the Quote/PDF,
 * and (implicitly, by construction) the Simulation screen — replacing a closed-form
 * `targetHours × (selectedBatteryKwh / requiredBatteryKwh)` ratio that two different screens
 * (`StepSystemReview.kt`, `ResultsScreen.kt`) each computed independently, neither of which ever
 * touched the actual load profile. Reported bug (2026-08-14, "CORRECT SOLAR SIZING..."): a
 * LOAD-BASED system showed "Estimated backup: 11.9 hours" while its own simulation showed the
 * battery carrying the house from ~5:30pm to only ~8:30pm before falling back to utility — the
 * ratio formula and the simulation had no relationship to each other.
 *
 * This runs the real [SimulationEngine.buildDayTimeline] — the exact engine the Simulation screen
 * itself uses, with the exact same appliance schedule ([defaultApplianceStates], built from the
 * same [QuoteInputs] the quote was calculated from) — as an actual outage: grid disconnected,
 * starting at dusk (the conventional worst case a backup rating is judged against — solar can't
 * help again until the following sunrise), battery starting full. It then reports the real elapsed
 * time until the selected coverage's load first goes unmet, not an assumed constant draw.
 */
object BackupEstimator {

    data class BackupEstimate(
        val hours: Double,
        /** True if the selected load stayed fully covered for the entire search window (a well-sized system) rather than hitting a real shortfall. */
        val sufficientForFullWindow: Boolean,
        val reason: String
    )

    /** How far forward to search before calling a system "sufficient" — long enough to cross one full night-to-morning-recharge cycle plus margin. */
    private const val MAX_HOURS_SEARCHED = 72.0
    private const val UNMET_EPSILON = 0.01

    /**
     * The same Critical Loads/Most Load/Custom fraction [com.lumix.estimator.domain.SystemCalculator]
     * already applies to `criticalDailyKwh` when sizing the battery — pulled out here so the
     * simulation-driven estimate and the sizing engine can never silently diverge on what
     * "Critical Loads" means.
     */
    fun coverageFraction(coverage: BackupCoverage, customFraction: Double): Double = when (coverage) {
        BackupCoverage.ESSENTIALS, BackupCoverage.CRITICAL_LOADS -> 0.6
        BackupCoverage.FULL, BackupCoverage.MOST_LOAD -> 1.0
        BackupCoverage.CUSTOM -> customFraction.coerceIn(0.0, 1.0)
    }

    fun estimate(
        config: SimSystemConfig,
        inputs: QuoteInputs,
        dayType: DayType = DayType.WEEKDAY
    ): BackupEstimate {
        if (!config.hasBattery || config.batteryCapacityKwh <= 0.0) {
            return BackupEstimate(0.0, false, "No battery in this system — nothing to estimate backup runtime from.")
        }

        val fraction = coverageFraction(inputs.backupCoverage, inputs.customBackupCoverageFraction)
        val appliances = defaultApplianceStates(inputs)
        val timeline = SimulationEngine.buildDayTimeline(
            config = config,
            gridConnected = false,
            startSocFraction = 1.0,
            applianceStates = appliances,
            dayType = dayType,
            startHour = SimulationEngine.SUNSET_HOUR,
            durationHours = MAX_HOURS_SEARCHED,
            loadMultiplier = fraction,
            resolutionMinutes = 5
        )

        val firstShortfallIndex = timeline.indexOfFirst { it.unmetLoadKw > UNMET_EPSILON }
        if (firstShortfallIndex < 0) {
            return BackupEstimate(
                hours = MAX_HOURS_SEARCHED,
                sufficientForFullWindow = true,
                reason = "Solar and battery covered the selected load through the full %.0f-hour outage window tested — this system can likely sustain longer outages too."
                    .format(MAX_HOURS_SEARCHED)
            )
        }

        val shortfallFrame = timeline[firstShortfallIndex]
        val elapsedHours = (shortfallFrame.hour - SimulationEngine.SUNSET_HOUR).coerceAtLeast(0.0)
        val socBeforeShortfall = if (firstShortfallIndex > 0) timeline[firstShortfallIndex - 1].batterySocPercent else shortfallFrame.batterySocPercent
        val reserveFloorPercent = SimulationEngine.BATTERY_MIN_SOC_FRACTION * 100.0
        val reason = if (socBeforeShortfall <= reserveFloorPercent + 1.5f) {
            "Battery reached its %.0f%% reserve floor before solar could recharge it — based on the simulated load profile and coverage setting."
                .format(reserveFloorPercent)
        } else {
            "Load exceeded the battery's available discharge power before its reserve floor was reached."
        }

        return BackupEstimate(hours = elapsedHours, sufficientForFullWindow = false, reason = reason)
    }
}
