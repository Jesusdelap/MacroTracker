package com.example.test1.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val PunkColorScheme = darkColorScheme(
    primary                = PunkPrimary,
    onPrimary              = PunkOnPrimary,
    primaryContainer       = PunkPrimaryContainer,
    onPrimaryContainer     = PunkOnPrimaryContainer,
    secondary              = PunkSecondary,
    onSecondary            = PunkOnSecondary,
    secondaryContainer     = PunkSecondaryContainer,
    onSecondaryContainer   = PunkOnSecondaryContainer,
    background             = PunkBackground,
    onBackground           = PunkOnBackground,
    surface                = PunkSurface,
    onSurface              = PunkOnSurface,
    surfaceVariant         = PunkSurfaceVar,
    onSurfaceVariant       = PunkOnSurfaceVar,
    outline                = PunkOutline,
    outlineVariant         = PunkOutlineVar,
    error                  = PunkError,
    onError                = PunkOnError,
    errorContainer         = PunkErrorContainer,
    onErrorContainer       = PunkOnErrorContainer,
)

/** Formas M3 construidas desde los tokens semánticos de Shape.kt */
private val PunkShapes = Shapes(
    extraSmall = AppShapeXs,
    small      = AppShapeSm,
    medium     = AppShapeMd,
    large      = AppShapeLg,
    extraLarge = AppShapeXl,
)

@Composable
fun Test1Theme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalMacroColors provides darkMacroColors) {
        MaterialTheme(
            colorScheme = PunkColorScheme,
            typography  = Typography,
            shapes      = PunkShapes,
            content     = content
        )
    }
}
