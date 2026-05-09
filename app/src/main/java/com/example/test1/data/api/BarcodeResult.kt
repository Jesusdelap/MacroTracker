package com.example.test1.data.api

import com.example.test1.data.db.entity.FoodItemEntity
import com.example.test1.data.db.entity.FoodItemSource
import com.example.test1.data.db.entity.FoodItemType
import com.example.test1.data.db.entity.ServingMode

data class BarcodeResult(
    val name: String,
    val brand: String?,
    val barcode: String,
    val kcalPer100g: Int,
    val proteinPer100g: Float,
    val carbsPer100g: Float,
    val fatPer100g: Float,
    val source: FoodItemSource,
    val fatSecretId: String? = null,
    val usdaFdcId: String? = null
)

fun BarcodeResult.toFoodItemEntity(): FoodItemEntity = FoodItemEntity(
    name           = name,
    brand          = brand,
    barcode        = barcode,
    itemType       = FoodItemType.PRODUCT.name,
    source         = source.name,
    servingMode    = ServingMode.PER_100G.name,
    servings       = 1f,
    kcalPerServing = kcalPer100g,
    protein        = proteinPer100g,
    carbs          = carbsPer100g,
    fat            = fatPer100g,
    fatSecretId    = fatSecretId,
    usdaFdcId      = usdaFdcId
)
