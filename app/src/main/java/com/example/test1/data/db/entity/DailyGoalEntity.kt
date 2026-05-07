package com.example.test1.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_goals")
data class DailyGoalEntity(
    @PrimaryKey val id: Int = 1,
    val kcal: Int = 2000,
    val protein: Int = 150,
    val carbs: Int = 200,
    val fat: Int = 65
)
