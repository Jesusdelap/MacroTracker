package com.example.test1.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test1.data.api.FoodChatResponse
import com.example.test1.data.api.GeminiService
import com.example.test1.data.api.IngredientMacro
import com.example.test1.data.api.MacroResult
import com.example.test1.data.api.RateLimitException
import com.example.test1.data.db.entity.ChatMessageEntity
import com.example.test1.data.db.entity.FoodEntryEntity
import com.example.test1.data.db.entity.RecipeEntity
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

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isUser: Boolean,
    val macroResult: MacroResult? = null,
    val isImageMessage: Boolean = false,
    val foodEntryId: Long? = null
)

data class EditingEntry(
    val entryId: Long,
    val messageId: Long,
    val originalMacro: MacroResult
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val debugInfo: String? = null,
    val editingEntry: EditingEntry? = null
)

class ChatViewModel(
    private val foodRepository: FoodRepository,
    private val recipeRepository: RecipeRepository,
    private val geminiService: GeminiService,
    private val sharedDate: StateFlow<String>,
    private val chatMessageRepository: ChatMessageRepository,
    private val maxChatDays: Int = 365
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val savedRecipes = recipeRepository.getAllRecipes()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val pendingHistory = mutableListOf<Pair<String, String>>()
    private var pendingImageBase64: String? = null
    private val editingHistory = mutableListOf<Pair<String, String>>()

    private val messageJson = Json { ignoreUnknownKeys = true }

    init {
        // Borrar mensajes más antiguos que maxChatDays al iniciar
        viewModelScope.launch {
            val cutoff = LocalDate.now().minusDays(maxChatDays.toLong()).toString()
            chatMessageRepository.deleteOlderThan(cutoff)
        }

        // Limpiar estado transitorio cuando cambia la fecha
        viewModelScope.launch {
            sharedDate.drop(1).collect {
                pendingHistory.clear()
                pendingImageBase64 = null
                editingHistory.clear()
                _uiState.update { it.copy(editingEntry = null, isLoading = false, error = null) }
            }
        }

        // Fuente de verdad: DB reactiva por fecha
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

    // Conversión entidad → modelo UI
    private fun ChatMessageEntity.toChatMessage(): ChatMessage = ChatMessage(
        id = id,
        text = text,
        isUser = isUser,
        macroResult = macroResultJson?.let {
            try { messageJson.decodeFromString<MacroResult>(it) } catch (_: Exception) { null }
        },
        isImageMessage = isImageMessage,
        foodEntryId = foodEntryId
    )

    // Conversión modelo UI → entidad
    private fun ChatMessage.toEntity(date: String): ChatMessageEntity = ChatMessageEntity(
        id = id,
        date = date,
        text = text,
        isUser = isUser,
        macroResultJson = macroResult?.let { messageJson.encodeToString(it) },
        isImageMessage = isImageMessage,
        foodEntryId = foodEntryId,
        timestamp = id
    )

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text, error = null) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isLoading) return

        val editingEntry = _uiState.value.editingEntry
        if (editingEntry != null) {
            handleAIEditMessage(text, editingEntry)
            return
        }

        _uiState.update { it.copy(inputText = "", isLoading = true) }

        val userMsg = ChatMessage(id = System.currentTimeMillis(), text = text, isUser = true)
        pendingHistory.add("user" to text)
        val historySnapshot = pendingHistory.toList()

        viewModelScope.launch {
            chatMessageRepository.insert(userMsg.toEntity(sharedDate.value))

            val recipeContext = buildRecipeContext()
            val imageBase64 = pendingImageBase64
            val result = if (imageBase64 != null) {
                geminiService.chatFoodWithImage(imageBase64, textHistory = historySnapshot, recipeContext = recipeContext)
            } else {
                geminiService.chatFood(historySnapshot, recipeContext)
            }
            if (result.isFailure) { handleError(result.exceptionOrNull()!!); return@launch }
            handleResponse(result.getOrThrow(), historySnapshot)
        }
    }

    private fun handleAIEditMessage(text: String, editingEntry: EditingEntry) {
        _uiState.update { it.copy(inputText = "", isLoading = true) }

        val userMsg = ChatMessage(id = System.currentTimeMillis(), text = text, isUser = true)

        if (editingHistory.isEmpty()) {
            val orig = editingEntry.originalMacro
            val ctx = "Tengo registrado \"${orig.name}\" (${orig.cal} kcal, ${orig.prot}g proteína, " +
                "${orig.carb}g carbohidratos, ${orig.fat}g grasa). Quiero corregir: $text"
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
                    id = System.currentTimeMillis(),
                    text = "Registro actualizado ✓",
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
                    id = System.currentTimeMillis(),
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
            val base64 = withContext(Dispatchers.IO) { compressToBase64(uri, context) }
            if (base64 == null) {
                val errMsg = ChatMessage(
                    id = System.currentTimeMillis(),
                    text = "No se pudo procesar la imagen.",
                    isUser = false
                )
                chatMessageRepository.insert(errMsg.toEntity(sharedDate.value))
                return@launch
            }

            pendingImageBase64 = base64
            pendingHistory.clear()

            val photoMsg = ChatMessage(
                id = System.currentTimeMillis(),
                text = "📷 Foto enviada",
                isUser = true,
                isImageMessage = true
            )
            chatMessageRepository.insert(photoMsg.toEntity(sharedDate.value))
            _uiState.update { it.copy(isLoading = true) }

            val recipeContext = buildRecipeContext()
            val result = geminiService.chatFoodWithImage(base64, recipeContext = recipeContext)
            if (result.isFailure) { handleError(result.exceptionOrNull()!!); return@launch }
            handleResponse(result.getOrThrow(), emptyList())
        }
    }

    private suspend fun handleResponse(response: FoodChatResponse, historySnapshot: List<Pair<String, String>>) {
        when {
            response.error == "no_food" -> {
                resetConversation()
                val aiMsg = ChatMessage(
                    id = System.currentTimeMillis(),
                    text = "No detecté ningún alimento en la imagen. Intenta con otra foto.",
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
                    id = System.currentTimeMillis(),
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
                    id = System.currentTimeMillis(),
                    text = response.question,
                    isUser = false
                )
                chatMessageRepository.insert(aiMsg.toEntity(sharedDate.value))
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun handleError(e: Throwable) {
        resetConversation()
        val msg = when (e) {
            is RateLimitException -> e.message!!
            else -> "Error al procesar: ${e.message ?: "Inténtalo de nuevo"}"
        }
        val aiMsg = ChatMessage(id = System.currentTimeMillis(), text = msg, isUser = false)
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
    }

    fun discardEntry(entryId: Long, messageId: Long) {
        viewModelScope.launch {
            foodRepository.deleteById(entryId)
            chatMessageRepository.updateMessage(messageId, "Registro descartado", null, null)
        }
        _uiState.update { state ->
            state.copy(
                editingEntry = if (state.editingEntry?.entryId == entryId) null else state.editingEntry
            )
        }
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
            id = System.currentTimeMillis(),
            text = "¿Qué quieres corregir? Describe los cambios y los ajustaré.",
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

    private fun buildRecipeContext(): String =
        savedRecipes.value.takeIf { it.isNotEmpty() }?.let { list ->
            "Recetas guardadas del usuario:\n" +
            list.joinToString("\n") { r ->
                "- ${r.name}: ${r.kcalPerServing} kcal, ${r.protein}g prot, ${r.carbs}g carb, ${r.fat}g grasa"
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

    fun saveAsRecipe(recipe: RecipeEntity) {
        viewModelScope.launch { recipeRepository.insert(recipe) }
    }
}
