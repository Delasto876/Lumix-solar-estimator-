package com.lumix.estimator.ui.simulation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumix.estimator.domain.simulation.ApplianceRun
import com.lumix.estimator.domain.simulation.ApplianceState
import com.lumix.estimator.domain.simulation.DayType
import com.lumix.estimator.domain.simulation.SimApplianceType
import com.lumix.estimator.domain.simulation.totalApplianceLoadKwAt
import com.lumix.estimator.ui.components.AnimatedCounterText
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixRadius
import com.lumix.estimator.ui.theme.numberDisplayStyle
import kotlin.math.roundToInt

/**
 * A fixed, simplified time-of-day window for the appliance picker's 3-chip schedule control.
 * [spanHours] is each period's own natural length, used to clamp a shared "hours" value so it
 * can never claim more of a period than actually exists in it.
 */
enum class DayPeriod(val label: String, val startHour: Double, val spanHours: Double) {
    MORNING("Morning", 6.0, 6.0),   // 6am - 12pm
    NOON("Noon", 12.0, 5.0),        // 12pm - 5pm
    NIGHT("Night", 17.0, 13.0)      // 5pm - 6am (wraps past midnight)
}

/** The longest period span — also doubles as "the whole period" when hours is left at its max. */
val MAX_DAY_PERIOD_SPAN = DayPeriod.entries.maxOf { it.spanHours }

/** Shortest a run can be trimmed to — 5 minutes, for genuinely brief appliances (microwave, iron, pump). */
const val MIN_RUN_HOURS = 5.0 / 60.0

/**
 * Turns a picker's quantity/hours-per-period/selected-periods into the [ApplianceRun]s the
 * simulation engine actually consumes, and whether the appliance counts as on at all. No
 * periods selected means off, matching the picker's "none = off" behavior directly — there's
 * no separate switch to fall out of sync with it.
 */
fun buildApplianceSchedule(quantity: Int, hoursPerPeriod: Double, periods: Set<DayPeriod>): Pair<Boolean, List<ApplianceRun>> {
    if (periods.isEmpty()) return false to listOf(ApplianceRun(quantity = quantity))
    val runs = periods.sortedBy { it.startHour }.map { period ->
        ApplianceRun(
            quantity = quantity,
            startHour = period.startHour,
            durationHours = hoursPerPeriod.coerceIn(MIN_RUN_HOURS, period.spanHours)
        )
    }
    return true to runs
}

/**
 * Best-effort read of an appliance's current runs back into the picker's quantity/hours/periods
 * shape. The only two shapes runs ever actually take are [defaultScheduleFor]'s realistic
 * starting schedule (nothing customized yet) or "whatever this same picker wrote last" — both
 * are handled exactly. When off, the quantity/hours still seed from the first default run (not
 * a generic 1-unit/max-span guess), so turning an off appliance on via a period chip starts from
 * a sensible duration instead of an arbitrary all-day block.
 */
private fun derivePickerState(state: ApplianceState): Triple<Int, Double, Set<DayPeriod>> {
    if (!state.enabled) {
        val seed = state.runs.firstOrNull()
        val quantity = seed?.quantity?.coerceAtLeast(1) ?: 1
        val hours = seed?.durationHours?.coerceIn(MIN_RUN_HOURS, MAX_DAY_PERIOD_SPAN) ?: MAX_DAY_PERIOD_SPAN
        return Triple(quantity, hours, emptySet())
    }
    val matched = state.runs.mapNotNull { run -> DayPeriod.entries.find { it.startHour == run.startHour }?.let { it to run } }
    if (matched.size == state.runs.size && matched.isNotEmpty()) {
        val quantity = matched.first().second.quantity
        val hours = matched.first().second.durationHours
        return Triple(quantity, hours, matched.map { it.first }.toSet())
    }
    return Triple(state.totalQuantity.coerceAtLeast(1), MAX_DAY_PERIOD_SPAN, DayPeriod.entries.toSet())
}

/** 5-minute steps below an hour (for short "event" appliances), 30-minute steps at/above it. */
private fun stepHours(current: Double, increase: Boolean): Double {
    val step = if (current < 1.0) MIN_RUN_HOURS else 0.5
    val next = if (increase) current + step else current - step
    return next.coerceIn(MIN_RUN_HOURS, MAX_DAY_PERIOD_SPAN)
}

/** Minutes below an hour ("10min"), hours (with leftover minutes) at/above it ("1h", "1h 30m"). */
private fun formatRunDuration(hours: Double): String {
    val totalMinutes = (hours * 60).roundToInt()
    if (totalMinutes < 60) return "${totalMinutes}min"
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (m == 0) "${h}h" else "${h}h ${m}m"
}

@Composable
fun AppliancesSheetContent(
    appliances: Map<SimApplianceType, ApplianceState>,
    currentHour: Double,
    onSetSchedule: (SimApplianceType, Int, Double, Set<DayPeriod>) -> Unit,
    modifier: Modifier = Modifier,
    dayType: DayType = DayType.WEEKDAY
) {
    val palette = LocalLumixPalette.current
    val currentLoadKw = totalApplianceLoadKwAt(appliances, currentHour, dayType)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(
            "APPLIANCES",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = palette.textSecondary
        )

        Text("CURRENT LOAD (at ${formatSimTime(currentHour)})", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, modifier = Modifier.padding(top = 12.dp))
        AnimatedCounterText(
            targetValue = currentLoadKw,
            format = { "%.2f kW".format(it) },
            style = numberDisplayStyle(size = 34.sp),
            color = palette.solarYellowText
        )
        Text(
            "Set how many of each appliance you have, how long they run, and which part of the day — Morning, Noon, Night, any combination, or none to switch it off. " +
                "DEFAULT SCHEDULE shown below is a typical starting point — edit anytime.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
            modifier = Modifier.padding(top = 6.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        SimApplianceType.entries.groupBy { it.category }.forEach { (category, types) ->
            Text(
                category.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = palette.solarYellowText,
                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
            )
            types.forEach { type ->
                val state = appliances[type] ?: ApplianceState()
                val (quantity, hours, periods) = remember(state) { derivePickerState(state) }
                ApplianceScheduleRow(
                    type = type,
                    quantity = quantity,
                    hours = hours,
                    periods = periods,
                    onChange = { q, h, p -> onSetSchedule(type, q, h, p) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ApplianceScheduleRow(
    type: SimApplianceType,
    quantity: Int,
    hours: Double,
    periods: Set<DayPeriod>,
    onChange: (Int, Double, Set<DayPeriod>) -> Unit
) {
    val palette = LocalLumixPalette.current
    val enabled = periods.isNotEmpty()

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Column {
            Text(type.label, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
            Text(
                if (enabled) {
                    "${type.watts} W each • $quantity unit${if (quantity == 1) "" else "s"} • ${formatRunDuration(hours)} • " +
                        periods.sortedBy { it.startHour }.joinToString(" + ") { it.label }
                } else {
                    "${type.watts} W each • off"
                },
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DayPeriod.entries.forEach { period ->
                val selected = period in periods
                PeriodChip(
                    label = period.label,
                    selected = selected,
                    onClick = {
                        val updated = if (selected) periods - period else periods + period
                        onChange(quantity, hours, updated)
                    }
                )
            }
        }

        if (enabled) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StepperRow(
                    label = "Amount",
                    value = quantity.toString(),
                    onDecrement = { onChange((quantity - 1).coerceAtLeast(1), hours, periods) },
                    onIncrement = { onChange((quantity + 1).coerceAtMost(50), hours, periods) }
                )
                StepperRow(
                    label = "Runs for",
                    value = formatRunDuration(hours),
                    onDecrement = { onChange(quantity, stepHours(hours, increase = false), periods) },
                    onIncrement = { onChange(quantity, stepHours(hours, increase = true), periods) }
                )
            }
        }
    }
}

@Composable
private fun PeriodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalLumixPalette.current
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (selected) palette.background else palette.textPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(LumixRadius.pill))
            .background(if (selected) palette.solarYellow else palette.glass)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun StepperRow(label: String, value: String, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    val palette = LocalLumixPalette.current
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        MiniStepper(
            value = value,
            onDecrement = onDecrement,
            onIncrement = onIncrement,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun MiniStepper(value: String, onDecrement: () -> Unit, onIncrement: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(LumixRadius.sm))
            .background(palette.glass),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "–",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary,
            modifier = Modifier.clickable { onDecrement() }.padding(horizontal = 12.dp, vertical = 6.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            "+",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary,
            modifier = Modifier.clickable { onIncrement() }.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
