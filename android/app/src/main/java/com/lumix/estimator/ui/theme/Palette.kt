package com.lumix.estimator.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic tokens beyond what Material3's ColorScheme roles cover — the app's
 * distinct energy/technical accents keep the same hue in both themes; only
 * their "on light" text variant changes for contrast.
 */
data class LumixPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val glass: Color,
    val scrim: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val outline: Color,
    val solarYellow: Color,
    val solarYellowText: Color,
    val energyGreen: Color,
    val energyGreenText: Color,
    val technicalCyan: Color,
    val technicalCyanText: Color,
    val solarAmber: Color,
    val solarAmberText: Color,
    val warningRed: Color,
    val warningRedText: Color
)

val LumixDarkPalette = LumixPalette(
    isDark = true,
    background = LumixColors.BackgroundDark,
    surface = LumixColors.SurfaceDark,
    surfaceElevated = LumixColors.SurfaceElevatedDark,
    glass = LumixColors.GlassDark,
    scrim = LumixColors.ScrimDark,
    textPrimary = LumixColors.TextPrimaryDark,
    textSecondary = LumixColors.TextSecondaryDark,
    outline = LumixColors.OutlineDark,
    solarYellow = LumixColors.SolarYellow,
    solarYellowText = LumixColors.SolarYellow,
    energyGreen = LumixColors.EnergyGreen,
    energyGreenText = LumixColors.EnergyGreen,
    technicalCyan = LumixColors.TechnicalCyan,
    technicalCyanText = LumixColors.TechnicalCyan,
    solarAmber = LumixColors.SolarAmber,
    solarAmberText = LumixColors.SolarAmber,
    warningRed = LumixColors.WarningRed,
    warningRedText = LumixColors.WarningRed
)

val LumixLightPalette = LumixPalette(
    isDark = false,
    background = LumixColors.BackgroundLight,
    surface = LumixColors.SurfaceLight,
    surfaceElevated = LumixColors.SurfaceElevatedLight,
    glass = LumixColors.GlassLight,
    scrim = LumixColors.ScrimLight,
    textPrimary = LumixColors.TextPrimaryLight,
    textSecondary = LumixColors.TextSecondaryLight,
    outline = LumixColors.OutlineLight,
    solarYellow = LumixColors.SolarYellow,
    solarYellowText = LumixColors.SolarYellowOnLight,
    energyGreen = LumixColors.EnergyGreen,
    energyGreenText = LumixColors.EnergyGreenOnLight,
    technicalCyan = LumixColors.TechnicalCyan,
    technicalCyanText = LumixColors.TechnicalCyanOnLight,
    solarAmber = LumixColors.SolarAmber,
    solarAmberText = LumixColors.SolarAmberOnLight,
    warningRed = LumixColors.WarningRed,
    warningRedText = LumixColors.WarningRedOnLight
)

val LocalLumixPalette = staticCompositionLocalOf { LumixDarkPalette }
