package com.lumix.estimator.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.lumix.estimator.data.QuoteRepository
import com.lumix.estimator.data.SavedQuote
import com.lumix.estimator.domain.QuoteMode
import com.lumix.estimator.domain.SystemMode
import com.lumix.estimator.domain.formatCurrency
import com.lumix.estimator.domain.formatQty
import com.lumix.estimator.pdf.QuotePdfGenerator
import com.lumix.estimator.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    quoteId: Long,
    quoteRepository: QuoteRepository,
    onNewQuote: () -> Unit,
    onBackToHome: () -> Unit
) {
    var saved by remember(quoteId) { mutableStateOf<SavedQuote?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSharing by remember { mutableStateOf(false) }

    LaunchedEffect(quoteId) {
        saved = quoteRepository.getSavedQuote(quoteId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quote Summary") },
                navigationIcon = {
                    IconButton(onClick = onBackToHome) {
                        Text("×", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
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

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SectionCard(title = "System Summary") {
                        val modeLabel = when (inputs.quoteMode) {
                            QuoteMode.GUIDED -> "Guided system quote"
                            QuoteMode.MANUAL -> "Manual system builder"
                            QuoteMode.LOAD -> "Load & inverter sizing"
                        }
                        val systemLabel = when (result.effectiveSystemMode) {
                            SystemMode.HYBRID -> "hybrid"
                            SystemMode.OFFGRID -> "off-grid"
                            SystemMode.GRIDTIE -> "grid-tie"
                        }
                        val location = listOf(inputs.nearestTown, inputs.parish).filter { it.isNotBlank() }.joinToString(", ")
                            .ifBlank { "Location not set" }

                        Text("Mode: $modeLabel")
                        Text("System: $systemLabel for ${inputs.propertyType.label} at $location")
                        Text("PV array: ${result.panelCount} x ${result.panelWatts} W (${"%.2f".format(result.pvKw)} kW)")
                        Text("Inverter: ${result.inverterName}")
                        if (result.totalBatteryKwh > 0) {
                            Text("Battery: ${"%.1f".format(result.totalBatteryKwh)} kWh (~backup ${"%.0f".format(inputs.backupHours)}h at 80% DOD)")
                        } else {
                            Text("Configuration shown without battery bank.")
                        }
                        HorizontalDivider()
                        Text(
                            "Estimated total: ${formatCurrency(result.grandTotal)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Materials ${formatCurrency(result.materialsTotal)} + Service (15%) ${formatCurrency(result.serviceCharge)} " +
                                "+ Delivery ${formatCurrency(result.deliveryCharge)} - Discount ${formatCurrency(result.discountAmount)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    SectionCard(title = "Key Sizing") {
                        Text("Design daily energy: ${"%.1f".format(result.designDailyKwh)} kWh/day")
                        Text("Peak load (approx): ${"%.0f".format(result.peakWatts)} W")
                        Text("Battery required (theoretical): ${"%.1f".format(result.batteryRequiredKwh)} kWh; installed: ${"%.1f".format(result.totalBatteryKwh)} kWh")
                        Text("Rows (4 panels/row): ${result.rows}, rails/row: ${result.railsPerRow}, total rails: ${result.totalRails}")
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
                        Text("Grand Total: ${formatCurrency(result.grandTotal)}", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onNewQuote, modifier = Modifier.weight(1f)) {
                    Text("New quote")
                }
                Button(
                    onClick = {
                        isSharing = true
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                QuotePdfGenerator.generate(context, inputs, result, current.timestamp)
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
                    enabled = !isSharing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isSharing) "Preparing..." else "Share PDF")
                }
            }
        }
    }
}
