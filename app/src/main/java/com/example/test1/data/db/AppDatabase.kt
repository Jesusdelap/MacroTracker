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
import com.example.test1.data.db.dao.FoodItemDao
import com.example.test1.data.db.entity.ChatMessageEntity
import com.example.test1.data.db.entity.DailyGoalEntity
import com.example.test1.data.db.entity.FoodEntryEntity
import com.example.test1.data.db.entity.FoodItemEntity

@Database(
    entities = [FoodEntryEntity::class, FoodItemEntity::class, DailyGoalEntity::class, ChatMessageEntity::class],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun foodItemDao(): FoodItemDao
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

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE food_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        itemType TEXT NOT NULL DEFAULT 'RECIPE',
                        source TEXT NOT NULL DEFAULT 'MANUAL',
                        brand TEXT,
                        barcode TEXT,
                        fatSecretId TEXT,
                        usdaFdcId TEXT,
                        servings REAL NOT NULL DEFAULT 1.0,
                        servingMode TEXT NOT NULL DEFAULT 'PER_SERVING',
                        kcalPerServing INTEGER NOT NULL,
                        protein REAL NOT NULL,
                        carbs REAL NOT NULL,
                        fat REAL NOT NULL,
                        ingredients TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        isFavorite INTEGER NOT NULL DEFAULT 0,
                        usageCount INTEGER NOT NULL DEFAULT 0,
                        lastUsedAt INTEGER
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO food_items (id, name, itemType, source, servings, servingMode,
                        kcalPerServing, protein, carbs, fat, ingredients,
                        createdAt, isFavorite, usageCount, lastUsedAt)
                    SELECT id, name, 'RECIPE', 'MANUAL', servings,
                        CASE WHEN isPer100g = 1 THEN 'PER_100G' ELSE 'PER_SERVING' END,
                        kcalPerServing, protein, carbs, fat, ingredients,
                        createdAt, isFavorite, usageCount, lastUsedAt
                    FROM recipes
                """.trimIndent())
                database.execSQL("DROP TABLE recipes")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN imagePath TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "macro_tracker.db")
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
