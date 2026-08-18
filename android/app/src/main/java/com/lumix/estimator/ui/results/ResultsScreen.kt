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
import androidx.compose.runtime.collectAsState
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
import com.lumix.estimator.data.QuoteRepository
import com.lumix.estimator.data.SavedQuote
import com.lumix.estimator.data.SettingsRepository
import com.lumix.estimator.domain.BackupCoverage
import com.lumix.estimator.domain.BusinessInfo
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteResult
import com.lumix.estimator.domain.SavingsCalculator
import com.lumix.estimator.domain.SystemMode
import com.lumix.estimator.domain.formatCurrency
import com.lumix.estimator.domain.formatQty
import com.lumix.estimator.domain.quoteNumberFor
import com.lumix.estimator.export.QuoteCsvGenerator
import com.lumix.estimator.export.QuoteHtmlGenerator
import com.lumix.estimator.pdf.QuotePdfGenerator
import com.lumix.estimator.ui.components.EnergyFlowDiagram
import com.lumix.estimator.ui.components.FlowNode
import com.lumix.estimator.ui.components.LumixPrimaryButton
import com.lumix.estimator.ui.components.LumixSecondaryButton
import com.lumix.estimator.ui.components.RingGauge
import com.lumix.estimator.ui.components.SavingsGraph
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    quoteId: Long,
    quoteRepository: QuoteRepository,
    settingsRepository: SettingsRepository,
    onNewQuote: () -> Unit,
    onBackToHome: () -> Unit,
    onSimulate: (Long) -> Unit,
    /** A76 (spec Phase 13): loads this exact saved quote's real inputs back into the wizard for editing — see `WizardViewModel.loadForEdit`'s own doc. */
    onEditSystem: (SavedQuote) -> Unit
) {
    var saved by remember(quoteId) { mutableStateOf<SavedQuote?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSharing by remember { mutableStateOf(false) }
    var sharingFormat by remember { mutableStateOf<String?>(null) }
    var selectedNode by remember { mutableStateOf<FlowNode?>(null) }
    val palette = LocalLumixPalette.current

    // A79 (spec Phase 16): the installer's own real business details, entered in Settings — see
    // BusinessInfo's own doc for why every field defaults blank and each export section only
    // renders once non-blank.
    val companyName by settingsRepository.companyName.collectAsState(initial = "")
    val companyAddress by settingsRepository.companyAddress.collectAsState(initial = "")
    val companyPhone by settingsRepository.companyPhone.collectAsState(initial = "")
    val companyEmail by settingsRepository.companyEmail.collectAsState(initial = "")
    val defaultWarranty by settingsRepository.defaultWarranty.collectAsState(initial = "")
    val paymentTerms by settingsRepository.paymentTerms.collectAsState(initial = "")
    val business = BusinessInfo(companyName, companyAddress, companyPhone, companyEmail, defaultWarranty, paymentTerms)

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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LumixSecondaryButton(text = "New quote", onClick = onNewQuote, modifier = Modifier.weight(1f))
                        LumixPrimaryButton(
                            text = if (isSharing && sharingFormat == "pdf") "Preparing…" else "Share PDF",
                            enabled = !isSharing,
                            onClick = {
                                isSharing = true
                                sharingFormat = "pdf"
                                scope.launch {
                                    val file = withContext(Dispatchers.IO) {
                                        QuotePdfGenerator.generate(context, quoteId, current.inputs, current.result, current.timestamp, business)
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
                    // A78 (spec Phase 15, §38 "Allow: PDF, HTML, CSV where appropriate") — the
                    // same already-computed QuoteResult, two more export formats.
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LumixSecondaryButton(
                            text = if (isSharing && sharingFormat == "html") "Preparing…" else "Share HTML",
                            enabled = !isSharing,
                            onClick = {
                                isSharing = true
                                sharingFormat = "html"
                                scope.launch {
                                    val file = withContext(Dispatchers.IO) {
                                        QuoteHtmlGenerator.generate(context, quoteId, current.inputs, current.result, current.timestamp, business)
                                    }
                                    isSharing = false
                                    context.startActivity(
                                        android.content.Intent.createChooser(
                                            QuoteHtmlGenerator.shareIntent(context, file),
                                            "Share quote HTML"
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        LumixSecondaryButton(
                            text = if (isSharing && sharingFormat == "csv") "Preparing…" else "Share CSV",
                            enabled = !isSharing,
                            onClick = {
                                isSharing = true
                                sharingFormat = "csv"
                                scope.launch {
                                    val file = withContext(Dispatchers.IO) {
                                        QuoteCsvGenerator.generate(context, quoteId, current.inputs, current.result, current.timestamp, business)
                                    }
                                    isSharing = false
                                    context.startActivity(
                                        android.content.Intent.createChooser(
                                            QuoteCsvGenerator.shareIntent(context, file),
                                            "Share quote CSV"
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
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
        val dailySolarKwh = result.pvKw * inputs.peakSunHours
        // A54: result.estimatedBackupHours comes from BackupEstimator's real grid-disconnected
        // simulation (computed once, in SystemCalculator.calculate) — the same figure System
        // Review and the PDF show, not a separately recomputed ratio.
        val backupMeetsTarget = result.estimatedBackupSufficient || result.estimatedBackupHours >= inputs.backupHours - 0.5
        val installationCost = result.serviceCharge + result.deliveryCharge

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
                        Text(
                            "Recommended solar system for ${inputs.propertyType.label.lowercase()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                        Text(
                            quoteNumberFor(quoteId),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                item {
                    SectionCard(title = "System") {
                        StatRow("PV array", "%.2f kWp".format(result.pvKw), "${result.panelCount} × ${result.panelWatts}W panels")
                        StatRow("Inverter", "%.1f kW".format(result.inverterKw), result.inverterName)
                        if (result.totalBatteryKwh > 0) {
                            StatRow("Battery", "%.1f kWh".format(result.totalBatteryKwh), result.batteryName ?: "")
                        }
                        LumixPrimaryButton(
                            text = "⚡ Explore Your Energy",
                            onClick = { onSimulate(quoteId) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        LumixSecondaryButton(
                            text = "✏️ Edit System",
                            onClick = { onEditSystem(current) },
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
                    SectionCard(title = "Performance") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            PerformanceStat(
                                label = "☀️ Daily Solar",
                                value = "%.1f kWh".format(dailySolarKwh),
                                modifier = Modifier.weight(1f)
                            )
                            PerformanceStat(
                                label = "📅 Annual Solar",
                                value = "%.1f MWh".format(dailySolarKwh * 365 / 1000.0),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            PerformanceStat(
                                label = "💰 Monthly Savings",
                                value = formatCurrency(projection.monthlySavings),
                                modifier = Modifier.weight(1f)
                            )
                            PerformanceStat(
                                label = "📉 Est. Payback",
                                value = projection.paybackYears?.let { "%.1f yrs".format(it) } ?: "—",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            RingGauge(
                                percent = projection.coveragePercent,
                                diameter = 96.dp,
                                strokeWidth = 9.dp,
                                label = "coverage"
                            )
                        }
                    }
                }

                if (result.totalBatteryKwh > 0) {
                    item {
                        SectionCard(title = "Backup") {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                PerformanceStat(label = "Target", value = "%.0f hrs".format(inputs.backupHours), modifier = Modifier.weight(1f))
                                PerformanceStat(
                                    label = "Estimated",
                                    value = if (result.estimatedBackupSufficient) "%.0f+ hrs".format(result.estimatedBackupHours) else "%.1f hrs".format(result.estimatedBackupHours),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                if (backupMeetsTarget) "✓ Meets backup target" else "⚠ Needs more battery to meet target",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (backupMeetsTarget) palette.energyGreenText else palette.warningRedText,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            if (result.estimatedBackupReason.isNotBlank()) {
                                Text(
                                    result.estimatedBackupReason,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textSecondary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    SectionCard(title = "How the energy flows") {
                        EnergyFlowDiagram(nodes = nodes, onNodeClick = { selectedNode = it })
                    }
                }

                item {
                    SectionCard(title = "20-year savings") {
                        SavingsGraph(yearly = projection.yearly)
                    }
                }

                item {
                    // A78 (spec Phase 15, §39 "Show: Original subtotal, Discount, Final subtotal,
                    // Tax/fees, Grand total"): Subtotal/Tax rows now read the same
                    // subtotalBeforeDiscount/taxAmount fields the PDF/HTML/CSV exports use — no
                    // separate re-addition of materialsTotal+installationCost here.
                    SectionCard(title = "Cost") {
                        StatRow("Equipment", formatCurrency(result.materialsTotal), null)
                        StatRow("Installation", formatCurrency(installationCost), null)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        StatRow("Subtotal", formatCurrency(result.subtotalBeforeDiscount), null)
                        if (result.discountAmount > 0) {
                            StatRow("Discount", "-${formatCurrency(result.discountAmount)}", null)
                        }
                        if (result.taxAmount > 0) {
                            StatRow("Tax/fees", formatCurrency(result.taxAmount), null)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        StatRow("TOTAL", formatCurrency(result.grandTotal), null, emphasize = true)
                    }
                }

                item {
                    SectionCard(title = "Material Breakdown") {
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
                                // A89/Ph21: never show a missing price as "$0.00" — see MaterialLine.unitPrice's own doc.
                                if (m.hasPrice) {
                                    Text(formatCurrency(m.subtotal), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    Text("Price not entered", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        if (!result.canFinalize) {
                            Text(
                                "This quote cannot be finalized until every price above is entered.",
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
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

/** A81 (Phase 18, restored): shown only when a Solar Site roof cap actually reduced the recommended array below what electricity usage alone called for. */
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

/** A single clean "label — value (+ optional detail)" row, for the System/Cost summary cards. */
@Composable
private fun StatRow(label: String, value: String, detail: String?, emphasize: Boolean = false) {
    val palette = LocalLumixPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                label,
                style = if (emphasize) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
                color = if (emphasize) palette.textPrimary else palette.textSecondary
            )
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
        }
        Text(
            value,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary
        )
    }
}

private fun backupCoverageLabel(coverage: BackupCoverage): String = when (coverage) {
    BackupCoverage.ESSENTIALS, BackupCoverage.CRITICAL_LOADS -> "critical loads only"
    BackupCoverage.FULL, BackupCoverage.MOST_LOAD -> "most household load"
    BackupCoverage.CUSTOM -> "the selected custom load"
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
            detail = if (result.estimatedBackupSufficient) {
                "Estimated backup: ${result.estimatedBackupHours.toInt()}+ hours in simulation, covering ${backupCoverageLabel(inputs.backupCoverage)}."
            } else {
                "Estimated backup: roughly ${"%.1f".format(result.estimatedBackupHours)} hours in simulation, covering ${backupCoverageLabel(inputs.backupCoverage)}."
            }
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
