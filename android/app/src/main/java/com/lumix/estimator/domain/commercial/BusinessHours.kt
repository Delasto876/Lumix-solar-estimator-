package com.lumix.estimator.domain.commercial

import com.lumix.estimator.domain.simulation.DayType
import kotlinx.serialization.Serializable

/**
 * Phase 28 (Commercial default — "Opening approximately 7:00-8:00 AM. Main operating period
 * approximately 8:00 AM-5:00/6:00 PM. Monday-Friday as primary business days. Saturday can be
 * partial/open. Sunday can be closed. User must be able to edit business opening/closing days and
 * hours."): one open/close window per [DayType] — Monday-Friday share the [DayType.WEEKDAY] bucket
 * (the same simplification Phase 28's residential weekly-kWh aggregation already uses), Saturday
 * and Sunday each editable independently. A null open/close pair means "closed" that day type.
 */
@Serializable
data class BusinessHours(
    val weekdayOpenHour: Double? = 7.0,
    val weekdayCloseHour: Double? = 18.0,
    val saturdayOpenHour: Double? = 8.0,
    val saturdayCloseHour: Double? = 13.0,
    val sundayOpenHour: Double? = null,
    val sundayCloseHour: Double? = null
) {
    private fun openHour(dayType: DayType): Double? = when (dayType) {
        DayType.WEEKDAY -> weekdayOpenHour
        DayType.SATURDAY -> saturdayOpenHour
        DayType.SUNDAY -> sundayOpenHour
    }

    private fun closeHour(dayType: DayType): Double? = when (dayType) {
        DayType.WEEKDAY -> weekdayCloseHour
        DayType.SATURDAY -> saturdayCloseHour
        DayType.SUNDAY -> sundayCloseHour
    }

    fun isOpen(dayType: DayType): Boolean = openHour(dayType) != null && closeHour(dayType) != null

    /** Hours open on [dayType], or 0.0 if closed that day (never negative — a close hour before open is treated as closed rather than wrapping past midnight, since a real business's operating window doesn't cross midnight the way a residential appliance run can). */
    fun hoursOpen(dayType: DayType): Double {
        val open = openHour(dayType) ?: return 0.0
        val close = closeHour(dayType) ?: return 0.0
        return (close - open).coerceAtLeast(0.0)
    }

    fun isOpenAt(hour: Double, dayType: DayType): Boolean {
        val open = openHour(dayType) ?: return false
        val close = closeHour(dayType) ?: return false
        val h = hour.mod(24.0)
        return h >= open && h < close
    }
}
