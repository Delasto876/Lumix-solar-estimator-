package com.lumix.estimator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixRadius

/**
 * A solid, slightly elevated surface — the default resting card. An optional
 * [accentColor] washes a very faint tint over it (a "mood" cue, e.g. status-colored),
 * without turning the whole card into a colored block.
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LumixRadius.lg),
    accentColor: Color? = null,
    content: @Composable () -> Unit
) {
    val palette = LocalLumixPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surfaceElevated)
            .then(if (accentColor != null) Modifier.background(accentColor.copy(alpha = 0.05f)) else Modifier)
            .border(1.dp, palette.outline, shape)
    ) {
        content()
    }
}

/** A translucent, low-opacity surface reserved for floating chrome — nav bars, overlays, sheets. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LumixRadius.pill),
    content: @Composable () -> Unit
) {
    val palette = LocalLumixPalette.current
    Box(
        modifier = modifier
            .clip(shape)
            .background(palette.surface.copy(alpha = if (palette.isDark) 0.72f else 0.88f))
            .border(1.dp, palette.outline, shape)
    ) {
        content()
    }
}
