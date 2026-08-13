package com.lumix.estimator.ui.simulation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.simulation.LoadAuditSummary
import com.lumix.estimator.ui.theme.LocalLumixPalette

/**
 * The headline load-audit numbers (spec §44) plus an optional "HOW WAS THIS CALCULATED?"
 * drill-down (spec §46) — the exact per-category kWh contributions that sum to [summary]'s own
 * [LoadAuditSummary.dailyEnergyKwh], so nothing shown here is a separate, potentially-disagreeing
 * estimate from what the digital twin and graph already display.
 */
@Composable
fun LoadAuditContent(summary: LoadAuditSummary, categoryBreakdown: Map<String, Double>, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    var showBreakdown by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Today's simulated load, summarized — the same underlying data as the graph above.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )

        AuditRow("Daily Energy", "%.1f kWh".format(summary.dailyEnergyKwh), "Average Load", "%.2f kW".format(summary.averageLoadKw))
        AuditRow(
            "Peak Load", "%.2f kW at %s".format(summary.peakLoadKw, formatSimTime(summary.peakLoadHour)),
            "Evening Peak (5-10pm)", "%.2f kW".format(summary.eveningPeakKw)
        )
        AuditRow("Base Load", "%.2f kW".format(summary.baseLoadKw), "Daytime Avg (8am-5pm)", "%.2f kW".format(summary.daytimeAvgLoadKw))
        AuditRow("Night Avg (10pm-6am)", "%.2f kW".format(summary.nightAvgLoadKw), null, null)

        if (summary.eveningPeakKw > summary.daytimeAvgLoadKw * 1.4 && summary.eveningPeakKw > 0.05) {
            Text(
                "Evening load runs well above the daytime average — typical for a Jamaican household, and worth sizing battery/backup coverage around.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.solarAmberText
            )
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth().clickable { showBreakdown = !showBreakdown },
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "HOW WAS THIS CALCULATED?",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
            Text(if (showBreakdown) "▾" else "▸", style = MaterialTheme.typography.titleMedium, color = palette.textSecondary)
        }

        AnimatedVisibility(visible = showBreakdown, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (categoryBreakdown.isEmpty()) {
                    Text("No appliances enabled yet.", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                } else {
                    categoryBreakdown.entries.sortedByDescending { it.value }.forEach { (category, kwh) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(category, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
                            Text("%.2f kWh".format(kwh), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total (appliances)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                        Text(
                            "%.2f kWh".format(categoryBreakdown.values.sum()),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.solarYellowText
                        )
                    }
                    Text(
                        "The difference from Daily Energy above is background/standby draw not tied to a specific scheduled appliance.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun AuditRow(label1: String, value1: String, label2: String?, value2: String?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AuditStat(label1, value1, Modifier.weight(1f))
        if (label2 != null && value2 != null) {
            AuditStat(label2, value2, Modifier.weight(1f))
        } else {
            Column(modifier = Modifier.weight(1f)) {}
        }
    }
}

@Composable
private fun AuditStat(label: String, value: String, modifier: Modifier = Modifier) {
    val palette = LocalLumixPalette.current
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = palette.textPrimary)
    }
}
