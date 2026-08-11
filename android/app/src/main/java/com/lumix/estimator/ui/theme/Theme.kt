package com.lumix.estimator.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = LumixColors.SolarYellow,
    onPrimary = Color(0xFF221A00),
    secondary = LumixColors.EnergyGreen,
    onSecondary = Color(0xFF00210F),
    tertiary = LumixColors.TechnicalCyan,
    onTertiary = Color(0xFF00223A),
    background = LumixColors.BackgroundDark,
    onBackground = LumixColors.TextPrimaryDark,
    surface = LumixColors.SurfaceDark,
    onSurface = LumixColors.TextPrimaryDark,
    surfaceVariant = LumixColors.SurfaceElevatedDark,
    onSurfaceVariant = LumixColors.TextSecondaryDark,
    outline = LumixColors.OutlineDark,
    error = LumixColors.WarningRed,
    onError = Color(0xFF2A0A08)
)

private val LightColorScheme = lightColorScheme(
    primary = LumixColors.SolarYellowOnLight,
    onPrimary = Color.White,
    secondary = LumixColors.EnergyGreenOnLight,
    onSecondary = Color.White,
    tertiary = LumixColors.TechnicalCyanOnLight,
    onTertiary = Color.White,
    background = LumixColors.BackgroundLight,
    onBackground = LumixColors.TextPrimaryLight,
    surface = LumixColors.SurfaceLight,
    onSurface = LumixColors.TextPrimaryLight,
    surfaceVariant = LumixColors.SurfaceElevatedLight,
    onSurfaceVariant = LumixColors.TextSecondaryLight,
    outline = LumixColors.OutlineLight,
    error = LumixColors.WarningRedOnLight,
    onError = Color.White
)

@Composable
fun LumixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val palette = if (darkTheme) LumixDarkPalette else LumixLightPalette

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = palette.background.toArgb()
            window.navigationBarColor = palette.background.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalLumixPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LumixTypography,
            shapes = LumixShapes,
            content = content
        )
    }
}
