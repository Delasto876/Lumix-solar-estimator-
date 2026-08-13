package com.lumix.estimator.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.data.PriceRepository
import com.lumix.estimator.data.QuoteRepository
import com.lumix.estimator.data.SettingsRepository
import com.lumix.estimator.data.ThemeMode
import com.lumix.estimator.domain.PriceFields
import com.lumix.estimator.domain.PriceList
import com.lumix.estimator.domain.SavingsCalculator
import com.lumix.estimator.domain.simulation.SimulationEngine
import com.lumix.estimator.ui.components.LargeTitleTopBar
import com.lumix.estimator.ui.components.LumixSecondaryButton
import com.lumix.estimator.ui.components.NumberField
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixRadius
import kotlinx.coroutines.launch

/**
 * The Settings tab: everything about the app itself rather than any one quote — appearance,
 * simulation defaults, the price list every quote is calculated from, and data management.
 * Replaces the old Profile tab, which was really just the price editor under a different name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    priceRepository: PriceRepository,
    settingsRepository: SettingsRepository,
    quoteRepository: QuoteRepository
) {
    val palette = LocalLumixPalette.current
    val scope = rememberCoroutineScope()

    val themeMode by settingsRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val defaultTechnicalMode by settingsRepository.defaultTechnicalMode.collectAsState(initial = false)
    val defaultGridServiceAmps by settingsRepository.defaultGridServiceAmps.collectAsState(initial = SimulationEngine.DEFAULT_GRID_SERVICE_AMPS)
    val billEscalationRate by settingsRepository.billEscalationRate.collectAsState(initial = SavingsCalculator.BILL_ESCALATION_RATE)
    val panelDegradationRate by settingsRepository.panelDegradationRate.collectAsState(initial = SavingsCalculator.PANEL_DEGRADATION_RATE)
    val billEscalationRatePercent = billEscalationRate * 100.0
    val panelDegradationRatePercent = panelDegradationRate * 100.0

    var editingDiscount by remember { mutableStateOf(false) }
    val regularPrices by priceRepository.regularPrices.collectAsState(initial = PriceList.DEFAULT)
    val discountPrices by priceRepository.discountPrices.collectAsState(initial = PriceList.DEFAULT)
    val currentPrices = if (editingDiscount) discountPrices else regularPrices

    var showClearHistoryConfirm by remember { mutableStateOf(false) }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(modifier = Modifier.fillMaxSize()) {
        LargeTitleTopBar(title = "Settings", subtitle = "Appearance, defaults, pricing, and data")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp + navBarBottom),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionCard(title = "Appearance") {
                    SettingsRow(icon = Icons.Default.Contrast, title = "Theme", subtitle = "Follows the system unless overridden") {}
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = { scope.launch { settingsRepository.setThemeMode(mode) } },
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                                icon = {
                                    Icon(
                                        when (mode) {
                                            ThemeMode.SYSTEM -> Icons.Default.Contrast
                                            ThemeMode.LIGHT -> Icons.Default.LightMode
                                            ThemeMode.DARK -> Icons.Default.DarkMode
                                        },
                                        contentDescription = null
                                    )
                                }
                            ) {
                                Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Simulation defaults") {
                    SettingsRow(
                        icon = Icons.Default.Speed,
                        title = "Technical mode by default",
                        subtitle = "Open the digital twin with full technical readouts showing",
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Switch(
                            checked = defaultTechnicalMode,
                            onCheckedChange = { scope.launch { settingsRepository.setDefaultTechnicalMode(it) } },
                            colors = SwitchDefaults.colors(checkedTrackColor = palette.solarYellow)
                        )
                    }
                    NumberField(
                        label = "Default grid service",
                        value = defaultGridServiceAmps,
                        onValueChange = { v -> scope.launch { settingsRepository.setDefaultGridServiceAmps(v.coerceIn(10.0, 200.0)) } },
                        allowDecimal = false,
                        suffix = "A",
                        supportingText = "The main-breaker rating new simulations start with — matches a typical Jamaican residential service unless changed."
                    )
                }
            }

            item {
                SectionCard(title = "Financial assumptions") {
                    Text(
                        "ESTIMATE — these two rates drive the 20-year savings projection on the Savings tab. They're assumptions, not measured or guaranteed figures.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                    NumberField(
                        label = "Annual bill escalation",
                        value = billEscalationRatePercent,
                        onValueChange = { v -> scope.launch { settingsRepository.setBillEscalationRate((v / 100.0).coerceIn(0.0, 0.30)) } },
                        suffix = "%/yr",
                        supportingText = "How much JPS-style electricity bills are assumed to climb each year."
                    )
                    NumberField(
                        label = "Annual panel degradation",
                        value = panelDegradationRatePercent,
                        onValueChange = { v -> scope.launch { settingsRepository.setPanelDegradationRate((v / 100.0).coerceIn(0.0, 5.0)) } },
                        suffix = "%/yr",
                        supportingText = "How much panel output is assumed to decline each year as they age."
                    )
                }
            }

            item {
                SectionCard(title = "Price list") {
                    Text(
                        "These prices feed every quote across the app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(false, true).forEachIndexed { index, isDiscount ->
                            SegmentedButton(
                                selected = editingDiscount == isDiscount,
                                onClick = { editingDiscount = isDiscount },
                                shape = SegmentedButtonDefaults.itemShape(index, 2)
                            ) {
                                Text(if (isDiscount) "Discount price list" else "Regular price list")
                            }
                        }
                    }
                }
            }

            PriceFields.groups.forEach { group ->
                item(key = "price_header_$group") {
                    Text(
                        group,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                items(PriceFields.all.filter { it.group == group }, key = { "price_${group}_${it.key}" }) { field ->
                    NumberField(
                        label = field.label,
                        value = field.get(currentPrices),
                        onValueChange = { v ->
                            val updated = field.set(currentPrices, v)
                            scope.launch {
                                if (editingDiscount) priceRepository.updateDiscount(updated) else priceRepository.updateRegular(updated)
                            }
                        },
                        suffix = "J$"
                    )
                }
            }

            item {
                LumixSecondaryButton(
                    text = "Reset ${if (editingDiscount) "discount" else "regular"} prices to default",
                    onClick = {
                        scope.launch {
                            if (editingDiscount) priceRepository.resetDiscountToDefault() else priceRepository.resetRegularToDefault()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                SectionCard(title = "Data") {
                    SettingsRow(
                        icon = Icons.Default.DeleteSweep,
                        title = "Clear quote history",
                        subtitle = "Permanently deletes every saved quote from this device"
                    ) {}
                    LumixSecondaryButton(
                        text = "Clear quote history",
                        onClick = { showClearHistoryConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Text(
                    "Lumix Solar Estimator",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text("Clear quote history?") },
            text = { Text("This permanently deletes every saved quote from this device. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { quoteRepository.clearAll() }
                    showClearHistoryConfirm = false
                }) {
                    Text("Clear", color = palette.warningRedText)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

/** A One UI-style settings list row: a leading icon in a soft rounded backdrop, title/subtitle, trailing control. */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit
) {
    val palette = LocalLumixPalette.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(LumixRadius.sm))
                .background(palette.glass),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = palette.solarYellowText)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
        }
        trailing()
    }
}
