package com.lumix.estimator.ui.wizard.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.Catalog
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.ui.components.LabeledDropdown
import com.lumix.estimator.ui.components.SectionCard

/**
 * A56: reached only via CREATE QUOTE, after a system has already been designed and calculated —
 * this is no longer the wizard's first step (spec §5–9: quote/customer details are never a
 * prerequisite for sizing or simulating a system). Nothing here is required (matches the rest of
 * this lenient, mostly-optional wizard — parish is the one exception, kept required since
 * downstream delivery/PDF content depends on it).
 */
@Composable
fun StepCustomer(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "Customer") {
            OutlinedTextField(
                value = inputs.customerName,
                onValueChange = { v -> onUpdate { it.copy(customerName = v) } },
                label = { Text("Customer name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = inputs.customerContact,
                onValueChange = { v -> onUpdate { it.copy(customerContact = v) } },
                label = { Text("Phone") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = inputs.customerEmail,
                onValueChange = { v -> onUpdate { it.copy(customerEmail = v) } },
                label = { Text("Email (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = inputs.customerAddress,
                onValueChange = { v -> onUpdate { it.copy(customerAddress = v) } },
                label = { Text("Address (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SectionCard(title = "Site location") {
            LabeledDropdown(
                label = "Parish",
                options = Catalog.parishes,
                selected = inputs.parish.ifBlank { "Select parish" },
                optionLabel = { it },
                onSelected = { v -> onUpdate { it.copy(parish = v, nearestTown = "") } }
            )

            val towns = Catalog.parishTowns[inputs.parish].orEmpty()
            LabeledDropdown(
                label = "Nearest town",
                options = towns.ifEmpty { listOf("Select a parish first") },
                selected = inputs.nearestTown.ifBlank { towns.firstOrNull() ?: "Select a parish first" },
                optionLabel = { it },
                onSelected = { v -> onUpdate { it.copy(nearestTown = v) } },
                supportingText = "Used for delivery & internal records."
            )
        }

        SectionCard(title = "Notes") {
            OutlinedTextField(
                value = inputs.customerNotes,
                onValueChange = { v -> onUpdate { it.copy(customerNotes = v) } },
                label = { Text("Anything worth remembering about this job (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }

        Text(
            "Next: pricing and any discount, then Save Quote.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
