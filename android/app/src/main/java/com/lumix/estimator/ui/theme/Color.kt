package com.lumix.estimator.ui.theme

import androidx.compose.ui.graphics.Color

object LumixColors {
    // Brand accents — stay vivid in both themes; used as fills, icon backgrounds,
    // rings, progress indicators, and glow effects rather than as body text.
    val SolarYellow = Color(0xFFFFD84D)
    val EnergyGreen = Color(0xFF63E6A5)
    val TechnicalCyan = Color(0xFF58C7FF)
    val SolarAmber = Color(0xFFFFB454)
    val WarningRed = Color(0xFFFF6B6B)

    // Dark theme — the "hero" experience
    val BackgroundDark = Color(0xFF080B10)
    val SurfaceDark = Color(0xFF11161D)
    val SurfaceElevatedDark = Color(0xFF161C25)
    val TextPrimaryDark = Color(0xFFF5F7FA)
    val TextSecondaryDark = Color(0xFF8993A1)
    val OutlineDark = Color(0x1FF5F7FA)
    val GlassDark = Color(0x14F5F7FA)
    val ScrimDark = Color(0xCC05070A)

    // Light theme — same language, softened
    val BackgroundLight = Color(0xFFF3F5F7)
    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceElevatedLight = Color(0xFFFFFFFF)
    val TextPrimaryLight = Color(0xFF10141A)
    val TextSecondaryLight = Color(0xFF5B6472)
    val OutlineLight = Color(0x1410141A)
    val GlassLight = Color(0x0A10141A)
    val ScrimLight = Color(0x99000000)

    // Darkened, text-safe variants of the accents for direct use as label/text
    // color in light mode, where the vivid accent alone would fail contrast.
    val SolarYellowOnLight = Color(0xFF8A5F00)
    val EnergyGreenOnLight = Color(0xFF157A4C)
    val TechnicalCyanOnLight = Color(0xFF0A6FB8)
    val SolarAmberOnLight = Color(0xFF9A5A12)
    val WarningRedOnLight = Color(0xFFC23B35)
}
