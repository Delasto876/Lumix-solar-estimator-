package com.lumix.estimator.ui.wizard.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.DiscountType
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.ui.components.LabeledDropdown
import com.lumix.estimator.ui.components.NumberField
import com.lumix.estimator.ui.components.SectionCard

private val budgetBands = listOf(
    "none" to "No fixed budget - show ideal",
    "under500" to "Under $500,000",
    "500-800" to "$500,000 - $800,000",
    "800-1200" to "$800,000 - $1,200,000",
    "1200-2000" to "$1,200,000 - $2,000,000",
    "over2000" to "Above $2,000,000"
)

@Composable
fun Step7Pricing(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "Pricing & discount") {
            LabeledDropdown(
                label = "Budget range (optional)",
                options = budgetBands,
                selected = budgetBands.first { it.first == inputs.budgetBand },
                optionLabel = { it.second },
                onSelected = { v -> onUpdate { it.copy(budgetBand = v.first) } }
            )

            NumberField(
                label = "Delivery / Transport charge (J$)",
                value = inputs.deliveryCharge,
                onValueChange = { v -> onUpdate { it.copy(deliveryCharge = v) } }
            )

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Use discount price list", modifier = Modifier.weight(1f))
                Switch(
                    checked = inputs.useDiscountPriceList,
                    onCheckedChange = { v -> onUpdate { it.copy(useDiscountPriceList = v) } }
                )
            }

            LabeledDropdown(
                label = "Extra discount type",
                options = DiscountType.entries,
                selected = inputs.discountType,
                optionLabel = {
                    when (it) {
                        DiscountType.NONE -> "No extra discount"
                        DiscountType.PERCENT -> "Percent (%)"
                        DiscountType.FIXED -> "Fixed amount (J$)"
                    }
                },
                onSelected = { v -> onUpdate { it.copy(discountType = v) } }
            )

            if (inputs.discountType != DiscountType.NONE) {
                NumberField(
                    label = "Extra discount value",
                    value = inputs.discountValue,
                    onValueChange = { v -> onUpdate { it.copy(discountValue = v) } },
                    supportingText = if (inputs.discountType == DiscountType.PERCENT)
                        "% off the total (materials + 15% service + delivery)." else null
                )
            }
        }

        SectionCard(title = "Customer") {
            OutlinedTextField(
                value = inputs.customerName,
                onValueChange = { v -> onUpdate { it.copy(customerName = v) } },
                label = { Text("Customer name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = inputs.customerContact,
                onValueChange = { v -> onUpdate { it.copy(customerContact = v) } },
                label = { Text("Customer contact (phone or email)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            "Tap Calculate to generate the quote. It will be saved to your quote history automatically.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
