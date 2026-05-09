package com.example.test1.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.example.test1.R

enum class MacroType { CALORIES, PROTEIN, CARBS, FAT }

// ── Labels de pantalla ────────────────────────────────────────────────────────

@Composable
fun MacroType.label(): String = when (this) {
    MacroType.CALORIES -> stringResource(R.string.macro_label_kcal)
    MacroType.PROTEIN  -> stringResource(R.string.macro_label_protein)
    MacroType.CARBS    -> stringResource(R.string.macro_label_carbs)
    MacroType.FAT      -> stringResource(R.string.macro_label_fat)
}

@Composable
fun MacroType.fullName(): String = when (this) {
    MacroType.CALORIES -> stringResource(R.string.macro_fullname_calories)
    MacroType.PROTEIN  -> stringResource(R.string.macro_fullname_protein)
    MacroType.CARBS    -> stringResource(R.string.macro_fullname_carbs)
    MacroType.FAT      -> stringResource(R.string.macro_fullname_fat)
}

/** Unidad de medida */
fun MacroType.unit(): String = when (this) {
    MacroType.CALORIES -> "kcal"
    MacroType.PROTEIN  -> "g"
    MacroType.CARBS    -> "g"
    MacroType.FAT      -> "g"
}

// ── Colores desde el theme (requieren contexto Composable) ────────────────────

/** Color saturado — para texto, iconos y borde */
@Composable
fun MacroType.color(): Color = with(MaterialTheme.macroColors) {
    when (this@color) {
        MacroType.CALORIES -> calories
        MacroType.PROTEIN  -> protein
        MacroType.CARBS    -> carbs
        MacroType.FAT      -> fat
    }
}

/** Color de fondo con alpha bajo — para superficies de pills y cards */
@Composable
fun MacroType.containerColor(): Color = with(MaterialTheme.macroColors) {
    when (this@containerColor) {
        MacroType.CALORIES -> caloriesContainer
        MacroType.PROTEIN  -> proteinContainer
        MacroType.CARBS    -> carbsContainer
        MacroType.FAT      -> fatContainer
    }
}

/** Color de borde semitransparente */
@Composable
fun MacroType.borderColor(): Color = with(MaterialTheme.macroColors) {
    when (this@borderColor) {
        MacroType.CALORIES -> caloriesBorder
        MacroType.PROTEIN  -> proteinBorder
        MacroType.CARBS    -> carbsBorder
        MacroType.FAT      -> fatBorder
    }
}
