package com.lumix.estimator.ui.wizard.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.ui.components.SectionCard

/** Only shown for zinc/metal roofs — folded into its own step so it's skipped entirely otherwise. */
@Composable
fun StepRoofMounting(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "Zinc roof rails") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Use center rail (3 rails per row)")
                    Text(
                        "Slab roofs always use 3 rails/row and expansion bolts, especially 550W+.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = inputs.zincCenterRail,
                    onCheckedChange = { v -> onUpdate { it.copy(zincCenterRail = v) } }
                )
            }
        }
    }
}
