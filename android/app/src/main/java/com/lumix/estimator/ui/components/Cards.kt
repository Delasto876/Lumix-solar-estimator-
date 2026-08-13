package com.lumix.estimator.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixMotion
import com.lumix.estimator.ui.theme.LumixRadius

/**
 * A solid, slightly elevated surface — the default resting card. Depth comes from the
 * surface/elevated contrast alone (a near-black-on-near-black premium panel, not an outlined
 * box) — a hairline border is opt-in via [bordered] for the rare case something genuinely
 * needs to read as a separate control rather than a resting surface. An optional [accentColor]
 * washes a very faint tint over it (a "mood" cue, e.g. status-colored), without turning the
 * whole card into a colored block.
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LumixRadius.lg),
    accentColor: Color? = null,
    bordered: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = LocalLumixPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (palette.isDark) 0.dp else 3.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
            .clip(shape)
            .background(palette.surfaceElevated)
            .then(if (accentColor != null) Modifier.background(accentColor.copy(alpha = 0.05f)) else Modifier)
            .then(if (bordered) Modifier.border(1.dp, palette.outline, shape) else Modifier)
    ) {
        content()
    }
}

/**
 * A large, tappable choice card — for turning a row of small buttons ("GUIDED / MANUAL /
 * LOAD") into a proper decision surface with room for a title and a one-line explanation.
 * The selected state animates in via a quiet tint and accent-colored title rather than a
 * heavy border or checkmark badge.
 */
@Composable
fun SelectionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLumixPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val titleColor by animateColorAsState(
        targetValue = if (selected) palette.solarYellowText else palette.textPrimary,
        animationSpec = LumixMotion.micro(),
        label = "selectionCardTitle"
    )
    val shape = RoundedCornerShape(LumixRadius.lg)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) palette.solarYellow.copy(alpha = if (palette.isDark) 0.10f else 0.08f) else palette.surfaceElevated)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(20.dp)
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = titleColor)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * A proactive suggestion, styled as intelligent assistance rather than a warning — no red,
 * no exclamation glyph. Always carries a real action; there's no non-interactive variant,
 * since a recommendation nobody can act on is just an unlabeled warning in disguise.
 */
@Composable
fun RecommendationCard(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLumixPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LumixRadius.lg))
            .background(palette.technicalCyan.copy(alpha = if (palette.isDark) 0.08f else 0.06f))
            .padding(20.dp)
    ) {
        Text(
            "RECOMMENDED",
            style = MaterialTheme.typography.labelSmall,
            color = palette.technicalCyanText,
            fontWeight = FontWeight.Bold
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
        )
        LumixSecondaryButton(text = actionLabel, onClick = onAction)
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
    ) {
        content()
    }
}
