package com.lumix.estimator.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

val LumixTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 44.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.3).sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.2.sp)
)

/** Dominant numbers ("$42,500", "7.2 kW") — bold, tight tracking, tabular figures. */
fun numberDisplayStyle(size: TextUnit = 44.sp, weight: FontWeight = FontWeight.Bold): TextStyle = TextStyle(
    fontWeight = weight,
    fontSize = size,
    letterSpacing = (-0.5).sp,
    fontFeatureSettings = "tnum"
)

/**
 * The single largest number on a screen — a dashboard's system size, a review's headline
 * capacity. Reserved for the one figure that should be understood before anything else on
 * the screen; using it more than once per screen defeats its own hierarchy.
 */
fun heroValueStyle(size: TextUnit = 72.sp): TextStyle = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = size,
    letterSpacing = (-1.5).sp,
    fontFeatureSettings = "tnum"
)
