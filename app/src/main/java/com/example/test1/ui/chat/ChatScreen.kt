package com.example.test1.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import com.example.test1.data.db.entity.RecipeEntity
import com.example.test1.ui.recipe.RecipeCreationDialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.test1.BuildConfig
import com.example.test1.MacroApp
import com.example.test1.data.api.IngredientMacro
import com.example.test1.data.api.MacroResult
import com.example.test1.ui.components.MacroPill
import com.example.test1.ui.theme.*
import java.io.File

private val CHAT_GREETINGS = listOf(
    "¿Qué has comido? Cuéntame y calculo los macros al momento.",
    "¡Hola! ¿Lista para registrar? Describe tu comida o manda una foto.",
    "¿Qué hay de comer hoy? Dímelo y lo apunto.",
    "¿Snack, comida o cena? Cuéntame y lo registramos juntos.",
    "¿Comiste algo rico? Descríbelo y te digo los macros.",
    "Puedes escribirme lo que has comido o enviar una foto directamente.",
    "¿Algo que quieras registrar? Soy todo oídos.",
    "¿Ya desayunaste? Cuéntame qué tomaste y lo añadimos.",
)

private fun formatChatDate(dateStr: String): String {
    val date    = java.time.LocalDate.parse(dateStr)
    val today   = java.time.LocalDate.now()
    val dayName = date.dayOfWeek
        .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("es"))
        .replaceFirstChar { it.uppercase() }
    val day   = date.dayOfMonth
    val month = date.month
        .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("es"))
    return when {
        date == today              -> "Hoy · $dayName $day de $month"
        date == today.minusDays(1) -> "Ayer · $dayName $day de $month"
        date == today.plusDays(1)  -> "Mañana · $dayName $day de $month"
        else                       -> "$dayName $day de $month"
    }
}

private data class EditIngredient(
    val uid: Long = System.nanoTime(),
    val name: String,
    val cal: String,
    val prot: String,
    val carb: String,
    val fat: String
)

@Composable
fun ChatScreen(onNavigateToSettings: () -> Unit) {
    val app      = LocalContext.current.applicationContext as MacroApp
    val vm: ChatViewModel = viewModel {
        ChatViewModel(
            app.foodRepository,
            app.recipeRepository,
            app.geminiService,
            app.selectedDate,
            app.chatMessageRepository,
            BuildConfig.MAX_CHAT_DAYS
        )
    }
    val uiState      by vm.uiState.collectAsState()
    val selectedDate by app.selectedDate.collectAsState()
    val greeting     = remember { CHAT_GREETINGS.random() }
    val today        = remember { java.time.LocalDate.now().toString() }
    val isPastDay    = selectedDate < today
    val listState    = rememberLazyListState()
    val keyboard     = LocalSoftwareKeyboardController.current
    val context      = LocalContext.current
    val haptics      = LocalHapticFeedback.current

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size - 1)
    }

    // ── Camera / gallery launchers ──────────────────────────────────────────
    var tempPhotoUri   by remember { mutableStateOf<android.net.Uri?>(null) }
    var showSourceMenu by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) tempPhotoUri?.let { vm.onImageCaptured(it, context) }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { vm.onImageCaptured(it, context) } }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "camera").also { it.mkdirs() }
                .let { File(it, "photo_${System.currentTimeMillis()}.jpg") }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            tempPhotoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    fun launchCamera() {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                val file = File(context.cacheDir, "camera").also { it.mkdirs() }
                    .let { File(it, "photo_${System.currentTimeMillis()}.jpg") }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                tempPhotoUri = uri
                cameraLauncher.launch(uri)
            }
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ── Debug dialog ────────────────────────────────────────────────────────
    uiState.debugInfo?.let { info ->
        AlertDialog(
            onDismissRequest = vm::clearDebugInfo,
            title   = { Text("Debug — respuesta Gemini") },
            text    = { Text(info, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = vm::clearDebugInfo) { Text("Cerrar") } }
        )
    }

    val actionsEnabled = !uiState.isLoading && uiState.editingEntry == null

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
                        .padding(start = Spacing.lg, end = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Registro",
                        style    = MaterialTheme.typography.titleMedium,
                        color    = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider(
                    color     = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state               = listState,
                modifier            = Modifier.weight(1f).padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                contentPadding      = PaddingValues(vertical = Spacing.sm)
            ) {
                item(key = "greeting") {
                    GreetingCard(
                        dateLabel = formatChatDate(selectedDate),
                        greeting  = greeting
                    )
                }
                items(uiState.messages, key = { it.id }) { msg ->
                    ChatBubble(
                        msg            = msg,
                        actionsEnabled = actionsEnabled,
                        haptics        = haptics,
                        onSaveAsRecipe = { recipe -> vm.saveAsRecipe(recipe) },
                        onDiscard      = { vm.discardEntry(msg.foodEntryId!!, msg.id) },
                        onEditManually = { newMacro -> vm.updateEntryManually(msg.foodEntryId!!, msg.id, newMacro) },
                        onStartAIEdit  = { vm.startAIEdit(msg.foodEntryId!!, msg.id, msg.macroResult!!) }
                    )
                }
                if (uiState.isLoading) {
                    item { TypingIndicator() }
                }
            }

            // ── AI edit mode banner ─────────────────────────────────────────
            uiState.editingEntry?.let { editing ->
                Surface(
                    color    = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier          = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = null,
                            modifier           = Modifier.size(15.dp),
                            tint               = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            "Corrigiendo: ${editing.originalMacro.name}",
                            style    = MaterialTheme.typography.bodySmall,
                            color    = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick        = vm::cancelAIEdit,
                            contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 0.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Cancelar",
                                modifier           = Modifier.size(14.dp),
                                tint               = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                "Cancelar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // ── Input bar ───────────────────────────────────────────────────
            Column {
                HorizontalDivider(
                    color     = MaterialTheme.colorScheme.outline,
                    thickness = 0.5.dp
                )
                if (isPastDay) {
                    Surface(
                        color    = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.History,
                                contentDescription = null,
                                modifier           = Modifier.size(16.dp),
                                tint               = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                "Historial — solo lectura",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                IconButton(
                                    onClick = { showSourceMenu = true },
                                    enabled = !uiState.isLoading && uiState.editingEntry == null
                                ) {
                                    Icon(Icons.Filled.PhotoCamera, contentDescription = "Foto")
                                }
                                DropdownMenu(
                                    expanded         = showSourceMenu,
                                    onDismissRequest = { showSourceMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text    = { Text("Cámara") },
                                        onClick = { showSourceMenu = false; launchCamera() }
                                    )
                                    DropdownMenuItem(
                                        text    = { Text("Galería") },
                                        onClick = {
                                            showSourceMenu = false
                                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value         = uiState.inputText,
                                onValueChange = vm::onInputChange,
                                modifier      = Modifier.weight(1f),
                                placeholder   = {
                                    Text(
                                        if (uiState.editingEntry != null) "Describe la corrección…" else "Describe tu comida…",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                textStyle       = MaterialTheme.typography.bodyMedium,
                                shape           = AppShapeLg,
                                maxLines        = 3,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { vm.sendMessage(); keyboard?.hide() }),
                                enabled         = !uiState.isLoading
                            )

                            Spacer(Modifier.width(Spacing.xs))
                            FilledIconButton(
                                onClick  = { vm.sendMessage(); keyboard?.hide() },
                                enabled  = uiState.inputText.isNotBlank() && !uiState.isLoading,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatBubble(
    msg: ChatMessage,
    actionsEnabled: Boolean,
    haptics: HapticFeedback,
    onSaveAsRecipe: (RecipeEntity) -> Unit,
    onDiscard: () -> Unit,
    onEditManually: (MacroResult) -> Unit,
    onStartAIEdit: () -> Unit
) {
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showEditSheet      by remember { mutableStateOf(false) }

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (msg.isUser) {
            Surface(
                color = if (msg.isImageMessage) MaterialTheme.colorScheme.secondaryContainer
                        else                    MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16, 4, 16, 16)
            ) {
                Row(
                    modifier              = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    if (msg.isImageMessage) {
                        Icon(
                            Icons.Filled.PhotoCamera,
                            contentDescription = null,
                            modifier           = Modifier.size(16.dp),
                            tint               = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        msg.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (msg.isImageMessage) MaterialTheme.colorScheme.onSecondaryContainer
                                else                    MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth(0.88f)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4, 16, 16, 16)
                ) {
                    Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = 10.dp)) {
                        Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                        msg.macroResult?.let { macro ->
                            if (macro.ingredients.isNotEmpty()) {
                                macro.ingredients.forEach { ing ->
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        ing.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(Spacing.xs))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                        modifier              = Modifier.fillMaxWidth()
                                    ) {
                                        MacroPill(MacroType.CALORIES, "${ing.cal}",   modifier = Modifier.weight(1f))
                                        MacroPill(MacroType.PROTEIN,  "${ing.prot}g", modifier = Modifier.weight(1f))
                                        MacroPill(MacroType.CARBS,    "${ing.carb}g", modifier = Modifier.weight(1f))
                                        MacroPill(MacroType.FAT,      "${ing.fat}g",  modifier = Modifier.weight(1f))
                                    }
                                }
                                Spacer(Modifier.height(Spacing.sm))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                            AppShapeXs
                                        )
                                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                        Text(
                                            "TOTAL",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                            modifier              = Modifier.fillMaxWidth()
                                        ) {
                                            MacroPill(MacroType.CALORIES, "${macro.cal}",   modifier = Modifier.weight(1f))
                                            MacroPill(MacroType.PROTEIN,  "${macro.prot}g", modifier = Modifier.weight(1f))
                                            MacroPill(MacroType.CARBS,    "${macro.carb}g", modifier = Modifier.weight(1f))
                                            MacroPill(MacroType.FAT,      "${macro.fat}g",  modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            } else {
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    modifier              = Modifier.fillMaxWidth()
                                ) {
                                    MacroPill(MacroType.CALORIES, "${macro.cal}",   modifier = Modifier.weight(1f))
                                    MacroPill(MacroType.PROTEIN,  "${macro.prot}g", modifier = Modifier.weight(1f))
                                    MacroPill(MacroType.CARBS,    "${macro.carb}g", modifier = Modifier.weight(1f))
                                    MacroPill(MacroType.FAT,      "${macro.fat}g",  modifier = Modifier.weight(1f))
                                }
                            }

                            if (msg.foodEntryId != null) {
                                Spacer(Modifier.height(10.dp))
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    TextButton(
                                        onClick        = { showDiscardConfirm = true },
                                        enabled        = actionsEnabled,
                                        contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = 2.dp),
                                        colors         = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Icon(Icons.Filled.Delete, null, Modifier.size(15.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text("Descartar", style = MaterialTheme.typography.labelSmall)
                                    }
                                    TextButton(
                                        onClick        = { showEditSheet = true },
                                        enabled        = actionsEnabled,
                                        contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Filled.Edit, null, Modifier.size(15.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text("Editar", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Button(
                                        onClick        = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onStartAIEdit()
                                        },
                                        enabled        = actionsEnabled,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = Spacing.xs),
                                        shape          = AppShapeMd
                                    ) {
                                        Icon(Icons.Filled.AutoFixHigh, null, Modifier.size(15.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text("Ajustar", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            Spacer(Modifier.height(2.dp))
                            SaveAsRecipeButton(macro, onSaveAsRecipe)

                            if (showDiscardConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showDiscardConfirm = false },
                                    title   = { Text("¿Descartar registro?") },
                                    text    = { Text("Se eliminará \"${macro.name}\" del registro de hoy.") },
                                    confirmButton = {
                                        TextButton(
                                            onClick = { showDiscardConfirm = false; onDiscard() },
                                            colors  = ButtonDefaults.textButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) { Text("Descartar") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDiscardConfirm = false }) { Text("Cancelar") }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditSheet && msg.macroResult != null) {
        EditEntrySheet(
            macro     = msg.macroResult,
            onDismiss = { showEditSheet = false },
            onSave    = { newMacro ->
                showEditSheet = false
                onEditManually(newMacro)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditEntrySheet(
    macro: MacroResult,
    onDismiss: () -> Unit,
    onSave: (MacroResult) -> Unit
) {
    val hasIngredients = macro.ingredients.isNotEmpty()
    var name by remember { mutableStateOf(macro.name) }

    var cal  by remember { mutableStateOf(macro.cal.toString()) }
    var prot by remember { mutableStateOf(macro.prot.toString()) }
    var carb by remember { mutableStateOf(macro.carb.toString()) }
    var fat  by remember { mutableStateOf(macro.fat.toString()) }

    val editIngredients = remember {
        mutableStateListOf(*macro.ingredients.map { ing ->
            EditIngredient(name = ing.name, cal = ing.cal.toString(), prot = ing.prot.toString(), carb = ing.carb.toString(), fat = ing.fat.toString())
        }.toTypedArray())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text("Editar registro", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text("Nombre del plato") },
                singleLine    = true,
                shape         = AppShapeMd,
                modifier      = Modifier.fillMaxWidth()
            )

            if (hasIngredients) {
                Text(
                    "Ingredientes",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                editIngredients.forEachIndexed { index, ing ->
                    key(ing.uid) {
                        IngredientEditorCard(
                            ingredient = ing,
                            canDelete  = editIngredients.size > 1,
                            onUpdate   = { editIngredients[index] = it },
                            onDelete   = { editIngredients.removeAt(index) }
                        )
                    }
                }

                OutlinedButton(
                    onClick  = {
                        editIngredients.add(EditIngredient(name = "", cal = "0", prot = "0", carb = "0", fat = "0"))
                    },
                    shape    = AppShapeMd,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Añadir ingrediente")
                }

                val totalCal  = editIngredients.sumOf { it.cal.toIntOrNull() ?: 0 }
                val totalProt = editIngredients.sumOf { it.prot.toFloatOrNull()?.toDouble() ?: 0.0 }.toFloat()
                val totalCarb = editIngredients.sumOf { it.carb.toFloatOrNull()?.toDouble() ?: 0.0 }.toFloat()
                val totalFat  = editIngredients.sumOf { it.fat.toFloatOrNull()?.toDouble() ?: 0.0 }.toFloat()

                HorizontalDivider()
                Text(
                    "Total calculado",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    MacroPill(MacroType.CALORIES, "$totalCal",     modifier = Modifier.weight(1f))
                    MacroPill(MacroType.PROTEIN,  "${totalProt}g", modifier = Modifier.weight(1f))
                    MacroPill(MacroType.CARBS,    "${totalCarb}g", modifier = Modifier.weight(1f))
                    MacroPill(MacroType.FAT,      "${totalFat}g",  modifier = Modifier.weight(1f))
                }

                Button(
                    onClick  = {
                        val ingredientMacros = editIngredients.mapIndexed { i, ing ->
                            IngredientMacro(
                                name = ing.name.trim().ifBlank { "Ingrediente ${i + 1}" },
                                cal  = ing.cal.toIntOrNull() ?: 0,
                                prot = ing.prot.toFloatOrNull() ?: 0f,
                                carb = ing.carb.toFloatOrNull() ?: 0f,
                                fat  = ing.fat.toFloatOrNull() ?: 0f
                            )
                        }
                        onSave(
                            MacroResult(
                                name        = name.trim().ifBlank { macro.name },
                                cal         = totalCal,
                                prot        = totalProt,
                                carb        = totalCarb,
                                fat         = totalFat,
                                ingredients = ingredientMacros
                            )
                        )
                    },
                    shape    = AppShapeMd,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Guardar cambios", style = MaterialTheme.typography.labelLarge) }

            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(value = cal,  onValueChange = { cal = it },  label = { Text("kcal") },          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),  singleLine = true, shape = AppShapeMd, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = prot, onValueChange = { prot = it }, label = { Text("Proteína (g)") },  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = AppShapeMd, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(value = carb, onValueChange = { carb = it }, label = { Text("Carbohid. (g)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = AppShapeMd, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = fat,  onValueChange = { fat = it },  label = { Text("Grasa (g)") },     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = AppShapeMd, modifier = Modifier.weight(1f))
                }
                Button(
                    onClick  = {
                        onSave(
                            MacroResult(
                                name = name.trim().ifBlank { macro.name },
                                cal  = cal.toIntOrNull()   ?: macro.cal,
                                prot = prot.toFloatOrNull() ?: macro.prot,
                                carb = carb.toFloatOrNull() ?: macro.carb,
                                fat  = fat.toFloatOrNull()  ?: macro.fat
                            )
                        )
                    },
                    shape    = AppShapeMd,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Guardar cambios", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
private fun IngredientEditorCard(
    ingredient: EditIngredient,
    canDelete: Boolean,
    onUpdate: (EditIngredient) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = AppShapeMd
    ) {
        Column(
            modifier            = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                OutlinedTextField(
                    value         = ingredient.name,
                    onValueChange = { onUpdate(ingredient.copy(name = it)) },
                    label         = { Text("Ingrediente") },
                    singleLine    = true,
                    shape         = AppShapeMd,
                    modifier      = Modifier.weight(1f)
                )
                if (canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Eliminar",
                            tint               = MaterialTheme.colorScheme.error,
                            modifier           = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                OutlinedTextField(value = ingredient.cal,  onValueChange = { onUpdate(ingredient.copy(cal  = it)) }, label = { Text("kcal") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),  singleLine = true, shape = AppShapeMd, modifier = Modifier.weight(1f))
                OutlinedTextField(value = ingredient.prot, onValueChange = { onUpdate(ingredient.copy(prot = it)) }, label = { Text("Prot") },  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = AppShapeMd, modifier = Modifier.weight(1f))
                OutlinedTextField(value = ingredient.carb, onValueChange = { onUpdate(ingredient.copy(carb = it)) }, label = { Text("Carb") },  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = AppShapeMd, modifier = Modifier.weight(1f))
                OutlinedTextField(value = ingredient.fat,  onValueChange = { onUpdate(ingredient.copy(fat  = it)) }, label = { Text("Grasa") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = AppShapeMd, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SaveAsRecipeButton(macro: MacroResult, onSave: (RecipeEntity) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val app = LocalContext.current.applicationContext as MacroApp

    AssistChip(
        onClick     = { showDialog = true },
        label       = { Text("Guardar como receta", style = MaterialTheme.typography.labelSmall) },
        leadingIcon = {
            Icon(Icons.Filled.Bookmark, contentDescription = null, modifier = Modifier.size(14.dp))
        }
    )

    if (showDialog) {
        RecipeCreationDialog(
            geminiService = app.geminiService,
            initialValues = macro,
            onSave        = { recipe -> onSave(recipe); showDialog = false },
            onDismiss     = { showDialog = false }
        )
    }
}

@Composable
private fun GreetingCard(dateLabel: String, greeting: String) {
    Column(
        modifier            = Modifier.fillMaxWidth(0.88f),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = AppShapeXs
        ) {
            Text(
                dateLabel,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = Spacing.xs),
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(4, 16, 16, 16)
        ) {
            Text(
                greeting,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = 10.dp),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(4, 16, 16, 16)
        ) {
            Row(
                modifier              = Modifier.padding(horizontal = Spacing.lg, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                repeat(3) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}
