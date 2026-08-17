package com.lumix.estimator.site

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Site tab's landing screen: previously saved sites, plus a choice of how to start a new
 * one. Map and manual entry are shown as equal peer options — manual is not hidden behind a
 * "no GPS?" fallback, since some installers already have coordinates and dimensions in hand
 * and the map itself needs a configured API key this build may not have yet.
 */
@Composable
fun SolarSiteEntryScreen(
    viewModel: SolarSiteViewModel,
    onOpenMap: () -> Unit,
    onOpenManual: () -> Unit,
    onOpenSite: (String) -> Unit
) {
    val palette = LocalLumixPalette.current
    val sites by viewModel.savedSites.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Solar Site", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                Text(
                    "Map a customer's roof and turn it into a real system estimate.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                EntryOptionCard(
                    icon = Icons.Default.Map,
                    title = "Use Satellite Map",
                    description = "Search the address, zoom to the roof, and trace it directly.",
                    onClick = onOpenMap,
                    modifier = Modifier.weight(1f)
                )
                EntryOptionCard(
                    icon = Icons.Default.LocationOn,
                    title = "Enter Manually",
                    description = "Type in coordinates and roof dimensions — no map needed.",
                    onClick = onOpenManual,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (sites.isNotEmpty()) {
            item {
                Text("Saved Sites", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            }
            items(sites.sortedByDescending { it.timestampMillis }) { site ->
                SavedSiteRow(site = site, onClick = { onOpenSite(site.id) })
            }
        } else {
            item {
                Text(
                    "No sites yet — start with either option above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
            }
        }
    }
}

@Composable
private fun EntryOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLumixPalette.current
    SectionCard(title = "", modifier = modifier.clickable(onClick = onClick)) {
        Icon(icon, contentDescription = null, tint = palette.solarYellowText, modifier = Modifier.size(28.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = palette.textPrimary)
        Text(description, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
    }
}

@Composable
private fun SavedSiteRow(site: SolarSite, onClick: () -> Unit) {
    val palette = LocalLumixPalette.current
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }
    SectionCard(title = "", modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    site.name ?: site.address ?: "%.4f, %.4f".format(site.latitude, site.longitude),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
                Text(
                    "${site.roofPlanes.size} roof plane(s) · %.2f kW · ${dateFormat.format(Date(site.timestampMillis))}".format(site.totalCapacityKw),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary
                )
            }
        }
    }
}
