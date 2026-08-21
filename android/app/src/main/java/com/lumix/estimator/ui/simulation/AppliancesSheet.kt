package com.lumix.estimator.ui.simulation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumix.estimator.domain.SystemType
import com.lumix.estimator.domain.commercial.CommercialIndustrialLoadCatalog
import com.lumix.estimator.domain.commercial.LoadInstance
import com.lumix.estimator.domain.commercial.commercialLoadKwAt
import com.lumix.estimator.domain.simulation.ApplianceRun
import com.lumix.estimator.domain.simulation.ApplianceState
import com.lumix.estimator.domain.simulation.DayType
import com.lumix.estimator.domain.simulation.SimApplianceType
import com.lumix.estimator.domain.simulation.defaultScheduleFor
import com.lumix.estimator.domain.simulation.totalApplianceLoadKwAt
import com.lumix.estimator.ui.components.AnimatedCounterText
import com.lumix.estimator.ui.components.CatalogLoadRow
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixColors
import com.lumix.estimator.ui.theme.LumixRadius
import com.lumix.estimator.ui.theme.numberDisplayStyle
import kotlin.math.roundToInt

/**
 * A34/A39: this app used to offer a Morning/Noon/Night picker where selecting a period gave an
 * appliance one shared duration applied to that whole bucket — an air conditioner "on for
 * Morning + Noon + Night" silently meant nameplate watts for up to 13 hours, regardless of how
 * the appliance actually behaves. Removed entirely. Every appliance already carries a real list
 * of [ApplianceRun]s (start hour, duration, day types) — [defaultScheduleFor] gives each one a
 * genuinely realistic starting shape (a kettle gets two 8-minute events, not "on all morning";
 * an air conditioner gets a single evening run, not 13 hours). The picker's old job — quantity/
 * hours/periods → a rebuilt run list — actively destroyed that real data on the first edit. This
 * file now edits the real runs directly instead.
 */

/** Shortest a run can be trimmed to — 5 minutes, for genuinely brief appliances (microwave, iron, pump). */
const val MIN_RUN_HOURS = 5.0 / 60.0

/** The longest a single run can span — a full day. */
private const val MAX_RUN_HOURS = 24.0

/** 5-minute steps below an hour (for short "event" appliances), 30-minute steps at/above it. */
private fun stepHours(current: Double, increase: Boolean, max: Double = MAX_RUN_HOURS): Double {
    val step = if (current < 1.0) MIN_RUN_HOURS else 0.5
    val next = if (increase) current + step else current - step
    return next.coerceIn(MIN_RUN_HOURS, max)
}

/** 15-minute steps, wrapping past midnight — fine enough to place a start time meaningfully. */
private fun stepStartHour(current: Double, increase: Boolean): Double {
    val step = 0.25
    return (if (increase) current + step else current - step).mod(24.0)
}

/** Minutes below an hour ("10min"), hours (with leftover minutes) at/above it ("1h", "1h 30m"). */
fun formatRunDuration(hours: Double): String {
    val totalMinutes = (hours * 60).roundToInt()
    if (totalMinutes < 60) return "${totalMinutes}min"
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (m == 0) "${h}h" else "${h}h ${m}m"
}

/**
 * One-line human-readable summary of an appliance's *real* schedule — built straight from its
 * actual runs, never a generic bucket label. A single long run reads as a real window ("5:30 PM
 * → 10:00 PM · 4h 30m/day"); several short runs read as events ("3 daily events · 8min avg"),
 * matching how a kettle or microwave is actually used, not a continuous draw.
 */
fun formatScheduleSummary(state: ApplianceState): String {
    if (!state.enabled || state.runs.isEmpty()) return "Off"
    val runs = state.runs
    if (runs.size == 1) {
        val run = runs[0]
        if (run.durationHours >= 23.9) return "Always on"
        val endHour = (run.startHour + run.durationHours).mod(24.0)
        return "${formatSimTime(run.startHour)} → ${formatSimTime(endHour)} · ${formatRunDuration(run.durationHours)}/day"
    }
    val allShort = runs.all { it.durationHours < 1.0 }
    return if (allShort) {
        val avgHours = runs.sumOf { it.durationHours } / runs.size
        "${runs.size} daily events · ${formatRunDuration(avgHours)} avg"
    } else {
        "${runs.size} runs/day · ${formatRunDuration(runs.sumOf { it.durationHours })} total"
    }
}

/** Real daily energy for one appliance — quantity × watts × duty factor × each run's own duration, summed. */
fun applianceDailyEnergyKwh(type: SimApplianceType, state: ApplianceState, dayType: DayType): Double {
    if (!state.enabled) return 0.0
    val watts = state.wattsOverride ?: type.watts.toDouble()
    return state.runs.filter { dayType in it.dayTypes }
        .sumOf { it.quantity * watts * type.dutyFactor * it.durationHours / 1000.0 }
}

@Composable
fun AppliancesSheetContent(
    appliances: Map<SimApplianceType, ApplianceState>,
    currentHour: Double,
    onSetAppliance: (SimApplianceType, ApplianceState) -> Unit,
    modifier: Modifier = Modifier,
    dayType: DayType = DayType.WEEKDAY,
    /**
     * Phase 32 ("for the appliance section... if commercial or industrial choose those in
     * appliances picker"): which catalog this sheet shows. RESIDENTIAL (the default, every
     * pre-existing caller) renders the [SimApplianceType] picker below completely unchanged;
     * anything else renders [CommercialAppliancesSheetContent] instead.
     */
    systemCategory: SystemType = SystemType.RESIDENTIAL,
    commercialLoads: List<LoadInstance> = emptyList(),
    onSetCommercialLoads: (List<LoadInstance>) -> Unit = {}
) {
    if (systemCategory != SystemType.RESIDENTIAL) {
        CommercialAppliancesSheetContent(
            systemCategory = systemCategory,
            loads = commercialLoads,
            currentHour = currentHour,
            onSetLoads = onSetCommercialLoads,
            modifier = modifier
        )
        return
    }

    val palette = LocalLumixPalette.current
    val currentLoadKw = totalApplianceLoadKwAt(appliances, currentHour, dayType)
    var editingType by remember { mutableStateOf<SimApplianceType?>(null) }

    val editing = editingType
    if (editing != null) {
        ApplianceScheduleEditorContent(
            type = editing,
            state = appliances[editing] ?: ApplianceState(),
            dayType = dayType,
            onChange = { newState -> onSetAppliance(editing, newState) },
            onBack = { editingType = null },
            modifier = modifier
        )
        return
    }

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
            "Every appliance below runs on its own real schedule — short events for things like a kettle or " +
                "microwave, a single window for things like an AC or TV, all-day duty cycling for a fridge. " +
                "Tap SCHEDULE to see or edit exactly when.",
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
                ApplianceSmartCard(
                    type = type,
                    state = state,
                    dayType = dayType,
                    onToggleEnabled = { enabled -> onSetAppliance(type, state.copy(enabled = enabled)) },
                    onOpenSchedule = { editingType = type }
                )
                HorizontalDivider()
            }
        }
    }
}

/**
 * Phase 32: the Commercial/Industrial counterpart of the RESIDENTIAL body above — same "CURRENT
 * LOAD" readout and always-visible catalog list shape, but sourced from
 * [CommercialIndustrialLoadCatalog.loadsFor] / [LoadInstance] (the exact loads configured on the
 * quote's Commercial/Industrial design step, via [CatalogLoadRow] — the same row this sheet's
 * wizard counterpart, [com.lumix.estimator.ui.wizard.steps.StepCommercialIndustrialDesign], uses)
 * instead of the residential [SimApplianceType] catalog. Deliberately scoped to the catalog's
 * standard load types only — the wizard's own "Custom Load" repeatable-add flow isn't duplicated
 * here; this sheet reviews/adjusts what's already configured rather than re-designing the load
 * list from scratch.
 */
@Composable
private fun CommercialAppliancesSheetContent(
    systemCategory: SystemType,
    loads: List<LoadInstance>,
    currentHour: Double,
    onSetLoads: (List<LoadInstance>) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLumixPalette.current
    val currentLoadKw = commercialLoadKwAt(loads, currentHour)
    val catalog = CommercialIndustrialLoadCatalog.loadsFor(systemCategory).filter { !it.isCustom }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(
            "LOADS",
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
            "Every load below runs on its own real window — set how many you have, how many hours/day they typically run, and roughly when. Only loads with a quantity above 0 draw any power.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
            modifier = Modifier.padding(top = 6.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        catalog.forEachIndexed { index, def ->
            val existing = loads.firstOrNull { it.definitionId == def.id }
            CatalogLoadRow(def, existing) { updated ->
                val list = loads.toMutableList()
                val existingIndex = list.indexOfFirst { it.definitionId == def.id }
                when {
                    updated == null && existingIndex >= 0 -> list.removeAt(existingIndex)
                    updated != null && existingIndex >= 0 -> list[existingIndex] = updated
                    updated != null -> list.add(updated)
                }
                onSetLoads(list)
            }
            if (index < catalog.size - 1) HorizontalDivider()
        }
    }
}

/** A compact card: name, quantity × watts, the appliance's real schedule summary, and daily energy. */
@Composable
private fun ApplianceSmartCard(
    type: SimApplianceType,
    state: ApplianceState,
    dayType: DayType,
    onToggleEnabled: (Boolean) -> Unit,
    onOpenSchedule: () -> Unit
) {
    val palette = LocalLumixPalette.current
    val quantity = state.runs.firstOrNull()?.quantity?.coerceAtLeast(1) ?: 1
    val dailyKwh = applianceDailyEnergyKwh(type, state, dayType)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(type.label, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
                Text(
                    "${type.watts} W each · $quantity unit${if (quantity == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary
                )
            }
            Switch(
                checked = state.enabled,
                onCheckedChange = onToggleEnabled,
                colors = SwitchDefaults.colors(checkedTrackColor = palette.solarYellow)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable { onOpenSchedule() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    formatScheduleSummary(state),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (state.enabled) palette.textPrimary else palette.textSecondary
                )
                if (state.enabled) {
                    Text(
                        "%.2f kWh/day".format(dailyKwh),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                }
            }
            Text(
                "SCHEDULE  ›",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = palette.solarYellowText
            )
        }

        if (state.enabled) {
            MiniTimelineBar(runs = state.runs, dayType = dayType, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

/** A slim, non-interactive 24h preview bar — just enough to see the schedule's real shape at a glance. */
@Composable
private fun MiniTimelineBar(runs: List<ApplianceRun>, dayType: DayType, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    Canvas(modifier = modifier.height(6.dp)) {
        drawRoundRect(
            color = palette.glass,
            cornerRadius = CornerRadius(3.dp.toPx()),
            size = size
        )
        runs.filter { dayType in it.dayTypes }.forEach { run ->
            drawTimelineBlock(run, size.width, size.height, LumixColors.SolarYellow.copy(alpha = 0.8f))
        }
    }
}

/** Draws one run's block(s) on a 0..24h-wide canvas, splitting at midnight for wraparound runs. */
private fun DrawScope.drawTimelineBlock(run: ApplianceRun, width: Float, height: Float, color: Color) {
    val startX = (run.startHour / 24.0 * width).toFloat()
    val endHour = run.startHour + run.durationHours
    if (endHour <= 24.0) {
        val endX = (endHour / 24.0 * width).toFloat()
        drawRoundRect(color = color, topLeft = Offset(startX, 0f), size = Size((endX - startX).coerceAtLeast(2f), height), cornerRadius = CornerRadius(height / 2f))
    } else {
        drawRoundRect(color = color, topLeft = Offset(startX, 0f), size = Size((width - startX).coerceAtLeast(2f), height), cornerRadius = CornerRadius(height / 2f))
        val wrapEndX = ((endHour - 24.0) / 24.0 * width).toFloat()
        drawRoundRect(color = color, topLeft = Offset(0f, 0f), size = Size(wrapEndX.coerceAtLeast(2f), height), cornerRadius = CornerRadius(height / 2f))
    }
}

/** Full schedule editor for one appliance: a labeled 24h timeline, each run's own controls, and quick presets. */
@Composable
private fun ApplianceScheduleEditorContent(
    type: SimApplianceType,
    state: ApplianceState,
    dayType: DayType,
    onChange: (ApplianceState) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLumixPalette.current
    val quantity = state.runs.firstOrNull()?.quantity?.coerceAtLeast(1) ?: 1

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Text(
                "‹ Back",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = palette.solarYellowText,
                modifier = Modifier.clickable { onBack() }
            )
        }
        Text(type.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        // A68: shows the real per-unit wattage this household was actually sized against (e.g.
        // AC's real BTU-derived average) when one exists, not the catalog's generic placeholder.
        val displayWatts = (state.wattsOverride ?: type.watts.toDouble()).roundToInt()
        Text(
            "$displayWatts W each · %.2f kWh/day".format(applianceDailyEnergyKwh(type, state, dayType)),
            style = MaterialTheme.typography.labelMedium,
            color = palette.textSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
        )

        FullTimelineBar(runs = state.runs, dayType = dayType, modifier = Modifier.fillMaxWidth())

        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("12 AM", "6 AM", "12 PM", "6 PM", "12 AM").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
        }

        Text(
            "QUICK SCHEDULE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = palette.textSecondary,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickPresetChip("Smart Default") {
                // A68: carry the current wattsOverride forward (e.g. AC's real BTU-derived
                // average) rather than resetting to the catalog's generic placeholder — this
                // preset changes the schedule shape/quantity, not what the household's units
                // actually draw.
                onChange(ApplianceState(enabled = true, runs = defaultScheduleFor(type).map { it.copy(quantity = quantity) }, wattsOverride = state.wattsOverride))
            }
            QuickPresetChip("Always On") {
                onChange(ApplianceState(enabled = true, runs = listOf(ApplianceRun(quantity = quantity, startHour = 0.0, durationHours = 24.0)), wattsOverride = state.wattsOverride))
            }
            QuickPresetChip("Off") {
                onChange(state.copy(enabled = false))
            }
        }

        StepperRow(
            label = "Quantity (applies to every run)",
            value = quantity.toString(),
            onDecrement = { onChange(state.copy(enabled = true, runs = state.runs.map { it.copy(quantity = (quantity - 1).coerceAtLeast(1)) })) },
            onIncrement = { onChange(state.copy(enabled = true, runs = state.runs.map { it.copy(quantity = (quantity + 1).coerceAtMost(50)) })) },
            modifier = Modifier.padding(top = 20.dp)
        )

        Text(
            "RUNS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = palette.textSecondary,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
        )
        state.runs.forEachIndexed { index, run ->
            RunEditorRow(
                run = run,
                canRemove = state.runs.size > 1,
                onChange = { updated -> onChange(state.copy(enabled = true, runs = state.runs.toMutableList().apply { this[index] = updated })) },
                onRemove = { onChange(state.copy(runs = state.runs.toMutableList().apply { removeAt(index) })) },
                modifier = Modifier.padding(bottom = 14.dp)
            )
        }
        Text(
            "+ Add run",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = palette.solarYellowText,
            modifier = Modifier
                .clickable {
                    val newRun = ApplianceRun(quantity = quantity, startHour = 18.0, durationHours = 0.5)
                    onChange(state.copy(enabled = true, runs = state.runs + newRun))
                }
                .padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun QuickPresetChip(label: String, onClick: () -> Unit) {
    val palette = LocalLumixPalette.current
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = palette.textPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(LumixRadius.pill))
            .background(palette.glass)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

/** One run's editable controls: start time, duration, and which days it applies to. */
@Composable
private fun RunEditorRow(run: ApplianceRun, canRemove: Boolean, onChange: (ApplianceRun) -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LumixRadius.md))
            .background(palette.glass)
            .padding(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${formatSimTime(run.startHour)} → ${formatSimTime((run.startHour + run.durationHours).mod(24.0))}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
            if (canRemove) {
                Text("Remove", style = MaterialTheme.typography.labelSmall, color = palette.warningRedText, modifier = Modifier.clickable { onRemove() })
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            StepperRow(
                label = "Starts",
                value = formatSimTime(run.startHour),
                onDecrement = { onChange(run.copy(startHour = stepStartHour(run.startHour, increase = false))) },
                onIncrement = { onChange(run.copy(startHour = stepStartHour(run.startHour, increase = true))) }
            )
            StepperRow(
                label = "Runs for",
                value = formatRunDuration(run.durationHours),
                onDecrement = { onChange(run.copy(durationHours = stepHours(run.durationHours, increase = false))) },
                onIncrement = { onChange(run.copy(durationHours = stepHours(run.durationHours, increase = true))) }
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DayType.entries.forEach { dt ->
                val selected = dt in run.dayTypes
                Text(
                    dt.label.take(3),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) palette.background else palette.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(LumixRadius.pill))
                        .background(if (selected) palette.solarYellow else Color.Transparent)
                        .clickable {
                            val updated = if (selected) run.dayTypes - dt else run.dayTypes + dt
                            if (updated.isNotEmpty()) onChange(run.copy(dayTypes = updated))
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

/** The premium 24h timeline: a track with every run drawn as a colored block, to scale. */
@Composable
private fun FullTimelineBar(runs: List<ApplianceRun>, dayType: DayType, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    Canvas(modifier = modifier.height(40.dp)) {
        drawRoundRect(color = palette.glass, cornerRadius = CornerRadius(8.dp.toPx()), size = size)
        // Quarter-day guide lines (6am/12pm/6pm) so the block positions read against real time.
        listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
            drawLine(
                color = palette.outline,
                start = Offset(size.width * fraction, 0f),
                end = Offset(size.width * fraction, size.height),
                strokeWidth = 1f
            )
        }
        runs.forEach { run ->
            val alpha = if (dayType in run.dayTypes) 0.9f else 0.25f
            drawTimelineBlock(run, size.width, size.height, LumixColors.SolarYellow.copy(alpha = alpha))
        }
    }
}

@Composable
private fun StepperRow(label: String, value: String, onDecrement: () -> Unit, onIncrement: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    Column(modifier = modifier) {
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
