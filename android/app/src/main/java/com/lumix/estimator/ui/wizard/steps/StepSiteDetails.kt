package com.lumix.estimator.ui.wizard.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumix.estimator.domain.JpsRate
import com.lumix.estimator.domain.PropertyType
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteMode
import com.lumix.estimator.domain.SystemTypeNew
import com.lumix.estimator.ui.components.LabeledDropdown
import com.lumix.estimator.ui.components.SectionCard
import com.lumix.estimator.ui.theme.LocalLumixPalette

/**
 * A88 (spec Phase 26 §17 — "Do NOT force the installer to enter every site detail before the
 * system can be sized... After the core system design: show CONTINUE TO SITE DETAILS... Separate
 * fields into: REQUIRED FOR ENGINEERING / REQUIRED FOR QUOTE / OPTIONAL SITE INFORMATION"): every
 * field here was confirmed (by grepping `SystemCalculator.kt`) to NOT feed the PV/battery/inverter/
 * backup sizing math at all — property type, "new installation vs. upgrade," JPS rate class
 * (affects only the Savings screen's utility rate, not sizing), and building height (storeys).
 * Reached from [com.lumix.estimator.ui.results.SystemResultScreen]'s "Continue to Site Details"
 * button, AFTER the system is already calculated — never a prerequisite for Calculate System.
 *
 * "REQUIRED FOR ENGINEERING" fields (parish/PSH, system mode, roof type) are deliberately NOT
 * here — see [StepLocation]/[StepRoofType]'s own docs for why those stayed pre-calculation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepSiteDetails(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    val palette = LocalLumixPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Optional — helps with the printed quote and record-keeping. None of this changes the system that was already sized.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )

        SectionCard(title = "Property") {
            LabeledDropdown(
                label = "Property type",
                options = PropertyType.entries,
                selected = inputs.propertyType,
                optionLabel = { it.label },
                onSelected = { v -> onUpdate { it.copy(propertyType = v) } }
            )
            LabeledDropdown(
                label = "System type",
                options = SystemTypeNew.entries,
                selected = inputs.systemType,
                optionLabel = { if (it == SystemTypeNew.NEW) "New installation" else "Upgrade / Expansion" },
                onSelected = { v -> onUpdate { it.copy(systemType = v) } }
            )
        }

        SectionCard(title = "Building height") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(false, true).forEachIndexed { index, twoPlus ->
                    SegmentedButton(
                        selected = inputs.twoOrMoreStoreys == twoPlus,
                        onClick = { onUpdate { it.copy(twoOrMoreStoreys = twoPlus) } },
                        shape = SegmentedButtonDefaults.itemShape(index, 2)
                    ) {
                        Text(if (twoPlus) "2+ storeys" else "1 storey")
                    }
                }
            }
        }

        if (inputs.quoteMode == QuoteMode.GUIDED) {
            SectionCard(title = "JPS rate") {
                LabeledDropdown(
                    label = "JPS rate type",
                    options = JpsRate.entries,
                    selected = inputs.jpsRate,
                    optionLabel = {
                        when (it) {
                            JpsRate.RESIDENTIAL -> "Residential"
                            JpsRate.COMMERCIAL -> "Commercial / Business"
                            JpsRate.UNKNOWN -> "Not sure"
                        }
                    },
                    onSelected = { v -> onUpdate { it.copy(jpsRate = v) } }
                )
                Text(
                    "Used for the Savings screen's utility rate estimate only — doesn't affect system sizing.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary
                )
            }
        }
    }
}
