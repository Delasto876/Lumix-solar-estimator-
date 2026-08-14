package com.lumix.estimator.ui.components

import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixRadius

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    SurfaceCard(modifier = modifier, shape = RoundedCornerShape(LumixRadius.lg), accentColor = accentColor) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (title.isNotBlank()) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalLumixPalette.current.textPrimary
                )
            }
            content()
        }
    }
}

/**
 * A61 (spec §10/12 — "collapsible/tabbed Settings"): a [SectionCard] whose body is hidden
 * behind a tappable header + chevron, same expand/collapse pattern A58's "WHY WAS THIS SYSTEM
 * SELECTED?" panel already established (`SystemResultScreen.kt`'s `DiagnosticsSection`) — reused
 * here rather than reinvented so the app has one collapsible-section idiom, not two.
 */
@Composable
fun CollapsibleSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalLumixPalette.current
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    SurfaceCard(modifier = modifier, shape = RoundedCornerShape(LumixRadius.lg)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                    }
                }
                Text(if (expanded) "▾" else "▸", style = MaterialTheme.typography.titleMedium, color = palette.textSecondary)
            }
            AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    content()
                }
            }
        }
    }
}

/**
 * A lighter-weight collapsible row for grouping content *inside* an already-expanded
 * [CollapsibleSectionCard] — e.g. one PV/battery/wiring category inside the Materials & Pricing
 * section — without stacking a second full card surface inside the first.
 */
@Composable
fun CollapsibleGroup(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalLumixPalette.current
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            Text(if (expanded) "▾" else "▸", style = MaterialTheme.typography.labelLarge, color = palette.textSecondary)
        }
        AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun lumixFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalLumixPalette.current.solarYellow,
    unfocusedBorderColor = LocalLumixPalette.current.outline,
    focusedLabelColor = LocalLumixPalette.current.solarYellowText,
    unfocusedLabelColor = LocalLumixPalette.current.textSecondary,
    cursorColor = LocalLumixPalette.current.solarYellow,
    focusedTextColor = LocalLumixPalette.current.textPrimary,
    unfocusedTextColor = LocalLumixPalette.current.textPrimary,
    focusedSupportingTextColor = LocalLumixPalette.current.textSecondary,
    unfocusedSupportingTextColor = LocalLumixPalette.current.textSecondary
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> LabeledDropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = supportingText?.let { { Text(it) } },
            shape = RoundedCornerShape(LumixRadius.sm),
            colors = lumixFieldColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )
        ExposedDropdownMenuDefaults.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun NumberField(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    allowDecimal: Boolean = true,
    allowNegative: Boolean = false,
    suffix: String? = null,
    isError: Boolean = false,
    supportingText: String? = null
) {
    var text by remember(value) { mutableStateOf(formatEditable(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val sanitized = input.filterIndexed { index, c ->
                c.isDigit() || (c == '.' && allowDecimal) || (c == '-' && allowNegative && index == 0)
            }
            text = sanitized
            onValueChange(sanitized.toDoubleOrNull() ?: 0.0)
        },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        shape = RoundedCornerShape(LumixRadius.sm),
        colors = lumixFieldColors(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun IntField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null
) {
    NumberField(
        label = label,
        value = value.toDouble(),
        onValueChange = { onValueChange(it.toInt()) },
        modifier = modifier,
        allowDecimal = false,
        supportingText = supportingText
    )
}

private fun formatEditable(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
