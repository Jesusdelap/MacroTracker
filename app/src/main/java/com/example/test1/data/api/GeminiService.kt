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
import java.util.concurrent.TimeUnit

class RateLimitException(val debugBody: String = "") : Exception(
    "Límite de peticiones alcanzado. Espera un momento e inténtalo de nuevo."
)

class GeminiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun chatFood(
        history: List<Pair<String, String>>,
        recipeContext: String = ""
    ): Result<FoodChatResponse> = runCatching {
        withExponentialBackoff {
            val prompt = if (recipeContext.isBlank()) FOOD_PROMPT else "$FOOD_PROMPT\n\n$recipeContext"
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
    }

    suspend fun chatFoodWithImage(
        imageBase64: String,
        mimeType: String = "image/jpeg",
        textHistory: List<Pair<String, String>> = emptyList(),
        recipeContext: String = ""
    ): Result<FoodChatResponse> = runCatching {
        withExponentialBackoff {
            val prompt = if (recipeContext.isBlank()) IMAGE_FOOD_PROMPT
                         else "$IMAGE_FOOD_PROMPT\n\n$recipeContext"
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
                    putJsonArray("parts") { addJsonObject { put("text", RECIPE_PROMPT) } }
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
            val body = response.body?.string() ?: error("Respuesta vacía de Gemini")
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

    companion object {
        private val IMAGE_FOOD_PROMPT = """
            Eres un asistente de nutrición. El usuario te envía una foto de lo que ha comido.
            Analiza la imagen y estima los macros directamente. Si no puedes determinar el tamaño exacto, usa una ración media habitual.
            Si la imagen no contiene ningún alimento reconocible: {"error":"no_food"}
            Si contiene comida, responde con uno de estos formatos JSON:
            - Alimento simple: {"name":"nombre","cal":número,"prot":número,"carb":número,"fat":número}
            - Plato con ingredientes identificables: {"name":"nombre del plato","cal":número,"prot":número,"carb":número,"fat":número,"ingredients":[{"name":"ingrediente","cal":número,"prot":número,"carb":número,"fat":número},...]}
              Los totales son la suma de los ingredientes.
            Los números son enteros o con máximo un decimal.
        """.trimIndent()

        private val FOOD_PROMPT = """
            Eres un asistente de nutrición. El usuario describe lo que ha comido.
            Si se proporcionan recetas guardadas y la descripción coincide, usa esos valores exactos.

            CRITERIO PARA ESTIMAR O PREGUNTAR:
            - ESTIMA SIEMPRE cuando el alimento es identificable, aunque falte la cantidad exacta → usa ración típica.
            - ESTIMA SIEMPRE cuando el usuario menciona ingredientes o cantidades aproximadas (ej: "un scoop", "100ml", "un plato").
            - PREGUNTA solo si el tipo de alimento es genuinamente ambiguo y sus macros varían drásticamente según la respuesta.
              Ejemplos válidos de pregunta: el usuario solo dice "comí en un restaurante" sin más detalle.
              Ejemplos inválidos: preguntar cuánto de algo cuando ya dio ingredientes o cantidades.

            RESPONDE con un JSON en uno de estos formatos:
            1) Alimento simple: {"name":"nombre","cal":número,"prot":número,"carb":número,"fat":número}
            2) Plato con varios ingredientes identificables: {"name":"nombre del plato","cal":número,"prot":número,"carb":número,"fat":número,"ingredients":[{"name":"ingrediente","cal":número,"prot":número,"carb":número,"fat":número},...]}
               Los macros totales son la suma de los ingredientes.
            3) Solo si es imposible identificar el alimento: {"question":"pregunta concisa en español"}

            Los números son enteros o con máximo un decimal.
        """.trimIndent()

        private val RECIPE_PROMPT = """
            Eres un experto en nutrición ayudando al usuario a crear una receta para una app de seguimiento de macros.
            Tu objetivo es recopilar el nombre de la receta e ingredientes con cantidades para calcular los macros totales.
            Mantén una conversación natural y amigable. Haz preguntas si necesitas más datos.
            Cuando tengas suficiente información, calcula los macros totales y responde así:
            Escribe un breve resumen en texto natural, luego en una línea nueva escribe exactamente:
            RECETA_JSON:{"name":"nombre","cal":350,"prot":25.0,"carb":40.0,"fat":8.5}
            Después pregunta si quiere ajustar algo.
            No uses markdown. Los números son enteros o con máximo un decimal. "cal" son kcal totales de la receta completa.
        """.trimIndent()
    }
}