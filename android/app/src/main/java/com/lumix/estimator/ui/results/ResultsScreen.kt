package com.lumix.estimator.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumix.estimator.data.QuoteRepository
import com.lumix.estimator.data.SavedQuote
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteResult
import com.lumix.estimator.domain.SavingsCalculator
import com.lumix.estimator.domain.SystemMode
import com.lumix.estimator.domain.formatCurrency
import com.lumix.estimator.domain.formatQty
import com.lumix.estimator.pdf.QuotePdfGenerator
import com.lumix.estimator.ui.components.AnimatedCounterText
import com.lumix.estimator.ui.components.EnergyFlowDiagram
import com.lumix.estimator.ui.components.FlowNode
import com.lumix.estimator.ui.components.LumixPrimaryButton
import com.lumix.estimator.ui.components.LumixSecondaryButton
import com.lumix.estimator.ui.components.RingGauge
import com.lumix.estimator.ui.components.RoofPanelVisualization
import com.lumix.estimator.ui.components.SavingsGraph
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixColors
import com.lumix.estimator.ui.theme.numberDisplayStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    quoteId: Long,
    quoteRepository: QuoteRepository,
    onNewQuote: () -> Unit,
    onBackToHome: () -> Unit,
    onSimulate: (Long) -> Unit
) {
    var saved by remember(quoteId) { mutableStateOf<SavedQuote?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSharing by remember { mutableStateOf(false) }
    var selectedNode by remember { mutableStateOf<FlowNode?>(null) }
    val palette = LocalLumixPalette.current

    LaunchedEffect(quoteId) {
        saved = quoteRepository.getSavedQuote(quoteId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Solar Recommendation") },
                navigationIcon = {
                    IconButton(onClick = onBackToHome) {
                        Text("×", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        },
        bottomBar = {
            val current = saved
            if (current != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LumixSecondaryButton(text = "New quote", onClick = onNewQuote, modifier = Modifier.weight(1f))
                    LumixPrimaryButton(
                        text = if (isSharing) "Preparing…" else "Share PDF",
                        enabled = !isSharing,
                        onClick = {
                            isSharing = true
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    QuotePdfGenerator.generate(context, current.inputs, current.result, current.timestamp)
                                }
                                isSharing = false
                                context.startActivity(
                                    android.content.Intent.createChooser(
                                        QuotePdfGenerator.shareIntent(context, file),
                                        "Share quote PDF"
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { padding ->
        val current = saved
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val inputs = current.inputs
        val result = current.result
        val projection = remember(current) { SavingsCalculator.project(inputs, result) }
        val nodes = remember(result) { buildFlowNodes(result, inputs, projection.coveragePercent) }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SectionCard(title = "") {
                        Text(
                            "☀️ SOLAR RECOMMENDATION",
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.solarYellowText,
                            fontWeight = FontWeight.Bold
                        )
                        AnimatedCounterText(
                            targetValue = result.pvKw,
                            format = { "%.1f kW".format(it) },
                            style = numberDisplayStyle(size = 46.sp),
                            color = palette.textPrimary
                        )
                        Text(
                            "Recommended solar system for ${inputs.propertyType.label.lowercase()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Estimated System", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("${result.panelCount} × ${result.panelWatts}W Panels", style = MaterialTheme.typography.bodyMedium)
                        Text(result.inverterName, style = MaterialTheme.typography.bodyMedium)
                        if (result.totalBatteryKwh > 0) {
                            Text("${"%.1f".format(result.totalBatteryKwh)} kWh Battery", style = MaterialTheme.typography.bodyMedium)
                        }
                        LumixPrimaryButton(
                            text = "⚡ Explore Your Energy",
                            onClick = { onSimulate(quoteId) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }

                if (result.isRoofConstrained) {
                    item {
                        RoofConstraintBanner(inputs = inputs, result = result)
                    }
                }

                if (result.backupCapacityWarningKw != null) {
                    item {
                        BackupCapacityWarningBanner(warningKw = result.backupCapacityWarningKw, inverterKw = result.inverterKw)
                    }
                }

                item {
                    SectionCard(title = "Estimated Performance") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            PerformanceStat(
                                label = "☀️ Solar Production",
                                value = "${projection.monthlyProductionKwh.toInt()} kWh/mo",
                                modifier = Modifier.weight(1f)
                            )
                            PerformanceStat(
                                label = "💰 Monthly Savings",
                                value = formatCurrency(projection.monthlySavings),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                PerformanceStat(
                                    label = "📉 Est. Payback",
                                    value = projection.paybackYears?.let { "%.1f yrs".format(it) } ?: "—"
                                )
                            }
                            RingGauge(
                                percent = projection.coveragePercent,
                                diameter = 96.dp,
                                strokeWidth = 9.dp,
                                label = "coverage"
                            )
                        }
                    }
                }

                item {
                    SectionCard(title = "How the energy flows") {
                        EnergyFlowDiagram(nodes = nodes, onNodeClick = { selectedNode = it })
                    }
                }

                if (result.panelCount > 0) {
                    item {
                        SectionCard(title = "Your roof, at a glance") {
                            RoofPanelVisualization(panelCount = result.panelCount)
                            Text(
                                "${result.panelCount} panels across ${result.rows} row${if (result.rows == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary
                            )
                        }
                    }
                }

                item {
                    SectionCard(title = "20-year savings") {
                        SavingsGraph(yearly = projection.yearly)
                    }
                }

                item {
                    SectionCard(title = "Material Cost Breakdown") {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("Item", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text("Qty", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text("Subtotal", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider()
                        result.materials.forEach { m ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(m.name, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium)
                                Text(formatQty(m.qty), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(formatCurrency(m.subtotal), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        HorizontalDivider()
                        Text(
                            "Grand Total: ${formatCurrency(result.grandTotal)}",
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                    }
                }
            }
        }

        val node = selectedNode
        if (node != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedNode = null },
                sheetState = rememberModalBottomSheetState()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(node.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(node.subtitle, style = MaterialTheme.typography.bodyLarge, color = palette.textSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                    Text(node.detail, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun RoofConstraintBanner(inputs: QuoteInputs, result: QuoteResult) {
    val palette = LocalLumixPalette.current
    val roofLabel = inputs.roofConstraint?.roofLabel
    SectionCard(title = "", modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🏠", style = MaterialTheme.typography.titleMedium)
            Column {
                Text(
                    "Your roof limits the recommended system",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.solarAmberText
                )
                Text(
                    "Your electricity usage calls for about %.1f kW, but %s can physically fit about %.1f kW (%d panels). Showing the roof-constrained system below."
                        .format(result.energyOptimalPvKw, roofLabel ?: "your traced roof", result.pvKw, result.panelCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary
                )
                Text(
                    "Consider tracing additional roof area, adding a second roof plane, or ground mounting to close the gap.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary
                )
            }
        }
    }
}

@Composable
private fun BackupCapacityWarningBanner(warningKw: Double, inverterKw: Double) {
    val palette = LocalLumixPalette.current
    SectionCard(title = "", modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("⚠️", style = MaterialTheme.typography.titleMedium)
            Column {
                Text(
                    "Backup load exceeds system capacity",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.warningRedText
                )
                Text(
                    "Backing up this much load needs about %.1f kW, but the selected inverter delivers %.1f kW."
                        .format(warningKw, inverterKw),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary
                )
                Text(
                    "Switch backup coverage to Critical Loads to back up only what this system can actually deliver, or consider a larger inverter.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary
                )
            }
        }
    }
}

@Composable
private fun PerformanceStat(label: String, value: String, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    Column(modifier = modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
    }
}

private fun buildFlowNodes(
    result: QuoteResult,
    inputs: QuoteInputs,
    coveragePercent: Float
): List<FlowNode> {
    val palette = LumixColors
    val nodes = mutableListOf(
        FlowNode(
            id = "sun",
            title = "Sunlight",
            subtitle = "~${"%.1f".format(inputs.peakSunHours)} peak sun hours/day",
            glyph = "☀️",
            accentColor = palette.SolarYellow,
            detail = "Panel and battery sizing throughout this quote is based on ${"%.1f".format(inputs.peakSunHours)} peak sun hours/day for this site — editable in the Energy step, not a measured value."
        ),
        FlowNode(
            id = "panels",
            title = "${result.panelCount} × ${result.panelWatts}W Panels",
            subtitle = "${"%.2f".format(result.pvKw)} kW array",
            glyph = "🟦",
            accentColor = palette.TechnicalCyan,
            detail = "Sunlight hits the array and is converted to DC electricity. This system uses ${result.panelCount} panels across ${result.rows} row(s)."
        ),
        FlowNode(
            id = "inverter",
            title = result.inverterName,
            subtitle = "${result.inverterKw} kW capacity",
            glyph = "⚡",
            accentColor = palette.TechnicalCyan,
            detail = "The inverter converts the panels' DC output into the AC electricity your home actually uses, sized above your estimated peak load with headroom."
        )
    )
    if (result.totalBatteryKwh > 0) {
        nodes += FlowNode(
            id = "battery",
            title = result.batteryName ?: "Battery bank",
            subtitle = "${"%.1f".format(result.totalBatteryKwh)} kWh installed",
            glyph = "🔋",
            accentColor = palette.EnergyGreen,
            detail = "Estimated backup: roughly ${inputs.backupHours.toInt()} hours, depending on which loads stay on during an outage."
        )
    }
    nodes += FlowNode(
        id = "home",
        title = "Your Home",
        subtitle = "${coveragePercent.toInt()}% of usage covered",
        glyph = "🏠",
        accentColor = palette.SolarAmber,
        detail = if (result.effectiveSystemMode == SystemMode.GRIDTIE) {
            "Any shortfall is drawn from the grid; any surplus flows back to it, depending on your JPS billing arrangement."
        } else {
            "Essential loads draw from solar first, then battery, then the grid — keeping the lights on even when JPS power is out."
        }
    )
    return nodes
}
