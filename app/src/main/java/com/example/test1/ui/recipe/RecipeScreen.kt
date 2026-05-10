package com.example.test1.ui.recipe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.test1.MacroApp
import com.example.test1.R
import com.example.test1.data.api.GeminiService
import com.example.test1.data.api.MacroResult
import com.example.test1.data.api.RateLimitException
import com.example.test1.data.db.entity.FoodItemEntity
import com.example.test1.data.db.entity.ServingMode
import com.example.test1.data.db.entity.FoodItemSource
import com.example.test1.data.db.entity.isPer100g
import com.example.test1.ui.components.MacroPill
import com.example.test1.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

private data class AiMsg(val text: String, val isUser: Boolean, val isInitial: Boolean = false)

private const val RECIPE_JSON_MARKER = "RECIPE_JSON:"
private val recipeJson = Json { ignoreUnknownKeys = true }

private fun extractRecipe(response: String): MacroResult? {
    val idx = response.indexOf(RECIPE_JSON_MARKER)
    if (idx < 0) return null
    return try {
        val raw = response.substring(idx + RECIPE_JSON_MARKER.length).trim()
        val jsonStr = if (raw.contains('\n')) raw.substringBefore('\n').trim() else raw.trim()
        recipeJson.decodeFromString<MacroResult>(jsonStr)
    } catch (_: Exception) { null }
}

private fun cleanAiText(response: String): String {
    val idx = response.indexOf(RECIPE_JSON_MARKER)
    if (idx < 0) return response.trim()
    val before = response.substring(0, idx).trimEnd()
    val after  = response.substring(idx + RECIPE_JSON_MARKER.length)
        .let { if (it.contains('\n')) it.substringAfter('\n').trimStart() else "" }
    return listOf(before, after).filter { it.isNotBlank() }.joinToString("\n").trim()
}

// ─── Main screen ───────────────────────────────────────────────────────────────

@Composable
fun RecipeScreen(onScanBarcode: () -> Unit) {
    val app = LocalContext.current.applicationContext as MacroApp
    val vm: RecipeViewModel = viewModel {
        RecipeViewModel(app.recipeRepository, app.foodRepository)
    }
    val uiState         by vm.uiState.collectAsState()
    val productsUiState by vm.productsUiState.collectAsState()
    var showAddDialog   by remember { mutableStateOf(false) }
    var editingRecipe   by remember { mutableStateOf<FoodItemEntity?>(null) }
    var selectedTab     by remember { mutableIntStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()
    val recipeDeletedFmt  = stringResource(R.string.recipe_deleted)
    val productDeletedFmt = stringResource(R.string.products_deleted)
    val undoLabel         = stringResource(R.string.action_undo)

    val searchValue: String = if (selectedTab == 0) uiState.searchQuery else productsUiState.searchQuery
    val onSearchValueChange: (String) -> Unit = { q ->
        if (selectedTab == 0) vm.onSearchChange(q) else vm.onProductSearchChange(q)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        .height(56.dp)
                        .padding(horizontal = 20.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            stringResource(R.string.recipe_title),
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onBackground
                        )
                        val count   = if (selectedTab == 0) uiState.recipes.size else productsUiState.products.size
                        val plurals = if (selectedTab == 0) R.plurals.recipe_count else R.plurals.products_count
                        if (count > 0) {
                            Text(
                                pluralStringResource(plurals, count, count),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                ) {
                    SegmentedButton(
                        selected = selectedTab == 0,
                        onClick  = { selectedTab = 0 },
                        shape    = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text(stringResource(R.string.recipes_tab)) }
                    SegmentedButton(
                        selected = selectedTab == 1,
                        onClick  = { selectedTab = 1 },
                        shape    = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text(stringResource(R.string.products_tab)) }
                }
                HorizontalDivider(
                    color     = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick        = { showAddDialog = true },
                    icon           = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text           = { Text(stringResource(R.string.recipe_new)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                FloatingActionButton(
                    onClick        = onScanBarcode,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor   = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.products_scan_cd))
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value         = searchValue,
                onValueChange = onSearchValueChange,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = 10.dp),
                placeholder  = {
                    Text(
                        if (selectedTab == 0) stringResource(R.string.recipe_search_hint)
                        else stringResource(R.string.products_search_hint)
                    )
                },
                leadingIcon  = {
                    Icon(Icons.Filled.Search, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    AnimatedVisibility(visible = searchValue.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                        IconButton(onClick = { onSearchValueChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.recipe_search_clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                singleLine = true,
                shape      = RoundedCornerShape(14.dp),
                colors     = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor   = MaterialTheme.colorScheme.primary
                )
            )

            if (selectedTab == 0) {
                if (uiState.recipes.isEmpty()) {
                    EmptyState(hasSearch = uiState.searchQuery.isNotBlank(), query = uiState.searchQuery)
                } else {
                    LazyColumn(
                        contentPadding      = PaddingValues(start = Spacing.lg, top = Spacing.xs, end = Spacing.lg, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.recipes, key = { it.id }) { recipe ->
                            RecipeCard(
                                recipe                = recipe,
                                onAddToToday          = { vm.addToToday(recipe) },
                                onAddToTodayWithGrams = { grams -> vm.addToTodayWithGrams(recipe, grams) },
                                onToggleFavorite      = { vm.toggleFavorite(recipe) },
                                onDelete = {
                                    vm.deleteRecipe(recipe)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message     = String.format(recipeDeletedFmt, recipe.name),
                                            actionLabel = undoLabel,
                                            duration    = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            vm.addRecipe(recipe.copy(id = 0))
                                        }
                                    }
                                },
                                onEdit = { editingRecipe = recipe }
                            )
                        }
                    }
                }
            } else {
                if (productsUiState.products.isEmpty()) {
                    ProductsEmptyState(
                        hasSearch = productsUiState.searchQuery.isNotBlank(),
                        query     = productsUiState.searchQuery,
                        onScan    = onScanBarcode
                    )
                } else {
                    LazyColumn(
                        contentPadding      = PaddingValues(start = Spacing.lg, top = Spacing.xs, end = Spacing.lg, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(productsUiState.products, key = { it.id }) { product ->
                            ProductCard(
                                product        = product,
                                onAddWithGrams = { grams -> vm.addProductToTodayWithGrams(product, grams) },
                                onDelete = {
                                    vm.deleteProduct(product)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message  = String.format(productDeletedFmt, product.name),
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        RecipeCreationDialog(
            geminiService = app.geminiService,
            onSave        = { recipe -> vm.addRecipe(recipe); showAddDialog = false },
            onDismiss     = { showAddDialog = false }
        )
    }

    editingRecipe?.let { recipe ->
        RecipeCreationDialog(
            geminiService  = app.geminiService,
            editingRecipe  = recipe,
            onSave         = { updated -> vm.updateRecipe(updated); editingRecipe = null },
            onDismiss      = { editingRecipe = null }
        )
    }
}

// ─── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(hasSearch: Boolean, query: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            modifier            = Modifier.padding(Spacing.xxxl)
        ) {
            Icon(
                imageVector        = if (hasSearch) Icons.Filled.SearchOff else Icons.Filled.Book,
                contentDescription = null,
                modifier           = Modifier.size(48.dp),
                tint               = TextTertiary.copy(alpha = 0.4f)
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text  = if (hasSearch) stringResource(R.string.recipe_no_results, query)
                            else stringResource(R.string.recipe_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!hasSearch) {
                    Text(
                        text  = stringResource(R.string.recipe_empty_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}

// ─── Recipe card ───────────────────────────────────────────────────────────────

@Composable
private fun RecipeCard(
    recipe: FoodItemEntity,
    onAddToToday: () -> Unit,
    onAddToTodayWithGrams: (Float) -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val dateStr = remember(recipe.createdAt) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(recipe.createdAt))
    }
    var showWeightPicker by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        },
        positionalThreshold = { it * 0.38f }
    )

    if (showWeightPicker) {
        WeightPickerDialog(
            recipe     = recipe,
            onConfirm  = { grams -> showWeightPicker = false; onAddToTodayWithGrams(grams) },
            onDismiss  = { showWeightPicker = false }
        )
    }

    SwipeToDismissBox(
        state                       = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val fraction = dismissState.progress
            val visible  = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .background(
                        MaterialTheme.colorScheme.errorContainer.copy(
                            alpha = if (visible) (fraction * 2f).coerceIn(0f, 1f) else 0f
                        )
                    )
                    .padding(end = Spacing.lg),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
            }
        }
    ) {
        ElevatedCard(
            modifier  = Modifier.fillMaxWidth(),
            shape     = MaterialTheme.shapes.large,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = Spacing.sm)) {
                        Text(
                            text     = recipe.name,
                            style    = MaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color    = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Icon(Icons.Filled.LocalFireDepartment, contentDescription = null,
                                modifier = Modifier.size(13.dp), tint = MaterialTheme.macroColors.calories)
                            Text(
                                text  = "${recipe.kcalPerServing} kcal${if (recipe.isPer100g) " ${stringResource(R.string.recipe_per_100g_suffix)}" else ""}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.macroColors.calories
                            )
                            val subtitle = buildString {
                                if (!recipe.isPer100g) append("· ${recipe.servings.toInt()} ${stringResource(R.string.recipe_serving_unit)} ")
                                append("· $dateStr")
                                if (recipe.usageCount > 0) append(" · ${recipe.usageCount}×")
                            }
                            Text(
                                text  = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary
                            )
                        }
                    }
                    // Favorite + Edit + Add buttons
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        IconButton(
                            onClick  = onToggleFavorite,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = if (recipe.isFavorite) stringResource(R.string.recipe_favorite_remove) else stringResource(R.string.recipe_favorite_add),
                                modifier = Modifier.size(16.dp),
                                tint     = if (recipe.isFavorite) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                        IconButton(
                            onClick  = onEdit,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit),
                                modifier = Modifier.size(16.dp),
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton(
                            onClick        = { if (recipe.isPer100g) showWeightPicker = true else onAddToToday() },
                            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                            modifier       = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(Spacing.xs))
                            Text(stringResource(R.string.action_add), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.md))

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MacroPill(MacroType.PROTEIN, "${recipe.protein.toInt()}g")
                    MacroPill(MacroType.CARBS,   "${recipe.carbs.toInt()}g")
                    MacroPill(MacroType.FAT,     "${recipe.fat.toInt()}g")
                    if (recipe.isPer100g) {
                        Box(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    AppShapeSm
                                )
                                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        ) {
                            Text(
                                stringResource(R.string.recipe_per_100g_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                if (recipe.ingredients.isNotBlank()) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text     = recipe.ingredients,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ─── Weight picker dialog ──────────────────────────────────────────────────────

@Composable
private fun WeightPickerDialog(
    recipe: FoodItemEntity,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var grams by remember { mutableStateOf("") }
    val gramsF = grams.toFloatOrNull()
    val ratio  = (gramsF ?: 0f) / 100f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(recipe.name, style = MaterialTheme.typography.titleMedium) },
        text  = {
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
                        MacroPill(MacroType.CALORIES, "${(recipe.kcalPerServing * ratio).toInt()}",  modifier = Modifier.weight(1f))
                        MacroPill(MacroType.PROTEIN,  "${"%.1f".format(recipe.protein * ratio)}g",  modifier = Modifier.weight(1f))
                        MacroPill(MacroType.CARBS,    "${"%.1f".format(recipe.carbs   * ratio)}g",  modifier = Modifier.weight(1f))
                        MacroPill(MacroType.FAT,      "${"%.1f".format(recipe.fat     * ratio)}g",  modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    gramsF?.let { onConfirm(it) }
                },
                enabled = gramsF != null && gramsF > 0f,
                shape   = AppShapeMd
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

// ─── Source chip ──────────────────────────────────────────────────────────────

@Composable
private fun SourceChip(source: String) {
    val (label, color) = when (source) {
        FoodItemSource.FATSECRET.name -> "FatSecret" to ProteinColor
        FoodItemSource.USDA.name      -> "USDA"      to CarbColor
        else                          -> "Manual"    to TextTertiary
    }
    Surface(shape = AppShapeXl, color = color.copy(alpha = 0.12f)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
            style    = MaterialTheme.typography.labelSmall,
            color    = color
        )
    }
}

// ─── Products empty state ─────────────────────────────────────────────────────

@Composable
private fun ProductsEmptyState(hasSearch: Boolean, query: String, onScan: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            modifier            = Modifier.padding(Spacing.xxxl)
        ) {
            Icon(
                imageVector        = if (hasSearch) Icons.Filled.SearchOff else Icons.Filled.QrCodeScanner,
                contentDescription = null,
                modifier           = Modifier.size(48.dp),
                tint               = TextTertiary.copy(alpha = 0.4f)
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text  = if (hasSearch) stringResource(R.string.recipe_no_results, query)
                            else stringResource(R.string.products_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!hasSearch) {
                    Text(
                        text  = stringResource(R.string.products_empty_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    FilledTonalButton(onClick = onScan) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.scanner_title))
                    }
                }
            }
        }
    }
}

// ─── Product card ─────────────────────────────────────────────────────────────

@Composable
private fun ProductCard(
    product: FoodItemEntity,
    onAddWithGrams: (Float) -> Unit,
    onDelete: () -> Unit
) {
    var showWeightPicker by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange  = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        },
        positionalThreshold = { it * 0.38f }
    )

    if (showWeightPicker) {
        WeightPickerDialog(
            recipe    = product,
            onConfirm = { grams -> showWeightPicker = false; onAddWithGrams(grams) },
            onDismiss = { showWeightPicker = false }
        )
    }

    SwipeToDismissBox(
        state                       = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val fraction = dismissState.progress
            val visible  = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .background(
                        MaterialTheme.colorScheme.errorContainer.copy(
                            alpha = if (visible) (fraction * 2f).coerceIn(0f, 1f) else 0f
                        )
                    )
                    .padding(end = Spacing.lg),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
            }
        }
    ) {
        ElevatedCard(
            modifier  = Modifier.fillMaxWidth(),
            shape     = MaterialTheme.shapes.large,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = Spacing.sm)) {
                        Text(
                            text     = product.name,
                            style    = MaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color    = MaterialTheme.colorScheme.onSurface
                        )
                        if (product.brand != null) {
                            Text(
                                text  = product.brand,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Icon(Icons.Filled.LocalFireDepartment, contentDescription = null,
                                modifier = Modifier.size(13.dp), tint = MaterialTheme.macroColors.calories)
                            Text(
                                text  = "${product.kcalPerServing} kcal ${stringResource(R.string.recipe_per_100g_suffix)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.macroColors.calories
                            )
                            if (product.usageCount > 0) {
                                Text(
                                    text  = "· ${product.usageCount}×",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextTertiary
                                )
                            }
                        }
                    }
                    FilledTonalButton(
                        onClick        = { showWeightPicker = true },
                        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                        modifier       = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.action_add), style = MaterialTheme.typography.labelLarge)
                    }
                }

                Spacer(Modifier.height(Spacing.md))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    SourceChip(product.source)
                    MacroPill(MacroType.PROTEIN, "${product.protein.toInt()}g")
                    MacroPill(MacroType.CARBS,   "${product.carbs.toInt()}g")
                    MacroPill(MacroType.FAT,      "${product.fat.toInt()}g")
                }
            }
        }
    }
}

// ─── Full-screen creation / edit dialog ────────────────────────────────────────

@Composable
internal fun RecipeCreationDialog(
    geminiService: GeminiService,
    initialValues: MacroResult? = null,
    editingRecipe: FoodItemEntity? = null,
    onSave: (FoodItemEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val isEditing   = editingRecipe != null
    var selectedTab by remember { mutableIntStateOf(0) }

    // AI chat state lifted here so it survives tab switches
    val greeting          = stringResource(R.string.recipe_ai_greeting)
    var aiMessages        by remember { mutableStateOf(listOf(AiMsg(greeting, isUser = false, isInitial = true))) }
    var aiInputText       by remember { mutableStateOf("") }
    var aiIsLoading       by remember { mutableStateOf(false) }
    var aiExtractedRecipe by remember { mutableStateOf<MacroResult?>(null) }
    var aiSummaryText     by remember { mutableStateOf("") }

    // Auto-navigate to Manual tab when AI detects a recipe; snapshot the summary text for Notes
    LaunchedEffect(aiExtractedRecipe) {
        if (aiExtractedRecipe != null) {
            aiSummaryText = aiMessages.lastOrNull { !it.isUser }?.text?.trim() ?: ""
            selectedTab = 0
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                when {
                                    isEditing       -> stringResource(R.string.recipe_dialog_edit)
                                    initialValues != null -> stringResource(R.string.recipe_dialog_save_as)
                                    else            -> stringResource(R.string.recipe_dialog_new)
                                },
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    if (!isEditing) {
                        PrimaryTabRow(selectedTabIndex = selectedTab) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick  = { selectedTab = 0 },
                                text     = { Text(stringResource(R.string.recipe_tab_manual)) },
                                icon     = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick  = { selectedTab = 1 },
                                text     = { Text(stringResource(R.string.recipe_tab_ai)) },
                                icon     = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                    if (selectedTab == 0 || isEditing) {
                        ManualTab(
                            initialValues = aiExtractedRecipe ?: initialValues,
                            initialNotes  = aiSummaryText.ifBlank { null },
                            editingRecipe = editingRecipe,
                            onSave        = onSave
                        )
                    } else {
                        AiTab(
                            geminiService           = geminiService,
                            messages                = aiMessages,
                            onMessagesChange        = { aiMessages = it },
                            inputText               = aiInputText,
                            onInputTextChange       = { aiInputText = it },
                            isLoading               = aiIsLoading,
                            onIsLoadingChange       = { aiIsLoading = it },
                            onExtractedRecipeChange = { aiExtractedRecipe = it }
                        )
                    }
                }
            }
        }
    }
}

// ─── Form helpers ──────────────────────────────────────────────────────────────

private enum class InputMode { PorRacion, Por100g }

@Composable
private fun FormSectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = TextTertiary)
}

@Composable
private fun RecipeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    required: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 5
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        modifier      = Modifier.fillMaxWidth(),
        placeholder   = {
            Row {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                if (required) Text(" *", style = MaterialTheme.typography.bodyMedium,
                    color = SemanticError.copy(alpha = 0.8f))
            }
        },
        textStyle  = MaterialTheme.typography.bodyMedium,
        singleLine = singleLine,
        minLines   = minLines,
        maxLines   = maxLines,
        shape      = AppShapeMd,
        colors     = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor    = Color.Transparent,
            focusedBorderColor      = MaterialTheme.colorScheme.primary,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedTextColor      = MaterialTheme.colorScheme.onSurface,
            focusedTextColor        = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun MacroInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String = "",
    required: Boolean = false
) {
    var isFocused    by remember { mutableStateOf(false) }
    val primary       = MaterialTheme.colorScheme.primary
    val surfaceColor  = MaterialTheme.colorScheme.surfaceVariant
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
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = onSurface)
                if (required) Text("*", style = MaterialTheme.typography.labelMedium, color = SemanticError)
            }
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
                                Text("—",
                                    style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.End),
                                    color = TextTertiary
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (unit.isNotBlank()) {
                    Text(unit, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }
            }
        }
    }
}

// ─── Manual tab ────────────────────────────────────────────────────────────────

@Composable
private fun ManualTab(
    initialValues: MacroResult? = null,
    initialNotes: String? = null,
    editingRecipe: FoodItemEntity? = null,
    onSave: (FoodItemEntity) -> Unit
) {
    val haptics = LocalHapticFeedback.current

    // Banner "Filled by AI" — visible only when pre-filled by AI, dismissed on first edit or X
    var showAiBanner by remember { mutableStateOf(initialNotes != null) }
    val dismissBanner: () -> Unit = { showAiBanner = false }

    // Pre-fill mode from editing recipe
    val startAs100g = editingRecipe?.isPer100g == true
    var inputMode by remember { mutableStateOf(if (startAs100g) InputMode.Por100g else InputMode.PorRacion) }

    // PorRacion fields
    var name    by remember { mutableStateOf(editingRecipe?.name ?: initialValues?.name ?: "") }
    var notes   by remember { mutableStateOf(editingRecipe?.ingredients ?: initialNotes ?: "") }
    var servings by remember { mutableStateOf(editingRecipe?.servings?.toInt()?.toString() ?: "1") }
    var kcal    by remember { mutableStateOf(
        if (!startAs100g) editingRecipe?.kcalPerServing?.toString() ?: initialValues?.cal?.toString() ?: ""
        else ""
    ) }
    var protein by remember { mutableStateOf(
        if (!startAs100g) editingRecipe?.protein?.toString() ?: initialValues?.prot?.toString() ?: ""
        else ""
    ) }
    var carbs   by remember { mutableStateOf(
        if (!startAs100g) editingRecipe?.carbs?.toString() ?: initialValues?.carb?.toString() ?: ""
        else ""
    ) }
    var fat     by remember { mutableStateOf(
        if (!startAs100g) editingRecipe?.fat?.toString() ?: initialValues?.fat?.toString() ?: ""
        else ""
    ) }

    // Por100g fields
    var kcal100  by remember { mutableStateOf(if (startAs100g) editingRecipe?.kcalPerServing?.toString() ?: "" else "") }
    var prot100  by remember { mutableStateOf(if (startAs100g) editingRecipe?.protein?.toString() ?: "" else "") }
    var carbs100 by remember { mutableStateOf(if (startAs100g) editingRecipe?.carbs?.toString() ?: "" else "") }
    var fat100   by remember { mutableStateOf(if (startAs100g) editingRecipe?.fat?.toString() ?: "" else "") }

    val calculatedKcal by remember {
        derivedStateOf {
            ((protein.toFloatOrNull() ?: 0f) * 4f +
             (carbs.toFloatOrNull()   ?: 0f) * 4f +
             (fat.toFloatOrNull()     ?: 0f) * 9f).roundToInt()
        }
    }
    val calculatedKcal100 by remember {
        derivedStateOf {
            ((prot100.toFloatOrNull()  ?: 0f) * 4f +
             (carbs100.toFloatOrNull() ?: 0f) * 4f +
             (fat100.toFloatOrNull()   ?: 0f) * 9f).roundToInt()
        }
    }

    LaunchedEffect(calculatedKcal) {
        if (calculatedKcal > 0) kcal = calculatedKcal.toString()
    }
    LaunchedEffect(calculatedKcal100) {
        if (calculatedKcal100 > 0) kcal100 = calculatedKcal100.toString()
    }

    Column(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = Spacing.xxl)
    ) {
        // ── BANNER "FILLED BY AI" ─────────────────────────────────────────
        AnimatedVisibility(visible = showAiBanner, enter = fadeIn(), exit = fadeOut()) {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.md)
                    .background(AccentPrimary.copy(alpha = 0.10f), AppShapeMd)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null,
                        modifier = Modifier.size(14.dp), tint = AccentPrimary)
                    Text(
                        stringResource(R.string.recipe_ai_filled_banner),
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentPrimary
                    )
                }
                IconButton(onClick = dismissBanner, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close),
                        modifier = Modifier.size(14.dp), tint = AccentPrimary)
                }
            }
        }

        // ── INFORMACIÓN BÁSICA ────────────────────────────────────────────
        FormSectionHeader(stringResource(R.string.recipe_section_basic))
        Spacer(Modifier.height(Spacing.lg))
        RecipeTextField(
            value = name, onValueChange = { name = it; dismissBanner() },
            placeholder = stringResource(R.string.recipe_name_field), required = true, singleLine = true
        )
        Spacer(Modifier.height(Spacing.lg))
        RecipeTextField(
            value = notes, onValueChange = { notes = it; dismissBanner() },
            placeholder = stringResource(R.string.recipe_notes_field), minLines = 3
        )

        Spacer(Modifier.height(Spacing.xxl))

        // ── PORCIONES ─────────────────────────────────────────────────────
        FormSectionHeader(stringResource(R.string.recipe_section_servings))
        Spacer(Modifier.height(Spacing.lg))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = inputMode == InputMode.PorRacion,
                onClick  = { inputMode = InputMode.PorRacion; dismissBanner() },
                shape    = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text(stringResource(R.string.recipe_mode_per_serving)) }
            SegmentedButton(
                selected = inputMode == InputMode.Por100g,
                onClick  = { inputMode = InputMode.Por100g; dismissBanner() },
                shape    = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text(stringResource(R.string.recipe_mode_per_100g)) }
        }
        Spacer(Modifier.height(Spacing.lg))
        if (inputMode == InputMode.PorRacion) {
            MacroInputRow(stringResource(R.string.recipe_servings_field), servings, { servings = it; dismissBanner() })
        } else {
            Text(
                stringResource(R.string.recipe_per_100g_note),
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }

        Spacer(Modifier.height(Spacing.xxl))

        // ── VALORES NUTRICIONALES ─────────────────────────────────────────
        FormSectionHeader(
            if (inputMode == InputMode.PorRacion) stringResource(R.string.recipe_section_nutrition_serving)
            else stringResource(R.string.recipe_section_nutrition_100g)
        )
        Spacer(Modifier.height(Spacing.lg))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (inputMode == InputMode.PorRacion) {
                MacroInputRow(stringResource(R.string.macro_fullname_protein),      protein, { protein = it; dismissBanner() }, "g",    required = true)
                MacroInputRow(stringResource(R.string.macro_fullname_carbs), carbs,   { carbs = it;   dismissBanner() }, "g",    required = true)
                MacroInputRow(stringResource(R.string.macro_fullname_fat),        fat,     { fat = it;     dismissBanner() }, "g",    required = true)
                MacroInputRow(stringResource(R.string.macro_fullname_calories),      kcal,    { kcal = it;    dismissBanner() }, "kcal", required = true)
                val kcalEntered = kcal.toIntOrNull()
                if (kcalEntered != null && calculatedKcal > 0 &&
                    abs(kcalEntered - calculatedKcal) > calculatedKcal * 0.10f) {
                    Surface(color = FatColor.copy(alpha = 0.12f), shape = AppShapeSm) {
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null,
                                modifier = Modifier.size(14.dp), tint = FatColor)
                            Text(
                                stringResource(R.string.macro_warning_kcal, calculatedKcal),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else {
                MacroInputRow(stringResource(R.string.macro_fullname_protein),      prot100,  { prot100 = it;  dismissBanner() }, "g",    required = true)
                MacroInputRow(stringResource(R.string.macro_fullname_carbs), carbs100, { carbs100 = it; dismissBanner() }, "g",    required = true)
                MacroInputRow(stringResource(R.string.macro_fullname_fat),        fat100,   { fat100 = it;   dismissBanner() }, "g",    required = true)
                MacroInputRow(stringResource(R.string.macro_fullname_calories),      kcal100,  { kcal100 = it;  dismissBanner() }, "kcal", required = true)
                val kcal100Entered = kcal100.toIntOrNull()
                if (kcal100Entered != null && calculatedKcal100 > 0 &&
                    abs(kcal100Entered - calculatedKcal100) > calculatedKcal100 * 0.10f) {
                    Surface(color = FatColor.copy(alpha = 0.12f), shape = AppShapeSm) {
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null,
                                modifier = Modifier.size(14.dp), tint = FatColor)
                            Text(
                                stringResource(R.string.macro_warning_kcal, calculatedKcal100),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

    }
    // ── GUARDAR (sticky bottom) ────────────────────────────────────────
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Button(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape    = AppShapeMd,
            onClick  = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (name.isBlank()) return@Button
                if (inputMode == InputMode.PorRacion) {
                    val k = kcal.toIntOrNull() ?: return@Button
                    val p = protein.toFloatOrNull() ?: return@Button
                    val c = carbs.toFloatOrNull() ?: return@Button
                    val f = fat.toFloatOrNull() ?: return@Button
                    onSave(FoodItemEntity(
                        id             = editingRecipe?.id ?: 0,
                        name           = name.trim(),
                        servings       = servings.toFloatOrNull() ?: 1f,
                        kcalPerServing = k,
                        protein        = p,
                        carbs          = c,
                        fat            = f,
                        ingredients    = notes.trim(),
                        servingMode    = ServingMode.PER_SERVING.name,
                        createdAt      = editingRecipe?.createdAt ?: System.currentTimeMillis(),
                        isFavorite     = editingRecipe?.isFavorite ?: false,
                        usageCount     = editingRecipe?.usageCount ?: 0,
                        lastUsedAt     = editingRecipe?.lastUsedAt
                    ))
                } else {
                    val k = kcal100.toIntOrNull() ?: return@Button
                    val p = prot100.toFloatOrNull() ?: return@Button
                    val c = carbs100.toFloatOrNull() ?: return@Button
                    val f = fat100.toFloatOrNull() ?: return@Button
                    onSave(FoodItemEntity(
                        id             = editingRecipe?.id ?: 0,
                        name           = name.trim(),
                        servings       = 1f,
                        kcalPerServing = k,
                        protein        = p,
                        carbs          = c,
                        fat            = f,
                        ingredients    = notes.trim(),
                        servingMode    = ServingMode.PER_100G.name,
                        createdAt      = editingRecipe?.createdAt ?: System.currentTimeMillis(),
                        isFavorite     = editingRecipe?.isFavorite ?: false,
                        usageCount     = editingRecipe?.usageCount ?: 0,
                        lastUsedAt     = editingRecipe?.lastUsedAt
                    ))
                }
            }
        ) {
            Text(
                if (editingRecipe != null) stringResource(R.string.recipe_save_changes) else stringResource(R.string.recipe_save),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
    } // outer Column
}

// ─── AI tab ────────────────────────────────────────────────────────────────────

@Composable
private fun AiTab(
    geminiService: GeminiService,
    messages: List<AiMsg>,
    onMessagesChange: (List<AiMsg>) -> Unit,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    isLoading: Boolean,
    onIsLoadingChange: (Boolean) -> Unit,
    onExtractedRecipeChange: (MacroResult?) -> Unit
) {
    val rateLimitMsg = stringResource(R.string.vm_chat_error_rate_limit)
    val aiErrorFmt   = stringResource(R.string.recipe_ai_error)
    val aiErrorRetry = stringResource(R.string.recipe_ai_error_retry)
    val scope        = rememberCoroutineScope()
    val listState    = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun send() {
        val text = inputText.trim()
        if (text.isBlank() || isLoading) return
        val updatedMessages = messages + AiMsg(text, isUser = true)
        onMessagesChange(updatedMessages)
        onInputTextChange("")
        onIsLoadingChange(true)
        scope.launch {
            val history = updatedMessages
                .filter { !it.isInitial }
                .map { (if (it.isUser) "user" else "model") to it.text }
            geminiService.chatRecipe(history)
                .onSuccess { response ->
                    val display = cleanAiText(response)
                    onMessagesChange(updatedMessages + AiMsg(display, isUser = false))
                    extractRecipe(response)?.let { onExtractedRecipeChange(it) }
                }
                .onFailure { e ->
                    onMessagesChange(updatedMessages + AiMsg(
                        if (e is RateLimitException) rateLimitMsg
                        else String.format(aiErrorFmt, e.message ?: aiErrorRetry),
                        isUser = false
                    ))
                }
            onIsLoadingChange(false)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state           = listState,
            modifier        = Modifier.weight(1f),
            contentPadding  = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            items(messages) { msg -> AiChatBubble(msg) }
            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier              = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Text(stringResource(R.string.recipe_ai_analyzing), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            OutlinedTextField(
                value         = inputText,
                onValueChange = onInputTextChange,
                modifier      = Modifier.weight(1f),
                placeholder   = { Text(stringResource(R.string.recipe_ai_input_hint)) },
                maxLines      = 3,
                shape         = AppShapeLg
            )
            IconButton(
                onClick  = { send() },
                enabled  = inputText.isNotBlank() && !isLoading,
                modifier = Modifier
                    .size(48.dp)
                    .clip(AppShapeMd)
                    .background(
                        if (inputText.isNotBlank() && !isLoading)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.chat_cd_send),
                    tint = if (inputText.isNotBlank() && !isLoading)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AiChatBubble(msg: AiMsg) {
    val isUser = msg.isUser
    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart    = if (isUser) 16.dp else 4.dp,
                topEnd      = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd   = 16.dp
            ),
            color    = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text     = msg.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color    = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                style    = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
