package com.example.test1.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class FoodItemType   { RECIPE, PRODUCT }
enum class ServingMode    { PER_SERVING, PER_100G }
enum class FoodItemSource { MANUAL, FATSECRET, USDA }

@Entity(tableName = "food_items")
data class FoodItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val itemType: String = FoodItemType.RECIPE.name,
    val source: String = FoodItemSource.MANUAL.name,
    val brand: String? = null,
    val barcode: String? = null,
    val fatSecretId: String? = null,
    val usdaFdcId: String? = null,
    val servings: Float = 1f,
    val servingMode: String = ServingMode.PER_SERVING.name,
    val kcalPerServing: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val ingredients: String = "",
    val aiChatJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val usageCount: Int = 0,
    val lastUsedAt: Long? = null
)

val FoodItemEntity.isPer100g: Boolean
    get() = servingMode == ServingMode.PER_100G.name
