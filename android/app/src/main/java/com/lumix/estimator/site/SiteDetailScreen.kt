package com.lumix.estimator.site

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.ui.theme.LocalLumixPalette

/**
 * Read-only view of a saved [SolarSite]: every roof plane's full [SolarPotentialCard]. Reached
 * by tapping a saved site from [SolarSiteEntryScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteDetailScreen(
    viewModel: SolarSiteViewModel,
    siteId: String,
    onUseRoof: ((RoofPlane) -> Unit)? = null,
    onBack: () -> Unit
) {
    val palette = LocalLumixPalette.current
    // 2026-08-18 audit fix: viewModel.getSite(id) reads a synchronous, non-reactive cache that's
    // only populated once the ViewModel's own Room Flow collection has emitted at least once —
    // on a cold app start restored directly to this screen (e.g. after Android kills the process
    // and the user returns via Recents), reading it here as a plain function call could return
    // null before that first emission arrives, and — because nothing this composable reads was
    // reactive — the screen would recompose. Deriving from the already-reactive savedSites
    // StateFlow instead means this screen updates the instant the real data arrives, same as
    // SolarSiteEntryScreen's own list already does.
    val savedSites by viewModel.savedSites.collectAsState()
    val site = savedSites.find { it.id == siteId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        site?.name ?: site?.address ?: "Solar Site",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Text("×", style = MaterialTheme.typography.titleLarge) } }
            )
        }
    ) { padding ->
        if (site == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Site not found.", color = palette.textSecondary)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "%.4f, %.4f".format(site.latitude, site.longitude),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                    Text(
                        "${site.roofPlanes.size} roof plane(s) · %.2f kW total potential".format(site.totalCapacityKw),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textPrimary
                    )
                }
            }

            if (site.roofPlanes.isEmpty()) {
                item {
                    Text("No roof planes traced for this site yet.", color = palette.textSecondary)
                }
            } else {
                items(site.roofPlanes) { plane ->
                    SolarPotentialCard(
                        plane = plane,
                        latitude = site.latitude,
                        longitude = site.longitude,
                        onUseThisRoof = onUseRoof?.let { callback -> { callback(plane) } }
                    )
                }
            }
        }
    }
}
