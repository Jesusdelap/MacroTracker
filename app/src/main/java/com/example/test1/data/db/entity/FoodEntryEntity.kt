package com.example.test1.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "food_entries")
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val name: String,
    val kcal: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val ingredientsJson: String? = null
)
