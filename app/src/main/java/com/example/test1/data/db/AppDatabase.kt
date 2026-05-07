package com.example.test1.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.test1.data.db.dao.ChatMessageDao
import com.example.test1.data.db.dao.DailyGoalDao
import com.example.test1.data.db.dao.FoodEntryDao
import com.example.test1.data.db.dao.RecipeDao
import com.example.test1.data.db.entity.ChatMessageEntity
import com.example.test1.data.db.entity.DailyGoalEntity
import com.example.test1.data.db.entity.FoodEntryEntity
import com.example.test1.data.db.entity.RecipeEntity

@Database(
    entities = [FoodEntryEntity::class, RecipeEntity::class, DailyGoalEntity::class, ChatMessageEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun recipeDao(): RecipeDao
    abstract fun dailyGoalDao(): DailyGoalDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "macro_tracker.db")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
