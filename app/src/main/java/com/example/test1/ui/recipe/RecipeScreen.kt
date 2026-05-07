package com.example.test1.ui.recipe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

private data class AiMsg(val text: String, val isUser: Boolean, val isInitial: Boolean = false)

private const val RECIPE_JSON_MARKER = "RECETA_JSON:"
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
    val after = response.substring(idx + RECIPE_JSON_MARKER.length)
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
    var showAddDialog by remember { mutableStateOf(false) }

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
                        .height(56.dp)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Recetario",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
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
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nueva receta") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = vm::onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                placeholder = { Text("Buscar receta...") },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    AnimatedVisibility(
                        visible = uiState.searchQuery.isNotBlank(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(onClick = { vm.onSearchChange("") }) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = "Limpiar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            if (uiState.recipes.isEmpty()) {
                EmptyState(hasSearch = uiState.searchQuery.isNotBlank(), query = uiState.searchQuery)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.recipes, key = { it.id }) { recipe ->
                        RecipeCard(
                            recipe = recipe,
                            onAddToToday = { vm.addToToday(recipe) },
                            onDelete = { vm.deleteRecipe(recipe) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        RecipeCreationDialog(
            geminiService = app.geminiService,
            onSave = { recipe -> vm.addRecipe(recipe); showAddDialog = false },
            onDismiss = { showAddDialog = false }
        )
    }
}

// ─── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(hasSearch: Boolean, query: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                if (hasSearch) Icons.Filled.SearchOff else Icons.Filled.Book,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                if (hasSearch) "Sin resultados para \"$query\""
                else "Aún no tienes recetas guardadas",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            if (!hasSearch) {
                Text(
                    "Pulsa el botón para crear tu primera receta",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ─── Recipe card ───────────────────────────────────────────────────────────────

@Composable
private fun RecipeCard(recipe: RecipeEntity, onAddToToday: () -> Unit, onDelete: () -> Unit) {
    val dateStr = remember(recipe.createdAt) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(recipe.createdAt))
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Colored accent strip
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(listOf(ProteinColor, CarbColor)),
                        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
            )
            Column(modifier = Modifier.padding(start = 12.dp, end = 14.dp, top = 14.dp, bottom = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            recipe.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.LocalFireDepartment,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = CalorieColor
                            )
                            Text(
                                "${recipe.kcalPerServing} kcal",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CalorieColor
                            )
                            Text(
                                "·  ${recipe.servings.toInt()} ración(es)  ·  $dateStr",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = onAddToToday,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Añadir", fontSize = 12.sp)
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Eliminar",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MacroPill(MacroType.PROTEIN, "${recipe.protein}g")
                    MacroPill(MacroType.CARBS,   "${recipe.carbs}g")
                    MacroPill(MacroType.FAT,     "${recipe.fat}g")
                }

                if (recipe.ingredients.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        recipe.ingredients,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ─── Full-screen creation dialog ───────────────────────────────────────────────

@Composable
internal fun RecipeCreationDialog(
    geminiService: GeminiService,
    initialValues: MacroResult? = null,
    onSave: (RecipeEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                if (initialValues != null) "Guardar como receta" else "Nueva receta",
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
                    PrimaryTabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Manual") },
                            icon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Con IA") },
                            icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                    if (selectedTab == 0) {
                        ManualTab(initialValues = initialValues, onSave = onSave)
                    } else {
                        AiTab(geminiService = geminiService, onSave = onSave)
                    }
                }
            }
        }
    }
}

// ─── Manual tab ────────────────────────────────────────────────────────────────

private enum class InputMode { PorRacion, Por100g }

@Composable
private fun ManualTab(initialValues: MacroResult? = null, onSave: (RecipeEntity) -> Unit) {
    var inputMode by remember { mutableStateOf(InputMode.PorRacion) }
    var name by remember { mutableStateOf(initialValues?.name ?: "") }
    var notes by remember { mutableStateOf("") }

    var servings by remember { mutableStateOf("1") }
    var kcal by remember { mutableStateOf(initialValues?.cal?.toString() ?: "") }
    var protein by remember { mutableStateOf(initialValues?.prot?.toString() ?: "") }
    var carbs by remember { mutableStateOf(initialValues?.carb?.toString() ?: "") }
    var fat by remember { mutableStateOf(initialValues?.fat?.toString() ?: "") }

    var grams by remember { mutableStateOf("") }
    var kcal100 by remember { mutableStateOf("") }
    var prot100 by remember { mutableStateOf("") }
    var carbs100 by remember { mutableStateOf("") }
    var fat100 by remember { mutableStateOf("") }

    val numOpts = KeyboardOptions(keyboardType = KeyboardType.Number)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Nombre *") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Filled.Restaurant, contentDescription = null) }
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = inputMode == InputMode.PorRacion,
                onClick = { inputMode = InputMode.PorRacion },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("Por ración / unidad") }
            SegmentedButton(
                selected = inputMode == InputMode.Por100g,
                onClick = { inputMode = InputMode.Por100g },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("Por 100g") }
        }

        if (inputMode == InputMode.PorRacion) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = servings, onValueChange = { servings = it }, label = { Text("Raciones") }, singleLine = true, keyboardOptions = numOpts, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = kcal, onValueChange = { kcal = it }, label = { Text("kcal *") }, singleLine = true, keyboardOptions = numOpts, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = protein, onValueChange = { protein = it }, label = { Text("Prot (g) *") }, singleLine = true, keyboardOptions = numOpts, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = carbs, onValueChange = { carbs = it }, label = { Text("Carb (g) *") }, singleLine = true, keyboardOptions = numOpts, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = fat, onValueChange = { fat = it }, label = { Text("Grasa (g) *") }, singleLine = true, keyboardOptions = numOpts, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
            }
        } else {
            OutlinedTextField(
                value = grams, onValueChange = { grams = it },
                label = { Text("Gramos a consumir *") }, singleLine = true,
                keyboardOptions = numOpts, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Text("Macros por 100g:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = kcal100, onValueChange = { kcal100 = it }, label = { Text("kcal *") }, singleLine = true, keyboardOptions = numOpts, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = prot100, onValueChange = { prot100 = it }, label = { Text("Prot (g) *") }, singleLine = true, keyboardOptions = numOpts, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = carbs100, onValueChange = { carbs100 = it }, label = { Text("Carb (g) *") }, singleLine = true, keyboardOptions = numOpts, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = fat100, onValueChange = { fat100 = it }, label = { Text("Grasa (g) *") }, singleLine = true, keyboardOptions = numOpts, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
            }
            val gramsF = grams.toFloatOrNull()
            val k = kcal100.toIntOrNull()
            val p = prot100.toFloatOrNull()
            val c = carbs100.toFloatOrNull()
            val f = fat100.toFloatOrNull()
            if (gramsF != null && k != null && p != null && c != null && f != null) {
                val r = gramsF / 100f
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Total para ${grams}g:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MacroPill(MacroType.CALORIES, "${(k * r).toInt()}")
                            MacroPill(MacroType.PROTEIN,  "${"%.1f".format(p * r)}g")
                            MacroPill(MacroType.CARBS,    "${"%.1f".format(c * r)}g")
                            MacroPill(MacroType.FAT,      "${"%.1f".format(f * r)}g")
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            label = { Text("Notas / Ingredientes") },
            maxLines = 4, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) }
        )

        Button(
            modifier = Modifier.fillMaxWidth().height(50.dp),
            onClick = {
                if (name.isBlank()) return@Button
                if (inputMode == InputMode.PorRacion) {
                    val k = kcal.toIntOrNull() ?: return@Button
                    val p = protein.toFloatOrNull() ?: return@Button
                    val c = carbs.toFloatOrNull() ?: return@Button
                    val f = fat.toFloatOrNull() ?: return@Button
                    onSave(RecipeEntity(name = name.trim(), servings = servings.toFloatOrNull() ?: 1f, kcalPerServing = k, protein = p, carbs = c, fat = f, ingredients = notes.trim()))
                } else {
                    val gramsF = grams.toFloatOrNull() ?: return@Button
                    val k = kcal100.toIntOrNull() ?: return@Button
                    val p = prot100.toFloatOrNull() ?: return@Button
                    val c = carbs100.toFloatOrNull() ?: return@Button
                    val f = fat100.toFloatOrNull() ?: return@Button
                    val r = gramsF / 100f
                    onSave(RecipeEntity(name = name.trim(), servings = 1f, kcalPerServing = (k * r).toInt(), protein = p * r, carbs = c * r, fat = f * r, ingredients = notes.trim()))
                }
            },
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Guardar receta", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─── AI tab ────────────────────────────────────────────────────────────────────

@Composable
private fun AiTab(geminiService: GeminiService, onSave: (RecipeEntity) -> Unit) {
    val greeting = "¡Hola! Cuéntame la receta que quieres crear: nombre e ingredientes con sus cantidades."
    var messages by remember { mutableStateOf(listOf(AiMsg(greeting, isUser = false, isInitial = true))) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var extractedRecipe by remember { mutableStateOf<MacroResult?>(null) }
    var aiName by remember { mutableStateOf("") }
    var aiNotes by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun send() {
        val text = inputText.trim()
        if (text.isBlank() || isLoading) return
        messages = messages + AiMsg(text, isUser = true)
        inputText = ""
        isLoading = true
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
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg -> AiChatBubble(msg) }
            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Text("Analizando...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Receta detectada", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedTextField(
                            value = aiName, onValueChange = { aiName = it },
                            label = { Text("Nombre") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MacroPill(MacroType.CALORIES, "${recipe.cal}")
                            MacroPill(MacroType.PROTEIN,  "${recipe.prot}g")
                            MacroPill(MacroType.CARBS,    "${recipe.carb}g")
                            MacroPill(MacroType.FAT,      "${recipe.fat}g")
                        }
                        OutlinedTextField(
                            value = aiNotes, onValueChange = { aiNotes = it },
                            label = { Text("Notas / Ingredientes") },
                            maxLines = 3, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            onClick = {
                                val finalName = aiName.trim().ifBlank { recipe.name }
                                onSave(RecipeEntity(name = finalName, servings = 1f, kcalPerServing = recipe.cal, protein = recipe.prot, carbs = recipe.carb, fat = recipe.fat, ingredients = aiNotes.trim()))
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Guardar receta", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Describe tu receta...") },
                maxLines = 3,
                shape = RoundedCornerShape(16.dp)
            )
            IconButton(
                onClick = { send() },
                enabled = inputText.isNotBlank() && !isLoading,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
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
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = msg.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}