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
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.RoofType
import com.lumix.estimator.ui.components.SectionCard

/**
 * A88 (spec Phase 26 §17 — "REQUIRED FOR ENGINEERING / REQUIRED FOR QUOTE / OPTIONAL SITE
 * INFORMATION... Only make a field mandatory when it is actually required"): roof type stays a
 * required, pre-calculation step in every mode — unlike the fields moved to
 * [StepSiteDetails] (property type, system type, JPS rate, storeys), roof type genuinely feeds
 * [com.lumix.estimator.domain.SystemCalculator]'s mounting-hardware math (rails/L-feet/clamps
 * differ by zinc vs. slab vs. shingle — see that file's own `railsPerRow`/`roofType` branches), so
 * moving it to the deferred Site Details step would make the materials count wrong until the
 * installer happened to visit that later screen. "Building height" (storeys) was split out to
 * [StepSiteDetails] instead — confirmed not read anywhere in `SystemCalculator`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepRoofType(inputs: QuoteInputs, onUpdate: ((QuoteInputs) -> QuoteInputs) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(title = "Roof type") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                RoofType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = inputs.roofType == type,
                        onClick = { onUpdate { it.copy(roofType = type) } },
                        shape = SegmentedButtonDefaults.itemShape(index, RoofType.entries.size)
                    ) {
                        Text(
                            when (type) {
                                RoofType.ZINC -> "Zinc / metal"
                                RoofType.SLAB -> "Concrete slab"
                                RoofType.SHINGLE -> "Shingle / Other"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
