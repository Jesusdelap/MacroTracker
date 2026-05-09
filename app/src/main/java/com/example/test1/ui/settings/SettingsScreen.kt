package com.example.test1.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import kotlin.math.abs
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.test1.MacroApp
import com.example.test1.R
import com.example.test1.ui.theme.*

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app     = LocalContext.current.applicationContext as MacroApp
    val vm: SettingsViewModel = viewModel { SettingsViewModel(app.goalRepository) }
    val goal    by vm.goal.collectAsState()
    val haptics = LocalHapticFeedback.current

    var kcal    by remember(goal.kcal)     { mutableStateOf(goal.kcal.toString()) }
    var protein by remember(goal.protein)  { mutableStateOf(goal.protein.toString()) }
    var carbs   by remember(goal.carbs)    { mutableStateOf(goal.carbs.toString()) }
    var fat     by remember(goal.fat)      { mutableStateOf(goal.fat.toString()) }

    val calculatedGoalKcal by remember {
        derivedStateOf {
            (protein.toIntOrNull() ?: 0) * 4 +
            (carbs.toIntOrNull()   ?: 0) * 4 +
            (fat.toIntOrNull()     ?: 0) * 9
        }
    }

    var showSavedSnackbar by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    if (showSavedSnackbar) {
        val savedText = stringResource(R.string.settings_saved)
        LaunchedEffect(Unit) {
            snackbarHostState.showSnackbar(savedText)
            showSavedSnackbar = false
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(end = Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_cd),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                HorizontalDivider(
                    color     = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl, vertical = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            Text(
                stringResource(R.string.settings_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── OBJETIVOS DIARIOS ─────────────────────────────────────────
            Text(
                stringResource(R.string.settings_section_goals),
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GoalInputRow(stringResource(R.string.macro_fullname_calories), kcal,    { kcal = it },    "kcal", CalorieColor)
                GoalInputRow(stringResource(R.string.macro_fullname_protein),  protein, { protein = it }, "g",    ProteinColor)
                GoalInputRow(stringResource(R.string.macro_fullname_carbs),    carbs,   { carbs = it },   "g",    CarbColor)
                GoalInputRow(stringResource(R.string.macro_fullname_fat),      fat,     { fat = it },     "g",    FatColor)

                if (calculatedGoalKcal > 0) {
                    val kcalEntered = kcal.toIntOrNull()
                    val hasMismatch = kcalEntered != null &&
                        abs(kcalEntered - calculatedGoalKcal) > calculatedGoalKcal * 0.10f
                    Surface(
                        color = if (hasMismatch) FatColor.copy(alpha = 0.12f)
                                else             MaterialTheme.colorScheme.surfaceVariant,
                        shape = AppShapeSm
                    ) {
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint     = if (hasMismatch) FatColor
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (hasMismatch)
                                    stringResource(R.string.settings_macros_mismatch, calculatedGoalKcal, kcalEntered!!)
                                else
                                    stringResource(R.string.settings_macros_equivalent, calculatedGoalKcal),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hasMismatch) MaterialTheme.colorScheme.onSurface
                                        else             MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape    = AppShapeMd,
                onClick  = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val k = kcal.toIntOrNull() ?: return@Button
                    val p = protein.toIntOrNull() ?: return@Button
                    val c = carbs.toIntOrNull() ?: return@Button
                    val f = fat.toIntOrNull() ?: return@Button
                    vm.saveGoal(k, p, c, f)
                    showSavedSnackbar = true
                }
            ) {
                Text(stringResource(R.string.settings_save), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun GoalInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    accentColor: Color
) {
    var isFocused    by remember { mutableStateOf(false) }
    val surfaceColor  = MaterialTheme.colorScheme.surfaceVariant
    val primary       = MaterialTheme.colorScheme.primary
    val onSurface     = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(surfaceColor, AppShapeMd)
            .border(1.dp, if (isFocused) primary else Color.Transparent, AppShapeMd)
            .padding(horizontal = Spacing.lg),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isFocused) accentColor else onSurface
            )
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                BasicTextField(
                    value           = value,
                    onValueChange   = onValueChange,
                    textStyle       = MaterialTheme.typography.bodyMedium.copy(
                        color               = onSurface,
                        textAlign           = TextAlign.End,
                        fontFeatureSettings = FontFeatureTnum
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine      = true,
                    cursorBrush     = SolidColor(primary),
                    modifier        = Modifier
                        .widthIn(min = 48.dp, max = 100.dp)
                        .onFocusChanged { isFocused = it.isFocused },
                    decorationBox   = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterEnd) {
                            if (value.isEmpty()) {
                                Text(
                                    "—",
                                    style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.End),
                                    color = TextTertiary
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                Text(unit, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            }
        }
    }
}
