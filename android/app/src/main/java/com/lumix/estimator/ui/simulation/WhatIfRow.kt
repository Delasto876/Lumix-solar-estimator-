package com.lumix.estimator.ui.simulation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixRadius

data class WhatIfAction(
    val glyph: String,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
fun WhatIfRow(actions: List<WhatIfAction>, modifier: Modifier = Modifier) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(actions) { action -> WhatIfChip(action) }
    }
}

@Composable
private fun WhatIfChip(action: WhatIfAction) {
    val palette = LocalLumixPalette.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(LumixRadius.pill))
            .background(palette.glass)
            .clickable(enabled = action.enabled) { action.onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(action.glyph, style = MaterialTheme.typography.bodyLarge)
        Text(
            action.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (action.enabled) palette.textPrimary else palette.textSecondary
        )
    }
}
