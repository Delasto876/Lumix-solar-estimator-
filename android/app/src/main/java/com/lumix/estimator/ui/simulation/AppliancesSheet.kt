package com.lumix.estimator.ui.simulation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumix.estimator.domain.simulation.ApplianceRun
import com.lumix.estimator.domain.simulation.ApplianceState
import com.lumix.estimator.domain.simulation.SimApplianceType
import com.lumix.estimator.domain.simulation.totalApplianceLoadKwAt
import com.lumix.estimator.ui.components.AnimatedCounterText
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixRadius
import com.lumix.estimator.ui.theme.numberDisplayStyle

@Composable
fun AppliancesSheetContent(
    appliances: Map<SimApplianceType, ApplianceState>,
    currentHour: Double,
    onToggle: (SimApplianceType) -> Unit,
    onSetRuns: (SimApplianceType, List<ApplianceRun>) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLumixPalette.current
    val currentLoadKw = totalApplianceLoadKwAt(appliances, currentHour)

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
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
            "Each appliance can run all day, or you can set specific time blocks (e.g. 3 units by day, 3 more by night) for a more accurate simulation.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
            modifier = Modifier.padding(top = 6.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        SimApplianceType.entries.forEach { type ->
            val state = appliances[type] ?: ApplianceState()
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(type) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(type.label, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
                        Text(
                            if (state.enabled) "${type.watts} W each • ${state.totalQuantity} unit${if (state.totalQuantity == 1) "" else "s"}" else "${type.watts} W each",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary
                        )
                    }
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = { onToggle(type) },
                        colors = SwitchDefaults.colors(checkedTrackColor = palette.solarYellow)
                    )
                }

                if (state.enabled) {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp)) {
                        state.runs.forEachIndexed { index, run ->
                            ApplianceRunRow(
                                run = run,
                                canRemove = state.runs.size > 1,
                                onChange = { updated -> onSetRuns(type, state.runs.toMutableList().apply { this[index] = updated }) },
                                onRemove = { onSetRuns(type, state.runs.filterIndexed { i, _ -> i != index }) }
                            )
                            if (index < state.runs.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                            }
                        }
                        TextButton(onClick = { onSetRuns(type, state.runs + ApplianceRun(quantity = 1, startHour = 18.0, durationHours = 4.0)) }) {
                            Text("+ Add time block", color = palette.solarYellowText, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ApplianceRunRow(
    run: ApplianceRun,
    canRemove: Boolean,
    onChange: (ApplianceRun) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLumixPalette.current
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        StepperRow(
            label = "Quantity",
            value = run.quantity.toString(),
            onDecrement = { onChange(run.copy(quantity = (run.quantity - 1).coerceAtLeast(1))) },
            onIncrement = { onChange(run.copy(quantity = (run.quantity + 1).coerceAtMost(50))) }
        )
        StepperRow(
            label = "Starts",
            value = formatSimTime(run.startHour),
            onDecrement = { onChange(run.copy(startHour = (run.startHour - 0.5).mod(24.0))) },
            onIncrement = { onChange(run.copy(startHour = (run.startHour + 0.5).mod(24.0))) }
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Runs for", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniStepper(
                    value = "%.1fh".format(run.durationHours),
                    onDecrement = { onChange(run.copy(durationHours = (run.durationHours - 0.5).coerceIn(0.5, 24.0))) },
                    onIncrement = { onChange(run.copy(durationHours = (run.durationHours + 0.5).coerceIn(0.5, 24.0))) }
                )
                if (canRemove) {
                    Text(
                        "Remove",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.warningRedText,
                        modifier = Modifier.clickable { onRemove() }.padding(start = 14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StepperRow(label: String, value: String, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    val palette = LocalLumixPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        MiniStepper(value = value, onDecrement = onDecrement, onIncrement = onIncrement)
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
