package com.example.test1.ui.chat

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.test1.R
import com.example.test1.data.api.FoodChatResponse
import com.example.test1.data.api.GeminiService
import com.example.test1.data.api.IngredientMacro
import com.example.test1.data.api.MacroResult
import com.example.test1.data.api.RateLimitException
import com.example.test1.data.db.entity.ChatMessageEntity
import com.example.test1.data.db.entity.FoodEntryEntity
import com.example.test1.data.db.entity.FoodItemEntity
import com.example.test1.data.repository.ChatMessageRepository
import com.example.test1.data.repository.FoodRepository
import com.example.test1.data.repository.RecipeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

data class ChatMessage(
    val id: Long = 0L,
    val text: String,
    val isUser: Boolean,
    val macroResult: MacroResult? = null,
    val isImageMessage: Boolean = false,
    val foodEntryId: Long? = null,
    val imagePath: String? = null,
    val imageUri: Uri? = null
)

data class EditingEntry(
    val entryId: Long,
    val messageId: Long,
    val originalMacro: MacroResult
)

data class PendingUndo(
    val entry: FoodEntryEntity,
    val messageId: Long,
    val messageName: String,
    val macroResultJson: String?
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val debugInfo: String? = null,
    val editingEntry: EditingEntry? = null,
    val pendingImageUri: Uri? = null,
    val pendingUndo: PendingUndo? = null
)

class ChatViewModel(
    application: Application,
    private val foodRepository: FoodRepository,
    private val recipeRepository: RecipeRepository,
    private val geminiService: GeminiService,
    private val sharedDate: StateFlow<String>,
    private val chatMessageRepository: ChatMessageRepository,
    private val maxChatDays: Int = 365
) : AndroidViewModel(application) {

    private fun str(resId: Int) = getApplication<Application>().getString(resId)
    private fun str(resId: Int, vararg args: Any) = getApplication<Application>().getString(resId, *args)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val savedRecipes = recipeRepository.getAllRecipes()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val pendingHistory = mutableListOf<Pair<String, String>>()
    private var pendingImageBase64: String? = null
    private var pendingImagePath: String? = null
    private val editingHistory = mutableListOf<Pair<String, String>>()

    private val messageJson = Json { ignoreUnknownKeys = true }

    init {
        viewModelScope.launch {
            val cutoff = LocalDate.now().minusDays(maxChatDays.toLong()).toString()
            chatMessageRepository.deleteOlderThan(cutoff)
        }

        viewModelScope.launch {
            sharedDate.drop(1).collect {
                pendingHistory.clear()
                pendingImageBase64 = null
                pendingImagePath = null
                editingHistory.clear()
                _uiState.update { it.copy(editingEntry = null, isLoading = false, error = null, pendingImageUri = null, pendingUndo = null) }
            }
        }

        viewModelScope.launch {
            sharedDate.flatMapLatest { date ->
                chatMessageRepository.getMessagesForDate(date)
            }.collect { entities ->
                _uiState.update { current ->
                    current.copy(messages = entities.map { it.toChatMessage() })
                }
            }
        }
    }

    private fun ChatMessageEntity.toChatMessage(): ChatMessage = ChatMessage(
        id = id,
        text = text,
        isUser = isUser,
        macroResult = macroResultJson?.let {
            try { messageJson.decodeFromString<MacroResult>(it) } catch (_: Exception) { null }
        },
        isImageMessage = isImageMessage,
        foodEntryId = foodEntryId,
        imagePath = imagePath,
        imageUri = imagePath?.let { Uri.fromFile(java.io.File(it)) }
    )

    private fun ChatMessage.toEntity(date: String): ChatMessageEntity = ChatMessageEntity(
        id = id,
        date = date,
        text = text,
        isUser = isUser,
        macroResultJson = macroResult?.let { messageJson.encodeToString(it) },
        isImageMessage = isImageMessage,
        foodEntryId = foodEntryId,
        timestamp = System.currentTimeMillis(),
        imagePath = imagePath
    )

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text, error = null) }
    }

    fun sendMessage() {
        val text     = _uiState.value.inputText.trim()
        val hasImage = pendingImageBase64 != null
        if ((text.isBlank() && !hasImage) || _uiState.value.isLoading) return

        val editingEntry = _uiState.value.editingEntry
        if (editingEntry != null) {
            handleAIEditMessage(text, editingEntry)
            return
        }

        val capturedImagePath = pendingImagePath
        pendingImagePath = null
        _uiState.update { it.copy(inputText = "", isLoading = true, pendingImageUri = null) }

        val displayText = if (text.isNotBlank()) text else str(R.string.chat_photo_label)
        val userMsg = ChatMessage(
            text           = displayText,
            isUser         = true,
            isImageMessage = hasImage,
            imagePath      = capturedImagePath,
            imageUri       = capturedImagePath?.let { Uri.fromFile(java.io.File(it)) }
        )
        // Capture history BEFORE adding current text, then add it for future turns
        val priorHistory = pendingHistory.toList()
        if (text.isNotBlank()) pendingHistory.add("user" to text)

        viewModelScope.launch {
            chatMessageRepository.insert(userMsg.toEntity(sharedDate.value))

            val recipeContext = buildRecipeContext()
            val imageBase64 = pendingImageBase64
            pendingImageBase64 = null
            val result = if (imageBase64 != null) {
                geminiService.chatFoodWithImage(
                    imageBase64,
                    userText      = text.ifBlank { null },
                    priorHistory  = priorHistory,
                    recipeContext = recipeContext
                )
            } else {
                geminiService.chatFood(pendingHistory.toList(), recipeContext)
            }
            if (result.isFailure) { handleError(result.exceptionOrNull()!!); return@launch }
            handleResponse(result.getOrThrow(), pendingHistory.toList())
        }
    }

    private fun handleAIEditMessage(text: String, editingEntry: EditingEntry) {
        _uiState.update { it.copy(inputText = "", isLoading = true) }

        val userMsg = ChatMessage(text = text, isUser = true)

        if (editingHistory.isEmpty()) {
            val orig = editingEntry.originalMacro
            val ingredientsCtx = if (orig.ingredients.isEmpty()) {
                ""
            } else {
                val list = orig.ingredients.joinToString(", ") {
                    "${it.name} (${it.cal.toInt()} kcal, ${it.prot}g prot, ${it.carb}g carb, ${it.fat}g fat)"
                }
                " The dish had these ingredients: $list."
            }
            val ctx = "I have logged \"${orig.name}\" (${orig.cal} kcal, ${orig.prot}g protein, " +
                "${orig.carb}g carbs, ${orig.fat}g fat).$ingredientsCtx I want to correct: $text"
            editingHistory.add("user" to ctx)
        } else {
            editingHistory.add("user" to text)
        }
        val historySnapshot = editingHistory.toList()

        viewModelScope.launch {
            chatMessageRepository.insert(userMsg.toEntity(sharedDate.value))
            val recipeContext = buildRecipeContext()
            val result = geminiService.chatFood(historySnapshot, recipeContext)
            if (result.isFailure) {
                editingHistory.clear()
                _uiState.update { it.copy(editingEntry = null) }
                handleError(result.exceptionOrNull()!!)
                return@launch
            }
            handleEditResponse(result.getOrThrow(), editingEntry)
        }
    }

    private suspend fun handleEditResponse(response: FoodChatResponse, editingEntry: EditingEntry) {
        when {
            response.asMacroResult != null -> {
                val newMacro = response.asMacroResult!!
                foodRepository.getById(editingEntry.entryId)?.let { existing ->
                    foodRepository.update(
                        existing.copy(
                            name = newMacro.name,
                            kcal = newMacro.cal,
                            protein = newMacro.prot,
                            carbs = newMacro.carb,
                            fat = newMacro.fat,
                            ingredientsJson = newMacro.ingredients.takeIf { it.isNotEmpty() }
                                ?.let { Json.encodeToString(it) }
                        )
                    )
                }
                chatMessageRepository.updateMessage(
                    editingEntry.messageId,
                    newMacro.name,
                    messageJson.encodeToString(newMacro),
                    editingEntry.entryId
                )
                val confirmMsg = ChatMessage(
                    text = buildUpdateSummary(editingEntry.originalMacro, newMacro),
                    isUser = false
                )
                chatMessageRepository.insert(confirmMsg.toEntity(sharedDate.value))
                editingHistory.clear()
                _uiState.update { it.copy(isLoading = false, editingEntry = null) }
            }
            response.question != null -> {
                val modelJson = buildJsonObject { put("question", response.question) }.toString()
                editingHistory.add("model" to modelJson)
                val aiMsg = ChatMessage(
                    text = response.question,
                    isUser = false
                )
                chatMessageRepository.insert(aiMsg.toEntity(sharedDate.value))
                _uiState.update { it.copy(isLoading = false) }
            }
            else -> {
                editingHistory.clear()
                _uiState.update { it.copy(editingEntry = null, isLoading = false) }
            }
        }
    }

    fun onImageCaptured(uri: Uri, context: Context) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            val (base64, savedPath) = withContext(Dispatchers.IO) {
                val b64 = compressToBase64(uri, context)
                val path = saveImageForDisplay(uri, context)
                b64 to path
            }
            if (base64 != null) {
                pendingImageBase64 = base64
                pendingImagePath = savedPath
                _uiState.update { it.copy(pendingImageUri = uri) }
            }
        }
    }

    private fun saveImageForDisplay(uri: Uri, context: Context): String? = runCatching {
        val dir = java.io.File(context.filesDir, "chat_images").also { it.mkdirs() }
        val file = java.io.File(dir, "img_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val original = BitmapFactory.decodeStream(input) ?: return null
            val maxPx = 480
            val scaled = if (original.width > maxPx || original.height > maxPx) {
                val scale = maxPx.toFloat() / maxOf(original.width, original.height)
                Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
            } else original
            java.io.FileOutputStream(file).use { out ->
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 40, out)
            }
            file.absolutePath
        }
    }.getOrNull()

    fun clearPendingImage() {
        pendingImageBase64 = null
        pendingImagePath = null
        _uiState.update { it.copy(pendingImageUri = null) }
    }

    private suspend fun handleResponse(response: FoodChatResponse, historySnapshot: List<Pair<String, String>>) {
        when {
            response.error == "no_food" -> {
                resetConversation()
                val aiMsg = ChatMessage(
                    text = str(R.string.vm_chat_no_food_in_image),
                    isUser = false
                )
                chatMessageRepository.insert(aiMsg.toEntity(sharedDate.value))
                _uiState.update { it.copy(isLoading = false) }
            }
            response.asMacroResult != null -> {
                val macro = response.asMacroResult!!
                val originalDesc = historySnapshot.firstOrNull { it.first == "user" }?.second
                    ?: _uiState.value.messages.lastOrNull { it.isUser && !it.isImageMessage }?.text
                    ?: macro.name
                resetConversation()
                val entryId = foodRepository.insert(
                    FoodEntryEntity(
                        date = sharedDate.value,
                        name = macro.name,
                        kcal = macro.cal,
                        protein = macro.prot,
                        carbs = macro.carb,
                        fat = macro.fat,
                        description = originalDesc,
                        ingredientsJson = macro.ingredients.takeIf { it.isNotEmpty() }
                            ?.let { Json.encodeToString(it) }
                    )
                )
                val aiMsg = ChatMessage(
                    text = macro.name,
                    isUser = false,
                    macroResult = macro,
                    foodEntryId = entryId
                )
                chatMessageRepository.insert(aiMsg.toEntity(sharedDate.value))
                _uiState.update { it.copy(isLoading = false) }
            }
            response.question != null -> {
                val modelJson = buildJsonObject { put("question", response.question) }.toString()
                pendingHistory.add("model" to modelJson)
                val aiMsg = ChatMessage(
                    text = response.question,
                    isUser = false
                )
                chatMessageRepository.insert(aiMsg.toEntity(sharedDate.value))
                _uiState.update { it.copy(isLoading = false) }
            }
            else -> _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun handleError(e: Throwable) {
        resetConversation()
        val msg = when (e) {
            is RateLimitException -> str(R.string.vm_chat_error_rate_limit)
            else -> str(R.string.vm_chat_error_processing, e.message ?: str(R.string.vm_chat_error_retry))
        }
        val aiMsg = ChatMessage(text = msg, isUser = false)
        chatMessageRepository.insert(aiMsg.toEntity(sharedDate.value))
        _uiState.update {
            it.copy(
                isLoading = false,
                error = msg,
                debugInfo = if (e is RateLimitException) e.debugBody else null
            )
        }
    }

    private fun resetConversation() {
        pendingHistory.clear()
        pendingImageBase64 = null
        _uiState.update { it.copy(pendingImageUri = null) }
    }

    fun discardEntry(entryId: Long, messageId: Long) {
        viewModelScope.launch {
            val entry = foodRepository.getById(entryId)
            val originalMsg = _uiState.value.messages.firstOrNull { it.id == messageId }
            foodRepository.deleteById(entryId)
            chatMessageRepository.updateMessage(messageId, str(R.string.vm_chat_entry_discarded), null, null)
            _uiState.update { state ->
                state.copy(
                    editingEntry = if (state.editingEntry?.entryId == entryId) null else state.editingEntry,
                    pendingUndo = if (entry != null && originalMsg != null) PendingUndo(
                        entry          = entry,
                        messageId      = messageId,
                        messageName    = originalMsg.text,
                        macroResultJson = originalMsg.macroResult?.let { messageJson.encodeToString(it) }
                    ) else null
                )
            }
        }
    }

    fun undoDiscard() {
        val undo = _uiState.value.pendingUndo ?: return
        _uiState.update { it.copy(pendingUndo = null) }
        viewModelScope.launch {
            foodRepository.insert(undo.entry)
            chatMessageRepository.updateMessage(undo.messageId, undo.messageName, undo.macroResultJson, undo.entry.id)
        }
    }

    fun dismissUndo() {
        _uiState.update { it.copy(pendingUndo = null) }
    }

    fun updateEntryManually(entryId: Long, messageId: Long, newMacro: MacroResult) {
        viewModelScope.launch {
            foodRepository.getById(entryId)?.let { existing ->
                foodRepository.update(
                    existing.copy(
                        name = newMacro.name,
                        kcal = newMacro.cal,
                        protein = newMacro.prot,
                        carbs = newMacro.carb,
                        fat = newMacro.fat,
                        ingredientsJson = newMacro.ingredients.takeIf { it.isNotEmpty() }
                            ?.let { Json.encodeToString(it) }
                    )
                )
            }
            chatMessageRepository.updateMessage(
                messageId,
                newMacro.name,
                messageJson.encodeToString(newMacro),
                entryId
            )
        }
    }

    fun startAIEdit(entryId: Long, messageId: Long, macro: MacroResult) {
        editingHistory.clear()
        val promptMsg = ChatMessage(
            text = str(R.string.vm_chat_edit_prompt),
            isUser = false
        )
        viewModelScope.launch {
            chatMessageRepository.insert(promptMsg.toEntity(sharedDate.value))
        }
        _uiState.update { it.copy(editingEntry = EditingEntry(entryId, messageId, macro)) }
    }

    fun cancelAIEdit() {
        editingHistory.clear()
        _uiState.update { it.copy(editingEntry = null) }
    }

    private fun buildUpdateSummary(old: MacroResult, updated: MacroResult): String {
        val lines = mutableListOf<String>()
        if (old.name != updated.name) lines.add("\"${old.name}\" → \"${updated.name}\"")
        val calDiff  = updated.cal  - old.cal
        val protDiff = updated.prot - old.prot
        val carbDiff = updated.carb - old.carb
        val fatDiff  = updated.fat  - old.fat
        fun fmt(diff: Number, unit: String): String {
            val d = diff.toFloat()
            return "${if (d >= 0f) "+" else ""}${d.roundToInt()}$unit"
        }
        lines.add("${updated.cal} kcal (${fmt(calDiff, " kcal")})")
        if (abs(protDiff) >= 0.5f) lines.add("${updated.prot.roundToInt()}g prot (${fmt(protDiff, "g")})")
        if (abs(carbDiff) >= 0.5f) lines.add("${updated.carb.roundToInt()}g carb (${fmt(carbDiff, "g")})")
        if (abs(fatDiff)  >= 0.5f) lines.add("${updated.fat.roundToInt()}g fat (${fmt(fatDiff, "g")})")
        return str(R.string.vm_chat_entry_updated) + "\n" + lines.joinToString("\n")
    }

    private fun buildRecipeContext(): String =
        savedRecipes.value.takeIf { it.isNotEmpty() }?.let { list ->
            "User's saved recipes:\n" +
            list.joinToString("\n") { r ->
                "- ${r.name}: ${r.kcalPerServing} kcal, ${r.protein}g protein, ${r.carbs}g carbs, ${r.fat}g fat"
            }
        } ?: ""

    private suspend fun compressToBase64(uri: Uri, context: Context): String? =
        withContext(Dispatchers.IO) {
            try {
                val original = context.contentResolver.openInputStream(uri)
                    ?.use { BitmapFactory.decodeStream(it) } ?: return@withContext null

                val maxDim = 1024
                val bitmap = if (original.width > maxDim || original.height > maxDim) {
                    val scale = maxDim.toFloat() / maxOf(original.width, original.height)
                    Bitmap.createScaledBitmap(
                        original,
                        (original.width * scale).toInt(),
                        (original.height * scale).toInt(),
                        true
                    )
                } else original

                val output = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, output)
                Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            } catch (_: Exception) { null }
        }

    fun clearDebugInfo() { _uiState.update { it.copy(debugInfo = null) } }

    fun repeatEntry(macro: MacroResult) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            val entryId = foodRepository.insert(
                FoodEntryEntity(
                    date = sharedDate.value,
                    name = macro.name,
                    kcal = macro.cal,
                    protein = macro.prot,
                    carbs = macro.carb,
                    fat = macro.fat,
                    description = str(R.string.vm_chat_repeated_desc, macro.name),
                    ingredientsJson = macro.ingredients.takeIf { it.isNotEmpty() }
                        ?.let { Json.encodeToString(it) }
                )
            )
            val confirmMsg = ChatMessage(
                text = str(R.string.vm_chat_repeated, macro.name),
                isUser = false,
                macroResult = macro,
                foodEntryId = entryId
            )
            chatMessageRepository.insert(confirmMsg.toEntity(sharedDate.value))
        }
    }

    fun saveAsRecipe(recipe: FoodItemEntity) {
        viewModelScope.launch { recipeRepository.insert(recipe) }
    }
}
