package com.example.test1.ui.product

import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.test1.MacroApp
import com.example.test1.R
import com.example.test1.data.api.BarcodeResult
import com.example.test1.data.db.entity.FoodItemEntity
import com.example.test1.data.db.entity.FoodItemSource
import com.example.test1.data.db.entity.FoodItemType
import com.example.test1.data.db.entity.ServingMode
import com.example.test1.ui.components.MacroPill
import com.example.test1.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ProductFormScreen(
    barcodeResult: BarcodeResult?,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val app     = LocalContext.current.applicationContext as MacroApp
    val vm: ProductFormViewModel = viewModel { ProductFormViewModel(app.recipeRepository, app.foodRepository) }
    val haptics = LocalHapticFeedback.current
    val scope   = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var name    by remember { mutableStateOf(barcodeResult?.name ?: "") }
    var brand   by remember { mutableStateOf(barcodeResult?.brand ?: "") }
    var kcal    by remember { mutableStateOf(barcodeResult?.kcalPer100g?.toString() ?: "") }
    var protein by remember { mutableStateOf(barcodeResult?.proteinPer100g?.let { it.roundToInt().toString() } ?: "") }
    var carbs   by remember { mutableStateOf(barcodeResult?.carbsPer100g?.let { it.roundToInt().toString() } ?: "") }
    var fat     by remember { mutableStateOf(barcodeResult?.fatPer100g?.let { it.roundToInt().toString() } ?: "") }
    var showWeightPicker by remember { mutableStateOf(false) }

    val entity by remember(name, brand, kcal, protein, carbs, fat) {
        derivedStateOf {
            FoodItemEntity(
                name           = name.trim(),
                brand          = brand.trim().ifBlank { null },
                barcode        = barcodeResult?.barcode,
                itemType       = FoodItemType.PRODUCT.name,
                source         = barcodeResult?.source?.name ?: FoodItemSource.MANUAL.name,
                fatSecretId    = barcodeResult?.fatSecretId,
                usdaFdcId      = barcodeResult?.usdaFdcId,
                servingMode    = ServingMode.PER_100G.name,
                kcalPerServing = kcal.toIntOrNull() ?: 0,
                protein        = protein.toFloatOrNull() ?: 0f,
                carbs          = carbs.toFloatOrNull() ?: 0f,
                fat            = fat.toFloatOrNull() ?: 0f
            )
        }
    }

    val isValid = name.isNotBlank() && kcal.toIntOrNull() != null

    if (showWeightPicker) {
        ProductWeightDialog(
            entity    = entity,
            onConfirm = { grams ->
                showWeightPicker = false
                vm.addToLogAndSave(entity, grams) { onComplete() }
            },
            onDismiss = { showWeightPicker = false }
        )
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
                            contentDescription = stringResource(R.string.scanner_back_cd),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        stringResource(R.string.product_form_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
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
            // Source + barcode row
            if (barcodeResult != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SourceBadge(source = barcodeResult.source)
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.product_form_barcode_label, barcodeResult.barcode),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── BASIC INFO ────────────────────────────────────────────────────
            Text(
                stringResource(R.string.product_form_section_basic),
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text(stringResource(R.string.product_form_name)) },
                    singleLine    = true,
                    shape         = AppShapeMd,
                    modifier      = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value         = brand,
                    onValueChange = { brand = it },
                    label         = { Text(stringResource(R.string.product_form_brand)) },
                    singleLine    = true,
                    shape         = AppShapeMd,
                    modifier      = Modifier.fillMaxWidth()
                )
            }

            // ── NUTRITION ─────────────────────────────────────────────────────
            Text(
                stringResource(R.string.product_form_section_nutrition),
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                MacroInputRow(stringResource(R.string.macro_fullname_calories), kcal,    { kcal = it },    "kcal", CalorieColor)
                MacroInputRow(stringResource(R.string.macro_fullname_protein),  protein, { protein = it }, "g",    ProteinColor)
                MacroInputRow(stringResource(R.string.macro_fullname_carbs),    carbs,   { carbs = it },   "g",    CarbColor)
                MacroInputRow(stringResource(R.string.macro_fullname_fat),      fat,     { fat = it },     "g",    FatColor)
            }

            // ── ACTIONS ───────────────────────────────────────────────────────
            Button(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = AppShapeMd,
                enabled  = isValid,
                onClick  = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showWeightPicker = true
                }
            ) {
                Text(stringResource(R.string.product_form_add_to_log), style = MaterialTheme.typography.labelLarge)
            }

            val savedText = stringResource(R.string.product_form_saved)
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = AppShapeMd,
                enabled  = isValid,
                onClick  = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        vm.saveProduct(entity)
                        snackbarHostState.showSnackbar(savedText)
                        onComplete()
                    }
                }
            ) {
                Text(stringResource(R.string.product_form_save), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SourceBadge(source: FoodItemSource) {
    val (label, color) = when (source) {
        FoodItemSource.FATSECRET -> "FatSecret" to ProteinColor
        FoodItemSource.USDA      -> "USDA"      to CarbColor
        FoodItemSource.MANUAL    -> stringResource(R.string.product_form_source_manual) to TextTertiary
    }
    Surface(
        shape = AppShapeXl,
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun ProductWeightDialog(
    entity: FoodItemEntity,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var grams by remember { mutableStateOf("") }
    val gramsF = grams.toFloatOrNull()
    val ratio  = (gramsF ?: 0f) / 100f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entity.name, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                Text(
                    stringResource(R.string.recipe_weight_question),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value         = grams,
                    onValueChange = { grams = it },
                    label         = { Text(stringResource(R.string.recipe_weight_field)) },
                    suffix        = { Text("g") },
                    singleLine    = true,
                    shape         = AppShapeMd,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier      = Modifier.fillMaxWidth()
                )
                if (gramsF != null && gramsF > 0f) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        MacroPill(MacroType.CALORIES, "${(entity.kcalPerServing * ratio).toInt()}",   modifier = Modifier.weight(1f))
                        MacroPill(MacroType.PROTEIN,  "${(entity.protein * ratio).roundToInt()}g",   modifier = Modifier.weight(1f))
                        MacroPill(MacroType.CARBS,    "${(entity.carbs   * ratio).roundToInt()}g",   modifier = Modifier.weight(1f))
                        MacroPill(MacroType.FAT,      "${(entity.fat     * ratio).roundToInt()}g",   modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); gramsF?.let { onConfirm(it) } },
                enabled  = gramsF != null && gramsF > 0f,
                shape    = AppShapeMd
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun MacroInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    accentColor: Color
) {
    var isFocused by remember { mutableStateOf(false) }
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val primary      = MaterialTheme.colorScheme.primary
    val onSurface    = MaterialTheme.colorScheme.onSurface

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
            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (isFocused) accentColor else onSurface)
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                BasicTextField(
                    value           = value,
                    onValueChange   = onValueChange,
                    textStyle       = MaterialTheme.typography.bodyMedium.copy(
                        color = onSurface, textAlign = TextAlign.End, fontFeatureSettings = FontFeatureTnum
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine      = true,
                    cursorBrush     = SolidColor(primary),
                    modifier        = Modifier
                        .widthIn(min = 48.dp, max = 100.dp)
                        .onFocusChanged { isFocused = it.isFocused },
                    decorationBox   = { inner ->
                        Box(contentAlignment = Alignment.CenterEnd) {
                            if (value.isEmpty()) Text("—", style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.End), color = TextTertiary)
                            inner()
                        }
                    }
                )
                Text(unit, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            }
        }
    }
}
