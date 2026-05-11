package com.example.test1.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colores semánticos de macros expuestos vía CompositionLocal.
 * Las variantes *Container se calculan al vuelo con alpha bajo para fondos.
 */
data class MacroColors(
    val calories: Color,
    val protein:  Color,
    val carbs:    Color,
    val fat:      Color,
) {
    val caloriesContainer: Color get() = calories.copy(alpha = 0.12f)
    val proteinContainer:  Color get() = protein.copy(alpha = 0.12f)
    val carbsContainer:    Color get() = carbs.copy(alpha = 0.12f)
    val fatContainer:      Color get() = fat.copy(alpha = 0.12f)

    val caloriesBorder: Color get() = calories.copy(alpha = 0.40f)
    val proteinBorder:  Color get() = protein.copy(alpha = 0.40f)
    val carbsBorder:    Color get() = carbs.copy(alpha = 0.40f)
    val fatBorder:      Color get() = fat.copy(alpha = 0.40f)
}

val darkMacroColors = MacroColors(
    calories = CalorieColor,
    protein  = ProteinColor,
    carbs    = CarbColor,
    fat      = FatColor,
)

val lightMacroColors = MacroColors(
    calories = Color(0xFFB85A1E),
    protein  = Color(0xFF883A7A),
    carbs    = Color(0xFF1F6E87),
    fat      = Color(0xFF7D5A10),
)

/** staticCompositionLocalOf: los colores no cambian en runtime → sin recomposición extra */
val LocalMacroColors = staticCompositionLocalOf { darkMacroColors }

/** Acceso desde cualquier Composable: MaterialTheme.macroColors.protein */
val MaterialTheme.macroColors: MacroColors
    @Composable get() = LocalMacroColors.current
