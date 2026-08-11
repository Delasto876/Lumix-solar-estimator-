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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
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

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(if (enabled) bgColor else bgColor.copy(alpha = 0.35f))
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick
            )
            .padding(PaddingValues(horizontal = 24.dp, vertical = 14.dp)),
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
                style = MaterialTheme.typography.titleSmall
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
    loading: Boolean = false
) = LumixButtonBase(text, onClick, LumixButtonTone.Primary, modifier, enabled, loading)

@Composable
fun LumixSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) = LumixButtonBase(text, onClick, LumixButtonTone.Secondary, modifier, enabled, loading)

@Composable
fun LumixGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) = LumixButtonBase(text, onClick, LumixButtonTone.Ghost, modifier, enabled, false)

@Composable
fun LumixDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) = LumixButtonBase(text, onClick, LumixButtonTone.Danger, modifier, enabled, false)

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
