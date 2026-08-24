package com.lumix.estimator.ui.wizard.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.BatterySpecSheet
import com.lumix.estimator.domain.Catalog
import com.lumix.estimator.domain.EquipmentSpecs
import com.lumix.estimator.domain.InverterOption
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.SystemType
import com.lumix.estimator.domain.commercial.BatteryPerInverterAllocation
import com.lumix.estimator.domain.commercial.BatteryPerInverterDesign
import com.lumix.estimator.domain.commercial.BusinessHours
import com.lumix.estimator.domain.commercial.CommercialFacilityType
import com.lumix.estimator.domain.commercial.CommercialIndustrialDesign
import com.lumix.estimator.domain.commercial.CommercialIndustrialLoadCatalog
import com.lumix.estimator.domain.commercial.DiversityFactor
import com.lumix.estimator.domain.commercial.DiversityFactorPreset
import com.lumix.estimator.domain.commercial.ElectricalService
import com.lumix.estimator.domain.commercial.ElectricalServicePreset
import com.lumix.estimator.domain.commercial.FacilityLoadLibrary
import com.lumix.estimator.domain.commercial.FacilityScheduleLibrary
import com.lumix.estimator.domain.commercial.IndustrialShiftSchedule
import com.lumix.estimator.domain.commercial.InverterUnitPvDesign
import com.lumix.estimator.domain.commercial.LoadInstance
import com.lumix.estimator.domain.commercial.hoursBetweenWrapping
import com.lumix.estimator.domain.commercial.ParallelInverterDesign
import com.lumix.estimator.domain.commercial.ParallelInverterValidator
import com.lumix.estimator.domain.commercial.Shift
import com.lumix.estimator.domain.commercial.StringAssignment
import com.lumix.estimator.domain.simulation.DayType
import com.lumix.estimator.ui.components.CatalogLoadRow
import com.lumix.estimator.ui.components.COMMERCIAL_AC_BTU_PER_WATT
import com.lumix.estimator.ui.components.IntField
import com.lumix.estimator.ui.components.LabeledDropdown
import com.lumix.estimator.ui.components.NumberField
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.components.TimeField
import com.lumix.estimator.ui.components.newInstanceFrom
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

    // Bugfix: must transform the LIVE design (it.commercialIndustrialDesign) at update time, not
    // the `design` val closed over from this composition — that val is a snapshot from whenever
    // this composable last ran, and reusing it here silently drops an edit whenever an update
    // fires before recomposition has caught up (e.g. two fields edited in quick succession). Every
    // other step in this wizard already reads its target fresh off `it` for the same reason (see
    // e.g. StepHouseholdAppliances' `it.copy(ac = it.ac.copy(...))`).
    fun updateDesign(transform: (CommercialIndustrialDesign) -> CommercialIndustrialDesign) {
        onUpdate { it.copy(commercialIndustrialDesign = transform(it.commercialIndustrialDesign ?: CommercialIndustrialDesign())) }
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
            BusinessHoursSection(design.businessHours, design.facility.commercialType) { hours -> updateDesign { it.copy(businessHours = hours) } }
        } else {
            IndustrialShiftSection(design.industrialShiftSchedule) { schedule -> updateDesign { it.copy(industrialShiftSchedule = schedule) } }
        }

        DiversityFactorSection(design.diversityFactor) { factor -> updateDesign { it.copy(diversityFactor = factor) } }

        ParallelInverterSection(design, ::updateDesign)
        BatteryPerInverterSection(design, ::updateDesign)
        TransformerSection(design, ::updateDesign)

        LoadsSection(inputs.systemCategory, design, ::updateDesign)

        DesignSummarySection(design)
    }
}

/**
 * Phase 43 (spec §16/§17 — the 7-preset Electrical Service picker plus real three-phase current/
 * kVA math): extends the existing free-form phase/voltage/frequency fields (Phase 27 §14) with a
 * quick-fill preset dropdown and a computed current/guidance readout. Every field below the preset
 * dropdown stays directly editable exactly as before — picking a preset only seeds starting values.
 */
@Composable
private fun ElectricalServiceSection(design: CommercialIndustrialDesign, updateDesign: ((CommercialIndustrialDesign) -> CommercialIndustrialDesign) -> Unit) {
    val service = design.electricalService

    fun applyPreset(current: ElectricalService, preset: ElectricalServicePreset): ElectricalService = if (preset == ElectricalServicePreset.CUSTOM) {
        current.copy(preset = preset)
    } else {
        current.copy(
            preset = preset,
            phase = preset.presetPhase ?: current.phase,
            nominalVoltage = preset.presetNominalVoltage ?: current.nominalVoltage,
            lineToNeutralVoltage = preset.presetLineToNeutralVoltage
        )
    }

    // Bugfix (same class this file's own StepCommercialIndustrialDesign doc already warns about
    // for `design`): must transform the LIVE electricalService (it.electricalService at update
    // time), not the `service` val snapshot closed over from composition — reusing that snapshot
    // would silently drop an edit whenever two updates fire before recomposition catches up. Any
    // direct edit to phase/voltage also drops the preset back to CUSTOM — otherwise the dropdown
    // would keep showing a preset label next to values the installer has since hand-changed.
    fun editField(transform: (ElectricalService) -> ElectricalService) {
        updateDesign { it.copy(electricalService = transform(it.electricalService).copy(preset = ElectricalServicePreset.CUSTOM)) }
    }

    SectionCard(title = "Electrical service") {
        LabeledDropdown(
            label = "Electrical service preset",
            options = ElectricalServicePreset.entries,
            selected = service.preset,
            optionLabel = { it.label },
            onSelected = { preset -> updateDesign { it.copy(electricalService = applyPreset(it.electricalService, preset)) } },
            supportingText = "Seeds phase/voltage below — every field stays editable either way.",
            modifier = Modifier.fillMaxWidth()
        )
        Text("Phase", style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            com.lumix.estimator.domain.commercial.LoadPhaseType.entries.forEachIndexed { index, phase ->
                SegmentedButton(
                    selected = service.phase == phase,
                    onClick = { editField { it.copy(phase = phase) } },
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
                label = if (service.phase == com.lumix.estimator.domain.commercial.LoadPhaseType.THREE_PHASE) "Line-to-line voltage" else "Nominal voltage",
                value = service.nominalVoltage, suffix = "V",
                onValueChange = { v -> editField { it.copy(nominalVoltage = v) } },
                modifier = Modifier.weight(1f)
            )
            NumberField(
                label = "Frequency", value = service.frequencyHz, suffix = "Hz",
                onValueChange = { v -> editField { it.copy(frequencyHz = v) } },
                modifier = Modifier.weight(1f)
            )
        }
        if (service.phase != com.lumix.estimator.domain.commercial.LoadPhaseType.SINGLE_PHASE) {
            NumberField(
                label = "Line-to-neutral voltage (optional)",
                value = service.lineToNeutralVoltage ?: 0.0, suffix = "V",
                onValueChange = { v -> editField { it.copy(lineToNeutralVoltage = if (v > 0.0) v else null) } }
            )
        }
        NumberField(
            label = "Utility service capacity (optional)",
            value = service.utilityServiceCapacityAmps ?: 0.0, suffix = "A",
            onValueChange = { v -> editField { it.copy(utilityServiceCapacityAmps = if (v > 0.0) v else null) } }
        )

        HorizontalDivider()
        Text(
            "Design load %.1f kW / %.1f kVA (PF %.2f)".format(design.designLoadKw, design.designApparentPowerKva, design.blendedPowerFactor),
            style = MaterialTheme.typography.bodyMedium
        )
        design.totalServiceCurrentAmps?.let { amps ->
            Text("Total service current: %.1f A".format(amps), style = MaterialTheme.typography.bodyMedium)
        }
        Text(design.electricalServiceGuidance, style = MaterialTheme.typography.labelSmall)
        Text(
            "If your inverter's AC output voltage or phase doesn't match this electrical service, add a transformer in the Transformer section below to bridge the gap.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )
    }
}

/**
 * Phase 46 (spec §18 — "Transformer Required? YES / NO... Do NOT automatically select a transformer
 * unless the voltage/phase mismatch requires one"): [Transformer.required] starts `false` and stays
 * that way until the installer flips it on themselves — nothing here inspects the chosen inverter or
 * electrical service to guess a mismatch (see [ElectricalServiceSection]'s own plain-text reminder
 * above instead, which nudges without deciding). Every field below only appears once `required` is
 * on, matching the spec's own "if YES:" structure.
 */
@Composable
private fun TransformerSection(design: CommercialIndustrialDesign, updateDesign: ((CommercialIndustrialDesign) -> CommercialIndustrialDesign) -> Unit) {
    val palette = LocalLumixPalette.current
    val transformer = design.transformer

    fun editTransformer(transform: (com.lumix.estimator.domain.commercial.Transformer) -> com.lumix.estimator.domain.commercial.Transformer) {
        updateDesign { it.copy(transformer = transform(it.transformer)) }
    }

    SectionCard(title = "Transformer") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Transformer required?", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = transformer.required, onCheckedChange = { checked -> editTransformer { it.copy(required = checked) } })
        }
        if (transformer.required) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    label = "Primary voltage", value = transformer.primaryVoltage, suffix = "V",
                    onValueChange = { v -> editTransformer { it.copy(primaryVoltage = v) } },
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    label = "Secondary voltage", value = transformer.secondaryVoltage, suffix = "V",
                    onValueChange = { v -> editTransformer { it.copy(secondaryVoltage = v) } },
                    modifier = Modifier.weight(1f)
                )
            }
            Text("Phase", style = MaterialTheme.typography.bodyMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                com.lumix.estimator.domain.commercial.LoadPhaseType.entries.forEachIndexed { index, phase ->
                    SegmentedButton(
                        selected = transformer.phase == phase,
                        onClick = { editTransformer { it.copy(phase = phase) } },
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
            Text("Direction", style = MaterialTheme.typography.bodyMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                com.lumix.estimator.domain.commercial.TransformerDirection.entries.forEachIndexed { index, direction ->
                    SegmentedButton(
                        selected = transformer.direction == direction,
                        onClick = { editTransformer { it.copy(direction = direction) } },
                        shape = SegmentedButtonDefaults.itemShape(index, com.lumix.estimator.domain.commercial.TransformerDirection.entries.size)
                    ) {
                        Text(if (direction == com.lumix.estimator.domain.commercial.TransformerDirection.STEP_UP) "Step-Up" else "Step-Down")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    label = "kVA rating", value = transformer.kvaRating, suffix = "kVA",
                    onValueChange = { v -> editTransformer { it.copy(kvaRating = v) } },
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    label = "Frequency", value = transformer.frequencyHz, suffix = "Hz",
                    onValueChange = { v -> editTransformer { it.copy(frequencyHz = v) } },
                    modifier = Modifier.weight(1f)
                )
            }
            NumberField(
                label = "Efficiency (illustrative — enter the real nameplate figure once known)",
                value = transformer.efficiencyPercent, suffix = "%",
                onValueChange = { v -> editTransformer { it.copy(efficiencyPercent = v.coerceIn(0.0, 100.0)) } }
            )
            HorizontalDivider()
            Text(
                "Transformer loss: %.2f kW — system must supply %.1f kW to deliver %.1f kW design load".format(
                    design.transformerLossKw, design.designLoadKwIncludingTransformerLoss, design.designLoadKw
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
        }
    }
}

/**
 * Phase 47 (spec §5/§6/§21 — facility-driven schedule suggestions): [facilityType] is only used to
 * look up an optional [FacilityScheduleLibrary] suggestion — everything else about this section is
 * unchanged from Phase 28. Applying the suggestion is always an explicit installer action (the
 * "Apply" button below), never automatic, matching the same contract [FacilityLoadLibrary]'s own doc
 * establishes for loads.
 */
@Composable
private fun BusinessHoursSection(hours: BusinessHours, facilityType: CommercialFacilityType?, onChange: (BusinessHours) -> Unit) {
    val palette = LocalLumixPalette.current
    val suggested = facilityType?.let { FacilityScheduleLibrary.suggestedBusinessHoursFor(it) }
    SectionCard(title = "Business hours") {
        if (suggested != null && suggested != hours) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Suggested schedule for ${facilityType?.label} — review and apply if it fits.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onChange(suggested) }) { Text("Apply") }
            }
        }
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
                TimeField(
                    label = "Open", hour = openHour ?: 7.0,
                    onChange = { v -> onChange(v, closeHour) },
                    modifier = Modifier.weight(1f)
                )
                TimeField(
                    label = "Close", hour = closeHour ?: 18.0,
                    onChange = { v -> onChange(openHour, v) },
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
        if (schedule.numberOfShifts >= 1) ShiftRow("Shift 1", schedule.shift1, previousShiftEnd = null) { s -> onChange(schedule.copy(shift1 = s)) }
        if (schedule.numberOfShifts >= 2) ShiftRow("Shift 2", schedule.shift2, previousShiftEnd = schedule.shift1?.endHour) { s -> onChange(schedule.copy(shift2 = s)) }
        if (schedule.numberOfShifts >= 3) ShiftRow("Shift 3", schedule.shift3, previousShiftEnd = schedule.shift2?.endHour) { s -> onChange(schedule.copy(shift3 = s)) }

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

/**
 * Phase 40 ("i want to enter like shift 1 from 6 am to 3pm and shift 2 starts 3pm to 11 pm"): a
 * not-yet-configured shift's Start/End default to [previousShiftEnd] (the prior shift's own End —
 * null for Shift 1, which has no predecessor) instead of a flat midnight, so a real back-to-back
 * schedule only needs each shift's END typed once — Shift 2 already shows Shift 1's end time as its
 * own starting point the moment Shift 1 is entered, rather than defaulting to 12:00 AM and forcing
 * the installer to retype the same clock time as both "Shift 1 End" and "Shift 2 Start." Purely a
 * DISPLAY default while [shift] is still null — the moment any field is actually edited, [shift]
 * becomes a real, independent value and this suggestion no longer applies, so it never silently
 * overwrites a shift the installer deliberately set to something else (e.g. a real gap between
 * shifts, or later going back and changing an earlier shift's end).
 */
@Composable
private fun ShiftRow(label: String, shift: Shift?, previousShiftEnd: Double?, onChange: (Shift) -> Unit) {
    val defaultStart = previousShiftEnd ?: 0.0
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalLumixPalette.current.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeField(
                label = "Start", hour = shift?.startHour ?: defaultStart,
                onChange = { v -> onChange(Shift(v, shift?.endHour ?: v)) },
                modifier = Modifier.weight(1f)
            )
            TimeField(
                label = "End", hour = shift?.endHour ?: defaultStart,
                onChange = { v -> onChange(Shift(shift?.startHour ?: defaultStart, v)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DiversityFactorSection(factor: DiversityFactor, onChange: (DiversityFactor) -> Unit) {
    val palette = LocalLumixPalette.current
    SectionCard(title = "Diversity / simultaneous-use factor") {
        Text(
            "What fraction of the connected load could realistically run at once? Defaults to 60% (not everything runs simultaneously) — push it up toward 85-100% if this site genuinely does run everything at once.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )
        Text(
            "${(factor.fraction * 100).toInt()}%",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = palette.solarYellowText
        )
        // Moving the slider at all writes CUSTOM — even back to 60% — since that still counts as
        // an explicit confirmation, distinct from never having touched the untouched PERCENT_60
        // default (see CommercialIndustrialCalculator's own "diversity factor not confirmed" check).
        Slider(
            value = factor.fraction.toFloat(),
            onValueChange = { v -> onChange(DiversityFactor(preset = DiversityFactorPreset.CUSTOM, customFraction = v.toDouble().coerceIn(0.0, 1.0))) },
            valueRange = 0f..1f,
            steps = 19
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0%", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            Text("100%", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        }
    }
}

/**
 * Phase 29 (§7-§10 parallel inverter + per-unit PV string/MPPT entry — deferred in Phase 28, now
 * built): "each inverter in the parallel connection have their own panel per MPPT... 3 inverters in
 * parallel... each 12kW inverter have 3 MPPT and each MPPT have 10 x 720W on each MPPT string."
 * Configures ONE inverter unit's MPPT/string layout — the SAME layout is applied to every parallel
 * unit, matching how these systems are actually installed (identical units, symmetric wiring) and
 * matching the domain model's own worked example. [ParallelInverterDesign.unitPvDesigns] still
 * carries a fully independent entry per unit underneath (never collapsed into one shared number),
 * so a future round can offer per-unit customization without a data-model change — only this
 * uniform-entry UI would need to change.
 *
 * Phase 30 ("use the same equipment and material list and price list... when i choose the inverter
 * from the drop down menu, it should have the mppt amount... also ask if paralleling inverter and
 * how much"): the inverter/panel fields are now [LabeledDropdown]s over [Catalog.manualInverters]/
 * [Catalog.panelWattages] — the SAME real, priced equipment list [StepInverter]/[StepPanels] already
 * use for residential MANUAL mode, not a separately-typed free-text name. Picking an inverter
 * resolves its real [EquipmentSpecs.InverterSpec] (via [EquipmentSpecs.inverterSpecFor]) and
 * auto-fills rated kW + MPPT count from that model's own datasheet — both fields stay editable
 * afterward (this app's own "always overridable" rule) for a unit not in the catalog, or a real
 * MPPT count the installer knows differs from what's on file. Paralleling is now an explicit
 * Switch — off means a single inverter, on reveals a manual "how many in parallel" count — instead
 * of always showing a bare count field.
 */
@Composable
private fun ParallelInverterSection(design: CommercialIndustrialDesign, updateDesign: ((CommercialIndustrialDesign) -> CommercialIndustrialDesign) -> Unit) {
    val palette = LocalLumixPalette.current
    val existing = design.parallelInverterDesign
    val modelId = existing?.inverterModelId ?: ""
    val ratedKwPerUnit = existing?.ratedKwPerUnit ?: 0.0
    val inverterCount = existing?.inverterCount ?: 1
    val panelWattage = existing?.panelWattage ?: 0
    val mpptsPerInverter = existing?.unitPvDesigns?.firstOrNull()?.strings?.size ?: 0
    val panelsPerString = existing?.unitPvDesigns?.firstOrNull()?.strings?.firstOrNull()?.panelCount ?: 0
    val paralleling = inverterCount > 1

    /** The real catalog spec behind an [InverterOption] — [InverterOption.id] is a short internal code, not the model string [ParallelInverterDesign.inverterModelId]/[ParallelInverterValidator] match against. */
    fun specFor(option: InverterOption) = EquipmentSpecs.inverterSpecFor(option.kw, option.name)
    val selectedOption = Catalog.manualInverters.firstOrNull { specFor(it)?.model == modelId }
    // Phase 34 ("if the inverter have 3 mppt, add that or i can use 2 mppt or all 3 or just 1"):
    // the installer can still use fewer than the model's full MPPT count — this only stops them
    // from entering MORE than the real hardware has.
    val maxMpptForSelected = selectedOption?.let { specFor(it)?.mpptCount }

    fun rebuild(
        newModelId: String = modelId,
        newRatedKwPerUnit: Double = ratedKwPerUnit,
        newInverterCount: Int = inverterCount,
        newPanelWattage: Int = panelWattage,
        newMpptsPerInverter: Int = mpptsPerInverter,
        newPanelsPerString: Int = panelsPerString
    ) {
        val unitPvDesigns = (0 until newInverterCount).map { unitIndex ->
            InverterUnitPvDesign(
                unitIndex = unitIndex,
                strings = (0 until newMpptsPerInverter).map { mpptIndex -> StringAssignment(mpptIndex, newPanelsPerString) }
            )
        }
        updateDesign {
            it.copy(
                parallelInverterDesign = ParallelInverterDesign(
                    inverterModelId = newModelId, ratedKwPerUnit = newRatedKwPerUnit, panelWattage = newPanelWattage,
                    inverterCount = newInverterCount, unitPvDesigns = unitPvDesigns
                )
            )
        }
    }

    SectionCard(title = "Parallel inverter system") {
        Text(
            "Pick the inverter from the same equipment catalog used elsewhere in this app — its MPPT count fills in automatically. Configure one unit's panel-per-MPPT layout; the same layout applies to every parallel unit.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )
        LabeledDropdown(
            label = "Inverter model",
            options = listOf<InverterOption?>(null) + Catalog.manualInverters,
            selected = selectedOption,
            optionLabel = { opt -> opt?.name ?: "Custom / not in catalog" },
            onSelected = { opt ->
                if (opt == null) {
                    rebuild(newModelId = "")
                } else {
                    val spec = specFor(opt)
                    rebuild(
                        newModelId = spec?.model ?: opt.name,
                        newRatedKwPerUnit = opt.kw,
                        newMpptsPerInverter = spec?.mpptCount ?: mpptsPerInverter
                    )
                }
            }
        )
        if (selectedOption == null) {
            OutlinedTextField(
                value = modelId,
                onValueChange = { v -> rebuild(newModelId = v) },
                label = { Text("Custom model name") },
                supportingText = { Text("Not in the catalog above — type an exact catalog model name to enable electrical/parallel validation, or any label for outside equipment.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                label = "Rated kW / unit", value = ratedKwPerUnit,
                onValueChange = { v -> rebuild(newRatedKwPerUnit = v) },
                modifier = Modifier.weight(1f)
            )
            IntField(
                label = "MPPTs / inverter", value = mpptsPerInverter,
                onValueChange = { v ->
                    val capped = v.coerceAtLeast(0).let { n -> maxMpptForSelected?.let { max -> n.coerceAtMost(max) } ?: n }
                    rebuild(newMpptsPerInverter = capped)
                },
                supportingText = maxMpptForSelected?.let { "Up to $it available on this model — use fewer if you're leaving some unwired" },
                modifier = Modifier.weight(1f)
            )
        }
        LabeledDropdown(
            label = "Panel model",
            options = Catalog.panelWattages,
            selected = panelWattage,
            optionLabel = { "$it W" },
            onSelected = { v -> rebuild(newPanelWattage = v) }
        )
        IntField(
            label = "Panels per MPPT string", value = panelsPerString,
            onValueChange = { v -> rebuild(newPanelsPerString = v.coerceAtLeast(0)) }
        )

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Paralleling multiple inverters?", modifier = Modifier.weight(1f), color = palette.textPrimary)
            Switch(
                checked = paralleling,
                onCheckedChange = { on -> rebuild(newInverterCount = if (on) inverterCount.coerceAtLeast(2) else 1) }
            )
        }
        if (paralleling) {
            IntField(
                label = "How many inverters in parallel", value = inverterCount,
                onValueChange = { v -> rebuild(newInverterCount = v.coerceAtLeast(2)) }
            )
        } else {
            Text(
                "Single inverter — not paralleled.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary
            )
        }

        val current = design.parallelInverterDesign
        if (current != null && current.inverterCount > 0) {
            Text(
                "Total: %d units x %.1f kW = %.1f kW | %d panels x %dW = %.1f kW PV"
                    .format(current.inverterCount, current.ratedKwPerUnit, current.totalInverterCapacityKw, current.totalPanelCount, current.panelWattage, current.totalPvKw),
                style = MaterialTheme.typography.labelSmall,
                color = palette.textPrimary
            )
            if (modelId.isNotBlank()) {
                val validation = ParallelInverterValidator.validate(current)
                (validation.warnings + validation.unitResults.flatMap { it.notes }).forEach { warning ->
                    Text("⚠ $warning", style = MaterialTheme.typography.labelSmall, color = palette.warningRedText)
                }
            }
        }
    }
}

/**
 * Phase 29 (§11/§12 battery-per-inverter entry — deferred in Phase 28, now built): "that same
 * inverter can have 3 x 16kWh battery and the same for the other inverter in the parallel
 * connection." One battery model + a batteries-per-unit count, applied uniformly across every
 * parallel inverter unit (same reasoning as [ParallelInverterSection] — [BatteryPerInverterDesign
 * .allocations] still carries a fully independent entry per unit underneath).
 */
@Composable
private fun BatteryPerInverterSection(design: CommercialIndustrialDesign, updateDesign: ((CommercialIndustrialDesign) -> CommercialIndustrialDesign) -> Unit) {
    val palette = LocalLumixPalette.current
    val inverterCount = design.parallelInverterDesign?.inverterCount ?: 0
    val existing = design.batteryPerInverterDesign
    val batteryModelId = existing?.allocations?.firstOrNull()?.batteryModelId ?: ""
    val batteriesPerUnit = existing?.allocations?.firstOrNull()?.batteryCount ?: 0
    val selectedBattery = EquipmentSpecs.batteries.firstOrNull { it.model == batteryModelId }

    fun rebuild(newBatteryModelId: String = batteryModelId, newBatteriesPerUnit: Int = batteriesPerUnit) {
        val allocations = (0 until inverterCount).map { unitIndex ->
            BatteryPerInverterAllocation(unitIndex, newBatteryModelId, newBatteriesPerUnit)
        }
        updateDesign { it.copy(batteryPerInverterDesign = BatteryPerInverterDesign(allocations)) }
    }

    // Phase 34 ("when i choose 6 inverter and i say 2 battery per inverter it should be 12
    // battery total"): the allocations list above only gets rebuilt when a battery field here is
    // directly edited — if the installer instead goes back and changes ParallelInverterSection's
    // own inverter count (e.g. 3 -> 6) without re-touching a battery field, this design's
    // allocations list silently stays at the old count, so totalBatteryCount/totalBatteryCapacityKwh
    // undercounts. Re-syncing here whenever inverterCount no longer matches what's actually
    // allocated keeps the total correct without requiring the installer to notice and re-enter it.
    LaunchedEffect(inverterCount, batteryModelId, batteriesPerUnit) {
        if (batteryModelId.isNotBlank() && existing?.allocations?.size != inverterCount) {
            rebuild()
        }
    }

    SectionCard(title = "Battery per inverter") {
        if (inverterCount <= 0) {
            Text(
                "Set up the parallel inverter system above first — batteries are allocated per inverter unit.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary
            )
            return@SectionCard
        }
        // Phase 30: same equipment catalog as the inverter/panel pickers above, in the same
        // LabeledDropdown style, instead of a TextButton row — EquipmentSpecs.batteries was
        // already the real, priced battery list; only the picker widget changed.
        LabeledDropdown(
            label = "Battery model",
            options = listOf<BatterySpecSheet?>(null) + EquipmentSpecs.batteries,
            selected = selectedBattery,
            optionLabel = { spec -> spec?.let { "${it.brand} ${it.model} (${it.ratingLabel})" } ?: "Select battery model" },
            onSelected = { spec -> if (spec != null) rebuild(newBatteryModelId = spec.model) }
        )
        IntField(
            label = "Batteries per inverter unit", value = batteriesPerUnit,
            onValueChange = { v -> rebuild(newBatteriesPerUnit = v.coerceAtLeast(0)) }
        )
        val current = design.batteryPerInverterDesign
        if (current != null && batteryModelId.isNotBlank()) {
            Text(
                "Total: $inverterCount units x $batteriesPerUnit batteries = ${current.totalBatteryCount} units, %.1f kWh"
                    .format(current.totalBatteryCapacityKwh),
                style = MaterialTheme.typography.labelSmall,
                color = palette.textPrimary
            )
        }
    }
}

@Composable
/**
 * Phase 31 ("for industrial, loads are blank where are the default loads and add them back so i
 * can pick runtime and amount and when they are likely to run"): every catalog load type is now
 * listed directly, always visible — matching how [StepHouseholdAppliances]' residential
 * `ApplianceType.entries` list already works (one row per type, quantity 0 = not included) —
 * instead of an empty "Your loads" list that only grew once you expanded a separate collapsed
 * catalog and tapped "+Add" per item. At most one [LoadInstance] exists per non-custom
 * [LoadDefinition.id] in [CommercialIndustrialDesign.loads] (quantity covers "how many of this
 * type"); the catalog's own "Custom Load" entry is the one exception still handled as a
 * repeatable add/remove list, since a custom entry has no fixed identity to key a single row on
 * and an installer may need several differently-specced custom loads.
 */
@Composable
private fun LoadsSection(
    systemType: SystemType,
    design: CommercialIndustrialDesign,
    updateDesign: ((CommercialIndustrialDesign) -> CommercialIndustrialDesign) -> Unit
) {
    val palette = LocalLumixPalette.current
    val catalog = CommercialIndustrialLoadCatalog.loadsFor(systemType)
    val allStandardDefs = catalog.filter { !it.isCustom }
    val customDef = catalog.firstOrNull { it.isCustom }

    // Phase 44/45 (spec §20 — "The selected facility type must control the default appliance list
    // in BOTH ESTIMATE and SIMULATION"): reorders (never hides) the same full catalog so the loads
    // typical for the chosen facility appear first, under their own header — everything still
    // starts at quantity 0 per §1's "Do NOT force facility assumptions on the user," this only
    // changes which rows the installer sees first. No facility chosen (or a Custom facility name)
    // falls back to the plain full-catalog order, unchanged from before Phase 44.
    val facilityLoadIds = design.facility.commercialType
        ?.takeIf { it != com.lumix.estimator.domain.commercial.CommercialFacilityType.CUSTOM }
        ?.let { FacilityLoadLibrary.defaultLoadIdsFor(it) }
        ?: design.facility.industrialType
            ?.takeIf { it != com.lumix.estimator.domain.commercial.IndustrialFacilityType.CUSTOM }
            ?.let { FacilityLoadLibrary.defaultLoadIdsFor(it) }
        ?: emptyList()
    val typicalDefs = facilityLoadIds.mapNotNull { id -> allStandardDefs.firstOrNull { it.id == id } }
    val otherDefs = allStandardDefs.filter { it.id !in facilityLoadIds }

    SectionCard(title = "Loads") {
        Text(
            "Enter how many of each you have, how many hours/day they typically run, and roughly when. Everything starts at 0 (not included) — only loads with a quantity are counted.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )

        @Composable
        fun loadRow(def: com.lumix.estimator.domain.commercial.LoadDefinition) {
            val existing = design.loads.firstOrNull { it.definitionId == def.id }
            CatalogLoadRow(def, existing) { updated ->
                updateDesign {
                    val list = it.loads.toMutableList()
                    val existingIndex = list.indexOfFirst { l -> l.definitionId == def.id }
                    when {
                        updated == null && existingIndex >= 0 -> list.removeAt(existingIndex)
                        updated != null && existingIndex >= 0 -> list[existingIndex] = updated
                        updated != null -> list.add(updated)
                    }
                    it.copy(loads = list)
                }
            }
        }

        if (typicalDefs.isNotEmpty()) {
            Text(
                "Typical for ${design.facility.displayLabel}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
            typicalDefs.forEach { def ->
                loadRow(def)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
            Text(
                "Other load types",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        otherDefs.forEachIndexed { index, def ->
            loadRow(def)
            if (index < otherDefs.size - 1) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }

    if (customDef != null) {
        SectionCard(title = "Custom loads") {
            val customEntries = design.loads.withIndex().filter { (_, load) -> load.definitionId == customDef.id }
            if (customEntries.isEmpty()) {
                Text(
                    "Anything not in the list above — add it here with its own wattage.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary
                )
            }
            customEntries.forEach { (index, load) ->
                LoadRow(load, isAcLoad = false) { updated ->
                    updateDesign { it.copy(loads = it.loads.toMutableList().apply { this[index] = updated }) }
                }
                TextButton(onClick = { updateDesign { it.copy(loads = it.loads.toMutableList().apply { removeAt(index) }) } }) {
                    Text("Remove", color = palette.warningRedText)
                }
            }
            TextButton(onClick = { updateDesign { it.copy(loads = it.loads + newInstanceFrom(customDef)) } }) {
                Text("+ Add custom load")
            }
        }
    }
}

@Composable
private fun LoadRow(load: LoadInstance, isAcLoad: Boolean, onChange: (LoadInstance) -> Unit) {
    val palette = LocalLumixPalette.current
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(load.label, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntField(
                label = "Qty", value = load.quantity,
                onValueChange = { v -> onChange(load.copy(quantity = v.coerceAtLeast(1))) },
                modifier = Modifier.weight(1f)
            )
            if (isAcLoad) {
                NumberField(
                    label = "BTU", value = load.btu ?: (load.ratedWatts * COMMERCIAL_AC_BTU_PER_WATT), suffix = "BTU",
                    onValueChange = { v -> onChange(load.copy(btu = v, ratedWatts = v / COMMERCIAL_AC_BTU_PER_WATT)) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                NumberField(
                    label = "Watts", value = load.ratedWatts, suffix = "W",
                    onValueChange = { v -> onChange(load.copy(ratedWatts = v)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                label = "Power factor", value = load.powerFactor,
                onValueChange = { v -> onChange(load.copy(powerFactor = v.coerceIn(0.01, 1.0))) },
                modifier = Modifier.weight(1f)
            )
            NumberField(
                label = "Duty cycle", value = load.dutyCycleFraction,
                onValueChange = { v -> onChange(load.copy(dutyCycleFraction = v.coerceIn(0.0, 1.0))) },
                modifier = Modifier.weight(1f)
            )
        }
        // Phase 36 ("I should be able to choose when to when just like residential"): explicit
        // Starts/Ends clock times, same as CatalogLoadRow's own fix — operatingHoursPerDay is
        // derived from the two endpoints, never typed directly.
        val loadStartHour = load.typicalStartHour ?: 0.0
        val loadEndHour = (loadStartHour + load.operatingHoursPerDay).mod(24.0)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            TimeField(
                label = "Starts", hour = loadStartHour,
                onChange = { v -> onChange(load.copy(typicalStartHour = v, operatingHoursPerDay = hoursBetweenWrapping(v, loadEndHour))) },
                modifier = Modifier.weight(1f)
            )
            TimeField(
                label = "Ends", hour = loadEndHour,
                onChange = { v -> onChange(load.copy(operatingHoursPerDay = hoursBetweenWrapping(loadStartHour, v))) },
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            "%.1fh/day".format(load.operatingHoursPerDay),
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )
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
