package com.lumix.estimator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.commercial.LoadDefinition
import com.lumix.estimator.domain.commercial.LoadInstance
import com.lumix.estimator.domain.commercial.decimalHourTo12Hour
import com.lumix.estimator.domain.commercial.hoursBetweenWrapping
import com.lumix.estimator.domain.commercial.twelveHourToDecimalHour
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixColors
import com.lumix.estimator.ui.theme.LumixRadius
import kotlin.math.roundToInt

/**
 * Phase 32 (shared by [com.lumix.estimator.ui.wizard.steps.StepCommercialIndustrialDesign]'s
 * "Loads" step and [com.lumix.estimator.ui.simulation.AppliancesSheetContent]'s Commercial/
 * Industrial branch — "for the appliance section... if commercial or industrial choose those in
 * appliances picker"): one catalog load type, always visible, quantity 0 = not included — see
 * [CatalogLoadRow]'s own doc. Extracted out of the wizard step (where it originated, Phase 31) so
 * the simulation's Appliances sheet can show the exact same picker instead of maintaining a
 * second, drifting copy.
 */

/** Matches SystemCalculator.acBtuPerWatt(AcInverterType.NON_INVERTER) — commercial/industrial AC loads have no inverter/non-inverter distinction of their own yet, so this uses the same non-inverter ratio residential AC defaults to. */
const val COMMERCIAL_AC_BTU_PER_WATT = 10.0

fun newInstanceFrom(def: LoadDefinition): LoadInstance = LoadInstance(
    definitionId = def.id,
    label = def.label,
    ratedWatts = def.defaultRatedWatts,
    voltage = def.defaultVoltage,
    phase = def.defaultPhase,
    frequencyHz = def.defaultFrequencyHz,
    powerFactor = def.defaultPowerFactor,
    operationType = def.defaultOperationType,
    priority = def.defaultPriority,
    startingSurgeWatts = def.defaultStartingSurgeMultiplier?.let { it * def.defaultRatedWatts },
    btu = def.defaultBtu,
    operatingHoursPerDay = def.defaultHoursPerDay ?: 0.0
)

/**
 * Phase 39 ("for the time and shift let me use 12hr clock instead of the 24hr clock type... for
 * industrial load use 12hr clock right throughout 12hr am 12hr pm"): a 12-hour Hour/Minute/AM-PM
 * time entry — the decimal-hour figures every Shift/BusinessHours/LoadInstance.typicalStartHour
 * field is stored as underneath (0.0-23.99, midnight-based — UNCHANGED by this round, a purely
 * additive entry/display conversion via [decimalHourTo12Hour]/[twelveHourToDecimalHour]) are hard
 * to type directly in 24-hour form ("14:00" for 2pm) — this now matches how installers actually
 * think and speak about a shift or a load's schedule. Shared by every Commercial/Industrial time
 * entry surface (shifts, load Starts/Ends — this file's own [CatalogLoadRow] and
 * `StepCommercialIndustrialDesign.kt`'s ShiftRow/custom-load LoadRow all call this one function),
 * so fixing the clock convention here fixes it everywhere at once.
 */
@Composable
fun TimeField(label: String, hour: Double, onChange: (Double) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    val clock = decimalHourTo12Hour(hour)

    fun emit(hour12: Int, minute: Int, isPm: Boolean) {
        onChange(twelveHourToDecimalHour(hour12, minute, isPm))
    }

    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            IntField(
                label = "H", value = clock.hour12,
                onValueChange = { newHour12 -> emit(newHour12, clock.minute, clock.isPm) },
                modifier = Modifier.weight(1f)
            )
            IntField(
                label = "MM", value = clock.minute,
                onValueChange = { newMinute -> emit(clock.hour12, newMinute, clock.isPm) },
                modifier = Modifier.weight(1f)
            )
            AmPmToggle(
                isPm = clock.isPm,
                onChange = { newIsPm -> emit(clock.hour12, clock.minute, newIsPm) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** A two-way AM/PM pill toggle, matching this app's existing day-type pill selectors (e.g. the residential Appliances sheet's RunEditorRow Weekday/Sat/Sun row). */
@Composable
private fun AmPmToggle(isPm: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(LumixRadius.sm))
            .background(palette.glass)
    ) {
        listOf(false to "AM", true to "PM").forEach { (pm, pmLabel) ->
            val selected = pm == isPm
            Text(
                pmLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) palette.background else palette.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(LumixRadius.sm))
                    .background(if (selected) palette.solarYellow else Color.Transparent)
                    .clickable { onChange(pm) }
                    .padding(vertical = 14.dp)
            )
        }
    }
}

/**
 * Phase 35 ("the time bar to show when equipment is on or off"): a slim, non-interactive 24h
 * preview bar for one load's single on-window — [startHour] through [startHour] + [durationHours],
 * wrapping past midnight the same way [com.lumix.estimator.domain.commercial.commercialLoadKwAt]
 * itself does, so what this draws always matches what actually contributes to the live simulation
 * dial. Same visual language as the residential Appliances sheet's own `MiniTimelineBar` (a
 * different composable — that one draws a `List<ApplianceRun>`, this one draws a single window —
 * kept separate rather than forcing a commercial load into an artificial multi-run shape it
 * doesn't have).
 */
@Composable
fun LoadWindowTimelineBar(startHour: Double, durationHours: Double, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    Canvas(modifier = modifier.height(6.dp)) {
        drawRoundRect(color = palette.glass, cornerRadius = CornerRadius(3.dp.toPx()), size = size)
        if (durationHours <= 0.0) return@Canvas
        val color = LumixColors.SolarYellow.copy(alpha = 0.8f)
        val start = startHour.mod(24.0)
        val startX = (start / 24.0 * size.width).toFloat()
        val endHour = start + durationHours
        if (endHour <= 24.0) {
            val endX = (endHour / 24.0 * size.width).toFloat()
            drawRoundRect(color = color, topLeft = Offset(startX, 0f), size = Size((endX - startX).coerceAtLeast(2f), size.height), cornerRadius = CornerRadius(size.height / 2f))
        } else {
            drawRoundRect(color = color, topLeft = Offset(startX, 0f), size = Size((size.width - startX).coerceAtLeast(2f), size.height), cornerRadius = CornerRadius(size.height / 2f))
            val wrapEndX = ((endHour - 24.0) / 24.0 * size.width).toFloat()
            drawRoundRect(color = color, topLeft = Offset(0f, 0f), size = Size(wrapEndX.coerceAtLeast(2f), size.height), cornerRadius = CornerRadius(size.height / 2f))
        }
    }
}

/**
 * One catalog load type, always visible — matching how the residential wizard's
 * `ApplianceType.entries` list already works (one row per type, quantity 0 = not included) instead
 * of an "Add a load" step. Raising the quantity above 0 reveals Watts (or BTU, for AC-type loads),
 * power factor, duty cycle, runtime, "when it typically starts," a real-power/apparent-power
 * readout, and a visual on/off timeline preview.
 */
@Composable
fun CatalogLoadRow(def: LoadDefinition, instance: LoadInstance?, onChange: (LoadInstance?) -> Unit) {
    val palette = LocalLumixPalette.current
    val quantity = instance?.quantity ?: 0

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(def.label, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
                Text(
                    if (def.isAcLoad) "${def.defaultBtu?.toInt() ?: 0} BTU typical" else "${def.defaultRatedWatts.toInt()}W typical",
                    style = MaterialTheme.typography.labelSmall, color = palette.textSecondary
                )
            }
            IntField(
                label = "Qty",
                value = quantity,
                onValueChange = { v ->
                    val q = v.coerceAtLeast(0)
                    when {
                        q <= 0 -> onChange(null)
                        instance != null -> onChange(instance.copy(quantity = q))
                        else -> onChange(newInstanceFrom(def).copy(quantity = q))
                    }
                },
                modifier = Modifier.weight(0.6f)
            )
        }
        if (instance != null && quantity > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                if (def.isAcLoad) {
                    NumberField(
                        label = "BTU", value = instance.btu ?: (instance.ratedWatts * COMMERCIAL_AC_BTU_PER_WATT), suffix = "BTU",
                        onValueChange = { v -> onChange(instance.copy(btu = v, ratedWatts = v / COMMERCIAL_AC_BTU_PER_WATT)) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    NumberField(
                        label = "Watts (each)", value = instance.ratedWatts, suffix = "W",
                        onValueChange = { v -> onChange(instance.copy(ratedWatts = v)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                NumberField(
                    label = "Power factor", value = instance.powerFactor,
                    onValueChange = { v -> onChange(instance.copy(powerFactor = v.coerceIn(0.01, 1.0))) },
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    label = "Duty cycle", value = instance.dutyCycleFraction,
                    supportingText = "Fraction of \"on\" time actually drawing rated power",
                    onValueChange = { v -> onChange(instance.copy(dutyCycleFraction = v.coerceIn(0.0, 1.0))) },
                    modifier = Modifier.weight(1f)
                )
            }
            // Phase 36 ("if I choose 6 hours it assume 6am to 12pm but what if its 6 hours from
            // 9am to 3pm I should be able to choose when to when just like residential"): explicit
            // Starts/Ends clock times — the same pattern the shift editor already uses — instead
            // of a duration typed separately from a start time. operatingHoursPerDay is DERIVED
            // from these two endpoints (hoursBetweenWrapping), never typed directly, so there's
            // only one way to end up with a mismatched window.
            val startHour = instance.typicalStartHour ?: 0.0
            val endHour = (startHour + instance.operatingHoursPerDay).mod(24.0)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                TimeField(
                    label = "Starts", hour = startHour,
                    onChange = { v -> onChange(instance.copy(typicalStartHour = v, operatingHoursPerDay = hoursBetweenWrapping(v, endHour))) },
                    modifier = Modifier.weight(1f)
                )
                TimeField(
                    label = "Ends", hour = endHour,
                    onChange = { v -> onChange(instance.copy(operatingHoursPerDay = hoursBetweenWrapping(startHour, v))) },
                    modifier = Modifier.weight(1f)
                )
            }
            // Phase 35 ("power engineering math's and physics"): surfaces the same kW/kVA/PF
            // formulas LoadInstance already computes for the wizard's connected/design-load
            // totals (ElectricalPower.apparentPowerKva = kW / PF) — real power scaled by the duty
            // cycle above is what actually contributes to the simulation dial (commercialLoadKwAt);
            // apparent power (kVA) is the larger figure a breaker/wire/generator must be sized to.
            Text(
                "%.1fh/day · %.2f kW real (while on) · %.2f kVA apparent · PF %.2f".format(
                    instance.operatingHoursPerDay,
                    instance.maximumExpectedRealPowerKw,
                    instance.maximumExpectedApparentPowerKva,
                    instance.effectivePowerFactor
                ),
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )
            LoadWindowTimelineBar(
                startHour = instance.typicalStartHour ?: 0.0,
                durationHours = instance.operatingHoursPerDay,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
    }
}
