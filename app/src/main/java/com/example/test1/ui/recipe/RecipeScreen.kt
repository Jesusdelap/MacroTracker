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
import com.example.test1.MacroApp
import com.example.test1.data.api.GeminiService
import com.example.test1.data.api.MacroResult
import com.example.test1.data.db.entity.RecipeEntity
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
fun RecipeScreen() {
    val app = LocalContext.current.applicationContext as MacroApp
    val vm: RecipeViewModel = viewModel {
        RecipeViewModel(app.recipeRepository, app.foodRepository)
    }
    val uiState by vm.uiState.collectAsState()
    var showAddDialog  by remember { mutableStateOf(false) }
    var editingRecipe  by remember { mutableStateOf<RecipeEntity?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                            "Recetario",
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onBackground
                        )
                        if (uiState.recipes.isNotEmpty()) {
                            Text(
                                "${uiState.recipes.size} receta${if (uiState.recipes.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                HorizontalDivider(
                    color     = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick        = { showAddDialog = true },
                icon           = { Icon(Icons.Filled.Add, contentDescription = null) },
                text           = { Text("Nueva receta") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor   = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value         = uiState.searchQuery,
                onValueChange = vm::onSearchChange,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = 10.dp),
                placeholder  = { Text("Buscar receta…") },
                leadingIcon  = {
                    Icon(Icons.Filled.Search, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    AnimatedVisibility(visible = uiState.searchQuery.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                        IconButton(onClick = { vm.onSearchChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Limpiar",
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
                                        message     = "\"${recipe.name}\" eliminada",
                                        actionLabel = "Deshacer",
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
                    text  = if (hasSearch) "Sin resultados para \"$query\""
                            else "Aún no tienes recetas guardadas",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!hasSearch) {
                    Text(
                        text  = "Crea tu primera receta pulsando el botón",
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
    recipe: RecipeEntity,
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
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar",
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
                                text  = "${recipe.kcalPerServing} kcal${if (recipe.isPer100g) " / 100g" else ""}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.macroColors.calories
                            )
                            val subtitle = buildString {
                                if (!recipe.isPer100g) append("· ${recipe.servings.toInt()} rac. ")
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
                                contentDescription = if (recipe.isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                                modifier = Modifier.size(16.dp),
                                tint     = if (recipe.isFavorite) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                        IconButton(
                            onClick  = onEdit,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar",
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
                            Text("Añadir", style = MaterialTheme.typography.labelLarge)
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
                                "por 100g",
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
    recipe: RecipeEntity,
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
                    "¿Cuántos gramos vas a tomar?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value         = grams,
                    onValueChange = { grams = it },
                    label         = { Text("Gramos") },
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
            ) { Text("Añadir") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ─── Full-screen creation / edit dialog ────────────────────────────────────────

@Composable
internal fun RecipeCreationDialog(
    geminiService: GeminiService,
    initialValues: MacroResult? = null,
    editingRecipe: RecipeEntity? = null,
    onSave: (RecipeEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val isEditing    = editingRecipe != null
    var selectedTab  by remember { mutableIntStateOf(0) }

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
                                    isEditing       -> "Editar receta"
                                    initialValues != null -> "Guardar como receta"
                                    else            -> "Nueva receta"
                                },
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Cerrar")
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
                                text     = { Text("Manual") },
                                icon     = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick  = { selectedTab = 1 },
                                text     = { Text("Con IA") },
                                icon     = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                    if (selectedTab == 0 || isEditing) {
                        ManualTab(initialValues = initialValues, editingRecipe = editingRecipe, onSave = onSave)
                    } else {
                        AiTab(geminiService = geminiService, onSave = onSave)
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
    editingRecipe: RecipeEntity? = null,
    onSave: (RecipeEntity) -> Unit
) {
    val haptics = LocalHapticFeedback.current

    // Pre-fill mode from editing recipe
    val startAs100g = editingRecipe?.isPer100g == true
    var inputMode by remember { mutableStateOf(if (startAs100g) InputMode.Por100g else InputMode.PorRacion) }

    // PorRacion fields
    var name    by remember { mutableStateOf(editingRecipe?.name ?: initialValues?.name ?: "") }
    var notes   by remember { mutableStateOf(editingRecipe?.ingredients ?: "") }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = Spacing.xxl)
    ) {
        // ── INFORMACIÓN BÁSICA ────────────────────────────────────────────
        FormSectionHeader("INFORMACIÓN BÁSICA")
        Spacer(Modifier.height(Spacing.lg))
        RecipeTextField(
            value = name, onValueChange = { name = it },
            placeholder = "Nombre de la receta", required = true, singleLine = true
        )
        Spacer(Modifier.height(Spacing.lg))
        RecipeTextField(
            value = notes, onValueChange = { notes = it },
            placeholder = "Notas / ingredientes (opcional)", minLines = 3
        )

        Spacer(Modifier.height(Spacing.xxl))

        // ── PORCIONES ─────────────────────────────────────────────────────
        FormSectionHeader("PORCIONES")
        Spacer(Modifier.height(Spacing.lg))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = inputMode == InputMode.PorRacion,
                onClick  = { inputMode = InputMode.PorRacion },
                shape    = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("Por ración") }
            SegmentedButton(
                selected = inputMode == InputMode.Por100g,
                onClick  = { inputMode = InputMode.Por100g },
                shape    = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("Por 100g") }
        }
        Spacer(Modifier.height(Spacing.lg))
        if (inputMode == InputMode.PorRacion) {
            MacroInputRow("Raciones", servings, { servings = it })
        } else {
            Text(
                "Se pedirá el peso al añadir esta receta al registro.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }

        Spacer(Modifier.height(Spacing.xxl))

        // ── VALORES NUTRICIONALES ─────────────────────────────────────────
        FormSectionHeader(
            if (inputMode == InputMode.PorRacion) "VALORES NUTRICIONALES (POR RACIÓN)" else "VALORES NUTRICIONALES (POR 100G)"
        )
        Spacer(Modifier.height(Spacing.lg))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (inputMode == InputMode.PorRacion) {
                MacroInputRow("Proteína",      protein, { protein = it }, "g",    required = true)
                MacroInputRow("Carbohidratos", carbs,   { carbs = it },   "g",    required = true)
                MacroInputRow("Grasas",        fat,     { fat = it },     "g",    required = true)
                MacroInputRow("Calorías",      kcal,    { kcal = it },    "kcal", required = true)
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
                                "Las macros calculan $calculatedKcal kcal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else {
                MacroInputRow("Proteína",      prot100,  { prot100 = it },  "g",    required = true)
                MacroInputRow("Carbohidratos", carbs100, { carbs100 = it }, "g",    required = true)
                MacroInputRow("Grasas",        fat100,   { fat100 = it },   "g",    required = true)
                MacroInputRow("Calorías",      kcal100,  { kcal100 = it },  "kcal", required = true)
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
                                "Las macros calculan $calculatedKcal100 kcal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.xxl))

        // ── GUARDAR ───────────────────────────────────────────────────────
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
                    onSave(RecipeEntity(
                        id             = editingRecipe?.id ?: 0,
                        name           = name.trim(),
                        servings       = servings.toFloatOrNull() ?: 1f,
                        kcalPerServing = k,
                        protein        = p,
                        carbs          = c,
                        fat            = f,
                        ingredients    = notes.trim(),
                        isPer100g      = false,
                        createdAt      = editingRecipe?.createdAt ?: System.currentTimeMillis()
                    ))
                } else {
                    val k = kcal100.toIntOrNull() ?: return@Button
                    val p = prot100.toFloatOrNull() ?: return@Button
                    val c = carbs100.toFloatOrNull() ?: return@Button
                    val f = fat100.toFloatOrNull() ?: return@Button
                    onSave(RecipeEntity(
                        id             = editingRecipe?.id ?: 0,
                        name           = name.trim(),
                        servings       = 1f,
                        kcalPerServing = k,
                        protein        = p,
                        carbs          = c,
                        fat            = f,
                        ingredients    = notes.trim(),
                        isPer100g      = true,
                        createdAt      = editingRecipe?.createdAt ?: System.currentTimeMillis()
                    ))
                }
            }
        ) {
            Text(
                if (editingRecipe != null) "Guardar cambios" else "Guardar receta",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

// ─── AI tab ────────────────────────────────────────────────────────────────────

@Composable
private fun AiTab(geminiService: GeminiService, onSave: (RecipeEntity) -> Unit) {
    val greeting = "¡Hola! Cuéntame la receta que quieres crear: nombre e ingredientes con sus cantidades."
    var messages       by remember { mutableStateOf(listOf(AiMsg(greeting, isUser = false, isInitial = true))) }
    var inputText      by remember { mutableStateOf("") }
    var isLoading      by remember { mutableStateOf(false) }
    var extractedRecipe by remember { mutableStateOf<MacroResult?>(null) }
    var aiName         by remember { mutableStateOf("") }
    var aiNotes        by remember { mutableStateOf("") }
    var aiPer100g      by remember { mutableStateOf(false) }
    val haptics        = LocalHapticFeedback.current
    val scope          = rememberCoroutineScope()
    val listState      = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun send() {
        val text = inputText.trim()
        if (text.isBlank() || isLoading) return
        messages   = messages + AiMsg(text, isUser = true)
        inputText  = ""
        isLoading  = true
        scope.launch {
            val history = messages
                .filter { !it.isInitial }
                .map { (if (it.isUser) "user" else "model") to it.text }
            geminiService.chatRecipe(history)
                .onSuccess { response ->
                    val display = cleanAiText(response)
                    messages = messages + AiMsg(display, isUser = false)
                    extractRecipe(response)?.let { recipe ->
                        extractedRecipe = recipe
                        if (aiName.isBlank()) aiName = recipe.name
                    }
                }
                .onFailure { e ->
                    messages = messages + AiMsg(
                        if (e.message?.contains("Límite") == true) e.message!!
                        else "Error: ${e.message ?: "Inténtalo de nuevo"}",
                        isUser = false
                    )
                }
            isLoading = false
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
                                Text("Analizando…", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Recipe detected card
        AnimatedVisibility(visible = extractedRecipe != null) {
            extractedRecipe?.let { recipe ->
                HorizontalDivider()
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
                    Column(
                        modifier            = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null,
                                modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Receta detectada", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedTextField(
                            value = aiName, onValueChange = { aiName = it },
                            label = { Text("Nombre") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), shape = AppShapeMd
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            MacroPill(MacroType.CALORIES, "${recipe.cal}")
                            MacroPill(MacroType.PROTEIN,  "${recipe.prot}g")
                            MacroPill(MacroType.CARBS,    "${recipe.carb}g")
                            MacroPill(MacroType.FAT,      "${recipe.fat}g")
                        }
                        // Per-100g toggle
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Valores por 100g", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Se pedirá el peso al añadir al registro",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextTertiary
                                )
                            }
                            Switch(checked = aiPer100g, onCheckedChange = { aiPer100g = it })
                        }
                        OutlinedTextField(
                            value = aiNotes, onValueChange = { aiNotes = it },
                            label = { Text("Notas / Ingredientes") },
                            maxLines = 3, modifier = Modifier.fillMaxWidth(), shape = AppShapeMd
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape    = AppShapeMd,
                            onClick  = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                val finalName = aiName.trim().ifBlank { recipe.name }
                                onSave(RecipeEntity(
                                    name           = finalName,
                                    servings       = 1f,
                                    kcalPerServing = recipe.cal,
                                    protein        = recipe.prot,
                                    carbs          = recipe.carb,
                                    fat            = recipe.fat,
                                    ingredients    = aiNotes.trim(),
                                    isPer100g      = aiPer100g
                                ))
                            }
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(Spacing.sm))
                            Text("Guardar receta", fontWeight = FontWeight.SemiBold)
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
                onValueChange = { inputText = it },
                modifier      = Modifier.weight(1f),
                placeholder   = { Text("Describe tu receta…") },
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
                    contentDescription = "Enviar",
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
