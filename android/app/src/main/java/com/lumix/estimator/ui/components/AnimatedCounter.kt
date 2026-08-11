package com.lumix.estimator.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.lumix.estimator.ui.theme.LumixMotion

/**
 * A number that eases toward [targetValue] instead of snapping, so results and
 * savings figures feel like they're being computed rather than swapped in.
 */
@Composable
fun AnimatedCounterText(
    targetValue: Double,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current
) {
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(targetValue) {
        animatable.animateTo(targetValue.toFloat(), animationSpec = LumixMotion.responsive())
    }
    Text(text = format(animatable.value.toDouble()), modifier = modifier, style = style, color = color)
}
