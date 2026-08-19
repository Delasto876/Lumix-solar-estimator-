package com.lumix.estimator.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixMotion
import com.lumix.estimator.ui.theme.LumixRadius

private enum class LumixButtonTone { Primary, Secondary, Ghost, Danger }

@Composable
private fun LumixButtonBase(
    text: String,
    onClick: () -> Unit,
    tone: LumixButtonTone,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    // 2026-08-19 ("buttons such as cancel, done and redo and undo is crocked"): the real cause —
    // 3-4 equal-`weight(1f)` buttons sharing one row (RoofDrawingControls' Undo/Clear/Cancel/Done,
    // RoofEditingControls' own rows) left too little width per button at the base 24dp horizontal
    // padding, so `Text`'s default unlimited-lines behavior let some labels wrap to a second line
    // while shorter ones on the same row didn't — different button heights on the same row reads
    // as "crooked." `compact` is a real, distinct button treatment (tighter padding, smaller type,
    // hard single-line truncation) for dense toolbar rows like those, not a hack — every button on
    // a shared row now has the same fixed height regardless of label length.
    compact: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) LumixMotion.PressScaleDown else 1f,
        animationSpec = LumixMotion.snappy(),
        label = "buttonPressScale"
    )

    val shape: Shape = RoundedCornerShape(LumixRadius.pill)
    val (bgColor, contentColor, borderColor) = buttonColors(tone)
    // A soft drop shadow (absent before — every button was perfectly flat) is what actually reads
    // as "premium" on a solid-fill pill button; Ghost stays flat by design (it's meant to look
    // like a bare label, not a raised surface), and a disabled button never casts one.
    val elevation = if (enabled && tone != LumixButtonTone.Ghost) (if (pressed) 1.dp else 4.dp) else 0.dp

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(elevation = elevation, shape = shape, clip = false)
            .clip(shape)
            .background(if (enabled) bgColor else bgColor.copy(alpha = 0.35f))
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick
            )
            .padding(
                PaddingValues(
                    horizontal = if (compact) 12.dp else 24.dp,
                    vertical = if (compact) 10.dp else 14.dp
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
            } else {
                leadingIcon?.invoke()
            }
            Text(
                text,
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.6f),
                fontWeight = FontWeight.SemiBold,
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun buttonColors(tone: LumixButtonTone): Triple<Color, Color, Color?> {
    val palette = LocalLumixPalette.current
    return when (tone) {
        LumixButtonTone.Primary -> Triple(palette.solarYellow, Color(0xFF221A00), null)
        LumixButtonTone.Secondary -> Triple(palette.surfaceElevated, palette.textPrimary, palette.outline)
        LumixButtonTone.Ghost -> Triple(Color.Transparent, palette.textPrimary, palette.outline)
        LumixButtonTone.Danger -> Triple(palette.warningRed.copy(alpha = 0.16f), palette.warningRedText, null)
    }
}

@Composable
fun LumixPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false
) = LumixButtonBase(text, onClick, LumixButtonTone.Primary, modifier, enabled, loading, compact)

@Composable
fun LumixSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false
) = LumixButtonBase(text, onClick, LumixButtonTone.Secondary, modifier, enabled, loading, compact)

@Composable
fun LumixGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false
) = LumixButtonBase(text, onClick, LumixButtonTone.Ghost, modifier, enabled, false, compact)

@Composable
fun LumixDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false
) = LumixButtonBase(text, onClick, LumixButtonTone.Danger, modifier, enabled, false, compact)

/** A round icon-only button with the same spring press feedback as the text buttons. */
@Composable
fun LumixIconButtonSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) LumixMotion.PressScaleDown else 1f,
        animationSpec = LumixMotion.snappy(),
        label = "iconButtonPressScale"
    )
    val palette = com.lumix.estimator.ui.theme.LocalLumixPalette.current

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(palette.glass)
            .border(1.dp, palette.outline, CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides palette.textPrimary) {
            content()
        }
    }
}
