package com.example.test1.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkColorScheme = darkColorScheme(
    primary              = PunkPrimary,
    onPrimary            = PunkOnPrimary,
    primaryContainer     = PunkPrimaryContainer,
    onPrimaryContainer   = PunkOnPrimaryContainer,
    secondary            = PunkSecondary,
    onSecondary          = PunkOnSecondary,
    secondaryContainer   = PunkSecondaryContainer,
    onSecondaryContainer = PunkOnSecondaryContainer,
    background           = PunkBackground,
    onBackground         = PunkOnBackground,
    surface              = PunkSurface,
    onSurface            = PunkOnSurface,
    surfaceVariant       = PunkSurfaceVar,
    onSurfaceVariant     = PunkOnSurfaceVar,
    outline              = PunkOutline,
    outlineVariant       = PunkOutlineVar,
    error                = PunkError,
    onError              = PunkOnError,
    errorContainer       = PunkErrorContainer,
    onErrorContainer     = PunkOnErrorContainer,
)

private val LightColorScheme = lightColorScheme(
    primary              = LightPrimary,
    onPrimary            = LightSurface,
    primaryContainer     = LightPrimaryContainer,
    onPrimaryContainer   = LightOnPrimaryContainer,
    secondary            = M3Secondary,
    onSecondary          = LightSurface,
    secondaryContainer   = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    background           = LightBackground,
    onBackground         = TextPrimary,
    surface              = LightSurface,
    onSurface            = TextPrimary,
    surfaceVariant       = LightSurfaceElevated,
    onSurfaceVariant     = LightOnSurfaceVariant,
    outline              = LightBorder,
    outlineVariant       = LightBorderSubtle,
    error                = LightError,
    onError              = LightSurface,
    errorContainer       = LightErrorContainer,
    onErrorContainer     = LightOnErrorContainer,
)

private val PunkShapes = Shapes(
    extraSmall = AppShapeXs,
    small      = AppShapeSm,
    medium     = AppShapeMd,
    large      = AppShapeLg,
    extraLarge = AppShapeXl,
)

@Composable
fun Test1Theme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val macroColors = if (darkTheme) darkMacroColors else lightMacroColors
    CompositionLocalProvider(LocalMacroColors provides macroColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = Typography,
            shapes      = PunkShapes,
            content     = content
        )
    }
}
