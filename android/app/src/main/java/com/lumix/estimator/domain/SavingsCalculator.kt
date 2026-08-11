package com.lumix.estimator.domain

import kotlinx.serialization.Serializable
import kotlin.math.min
import kotlin.math.pow

@Serializable
data class SavingsYearPoint(
    val year: Int,
    val costWithoutSolar: Double,
    val costWithSolar: Double,
    val yearlySavings: Double,
    val cumulativeSavings: Double
)

@Serializable
data class SavingsProjection(
    val monthlyProductionKwh: Double,
    val coveragePercent: Float,
    val baselineMonthlyBill: Double,
    val monthlySavings: Double,
    val paybackYears: Double?,
    val yearly: List<SavingsYearPoint>
)

object SavingsCalculator {
    // Assumptions behind the 20-year projection: JPS-style bills climb ~6%/year, and
    // panel output declines ~0.5%/year — both are estimates, not measured figures.
    const val BILL_ESCALATION_RATE = 0.06
    const val PANEL_DEGRADATION_RATE = 0.005
    const val PROJECTION_YEARS = 20

    fun project(inputs: QuoteInputs, result: QuoteResult): SavingsProjection {
        val monthlyProductionKwh = result.pvKw * SystemCalculator.PSH * 30.0
        val requiredMonthlyKwh = result.designDailyKwh * 30.0

        val coveragePercent = if (requiredMonthlyKwh <= 0.01) {
            0f
        } else {
            (monthlyProductionKwh / requiredMonthlyKwh * 100.0).coerceIn(0.0, 100.0).toFloat()
        }

        val baselineMonthlyBill = if (
            inputs.quoteMode == QuoteMode.GUIDED && inputs.usageMode == UsageMode.BILL && inputs.avgBill > 0
        ) {
            inputs.avgBill
        } else {
            requiredMonthlyKwh * SystemCalculator.BLENDED_TARIFF
        }

        val coveredFraction = if (requiredMonthlyKwh <= 0.01) {
            0.0
        } else {
            (min(monthlyProductionKwh, requiredMonthlyKwh) / requiredMonthlyKwh).coerceIn(0.0, 1.0)
        }

        val monthlySavings = baselineMonthlyBill * coveredFraction
        val annualSavingsYear1 = monthlySavings * 12.0
        val paybackYears = if (annualSavingsYear1 <= 0.0) null else result.grandTotal / annualSavingsYear1

        val yearly = mutableListOf<SavingsYearPoint>()
        var cumulative = 0.0
        for (year in 1..PROJECTION_YEARS) {
            val escalation = (1.0 + BILL_ESCALATION_RATE).pow(year - 1)
            val degradation = (1.0 - PANEL_DEGRADATION_RATE).pow(year - 1)
            val costWithoutSolar = baselineMonthlyBill * 12.0 * escalation
            val yearlySavings = (annualSavingsYear1 * escalation * degradation).coerceAtMost(costWithoutSolar)
            val costWithSolar = costWithoutSolar - yearlySavings
            cumulative += yearlySavings
            yearly += SavingsYearPoint(
                year = year,
                costWithoutSolar = costWithoutSolar,
                costWithSolar = costWithSolar,
                yearlySavings = yearlySavings,
                cumulativeSavings = cumulative
            )
        }

        return SavingsProjection(
            monthlyProductionKwh = monthlyProductionKwh,
            coveragePercent = coveragePercent,
            baselineMonthlyBill = baselineMonthlyBill,
            monthlySavings = monthlySavings,
            paybackYears = paybackYears,
            yearly = yearly
        )
    }
}
