package com.lumix.estimator.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Shared motion vocabulary. Springs are tuned closer to Spring.DampingRatioLowBouncy's
 * neighbor (MediumBouncy) but with high stiffness, so movement reads as snappy and
 * physical rather than the exaggerated bounce the brief explicitly warns against.
 */
object LumixMotion {
    fun <T> snappy(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    fun <T> gentle(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    fun <T> responsive(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val PressScaleDown = 0.97f

    val fadeThrough = tween<Float>(220)
}
