package com.lumix.estimator.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object LumixRadius {
    val sm = 12.dp
    val md = 20.dp
    val lg = 28.dp
    val xl = 36.dp
    val pill = 999.dp
}

val LumixShapes = Shapes(
    extraSmall = RoundedCornerShape(LumixRadius.sm),
    small = RoundedCornerShape(LumixRadius.sm),
    medium = RoundedCornerShape(LumixRadius.md),
    large = RoundedCornerShape(LumixRadius.lg),
    extraLarge = RoundedCornerShape(LumixRadius.xl)
)
