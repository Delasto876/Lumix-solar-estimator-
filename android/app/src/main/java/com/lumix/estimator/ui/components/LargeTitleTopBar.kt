package com.lumix.estimator.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.ui.theme.LocalLumixPalette

/**
 * The One UI-style screen header: a big, bold, left-aligned title sitting directly on the
 * screen's own background (no bar fill, no shadow/elevation) rather than Material's small
 * centered [androidx.compose.material3.TopAppBar]. Used as each top-level tab's `topBar`, so
 * it's a shared shell piece every tab picks up automatically rather than something each screen
 * reimplements. [onBack], when non-null, renders a small back affordance above the title for
 * the rare case this same screen is also reachable as a pushed (non-tab) route.
 */
@Composable
fun LargeTitleTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val palette = LocalLumixPalette.current
    Column(modifier = modifier.fillMaxWidth().statusBarsPadding()) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
                Text("×", style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = if (onBack != null) 0.dp else 14.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.textPrimary
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}
