package com.lumix.estimator.ui.wizard.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.DiscountType
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.ui.components.LabeledDropdown
import com.lumix.estimator.ui.components.NumberField
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixRadius

private val budgetBands = listOf(
    "none" to "No fixed budget - show ideal",
    "under500" to "Under $500,000",
    "500-800" to "$500,000 - $800,000",
    "800-1200" to "$800,000 - $1,200,000",
    "1200-2000" to "$1,200,000 - $2,000,000",
    "over2000" to "Above $2,000,000"
)

/**
 * A29/A43: discount is explicitly optional — [DiscountType.NONE] (the actual default, and what
 * SKIP sets it back to) never blocked continuing ([com.lumix.estimator.domain.Validation
 * .pricingErrors] has no rule requiring a discount), but the step used to present a dropdown
 * defaulting to "No extra discount" with no visible affordance for "I'm done here." Now an
 * explicit ADD DISCOUNT / SKIP choice fronts the discount section so that's obvious.
 */
@Composable
fun Step7Pricing(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    var discountOpen by remember(inputs.discountType) { mutableStateOf(inputs.discountType != DiscountType.NONE) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "Pricing") {
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
                // A89/Ph21 follow-up: delivery is entered manually by explicit installer choice
                // ("let me manually enter delivery price") — SystemCalculator reads this field
                // directly, never overriding it with a computed distance-based figure.
                onValueChange = { v -> onUpdate { it.copy(deliveryCharge = v) } },
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        SectionCard(title = "Discount") {
            Text(
                "Optional — add an extra percentage or fixed-amount discount, or skip straight to calculating the quote.",
                style = MaterialTheme.typography.labelSmall,
                color = LocalLumixPalette.current.textSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (!discountOpen) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PricingChoiceButton(
                        label = "ADD DISCOUNT",
                        primary = true,
                        onClick = {
                            discountOpen = true
                            if (inputs.discountType == DiscountType.NONE) onUpdate { it.copy(discountType = DiscountType.PERCENT) }
                        }
                    )
                    PricingChoiceButton(label = "SKIP", primary = false, onClick = { discountOpen = false })
                }
            } else {
                LabeledDropdown(
                    label = "Discount type",
                    options = listOf(DiscountType.PERCENT, DiscountType.FIXED),
                    selected = inputs.discountType.takeIf { it != DiscountType.NONE } ?: DiscountType.PERCENT,
                    optionLabel = { if (it == DiscountType.PERCENT) "Percent (%)" else "Fixed amount (J$)" },
                    onSelected = { v -> onUpdate { it.copy(discountType = v) } }
                )
                NumberField(
                    label = "Discount value",
                    value = inputs.discountValue,
                    onValueChange = { v -> onUpdate { it.copy(discountValue = v) } },
                    supportingText = if (inputs.discountType == DiscountType.PERCENT)
                        // A79 (spec Phase 16): the service rate is now configurable in Settings
                        // (PriceList.serviceRatePercent), no longer always 15% — this copy no
                        // longer names a specific figure that could go stale.
                        "% off the total (materials + service + delivery)." else null,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    "Remove discount",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = LocalLumixPalette.current.warningRedText,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clickable {
                            discountOpen = false
                            onUpdate { it.copy(discountType = DiscountType.NONE, discountValue = 0.0) }
                        }
                )
            }
        }

        Text(
            "Tap Save Quote to finish. This system was already calculated — this just adds the customer, pricing, and discount details to it.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PricingChoiceButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val palette = LocalLumixPalette.current
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (primary) palette.background else palette.textPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(LumixRadius.pill))
            .background(if (primary) palette.solarYellow else palette.glass)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp)
    )
}
