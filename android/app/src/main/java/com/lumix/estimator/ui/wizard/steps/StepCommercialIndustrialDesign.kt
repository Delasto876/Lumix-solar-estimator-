package com.lumix.estimator.ui.wizard.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.SystemType
import com.lumix.estimator.domain.commercial.BusinessHours
import com.lumix.estimator.domain.commercial.CommercialIndustrialDesign
import com.lumix.estimator.domain.commercial.CommercialIndustrialLoadCatalog
import com.lumix.estimator.domain.commercial.DiversityFactor
import com.lumix.estimator.domain.commercial.DiversityFactorPreset
import com.lumix.estimator.domain.commercial.IndustrialShiftSchedule
import com.lumix.estimator.domain.commercial.LoadDefinition
import com.lumix.estimator.domain.commercial.LoadInstance
import com.lumix.estimator.domain.commercial.Shift
import com.lumix.estimator.domain.simulation.DayType
import com.lumix.estimator.ui.components.CollapsibleSectionCard
import com.lumix.estimator.ui.components.IntField
import com.lumix.estimator.ui.components.NumberField
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette

/**
 * Phase 28 §9/§13 (the manual COMMERCIAL/INDUSTRIAL design workflow — no auto-recommendation
 * engine exists yet, see [com.lumix.estimator.domain.commercial.CommercialIndustrialCalculator]'s
 * own doc): the first UI ever built for [CommercialIndustrialDesign] — Phase 27 built the domain
 * layer with none. Deliberately scoped to what a manual design actually needs to size a load
 * (electrical service, schedule, diversity factor, load list) — parallel-inverter/PV-string/
 * battery-per-inverter entry (§7-§12) and a per-load drag-editable time-bar (§10) are NOT built
 * this round; see the completion report's "deferred" section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepCommercialIndustrialDesign(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    val palette = LocalLumixPalette.current
    val design = inputs.commercialIndustrialDesign ?: CommercialIndustrialDesign()

    fun updateDesign(transform: (CommercialIndustrialDesign) -> CommercialIndustrialDesign) {
        onUpdate { it.copy(commercialIndustrialDesign = transform(design)) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Manual load design",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary
        )
        Text(
            if (inputs.systemCategory == SystemType.INDUSTRIAL) {
                "Enter your own shift schedule and equipment — nothing here is assumed for you."
            } else {
                "Enter your business hours and equipment. Loads that need to run 24 hours (refrigeration, security, networking) can be set to run all day regardless of business hours."
            },
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )

        ElectricalServiceSection(design, ::updateDesign)

        if (inputs.systemCategory == SystemType.COMMERCIAL) {
            BusinessHoursSection(design.businessHours) { hours -> updateDesign { it.copy(businessHours = hours) } }
        } else {
            IndustrialShiftSection(design.industrialShiftSchedule) { schedule -> updateDesign { it.copy(industrialShiftSchedule = schedule) } }
        }

        DiversityFactorSection(design.diversityFactor) { factor -> updateDesign { it.copy(diversityFactor = factor) } }

        LoadsSection(inputs.systemCategory, design, ::updateDesign)

        DesignSummarySection(design)
    }
}

@Composable
private fun ElectricalServiceSection(design: CommercialIndustrialDesign, updateDesign: ((CommercialIndustrialDesign) -> CommercialIndustrialDesign) -> Unit) {
    val service = design.electricalService
    SectionCard(title = "Electrical service") {
        Text("Phase", style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            com.lumix.estimator.domain.commercial.LoadPhaseType.entries.forEachIndexed { index, phase ->
                SegmentedButton(
                    selected = service.phase == phase,
                    onClick = { updateDesign { it.copy(electricalService = service.copy(phase = phase)) } },
                    shape = SegmentedButtonDefaults.itemShape(index, com.lumix.estimator.domain.commercial.LoadPhaseType.entries.size)
                ) {
                    Text(when (phase) {
                        com.lumix.estimator.domain.commercial.LoadPhaseType.SINGLE_PHASE -> "Single"
                        com.lumix.estimator.domain.commercial.LoadPhaseType.SPLIT_PHASE -> "Split"
                        com.lumix.estimator.domain.commercial.LoadPhaseType.THREE_PHASE -> "Three"
                    })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                label = "Nominal voltage", value = service.nominalVoltage, suffix = "V",
                onValueChange = { v -> updateDesign { it.copy(electricalService = service.copy(nominalVoltage = v)) } },
                modifier = Modifier.weight(1f)
            )
            NumberField(
                label = "Frequency", value = service.frequencyHz, suffix = "Hz",
                onValueChange = { v -> updateDesign { it.copy(electricalService = service.copy(frequencyHz = v)) } },
                modifier = Modifier.weight(1f)
            )
        }
        NumberField(
            label = "Utility service capacity (optional)",
            value = service.utilityServiceCapacityAmps ?: 0.0, suffix = "A",
            onValueChange = { v -> updateDesign { it.copy(electricalService = service.copy(utilityServiceCapacityAmps = if (v > 0.0) v else null)) } }
        )
    }
}

@Composable
private fun BusinessHoursSection(hours: BusinessHours, onChange: (BusinessHours) -> Unit) {
    SectionCard(title = "Business hours") {
        DayHoursRow("Weekday (Mon-Fri)", hours.weekdayOpenHour, hours.weekdayCloseHour) { open, close ->
            onChange(hours.copy(weekdayOpenHour = open, weekdayCloseHour = close))
        }
        DayHoursRow("Saturday", hours.saturdayOpenHour, hours.saturdayCloseHour) { open, close ->
            onChange(hours.copy(saturdayOpenHour = open, saturdayCloseHour = close))
        }
        DayHoursRow("Sunday", hours.sundayOpenHour, hours.sundayCloseHour) { open, close ->
            onChange(hours.copy(sundayOpenHour = open, sundayCloseHour = close))
        }
    }
}

@Composable
private fun DayHoursRow(label: String, openHour: Double?, closeHour: Double?, onChange: (Double?, Double?) -> Unit) {
    val palette = LocalLumixPalette.current
    val isOpen = openHour != null && closeHour != null
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), color = palette.textPrimary)
            Switch(
                checked = isOpen,
                onCheckedChange = { open -> if (open) onChange(openHour ?: 7.0, closeHour ?: 18.0) else onChange(null, null) }
            )
        }
        if (isOpen) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                NumberField(
                    label = "Open", value = openHour ?: 7.0, suffix = "h",
                    onValueChange = { v -> onChange(v, closeHour) },
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    label = "Close", value = closeHour ?: 18.0, suffix = "h",
                    onValueChange = { v -> onChange(openHour, v) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun IndustrialShiftSection(schedule: IndustrialShiftSchedule, onChange: (IndustrialShiftSchedule) -> Unit) {
    val palette = LocalLumixPalette.current
    SectionCard(title = "Shift schedule") {
        if (!schedule.isConfigured) {
            Text(
                "Not yet configured — enter at least one shift and your working days below before this design can be sized.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.warningRedText
            )
        }
        IntField(
            label = "Number of shifts",
            value = schedule.numberOfShifts,
            onValueChange = { n -> onChange(schedule.copy(numberOfShifts = n.coerceIn(0, 3))) }
        )
        if (schedule.numberOfShifts >= 1) ShiftRow("Shift 1", schedule.shift1) { s -> onChange(schedule.copy(shift1 = s)) }
        if (schedule.numberOfShifts >= 2) ShiftRow("Shift 2", schedule.shift2) { s -> onChange(schedule.copy(shift2 = s)) }
        if (schedule.numberOfShifts >= 3) ShiftRow("Shift 3", schedule.shift3) { s -> onChange(schedule.copy(shift3 = s)) }

        Text("Working days", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DayType.entries.forEach { dayType ->
                val selected = dayType in schedule.workingDayTypes
                TextButton(onClick = {
                    val updated = if (selected) schedule.workingDayTypes - dayType else schedule.workingDayTypes + dayType
                    onChange(schedule.copy(workingDayTypes = updated))
                }) {
                    Text(
                        (if (selected) "✓ " else "") + dayType.label,
                        color = if (selected) palette.solarYellowText else palette.textSecondary
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Weekend operation", modifier = Modifier.weight(1f), color = palette.textPrimary)
            Switch(checked = schedule.weekendOperation, onCheckedChange = { v -> onChange(schedule.copy(weekendOperation = v)) })
        }
        Text(
            "Production hours/day: %.1f".format(schedule.productionHoursPerDay),
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )
    }
}

@Composable
private fun ShiftRow(label: String, shift: Shift?, onChange: (Shift) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(
            label = "$label start", value = shift?.startHour ?: 0.0, suffix = "h",
            onValueChange = { v -> onChange(Shift(v, shift?.endHour ?: v)) },
            modifier = Modifier.weight(1f)
        )
        NumberField(
            label = "$label end", value = shift?.endHour ?: 0.0, suffix = "h",
            onValueChange = { v -> onChange(Shift(shift?.startHour ?: 0.0, v)) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DiversityFactorSection(factor: DiversityFactor, onChange: (DiversityFactor) -> Unit) {
    val palette = LocalLumixPalette.current
    SectionCard(title = "Diversity / simultaneous-use factor") {
        Text(
            "What fraction of the connected load could realistically run at once? Confirm this rather than leaving it at the 100% default.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DiversityFactorPreset.entries.forEach { preset ->
                val selected = factor.preset == preset
                TextButton(onClick = { onChange(factor.copy(preset = preset)) }) {
                    Text(
                        (if (selected) "✓ " else "") + (preset.fraction?.let { "${(it * 100).toInt()}%" } ?: "Custom"),
                        color = if (selected) palette.solarYellowText else palette.textSecondary
                    )
                }
            }
        }
        if (factor.preset == DiversityFactorPreset.CUSTOM) {
            NumberField(
                label = "Custom fraction (0-1)", value = factor.customFraction,
                onValueChange = { v -> onChange(factor.copy(customFraction = v.coerceIn(0.0, 1.0))) }
            )
        }
    }
}

@Composable
private fun LoadsSection(
    systemType: SystemType,
    design: CommercialIndustrialDesign,
    updateDesign: ((CommercialIndustrialDesign) -> CommercialIndustrialDesign) -> Unit
) {
    val palette = LocalLumixPalette.current
    val catalog = CommercialIndustrialLoadCatalog.loadsFor(systemType)

    SectionCard(title = "Your loads") {
        if (design.loads.isEmpty()) {
            Text("No loads added yet — add some below.", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        }
        design.loads.forEachIndexed { index, load ->
            LoadRow(load) { updated ->
                updateDesign { it.copy(loads = it.loads.toMutableList().apply { this[index] = updated }) }
            }
            TextButton(onClick = { updateDesign { it.copy(loads = it.loads.toMutableList().apply { removeAt(index) }) } }) {
                Text("Remove", color = palette.warningRedText)
            }
        }
    }

    CollapsibleSectionCard(title = "Add a load", subtitle = "${catalog.size} types available") {
        catalog.forEach { def ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(def.label, color = palette.textPrimary)
                    Text("${def.defaultRatedWatts.toInt()}W typical", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                }
                TextButton(onClick = { updateDesign { it.copy(loads = it.loads + newInstanceFrom(def)) } }) {
                    Text("+ Add")
                }
            }
        }
    }
}

private fun newInstanceFrom(def: LoadDefinition): LoadInstance = LoadInstance(
    definitionId = def.id,
    label = def.label,
    ratedWatts = def.defaultRatedWatts,
    voltage = def.defaultVoltage,
    phase = def.defaultPhase,
    frequencyHz = def.defaultFrequencyHz,
    powerFactor = def.defaultPowerFactor,
    operationType = def.defaultOperationType,
    priority = def.defaultPriority,
    startingSurgeWatts = def.defaultStartingSurgeMultiplier?.let { it * def.defaultRatedWatts }
)

@Composable
private fun LoadRow(load: LoadInstance, onChange: (LoadInstance) -> Unit) {
    val palette = LocalLumixPalette.current
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(load.label, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntField(
                label = "Qty", value = load.quantity,
                onValueChange = { v -> onChange(load.copy(quantity = v.coerceAtLeast(1))) },
                modifier = Modifier.weight(1f)
            )
            NumberField(
                label = "Watts", value = load.ratedWatts, suffix = "W",
                onValueChange = { v -> onChange(load.copy(ratedWatts = v)) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                label = "Power factor", value = load.powerFactor,
                onValueChange = { v -> onChange(load.copy(powerFactor = v.coerceIn(0.01, 1.0))) },
                modifier = Modifier.weight(1f)
            )
            NumberField(
                label = "Hours/day", value = load.operatingHoursPerDay, suffix = "h",
                onValueChange = { v -> onChange(load.copy(operatingHoursPerDay = v.coerceIn(0.0, 24.0))) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DesignSummarySection(design: CommercialIndustrialDesign) {
    val palette = LocalLumixPalette.current
    SectionCard(title = "Load summary") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryStat("CONNECTED LOAD", "%.2f kW".format(design.connectedLoadKw), Modifier.weight(1f))
            SummaryStat("DESIGN LOAD", "%.2f kW".format(design.designLoadKw), Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryStat("DESIGN kVA", "%.2f kVA".format(design.designApparentPowerKva), Modifier.weight(1f))
            SummaryStat("EST. DAILY ENERGY", "%.1f kWh".format(design.estimatedDailyEnergyKwh), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = palette.textPrimary)
    }
}
