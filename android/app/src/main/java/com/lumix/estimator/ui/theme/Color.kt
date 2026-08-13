package com.lumix.estimator.ui.theme

import androidx.compose.ui.graphics.Color

object LumixColors {
    // Brand accents — restrained rather than neon (a premium instrument panel reads state
    // through a handful of quiet, deliberate hues, not saturated highlighter color). Stay
    // consistent in both themes; used as fills, icon backgrounds, rings, progress indicators —
    // never as large decorative blocks or gradients.
    val SolarYellow = Color(0xFFE8B04D)
    val EnergyGreen = Color(0xFF5FCFA0)
    val TechnicalCyan = Color(0xFF62B8E8)
    val SolarAmber = Color(0xFFE8A25A)
    val WarningRed = Color(0xFFD9695F)

    // Dark theme — the "hero" experience. Near-black graphite, per the premium-automotive
    // brief: background/surface/elevated read as three barely-distinct steps up, not three
    // different colors — depth comes from that closeness, not contrast.
    val BackgroundDark = Color(0xFF080A0D)
    val SurfaceDark = Color(0xFF101419)
    val SurfaceElevatedDark = Color(0xFF151A20)
    val TextPrimaryDark = Color(0xFFF5F5F5)
    val TextSecondaryDark = Color(0xFF8E969F)
    val OutlineDark = Color(0x14F5F5F5)
    val GlassDark = Color(0x14F5F5F5)
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
