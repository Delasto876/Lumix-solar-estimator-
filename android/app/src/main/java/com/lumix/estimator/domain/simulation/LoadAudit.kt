package com.lumix.estimator.domain.simulation

/**
 * The headline load-audit figures a real energy audit reports (spec §44): total daily energy,
 * the average and peak draw, the evening peak specifically (Jamaican residential demand is
 * structurally evening-heavy — see [SimulationEngine]'s own load shape), a base/overnight
 * floor, and the daytime/night averages that show how much the load shape actually swings.
 * Computed once from the already-built [SimFrame] timeline — the exact same data the digital
 * twin and the 24h graph show, so this can never disagree with either.
 */
data class LoadAuditSummary(
    val dailyEnergyKwh: Double,
    val averageLoadKw: Double,
    val peakLoadKw: Double,
    val peakLoadHour: Double,
    val eveningPeakKw: Double,
    val baseLoadKw: Double,
    val daytimeAvgLoadKw: Double,
    val nightAvgLoadKw: Double
)

object LoadAudit {
    private const val EVENING_START_HOUR = 17.0
    private const val EVENING_END_HOUR = 22.0
    private const val DAYTIME_START_HOUR = 8.0
    private const val DAYTIME_END_HOUR = 17.0
    private const val NIGHT_START_HOUR = 22.0
    private const val NIGHT_END_HOUR = 6.0

    fun compute(timeline: List<SimFrame>): LoadAuditSummary {
        if (timeline.isEmpty()) {
            return LoadAuditSummary(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }
        val dt = if (timeline.size > 1) timeline[1].hour - timeline[0].hour else 5.0 / 60.0
        val dailyEnergyKwh = timeline.sumOf { it.houseLoadKw * dt }
        val averageLoadKw = dailyEnergyKwh / 24.0

        val peakFrame = timeline.maxBy { it.houseLoadKw }
        val eveningFrames = timeline.filter { it.hour in EVENING_START_HOUR..EVENING_END_HOUR }
        val eveningPeakKw = eveningFrames.maxOfOrNull { it.houseLoadKw } ?: 0.0
        val baseLoadKw = timeline.minOf { it.houseLoadKw }

        val daytimeFrames = timeline.filter { it.hour in DAYTIME_START_HOUR..DAYTIME_END_HOUR }
        val daytimeAvgLoadKw = daytimeFrames.map { it.houseLoadKw }.average()
        val nightFrames = timeline.filter { it.hour >= NIGHT_START_HOUR || it.hour < NIGHT_END_HOUR }
        val nightAvgLoadKw = nightFrames.map { it.houseLoadKw }.average()

        return LoadAuditSummary(
            dailyEnergyKwh = dailyEnergyKwh,
            averageLoadKw = averageLoadKw,
            peakLoadKw = peakFrame.houseLoadKw,
            peakLoadHour = peakFrame.hour,
            eveningPeakKw = eveningPeakKw,
            baseLoadKw = baseLoadKw,
            daytimeAvgLoadKw = daytimeAvgLoadKw,
            nightAvgLoadKw = nightAvgLoadKw
        )
    }
}
