package com.lumix.estimator.ui.history

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.data.QuoteEntity
import com.lumix.estimator.data.QuoteRepository
import com.lumix.estimator.domain.formatCurrency
import com.lumix.estimator.ui.components.LumixPrimaryButton
import com.lumix.estimator.ui.components.SurfaceCard
import com.lumix.estimator.ui.theme.LocalLumixPalette
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    quoteRepository: QuoteRepository,
    onOpenQuote: (Long) -> Unit,
    onStartQuote: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val palette = LocalLumixPalette.current
    val quotes by quoteRepository.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Systems") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Text("×", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (quotes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Your energy story starts here.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Text(
                        "Every system you size gets saved here, so you can revisit or share it later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
                    )
                    LumixPrimaryButton(text = "Start your estimate", onClick = onStartQuote)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(quotes, key = { it.id }) { quote ->
                QuoteRow(
                    quote = quote,
                    onClick = { onOpenQuote(quote.id) },
                    onDelete = { scope.launch { quoteRepository.delete(quote) } }
                )
            }
        }
    }
}

@Composable
private fun QuoteRow(quote: QuoteEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val palette = LocalLumixPalette.current
    SurfaceCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val title = quote.customerName.ifBlank { "Unnamed customer" }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Text(
                    listOfNotNull(quote.nearestTown.ifBlank { null }, quote.parish.ifBlank { null }).joinToString(", ")
                        .ifBlank { "No location" },
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary
                )
                Text(dateFormat.format(Date(quote.timestamp)), style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                Text("${quote.panelCount} panels · ${quote.inverterName}", style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
                Text(formatCurrency(quote.grandTotal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.solarYellowText)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete quote", tint = palette.textSecondary)
            }
        }
    }
}
