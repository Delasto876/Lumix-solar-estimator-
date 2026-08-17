package com.lumix.estimator.ui.estimate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.ui.components.LargeTitleTopBar
import com.lumix.estimator.ui.components.LumixPrimaryButton
import com.lumix.estimator.ui.components.LumixSecondaryButton
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette

/**
 * A81 (Phase 18, restored): [onOpenSite] is a new, optional entry into Solar Site's roof-tracing
 * flow. Deliberately NOT a new bottom-nav tab — A16 fixed a real label-clipping bug specific to
 * 6 tabs on this same `FloatingBottomNav` (still an even-width-per-tab layout with no scroll/
 * overflow handling), and re-adding a 6th tab would risk reintroducing that exact regression.
 * Placed here instead, next to the guided-quote entry point it's an alternative starting point
 * for. Defaults to a no-op so this screen's own preview/tests don't need a real nav callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstimateLandingScreen(onStartQuote: () -> Unit, onOpenSite: () -> Unit = {}) {
    val palette = LocalLumixPalette.current

    Scaffold(
        topBar = { LargeTitleTopBar(title = "Estimate") }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "A guided walkthrough of your site, usage, and backup needs — we'll size the panels, inverter, and battery for you.",
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textSecondary
            )

            SectionCard(title = "What you'll need") {
                BulletLine("Your average JPS bill, or a recent kWh reading")
                BulletLine("Roof type and how many storeys")
                BulletLine("Any AC units or heavy appliances")
                BulletLine("How long you'd like backup during an outage")
            }

            LumixPrimaryButton(
                text = "Start new quote",
                onClick = onStartQuote,
                modifier = Modifier.fillMaxWidth()
            )

            SectionCard(title = "Have a roof to trace?") {
                Text(
                    "Use the satellite map to trace your actual roof — panel count and layout come from real geometry, not just your electricity usage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
                LumixSecondaryButton(
                    text = "🛰️ Open Solar Site",
                    onClick = onOpenSite,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun BulletLine(text: String) {
    val palette = LocalLumixPalette.current
    Text(
        "•  $text",
        style = MaterialTheme.typography.bodyMedium,
        color = palette.textPrimary,
        fontWeight = FontWeight.Normal
    )
}
