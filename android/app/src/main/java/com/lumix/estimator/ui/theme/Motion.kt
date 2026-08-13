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

    // Named duration tiers so motion reads as deliberate rather than ad hoc — pick the tier
    // that matches what's moving, not a duration that merely "looks fine" in isolation.
    /** Micro-interactions: press feedback, toggle states, small value changes. */
    const val DURATION_MICRO = 180
    /** Screen-to-screen transitions: wizard steps, tab switches. */
    const val DURATION_SCREEN = 300
    /** Major transitions: calculation reveal, first-load hero entrance. */
    const val DURATION_MAJOR = 420

    fun <T> micro(): androidx.compose.animation.core.TweenSpec<T> = tween(DURATION_MICRO)
    fun <T> screen(): androidx.compose.animation.core.TweenSpec<T> = tween(DURATION_SCREEN)
    fun <T> major(): androidx.compose.animation.core.TweenSpec<T> = tween(DURATION_MAJOR)
}
