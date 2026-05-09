package com.example.test1.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun recipeDao(): RecipeDao
    abstract fun dailyGoalDao(): DailyGoalDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE recipes ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE recipes ADD COLUMN usageCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE recipes ADD COLUMN lastUsedAt INTEGER")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "macro_tracker.db")
                    .addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
