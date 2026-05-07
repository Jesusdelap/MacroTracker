package com.example.test1.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val servings: Float = 1f,
    val kcalPerServing: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val ingredients: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isPer100g: Boolean = false
)
