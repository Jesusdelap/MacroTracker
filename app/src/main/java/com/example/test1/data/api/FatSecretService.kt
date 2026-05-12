package com.example.test1.data.api

import com.example.test1.BuildConfig
import com.example.test1.data.db.entity.FoodItemSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class FatSecretService(
    private val clientId: String = BuildConfig.FATSECRET_CLIENT_ID,
    private val clientSecret: String = BuildConfig.FATSECRET_CLIENT_SECRET
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cachedToken: String? = null
    @Volatile private var tokenExpiresAt: Long = 0L

    private suspend fun getToken(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cachedToken?.takeIf { now < tokenExpiresAt - 60_000L }?.let { return@withContext it }

        val body = FormBody.Builder()
            .add("grant_type", "client_credentials")
            .add("scope", "basic")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .build()

        val request = Request.Builder()
            .url("https://oauth.fatsecret.com/connect/token")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: error("FatSecret: empty token response")
            if (!response.isSuccessful) error("FatSecret token ${response.code}: ${text.take(200)}")
            val obj = json.parseToJsonElement(text).jsonObject
            val token = obj["access_token"]!!.jsonPrimitive.content
            val expiresIn = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 86_400L
            cachedToken = token
            tokenExpiresAt = now + expiresIn * 1_000L
            token
        }
    }

    suspend fun lookup(barcode: String): Result<BarcodeResult> = runCatching {
        if (clientId.isBlank() || clientSecret.isBlank())
            throw BarcodeLookupException(BarcodeLookupError.Configuration, "FatSecret credentials not configured")
        val token = try {
            getToken()
        } catch (e: IOException) {
            throw BarcodeLookupException(BarcodeLookupError.Network, "Network error while connecting to FatSecret", e)
        } catch (e: Exception) {
            throw BarcodeLookupException(BarcodeLookupError.ServiceUnavailable, e.message ?: "FatSecret unavailable", e)
        }
        val foodId = findIdByBarcode(barcode, token)
            ?: throw BarcodeLookupException(BarcodeLookupError.NotFound, "Barcode $barcode not found in FatSecret")
        try {
            getFoodDetails(foodId, barcode, token)
        } catch (e: IOException) {
            throw BarcodeLookupException(BarcodeLookupError.Network, "Network error while loading FatSecret details", e)
        } catch (e: BarcodeLookupException) {
            throw e
        } catch (e: Exception) {
            throw BarcodeLookupException(BarcodeLookupError.ServiceUnavailable, e.message ?: "FatSecret unavailable", e)
        }
    }

    private suspend fun findIdByBarcode(barcode: String, token: String): String? =
        withContext(Dispatchers.IO) {
            val url = "https://platform.fatsecret.com/rest/server.api" +
                      "?method=food.find_id_for_barcode&barcode=$barcode&format=json"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code in 500..599) {
                        throw BarcodeLookupException(BarcodeLookupError.ServiceUnavailable, "FatSecret lookup ${response.code}")
                    }
                    return@withContext null
                }
                val text = response.body?.string() ?: return@withContext null
                val obj = json.parseToJsonElement(text).jsonObject
                obj["error"]?.jsonObject?.let {
                    val message = it["message"]?.jsonPrimitive?.content.orEmpty()
                    val reason = if (message.contains("IP", ignoreCase = true)) {
                        BarcodeLookupError.Configuration
                    } else {
                        BarcodeLookupError.ServiceUnavailable
                    }
                    throw BarcodeLookupException(reason, message.ifBlank { "FatSecret lookup error" })
                }
                obj["food_id"]
                    ?.jsonObject?.get("value")?.jsonPrimitive?.content
            }
        }

    private suspend fun getFoodDetails(
        foodId: String,
        barcode: String,
        token: String
    ): BarcodeResult = withContext(Dispatchers.IO) {
        val url = "https://platform.fatsecret.com/rest/server.api" +
                  "?method=food.get.v4&food_id=$foodId&format=json"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: error("FatSecret: empty food response")
            if (!response.isSuccessful) error("FatSecret food.get ${response.code}")
            val food = json.parseToJsonElement(text).jsonObject["food"]!!.jsonObject

            val name = food["food_name"]!!.jsonPrimitive.content
            val brand = food["brand_name"]?.jsonPrimitive?.content

            val servingEl = food["servings"]!!.jsonObject["serving"]!!
            val servings = if (servingEl is JsonArray) servingEl.map { it.jsonObject }
                           else listOf(servingEl.jsonObject)

            // Prefer serving that is already 100 g
            val per100g = servings.firstOrNull { s ->
                val amt = s["metric_serving_amount"]?.jsonPrimitive?.floatOrNull
                val unit = s["metric_serving_unit"]?.jsonPrimitive?.content?.lowercase()
                amt != null && amt in 99f..101f && unit == "g"
            }

            val serving = per100g ?: servings.first()
            val metric = serving["metric_serving_amount"]?.jsonPrimitive?.floatOrNull
            val scale = when {
                per100g != null            -> 1f
                metric != null && metric > 0 -> 100f / metric
                else                       -> 1f
            }

            fun f(key: String) = (serving[key]?.jsonPrimitive?.floatOrNull ?: 0f) * scale

            BarcodeResult(
                name           = name,
                brand          = brand,
                barcode        = barcode,
                kcalPer100g    = f("calories").toInt(),
                proteinPer100g = f("protein"),
                carbsPer100g   = f("carbohydrate"),
                fatPer100g     = f("fat"),
                source         = FoodItemSource.FATSECRET,
                fatSecretId    = foodId
            )
        }
    }
}
