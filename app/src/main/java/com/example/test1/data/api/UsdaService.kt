package com.example.test1.data.api

import com.example.test1.BuildConfig
import com.example.test1.data.db.entity.FoodItemSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class UsdaService(
    private val apiKey: String = BuildConfig.USDA_API_KEY
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookup(barcode: String): Result<BarcodeResult> = runCatching {
        withContext(Dispatchers.IO) {
            val url = "https://api.nal.usda.gov/fdc/v1/foods/search" +
                "?query=$barcode&dataType=Branded&pageSize=5&api_key=$apiKey"
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: error("USDA: empty response")
                if (!response.isSuccessful) error("USDA ${response.code}: ${text.take(200)}")

                val foods = json.parseToJsonElement(text).jsonObject["foods"]?.jsonArray
                    ?: error("USDA: missing foods array")

                val stripped = barcode.trimStart('0')
                val food = foods.map { it.jsonObject }.firstOrNull { item ->
                    item["gtinUpc"]?.jsonPrimitive?.content?.trimStart('0') == stripped
                } ?: error("Barcode $barcode not found in USDA")

                val fdcId = food["fdcId"]!!.jsonPrimitive.long.toString()
                val name = food["description"]!!.jsonPrimitive.content
                val brand = food["brandOwner"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                val nutrients = food["foodNutrients"]?.jsonArray ?: JsonArray(emptyList())

                fun nutrient(id: Int): Float =
                    nutrients.firstOrNull { it.jsonObject["nutrientId"]?.jsonPrimitive?.intOrNull == id }
                        ?.jsonObject?.get("value")?.jsonPrimitive?.floatOrNull ?: 0f

                BarcodeResult(
                    name           = name,
                    brand          = brand,
                    barcode        = barcode,
                    kcalPer100g    = nutrient(1008).toInt(),
                    proteinPer100g = nutrient(1003),
                    carbsPer100g   = nutrient(1005),
                    fatPer100g     = nutrient(1004),
                    source         = FoodItemSource.USDA,
                    usdaFdcId      = fdcId
                )
            }
        }
    }
}
