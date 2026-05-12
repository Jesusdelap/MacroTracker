package com.example.test1.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Warning
import com.example.test1.data.db.entity.FoodItemEntity
import com.example.test1.ui.recipe.RecipeCreationDialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.test1.BuildConfig
import com.example.test1.MacroApp
import com.example.test1.R
import com.example.test1.data.api.IngredientMacro
import com.example.test1.data.api.MacroResult
import com.example.test1.ui.components.MacroPill
import com.example.test1.ui.theme.*
import java.io.File
import kotlin.math.roundToInt

@Composable
private fun formatChatDate(dateStr: String): String {
    val today     = stringResource(R.string.date_today)
    val yesterday = stringResource(R.string.date_yesterday)
    val tomorrow  = stringResource(R.string.date_tomorrow)
    val pattern   = stringResource(R.string.date_chat_format)
    val locale    = LocalConfiguration.current.locales[0]
    val date = java.time.LocalDate.parse(dateStr)
    val now  = java.time.LocalDate.now()
    val formatted = java.time.format.DateTimeFormatter
        .ofPattern(pattern, locale)
        .format(date)
        .replaceFirstChar { it.uppercase() }
    return when {
        date == now              -> "$today · $formatted"
        date == now.minusDays(1) -> "$yesterday · $formatted"
        date == now.plusDays(1)  -> "$tomorrow · $formatted"
        else                     -> formatted
    }
}

private data class EditIngredient(
    val uid: Long = System.nanoTime(),
    val name: String,
    val cal: String,
    val prot: String,
    val carb: String,
    val fat: String
) {
    val calculatedCal: Int get() =
        ((prot.toFloatOrNull() ?: 0f) * 4f +
         (carb.toFloatOrNull() ?: 0f) * 4f +
         (fat.toFloatOrNull() ?: 0f) * 9f).roundToInt()
    val hasMismatch: Boolean get() {
        val entered = cal.toIntOrNull() ?: return false
        return calculatedCal > 0 &&
            kotlin.math.abs(entered - calculatedCal) > calculatedCal * 0.10f
    }
}

@Composable
fun ChatScreen(onNavigateToSettings: () -> Unit) {
    val app      = LocalContext.current.applicationContext as MacroApp
    val vm: ChatViewModel = viewModel {
        ChatViewModel(
            app,
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
    val greetings    = stringArrayResource(R.array.chat_greetings)
    val greeting     = remember { greetings.random() }
    val today        = remember { java.time.LocalDate.now().toString() }
    val isPastDay    = selectedDate < today
    val listState    = rememberLazyListState()
    val keyboard     = LocalSoftwareKeyboardController.current
    val context      = LocalContext.current
    val haptics      = LocalHapticFeedback.current

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size - 1)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.action_undo)
    val discardedText = stringResource(R.string.vm_chat_entry_discarded)
    LaunchedEffect(uiState.pendingUndo) {
        uiState.pendingUndo ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message     = discardedText,
            actionLabel = undoLabel,
            duration    = SnackbarDuration.Short
        )
        when (result) {
            SnackbarResult.ActionPerformed -> vm.undoDiscard()
            SnackbarResult.Dismissed       -> vm.dismissUndo()
        }
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
            title   = { Text(stringResource(R.string.chat_debug_title)) },
            text    = { Text(info, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = vm::clearDebugInfo) { Text(stringResource(R.string.action_close)) } }
        )
    }

    val actionsEnabled = !uiState.isLoading && uiState.editingEntry == null

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
                        .height(44.dp)
                        .padding(start = Spacing.lg, end = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.chat_title),
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
                        onStartAIEdit  = { vm.startAIEdit(msg.foodEntryId!!, msg.id, msg.macroResult!!) },
                        onRepeat       = { vm.repeatEntry(msg.macroResult!!) }
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
                            stringResource(R.string.chat_correcting, editing.originalMacro.name),
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
                                contentDescription = stringResource(R.string.action_cancel),
                                modifier           = Modifier.size(14.dp),
                                tint               = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                stringResource(R.string.action_cancel),
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
                                stringResource(R.string.chat_history_readonly),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column {
                            // Photo attachment preview
                            uiState.pendingImageUri?.let { uri ->
                                AttachmentPreview(uri = uri, onRemove = vm::clearPendingImage)
                            }

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
                                        Icon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.chat_cd_photo))
                                    }
                                    DropdownMenu(
                                        expanded         = showSourceMenu,
                                        onDismissRequest = { showSourceMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text    = { Text(stringResource(R.string.chat_source_camera)) },
                                            onClick = { showSourceMenu = false; launchCamera() }
                                        )
                                        DropdownMenuItem(
                                            text    = { Text(stringResource(R.string.chat_source_gallery)) },
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
                                            if (uiState.editingEntry != null) stringResource(R.string.chat_input_hint_edit) else stringResource(R.string.chat_input_hint),
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
                                    enabled  = (uiState.inputText.isNotBlank() || uiState.pendingImageUri != null) && !uiState.isLoading,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_cd_send))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageThumbnailBubble(uri: Uri) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?.let { bmp ->
                        val maxPx = 512
                        if (bmp.width > maxPx || bmp.height > maxPx) {
                            val scale = maxPx.toFloat() / maxOf(bmp.width, bmp.height)
                            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                        } else bmp
                    }
            }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(
            bitmap             = bitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .size(width = 180.dp, height = 135.dp)
                .clip(RoundedCornerShape(12.dp))
        )
    } else {
        Box(
            modifier         = Modifier
                .size(width = 180.dp, height = 135.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun AttachmentPreview(uri: Uri, onRemove: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?.let { bmp ->
                        val maxPx = 256
                        if (bmp.width > maxPx || bmp.height > maxPx) {
                            val scale = maxPx.toFloat() / maxOf(bmp.width, bmp.height)
                            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                        } else bmp
                    }
            }.getOrNull()
        }
    }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            val imageSize = 56.dp
            if (bitmap != null) {
                Image(
                    bitmap             = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.size(imageSize).clip(AppShapeSm)
                )
            } else {
                Box(
                    modifier         = Modifier
                        .size(imageSize)
                        .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f), AppShapeSm),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
            Text(
                stringResource(R.string.chat_photo_label),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                    modifier           = Modifier.size(16.dp),
                    tint               = MaterialTheme.colorScheme.onSecondaryContainer
                )
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
    onSaveAsRecipe: (FoodItemEntity) -> Unit,
    onDiscard: () -> Unit,
    onEditManually: (MacroResult) -> Unit,
    onStartAIEdit: () -> Unit,
    onRepeat: () -> Unit
) {
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showEditSheet      by remember { mutableStateOf(false) }

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (msg.isUser) {
            Column(horizontalAlignment = Alignment.End) {
                msg.imageUri?.let { uri ->
                    ImageThumbnailBubble(uri = uri)
                    Spacer(Modifier.height(Spacing.xs))
                }
                if (msg.text.isNotBlank() && !(msg.isImageMessage && msg.imageUri != null && msg.text == stringResource(R.string.chat_photo_label))) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(16, 4, 16, 16)
                    ) {
                        Text(
                            msg.text,
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                } else if (msg.isImageMessage && msg.imageUri == null) {
                    // Mensaje de imagen cargado desde DB (sin URI en memoria)
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16, 4, 16, 16)
                    ) {
                        Row(
                            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Icon(
                                Icons.Filled.PhotoCamera,
                                contentDescription = null,
                                modifier           = Modifier.size(16.dp),
                                tint               = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                msg.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
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
                                        MacroPill(MacroType.CALORIES, "${ing.cal.roundToInt()}",   modifier = Modifier.weight(1f))
                                        MacroPill(MacroType.PROTEIN,  "${ing.prot.roundToInt()}g", modifier = Modifier.weight(1f))
                                        MacroPill(MacroType.CARBS,    "${ing.carb.roundToInt()}g", modifier = Modifier.weight(1f))
                                        MacroPill(MacroType.FAT,      "${ing.fat.roundToInt()}g",  modifier = Modifier.weight(1f))
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
                                            stringResource(R.string.chat_total_label),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                            modifier              = Modifier.fillMaxWidth()
                                        ) {
                                            MacroPill(MacroType.CALORIES, "${macro.cal}",   modifier = Modifier.weight(1f))
                                            MacroPill(MacroType.PROTEIN,  "${macro.prot.roundToInt()}g", modifier = Modifier.weight(1f))
                                            MacroPill(MacroType.CARBS,    "${macro.carb.roundToInt()}g", modifier = Modifier.weight(1f))
                                            MacroPill(MacroType.FAT,      "${macro.fat.roundToInt()}g",  modifier = Modifier.weight(1f))
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
                                    MacroPill(MacroType.PROTEIN,  "${macro.prot.roundToInt()}g", modifier = Modifier.weight(1f))
                                    MacroPill(MacroType.CARBS,    "${macro.carb.roundToInt()}g", modifier = Modifier.weight(1f))
                                    MacroPill(MacroType.FAT,      "${macro.fat.roundToInt()}g",  modifier = Modifier.weight(1f))
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
                                        Text(stringResource(R.string.chat_action_discard), style = MaterialTheme.typography.labelSmall)
                                    }
                                    TextButton(
                                        onClick        = { showEditSheet = true },
                                        enabled        = actionsEnabled,
                                        contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Filled.Edit, null, Modifier.size(15.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text(stringResource(R.string.action_edit), style = MaterialTheme.typography.labelSmall)
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
                                        Text(stringResource(R.string.chat_action_adjust), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            Spacer(Modifier.height(2.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                if (macro.fromRecipe != null) {
                                    AssistChip(
                                        onClick     = {},
                                        label       = { Text(stringResource(R.string.chat_from_recipes), style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Bookmark, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    )
                                } else {
                                    SaveAsRecipeButton(macro, onSaveAsRecipe)
                                }
                                if (msg.foodEntryId != null) {
                                    AssistChip(
                                        onClick     = { onRepeat() },
                                        label       = { Text(stringResource(R.string.chat_action_repeat), style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    )
                                }
                            }

                            if (showDiscardConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showDiscardConfirm = false },
                                    title   = { Text(stringResource(R.string.chat_discard_title)) },
                                    text    = { Text(stringResource(R.string.chat_discard_message, macro.name)) },
                                    confirmButton = {
                                        TextButton(
                                            onClick = { showDiscardConfirm = false; onDiscard() },
                                            colors  = ButtonDefaults.textButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) { Text(stringResource(R.string.chat_action_discard)) }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDiscardConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
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
    val hasIngredients       = macro.ingredients.isNotEmpty()
    val ingredientDefaultFmt = stringResource(R.string.chat_ingredient_default)
    var name by remember { mutableStateOf(macro.name) }

    var cal  by remember { mutableStateOf(macro.cal.toString()) }
    var prot by remember { mutableStateOf(macro.prot.toString()) }
    var carb by remember { mutableStateOf(macro.carb.toString()) }
    var fat  by remember { mutableStateOf(macro.fat.toString()) }

    val calculatedCal by remember {
        derivedStateOf {
            ((prot.toFloatOrNull() ?: 0f) * 4f +
             (carb.toFloatOrNull() ?: 0f) * 4f +
             (fat.toFloatOrNull() ?: 0f) * 9f).roundToInt()
        }
    }
    val calMismatch by remember {
        derivedStateOf {
            val entered = cal.toIntOrNull()
            entered != null && calculatedCal > 0 &&
            kotlin.math.abs(entered - calculatedCal) > calculatedCal * 0.10f
        }
    }

    val editIngredients = remember {
        mutableStateListOf(*macro.ingredients.map { ing ->
            EditIngredient(name = ing.name, cal = ing.cal.roundToInt().toString(), prot = ing.prot.toString(), carb = ing.carb.toString(), fat = ing.fat.toString())
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
            Text(stringResource(R.string.chat_edit_title), style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text(stringResource(R.string.chat_dish_name)) },
                singleLine    = true,
                shape         = AppShapeMd,
                modifier      = Modifier.fillMaxWidth()
            )

            if (hasIngredients) {
                Text(
                    stringResource(R.string.chat_ingredients_title),
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
                    Text(stringResource(R.string.chat_add_ingredient))
                }

                val totalCal  = editIngredients.sumOf { it.cal.toIntOrNull() ?: it.calculatedCal }
                val totalProt = editIngredients.sumOf { it.prot.toFloatOrNull()?.toDouble() ?: 0.0 }.toFloat()
                val totalCarb = editIngredients.sumOf { it.carb.toFloatOrNull()?.toDouble() ?: 0.0 }.toFloat()
                val totalFat  = editIngredients.sumOf { it.fat.toFloatOrNull()?.toDouble() ?: 0.0 }.toFloat()

                HorizontalDivider()
                Text(
                    stringResource(R.string.chat_total_calculated),
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
                                name = ing.name.trim().ifBlank { String.format(ingredientDefaultFmt, i + 1) },
                                cal  = (ing.cal.toIntOrNull() ?: ing.calculatedCal).toDouble(),
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
                ) { Text(stringResource(R.string.chat_save_changes), style = MaterialTheme.typography.labelLarge) }

            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(value = prot, onValueChange = { prot = it }, label = { Text(stringResource(R.string.macro_field_protein)) },     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = AppShapeMd, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = carb, onValueChange = { carb = it }, label = { Text(stringResource(R.string.macro_field_carbs_abbr)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = AppShapeMd, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(value = fat,  onValueChange = { fat = it },  label = { Text(stringResource(R.string.macro_field_fat)) },  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = AppShapeMd, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = cal,  onValueChange = { cal = it },  label = { Text(stringResource(R.string.macro_field_kcal)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),  singleLine = true, shape = AppShapeMd, modifier = Modifier.weight(1f))
                }
                if (calMismatch) {
                    Surface(color = FatColor.copy(alpha = 0.12f), shape = AppShapeXs) {
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
                                stringResource(R.string.macro_warning_kcal, calculatedCal),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Button(
                    onClick  = {
                        onSave(
                            MacroResult(
                                name = name.trim().ifBlank { macro.name },
                                cal  = cal.toIntOrNull() ?: macro.cal,
                                prot = prot.toFloatOrNull() ?: macro.prot,
                                carb = carb.toFloatOrNull() ?: macro.carb,
                                fat  = fat.toFloatOrNull()  ?: macro.fat
                            )
                        )
                    },
                    shape    = AppShapeMd,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.chat_save_changes), style = MaterialTheme.typography.labelLarge) }
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
                    label         = { Text(stringResource(R.string.chat_ingredient_label)) },
                    singleLine    = true,
                    shape         = AppShapeMd,
                    modifier      = Modifier.weight(1f)
                )
                if (canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint               = MaterialTheme.colorScheme.error,
                            modifier           = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                OutlinedTextField(value = ingredient.prot, onValueChange = { onUpdate(ingredient.copy(prot = it)) }, label = { Text(stringResource(R.string.macro_field_protein_short)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = AppShapeMd, modifier = Modifier.weight(1f))
                OutlinedTextField(value = ingredient.carb, onValueChange = { onUpdate(ingredient.copy(carb = it)) }, label = { Text(stringResource(R.string.macro_field_carbs_short)) },   keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = AppShapeMd, modifier = Modifier.weight(1f))
                OutlinedTextField(value = ingredient.fat,  onValueChange = { onUpdate(ingredient.copy(fat  = it)) }, label = { Text(stringResource(R.string.macro_field_fat_short)) },    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = AppShapeMd, modifier = Modifier.weight(1f))
                OutlinedTextField(value = ingredient.cal,  onValueChange = { onUpdate(ingredient.copy(cal  = it)) }, label = { Text(stringResource(R.string.macro_field_kcal)) },          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),  singleLine = true, shape = AppShapeMd, modifier = Modifier.weight(1f))
            }
            if (ingredient.hasMismatch) {
                Surface(color = FatColor.copy(alpha = 0.12f), shape = AppShapeXs) {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.sm, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null,
                            modifier = Modifier.size(12.dp), tint = FatColor)
                        Text(
                            stringResource(R.string.chat_ingredient_kcal_hint, ingredient.calculatedCal),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveAsRecipeButton(macro: MacroResult, onSave: (FoodItemEntity) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val app = LocalContext.current.applicationContext as MacroApp

    AssistChip(
        onClick     = { showDialog = true },
        label       = { Text(stringResource(R.string.chat_save_as_recipe), style = MaterialTheme.typography.labelSmall) },
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
