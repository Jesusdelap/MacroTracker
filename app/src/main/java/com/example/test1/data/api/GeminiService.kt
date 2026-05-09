package com.example.test1.data.api

import com.example.test1.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.concurrent.TimeUnit

class RateLimitException(val debugBody: String = "") : Exception("Rate limit reached")

class GeminiService {

    private val language = Locale.getDefault().displayLanguage

    private val responseCache = java.util.concurrent.ConcurrentHashMap<String, FoodChatResponse>(64)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun chatFood(
        history: List<Pair<String, String>>,
        recipeContext: String = ""
    ): Result<FoodChatResponse> = runCatching {
        val cacheKey = if (history.size == 1 && history[0].first == "user")
            history[0].second.lowercase().trim() + "|" + recipeContext
        else null

        cacheKey?.let { responseCache[it] }?.let { return@runCatching it }

        val response = withExponentialBackoff {
            val basePrompt = GeminiPrompts.food(language)
            val prompt = if (recipeContext.isBlank()) basePrompt else "$basePrompt\n\n$recipeContext"
            val body = buildJsonObject {
                putJsonArray("contents") {
                    history.forEach { (role, text) ->
                        addJsonObject {
                            put("role", role)
                            putJsonArray("parts") { addJsonObject { put("text", text) } }
                        }
                    }
                }
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { addJsonObject { put("text", prompt) } }
                }
                putJsonObject("generationConfig") {
                    put("responseMimeType", "application/json")
                }
            }.toString()
            json.decodeFromString<FoodChatResponse>(callGemini(body))
        }

        if (cacheKey != null && response.asMacroResult != null) {
            if (responseCache.size >= 100) responseCache.clear()
            responseCache[cacheKey] = response
        }
        response
    }

    suspend fun chatFoodWithImage(
        imageBase64: String,
        mimeType: String = "image/jpeg",
        textHistory: List<Pair<String, String>> = emptyList(),
        recipeContext: String = ""
    ): Result<FoodChatResponse> = runCatching {
        withExponentialBackoff {
            val basePrompt = GeminiPrompts.imageFood(language)
            val prompt = if (recipeContext.isBlank()) basePrompt
                         else "$basePrompt\n\n$recipeContext"
            val body = buildJsonObject {
                putJsonArray("contents") {
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            addJsonObject {
                                putJsonObject("inline_data") {
                                    put("mime_type", mimeType)
                                    put("data", imageBase64)
                                }
                            }
                        }
                    }
                    textHistory.forEach { (role, text) ->
                        addJsonObject {
                            put("role", role)
                            putJsonArray("parts") { addJsonObject { put("text", text) } }
                        }
                    }
                }
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { addJsonObject { put("text", prompt) } }
                }
                putJsonObject("generationConfig") {
                    put("responseMimeType", "application/json")
                }
            }.toString()
            json.decodeFromString<FoodChatResponse>(callGemini(body))
        }
    }

    suspend fun chatRecipe(history: List<Pair<String, String>>): Result<String> = runCatching {
        withExponentialBackoff {
            val body = buildJsonObject {
                putJsonArray("contents") {
                    history.forEach { (role, text) ->
                        addJsonObject {
                            put("role", role)
                            putJsonArray("parts") { addJsonObject { put("text", text) } }
                        }
                    }
                }
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { addJsonObject { put("text", GeminiPrompts.recipe(language)) } }
                }
            }.toString()
            callGemini(body)
        }
    }

    private suspend fun callGemini(requestBody: String): String = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent" +
                  "?key=${BuildConfig.GEMINI_API_KEY}"
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: error("Empty response from Gemini")
            if (response.code == 429) throw RateLimitException("HTTP 429\n${body.take(600)}")
            if (!response.isSuccessful || body.contains("RESOURCE_EXHAUSTED"))
                error("HTTP ${response.code}: ${body.take(400)}")

            json.parseToJsonElement(body)
                .jsonObject["candidates"]!!
                .jsonArray[0]
                .jsonObject["content"]!!
                .jsonObject["parts"]!!
                .jsonArray[0]
                .jsonObject["text"]!!
                .jsonPrimitive.content
        }
    }

    private suspend fun <T> withExponentialBackoff(
        maxAttempts: Int = 4,
        block: suspend () -> T
    ): T {
        var delayMs = 2_000L
        var lastException: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                if (e !is RateLimitException) throw e
                lastException = e
                if (attempt < maxAttempts - 1) {
                    delay(delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(30_000L)
                }
            }
        }
        throw lastException ?: RateLimitException()
    }

}